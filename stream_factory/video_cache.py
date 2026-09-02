"""源视频缓存模块

把上游源视频（m3u8 播放列表 + 分片 / mp4 直链）缓存到本地磁盘，供后续转流复用，
从而减少重复网络请求与重复下载：

- **长驻连接池**：模块级 ``httpx.AsyncClient`` 懒加载复用，多会话共享同一 TCP 连接池；
- **并发去重**：同一 ``source_url`` 同时仅一个下载任务，其他会话 ``await`` 后复用（双重检查）；
- **保留 HLS 结构**：缓存 m3u8 播放列表、AES-128 解密 key、fMP4 init 分片与媒体分片，播放列表 URI 重写为本地相对引用；
- **TTL 过期**：命中未过期的缓存直接返回本地路径，过期后惰性重新下载。

本模块只依赖 ``stream_factory.config``，不跨模块 import ``media_source``（遵守「低耦合」）。
"""
import asyncio
import hashlib
import json
import logging
import os
import re
import time
from typing import Dict, List, Optional
from urllib.parse import urljoin, urlparse

import httpx

from stream_factory import blacklist
from stream_factory import config

logger = logging.getLogger("stream_factory.video_cache")

# 长驻连接池：所有会话共享同一个 httpx 客户端，复用底层 TCP 连接（懒加载）
_client: Optional[httpx.AsyncClient] = None

# per-key 并发去重锁（仿 media_source/cache.py 的 FileCache）
_locks: Dict[str, asyncio.Lock] = {}
_locks_guard = asyncio.Lock()

# Master playlist 最大递归层级，防止畸形嵌套
_MAX_DEPTH = 3

# URI 属性（#EXT-X-KEY / #EXT-X-MAP 行内 ``URI="..."``）
_URI_ATTR_RE = re.compile(r'URI\s*=\s*"([^"]*)"')


def _get_client() -> httpx.AsyncClient:
    """懒加载长驻 httpx 客户端（连接池），复用 TCP 连接。"""
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(follow_redirects=True)
    return _client


async def close_video_cache() -> None:
    """关闭长驻连接池（服务退出时调用）。"""
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
        _client = None


async def _get_lock(key: str) -> asyncio.Lock:
    """获取 per-key 并发锁（用 ``_locks_guard`` 保护字典并发读写）。"""
    async with _locks_guard:
        lock = _locks.get(key)
        if lock is None:
            lock = asyncio.Lock()
            _locks[key] = lock
        return lock


# ---- 缓存目录 / 元数据 ----
def _cache_dir(url: str) -> str:
    """源 URL 对应的缓存目录：``{VIDEO_CACHE_ROOT}/{md5(url)}/``。"""
    digest = hashlib.md5(url.encode("utf-8")).hexdigest()
    return os.path.join(config.VIDEO_CACHE_ROOT, digest)


def _meta_path(url: str) -> str:
    return os.path.join(_cache_dir(url), "meta.json")


def _local_path(url: str, source_type: str) -> str:
    """命中缓存后返回的本地路径：mp4 → source.mp4；m3u8 → index.m3u8。"""
    if source_type == "mp4":
        return os.path.join(_cache_dir(url), "source.mp4")
    return os.path.join(_cache_dir(url), "index.m3u8")


def _load_meta(url: str) -> Optional[Dict]:
    """读取缓存元数据，损坏/缺失返回 ``None``。"""
    try:
        with open(_meta_path(url), "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None


def _save_meta(url: str, source_type: str) -> None:
    """写入缓存元数据（原子替换，避免写一半的 meta 被读到）。"""
    now = time.time()
    meta = {
        "url": url,
        "source_type": source_type,
        "ts": now,
        "expires": now + config.VIDEO_CACHE_TTL,
    }
    dir_ = _cache_dir(url)
    os.makedirs(dir_, exist_ok=True)
    tmp = os.path.join(dir_, "meta.json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False)
    os.replace(tmp, _meta_path(url))


def _is_hit(url: str, source_type: str) -> bool:
    """判断是否命中未过期缓存（元数据未过期且本地文件存在）。"""
    meta = _load_meta(url)
    if meta is None or meta.get("expires", 0) <= time.time():
        return False
    return os.path.exists(_local_path(url, source_type))


# ---- 主入口 ----
async def ensure_source(
    url: str,
    source_type: str = "m3u8",
    headers: Optional[Dict[str, str]] = None,
    url_handlers: Optional[List] = None,
) -> str:
    """确保源视频已缓存到本地，返回本地路径（未缓存时先下载）。

    命中缓存或下载成功后返回本地文件路径；下载失败 / 嵌套过深 / 其他异常时**降级直连**，
    返回原 ``url``，保持现有 ffmpeg 直连拉流行为不变。

    空 URL 与本地路径（``file://`` / 绝对路径）直接原样返回，无需缓存。
    """
    if not url:
        return url
    # 已是本地文件路径（降级产物 / 手动传本地源），无需缓存
    if url.startswith(("file://", "/")):
        return url

    if _is_hit(url, source_type):
        return _local_path(url, source_type)

    lock = await _get_lock(url)
    async with lock:
        # 双重检查：等锁期间可能已被其他会话缓存
        if _is_hit(url, source_type):
            return _local_path(url, source_type)

        try:
            if source_type == "mp4":
                await _cache_mp4(url, headers)
            else:
                ok = await _cache_m3u8(url, headers, url_handlers)
                if not ok:
                    # 嵌套过深等无法缓存场景：降级直连
                    return url
        except Exception as exc:  # noqa: BLE001 - 任何下载失败都降级直连
            logger.warning("源视频缓存失败，降级直连：%s（%s）", url, exc)
            return url

        _save_meta(url, source_type)
        return _local_path(url, source_type)


# ---- mp4 直链 ----
async def _cache_mp4(url: str, headers: Optional[Dict[str, str]]) -> None:
    """流式下载 mp4 直链到 ``source.mp4``（边下边写，不整体入内存）。"""
    client = _get_client()
    dir_ = _cache_dir(url)
    os.makedirs(dir_, exist_ok=True)
    tmp = os.path.join(dir_, "source.mp4.tmp")
    dst = os.path.join(dir_, "source.mp4")
    async with client.stream("GET", url, headers=headers) as resp:
        resp.raise_for_status()
        with open(tmp, "wb") as f:
            async for chunk in resp.aiter_bytes():
                f.write(chunk)
    os.replace(tmp, dst)


# ---- m3u8 播放列表 + 分片 ----


def _rewrite_uri_attr(
    line: str, base_url: str, seen: Dict[str, str], prefix: str, default_ext: str
) -> tuple:
    """重写含 URI 属性的行（``#EXT-X-KEY`` / ``#EXT-X-MAP``），返回三元组。

    - ``rewritten``：URI 已替换为本地文件名的新行（无 URI 时返回原行）；
    - ``local_name``：本地文件名（无 URI 时为 ``""``）；
    - ``abs_url``：解析后的绝对 URI（无 URI 时为 ``""``）。

    ``seen`` 用于同一绝对 URI 去重（复用同一本地文件），避免重复下载。
    """
    m = _URI_ATTR_RE.search(line)
    if not m or not m.group(1):
        return line, "", ""
    raw_uri = m.group(1)
    abs_url = urljoin(base_url, raw_uri)
    local_name = seen.get(abs_url)
    if local_name is None:
        ext = os.path.splitext(urlparse(abs_url).path)[1] or default_ext
        local_name = f"{prefix}_{len(seen) + 1:04d}{ext}"
        seen[abs_url] = local_name
    return line.replace(raw_uri, local_name), local_name, abs_url


async def _cache_m3u8(
    url: str, headers: Optional[Dict[str, str]], url_handlers: Optional[List] = None
) -> bool:
    """下载 m3u8 源并缓存，返回是否成功（``False`` 表示需降级直连）。

    ``dir_`` 固定用**原始 url** 的缓存目录；Master 递归到 variant 时目录不变，
    保证 ``ensure_source`` 按原始 url 计算出的本地路径与缓存落盘位置一致。
    """
    client = _get_client()
    resp = await client.get(url, headers=headers)
    resp.raise_for_status()
    return await _cache_m3u8_text(
        url, resp.text, headers, depth=0, dir_=_cache_dir(url), url_handlers=url_handlers
    )


async def _cache_m3u8_text(
    base_url: str,
    text: str,
    headers: Optional[Dict[str, str]],
    depth: int,
    dir_: str,
    url_handlers: Optional[List] = None,
) -> bool:
    """解析播放列表文本并缓存 key / 分片，返回是否成功。

    Master 递归选带宽最高的 variant；Media 则把 key（AES-128 解密密钥）、
    init 分片（fMP4）与媒体分片全部下载到本地，播放列表 URI 重写为本地引用，
    使 ffmpeg 完全离线拉流，不再对源站发起重复请求。
    """
    if depth > _MAX_DEPTH:
        logger.warning("m3u8 嵌套层级过深，降级直连：%s", base_url)
        return False

    lines = text.splitlines()

    # Master playlist：选带宽最高的 variant 递归缓存其 media playlist（缓存目录不变）
    variants = _extract_variants(lines)
    if variants:
        sub_url = urljoin(base_url, variants[0])
        client = _get_client()
        resp = await client.get(sub_url, headers=headers)
        resp.raise_for_status()
        return await _cache_m3u8_text(
            sub_url, resp.text, headers, depth + 1, dir_, url_handlers
        )

    # Media playlist：下载 key（AES-128）/ init 分片（fMP4）/ 媒体分片，全部重写为本地引用
    os.makedirs(dir_, exist_ok=True)

    jobs: List[tuple] = []          # (本地文件名, 绝对 URL) —— 待下载文件
    segments: List[tuple] = []      # 媒体分片 (本地文件名, 绝对 URL) —— 供黑名单/OCR 过滤
    rewritten: List[str] = []       # 重写后的播放列表行
    key_uris: Dict[str, str] = {}   # 绝对 key URI → 本地文件名（去重）
    init_uris: Dict[str, str] = {}  # 绝对 init URI → 本地文件名（去重）
    seg_idx = 0
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("#EXT-X-KEY") and "URI=" in stripped:
            # 解密密钥：下载 key，URI 重写为本地文件名（METHOD=NONE 无 URI，走注释分支原样保留）
            new_line, local_name, abs_url = _rewrite_uri_attr(
                line, base_url, key_uris, "key", ".key"
            )
            rewritten.append(new_line)
            if local_name:
                jobs.append((local_name, abs_url))
        elif stripped.startswith("#EXT-X-MAP"):
            # fMP4 init 分片：下载到本地，URI 重写为本地文件名
            new_line, local_name, abs_url = _rewrite_uri_attr(
                line, base_url, init_uris, "init", ".mp4"
            )
            rewritten.append(new_line)
            if local_name:
                jobs.append((local_name, abs_url))
        elif not stripped or stripped.startswith("#"):
            # 其余注释行（#EXTM3U / #EXTINF / #EXT-X-* / 空行）原样保留
            rewritten.append(line)
        else:
            # 媒体分片：下载到本地，行替换为本地文件名
            abs_url = urljoin(base_url, stripped)
            ext = os.path.splitext(urlparse(abs_url).path)[1] or ".ts"
            name = f"segment_{seg_idx:04d}{ext}"
            jobs.append((name, abs_url))
            segments.append((name, abs_url))
            rewritten.append(name)
            seg_idx += 1

    # 命中黑名单的分片：直接跳过（不下载、不 OCR）
    blocked: set = set()
    if url_handlers:
        for name, abs_url in segments:
            if blacklist.is_blacklisted(abs_url):
                blocked.add(name)

    # 并发下载（key / init / 未被拉黑的分片），信号量限流
    sem = asyncio.Semaphore(config.VIDEO_CACHE_CONCURRENCY)

    async def _download(name: str, file_url: str) -> None:
        async with sem:
            client = _get_client()
            tmp = os.path.join(dir_, name + ".tmp")
            dst = os.path.join(dir_, name)
            async with client.stream("GET", file_url, headers=headers) as r:
                r.raise_for_status()
                with open(tmp, "wb") as f:
                    async for chunk in r.aiter_bytes():
                        f.write(chunk)
            os.replace(tmp, dst)

    await asyncio.gather(
        *[_download(name, file_url) for name, file_url in jobs if name not in blocked]
    )

    # 下载后对未被拉黑的分片跑 URL 处理器（如 OCR 违规词检测），命中则拉黑
    if url_handlers:
        async def _check(name: str, abs_url: str) -> Optional[str]:
            local = os.path.join(dir_, name)
            for handler in url_handlers:
                try:
                    if await handler.handle(abs_url, local):
                        blacklist.mark(abs_url, handler.name)
                        return name
                except Exception as exc:  # noqa: BLE001 - 处理器异常一律放行
                    logger.warning(
                        "URL 处理器 %s 异常，放行分片 %s：%s", handler.name, abs_url, exc
                    )
            return None

        hits = await asyncio.gather(
            *[_check(name, abs_url) for name, abs_url in segments if name not in blocked]
        )
        for hit in hits:
            if hit:
                blocked.add(hit)

    # 写重写后的播放列表：跳过被拉黑的分片行（从 index.m3u8 移除 → ffmpeg 不推流）
    with open(os.path.join(dir_, "index.m3u8"), "w", encoding="utf-8") as f:
        for line in rewritten:
            stripped = line.strip()
            if stripped and not stripped.startswith("#") and stripped in blocked:
                continue
            f.write(line + "\n")
    return True


def _extract_variants(lines: List[str]) -> List[str]:
    """从 Master playlist 提取各 variant 的 URI，按带宽降序返回（最高带宽优先）。"""
    variants: List[tuple] = []
    for i, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF"):
            m = re.search(r"BANDWIDTH=(\d+)", line)
            bandwidth = int(m.group(1)) if m else 0
            if i + 1 < len(lines):
                uri = lines[i + 1].strip()
                if uri and not uri.startswith("#"):
                    variants.append((bandwidth, uri))
    variants.sort(key=lambda x: x[0], reverse=True)
    return [uri for _, uri in variants]
