"""处理结果缓存模块

把「去广告转流后的 HLS 输出目录」作为处理结果缓存，按内容寻址复用：
当「源视频 url + 帧滤镜（``filters``）+ 流裁剪/空白（``trims``/``blanks``）」都未变化时，
同一输入产出同一 ``sid``（内容寻址），命中后跳过 ffmpeg 转流，直接复用已生成的 HLS，
从而减少 CPU 密集的去广告处理压力。

- **内容寻址**：``cache_key(req)`` 把规范化的 ``StreamRequest`` 哈希为 16 位十六进制 ``sid``；
- **命中判断**：``is_hit(sid)`` 判断 ``index.m3u8`` 存在且 ``meta.json`` 未过期；
- **完成登记**：``mark_complete(...)`` 在 ffmpeg 正常转流结束后写 ``meta.json``（原子替换）；
- **TTL 过期**：过期后 ``is_hit`` 返回 False，下次同内容请求重新转流覆盖（惰性清理）。

本模块只依赖 ``stream_factory.config`` 与 ``stream_factory.rules``，不跨模块 import
``media_source``（遵守「低耦合」）。
"""
import hashlib
import json
import logging
import os
import time
from typing import Dict, Optional

from stream_factory import config
from stream_factory.rules import StreamRequest

logger = logging.getLogger("stream_factory.process_cache")


def cache_key(req: StreamRequest, extra: str = "") -> str:
    """计算处理结果的缓存键（内容寻址 ``sid``）：规范化 ``StreamRequest`` 的 md5 前 16 位。

    规范化 = ``req.model_dump(mode="json")`` 后按键排序序列化，保证「源 url + 帧滤镜 +
    流裁剪/空白 + headers」完全一致时产出相同 ``sid``；任一变化则 ``sid`` 不同。

    ``extra`` 为额外指纹维度（如 URL 处理器的配置指纹）：URL 处理器会改变输出
    （拉黑分片），但 ``StreamRequest`` 本身不含处理器信息，故需把其指纹拼入哈希，
    否则处理器配置变化时 ``sid`` 不变，会错误复用旧 HLS。
    """
    data = req.model_dump(mode="json")
    if extra:
        data["_url_handlers"] = extra
    raw = json.dumps(data, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.md5(raw.encode("utf-8")).hexdigest()[:16]


def hls_dir(sid: str) -> str:
    """sid 对应的处理缓存目录（即 HLS 输出目录）。"""
    return os.path.join(config.HLS_ROOT, sid)


def _meta_path(sid: str) -> str:
    return os.path.join(hls_dir(sid), "meta.json")


def _load_meta(sid: str) -> Optional[Dict]:
    """读取处理缓存元数据，损坏/缺失返回 ``None``。"""
    try:
        with open(_meta_path(sid), "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None


def is_hit(sid: str) -> bool:
    """判断是否命中未过期的处理缓存（``index.m3u8`` 存在且 ``meta.json`` 未过期）。"""
    meta = _load_meta(sid)
    if meta is None or meta.get("expires", 0) <= time.time():
        return False
    return os.path.exists(os.path.join(hls_dir(sid), "index.m3u8"))


def is_complete(hls_dir_: str) -> bool:
    """判断 HLS 目录是否为完整缓存（存在未过期的 ``meta.json``），供 ``stop`` 决定是否清理。

    无 ``meta.json`` / 已过期视为「半成品」（转流中或已失效），可安全清理。
    """
    try:
        with open(os.path.join(hls_dir_, "meta.json"), "r", encoding="utf-8") as f:
            meta = json.load(f)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return False
    return meta.get("expires", 0) > time.time()


def mark_complete(hls_dir_: str, sid: str, source_url: str = "") -> None:
    """ffmpeg 正常转流结束后登记缓存完成（原子写 ``meta.json``）。"""
    now = time.time()
    meta = {
        "sid": sid,
        "source_url": source_url,
        "ts": now,
        "expires": now + config.PROCESS_CACHE_TTL,
    }
    os.makedirs(hls_dir_, exist_ok=True)
    tmp = os.path.join(hls_dir_, "meta.json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False)
    os.replace(tmp, os.path.join(hls_dir_, "meta.json"))
