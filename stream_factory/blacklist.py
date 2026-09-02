"""黑名单持久化模块

记录被 URL 处理器（如 OCR）判定违规、需要「跳过推流」的 ts 分片源 URL。
命中黑名单的 ts 在源视频缓存阶段直接跳过（不下载、不 OCR），并从重写后的
``index.m3u8`` 中移除，从而让 ffmpeg 不推流该分片。

- **键**：ts 的源绝对 URL 的 md5（同 URL 同内容）；
- **持久化**：``{BLACKLIST_ROOT}/{md5}.json``，原子写（tmp + os.replace）；
- **TTL**：过期惰性失效（过期文件被 ``is_blacklisted`` 当作未命中）。

本模块只依赖 ``stream_factory.config``，不跨模块 import ``media_source``（守「低耦合」）。
"""
import hashlib
import json
import os
import time
from typing import Optional

from stream_factory import config


def _key(ts_url: str) -> str:
    """ts 源 URL 的 md5（黑名单文件名）。"""
    return hashlib.md5(ts_url.encode("utf-8")).hexdigest()


def _path(ts_url: str) -> str:
    return os.path.join(config.BLACKLIST_ROOT, _key(ts_url) + ".json")


def _load(ts_url: str) -> Optional[dict]:
    """读取黑名单条目，损坏/缺失返回 None。"""
    try:
        with open(_path(ts_url), "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None


def is_blacklisted(ts_url: str) -> bool:
    """判断该 ts 是否已命中未过期的黑名单。"""
    meta = _load(ts_url)
    if meta is None or meta.get("expires", 0) <= time.time():
        return False
    return True


def mark(ts_url: str, word: str = "") -> None:
    """把 ts 拉入黑名单（原子写）。"""
    now = time.time()
    entry = {
        "url": ts_url,
        "word": word,
        "ts": now,
        "expires": now + config.BLACKLIST_TTL,
    }
    os.makedirs(config.BLACKLIST_ROOT, exist_ok=True)
    tmp = _path(ts_url) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(entry, f, ensure_ascii=False)
    os.replace(tmp, _path(ts_url))


def clear() -> int:
    """清空黑名单，返回删除的文件数。"""
    if not os.path.isdir(config.BLACKLIST_ROOT):
        return 0
    count = 0
    for name in os.listdir(config.BLACKLIST_ROOT):
        if name.endswith(".json"):
            try:
                os.remove(os.path.join(config.BLACKLIST_ROOT, name))
                count += 1
            except OSError:
                pass
    return count
