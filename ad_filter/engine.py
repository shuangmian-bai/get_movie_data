"""去广告处理引擎

把「传入 m3u8 → 逐分片检测 → 丢弃 / 去水印 / 代理 → 重写 m3u8」串起来，
并按内容寻址做结果幂等复用（同一源 + 同一检测器配置 → 同一 sid → 复用结果）。

- 下载 m3u8（Master 选最高带宽 variant；分片 URI 相对/绝对由 ``m3u8`` 模块统一解析）；
- 逐分片下载到临时目录 → 检测器分类 → 按类处理：
  - ``full`` → 丢弃（重写后的 m3u8 不输出该分片块）；
  - ``watermark`` → 去水印器落盘到 ``{sid}/`` 目录，引用 ``file/{name}``；
  - ``none`` → 不落盘，引用 ``proxy/{name}``（本服务代理上游，带防盗链头）；
- 写 ``index.m3u8`` + ``meta.json``（分片名 → 上游 URL 映射，供代理查询）。

本地只保存「修改了画面的分片」（去水印后的），符合省盘目标。
"""
import asyncio
import hashlib
import json
import logging
import os
import shutil
import tempfile
import time
from typing import Dict, List, Optional, Tuple
from urllib.parse import urljoin

import httpx

from ad_filter import config
from ad_filter import m3u8 as m3u8_parser
from ad_filter.models import (
    DetectionResult,
    ProcessRequest,
    ProcessResult,
    ProcessStats,
)
from ad_filter.proxy import _get_client

logger = logging.getLogger("ad_filter.engine")

# per-sid 并发锁（避免同一内容并发重复处理）
_locks: Dict[str, asyncio.Lock] = {}
_locks_guard = asyncio.Lock()


def compute_sid(req: ProcessRequest, detectors: List) -> str:
    """内容寻址 sid：源 m3u8 + headers + 检测器指纹 → md5 前 16 位。"""
    fp = "|".join(d.fingerprint() for d in detectors)
    raw = f"{req.m3u8_url}\n{json.dumps(req.headers, sort_keys=True)}\n{fp}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()[:16]


def sid_dir(sid: str) -> str:
    """会话输出目录：``{OUTPUT_ROOT}/{sid}/``。"""
    return os.path.join(config.OUTPUT_ROOT, sid)


def load_meta(sid: str) -> Optional[dict]:
    """读取会话元数据（供代理端点查询分片 URL）。"""
    try:
        with open(os.path.join(sid_dir(sid), "meta.json"), "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None


async def _get_lock(sid: str) -> asyncio.Lock:
    """获取 per-sid 并发锁（用 ``_locks_guard`` 保护字典并发读写）。"""
    async with _locks_guard:
        lock = _locks.get(sid)
        if lock is None:
            lock = asyncio.Lock()
            _locks[sid] = lock
        return lock


async def process(req: ProcessRequest, detectors: List, remover) -> ProcessResult:
    """处理一次去广告请求，返回处理结果（含处理后 m3u8 地址）。"""
    sid = compute_sid(req, detectors)
    dir_ = sid_dir(sid)
    os.makedirs(dir_, exist_ok=True)

    lock = await _get_lock(sid)
    async with lock:
        # 幂等复用：已有处理结果直接返回
        meta = load_meta(sid)
        if meta and os.path.exists(os.path.join(dir_, "index.m3u8")):
            return _result(req, sid, ProcessStats(**meta.get("stats", {})))
        meta = await _run(req, sid, dir_, detectors, remover)
    return _result(req, sid, ProcessStats(**meta["stats"]))


def _result(req: ProcessRequest, sid: str, stats: ProcessStats) -> ProcessResult:
    """组装处理结果（playlist_url 为同源相对路径，前端可直接播）。"""
    return ProcessResult(
        sid=sid,
        playlist_url=f"/ad_filter/{sid}/index.m3u8",
        stats=stats,
    )


async def _fetch_text(client: httpx.AsyncClient, url: str, headers: Dict[str, str]) -> str:
    """下载 m3u8 文本。"""
    resp = await client.get(url, headers=headers)
    resp.raise_for_status()
    return resp.text


async def _download(
    client: httpx.AsyncClient, url: str, headers: Dict[str, str], dst: str
) -> None:
    """流式下载单个分片到本地（边下边写，不整体入内存）。"""
    tmp = dst + ".tmp"
    async with client.stream("GET", url, headers=headers) as r:
        r.raise_for_status()
        with open(tmp, "wb") as f:
            async for chunk in r.aiter_bytes():
                f.write(chunk)
    os.replace(tmp, dst)


async def _detect_any(
    detectors: List, segment_path: str, segment_url: str
) -> DetectionResult:
    """顺序跑检测器链，返回首个非 none 的结果；全 none 则放行。"""
    for d in detectors:
        try:
            r = await d.detect(segment_path, segment_url)
        except Exception as exc:  # noqa: BLE001 - 检测器异常一律放行
            logger.warning("检测器 %s 异常，放行分片 %s：%s", d.name, segment_url, exc)
            continue
        if r.ad_type != "none":
            return r
    return DetectionResult()


def _write_playlist(dir_: str, lines: List[str]) -> None:
    """原子写 ``index.m3u8``（追加 ENDLIST，作为点播）。"""
    index = os.path.join(dir_, "index.m3u8")
    tmp = index + ".tmp"
    content = "".join(line + "\n" for line in lines)
    content += "#EXT-X-ENDLIST\n"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(content)
    os.replace(tmp, index)


def _write_meta(dir_: str, meta: dict) -> None:
    """原子写会话元数据。"""
    tmp = os.path.join(dir_, "meta.json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False)
    os.replace(tmp, os.path.join(dir_, "meta.json"))


async def _run(
    req: ProcessRequest, sid: str, dir_: str, detectors: List, remover
) -> dict:
    """执行一次完整处理，返回元数据 dict。"""
    client = _get_client()

    # 1) 下载并定位 Media 播放列表（Master 递归选最高带宽 variant）
    playlist_url = req.m3u8_url
    text = await _fetch_text(client, playlist_url, req.headers)
    if m3u8_parser.is_master(text):
        variants = m3u8_parser.extract_variants(text.splitlines())
        if not variants:
            raise RuntimeError("Master 播放列表无可用 variant")
        playlist_url = urljoin(playlist_url, variants[0])
        text = await _fetch_text(client, playlist_url, req.headers)

    # 2) 解析 Media 播放列表
    header_lines, segments = m3u8_parser.parse_media(text, playlist_url)

    stats = ProcessStats(total=len(segments))
    tmpdir = tempfile.mkdtemp(prefix="adfilter_")
    try:
        sem = asyncio.Semaphore(config.SEGMENT_CONCURRENCY)

        async def _handle(idx: int, tags: List[str], abs_url: str):
            async with sem:
                ext = os.path.splitext(abs_url.split("?")[0])[1] or ".ts"
                name = f"segment_{idx:04d}{ext}"
                local = os.path.join(tmpdir, name)
                try:
                    await _download(client, abs_url, req.headers, local)
                except Exception as exc:  # noqa: BLE001 - 下载失败按正常处理（代理）
                    logger.warning("分片下载失败，按正常处理（代理）：%s（%s）", abs_url, exc)
                    return ("proxy", name, abs_url)
                try:
                    result = await _detect_any(detectors, local, abs_url)
                    if result.ad_type == "full":
                        return (None, name, abs_url)  # 丢弃
                    if result.ad_type == "watermark":
                        out = os.path.join(dir_, name)
                        await remover.remove(local, result.boxes, out)
                        return ("file", name, abs_url)
                    return ("proxy", name, abs_url)
                finally:
                    try:
                        os.remove(local)
                    except OSError:
                        pass

        results = await asyncio.gather(
            *[
                _handle(i, tags, url)
                for i, (tags, url) in enumerate(segments)
            ]
        )

        # 3) 按原顺序重写 m3u8
        out_lines = list(header_lines)
        seg_map: Dict[str, str] = {}
        for (tags, _), (kind, name, abs_url) in zip(segments, results):
            if kind is None:
                stats.full += 1
                continue
            out_lines.extend(tags)
            if kind == "file":
                stats.watermark += 1
                out_lines.append(f"file/{name}")
            else:
                stats.passed += 1
                out_lines.append(f"proxy/{name}")
                seg_map[name] = abs_url

        _write_playlist(dir_, out_lines)
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)

    # 4) 写会话元数据
    meta = {
        "sid": sid,
        "m3u8_url": req.m3u8_url,
        "headers": req.headers,
        "segments": seg_map,
        "stats": stats.model_dump(),
        "ts": time.time(),
    }
    _write_meta(dir_, meta)
    return meta
