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

# 进行中的流式缓存任务（key=source_url）：首分片就绪即返回、后台继续追加剩余分片
_inflight: Dict[str, "_StreamingCache"] = {}

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
    """确保源视频可用，返回 ffmpeg 应读取的路径。

    - **mp4 / 本地路径 / 完整缓存命中**：行为与旧版一致（mp4 先下载完，命中缓存直接返回本地）。
    - **m3u8（未命中）**：默认**边下边推**——首个分片下载（并 OCR）完成即返回本地
      ``index.m3u8``，后台任务继续按序「下载 → OCR → 追加」剩余分片；ffmpeg 读本地 live
      播放列表（无 ``#EXT-X-ENDLIST``）边读边推。首个分片 / key / init 下载失败、嵌套过深
      或就绪超时时**降级直连**，返回原 ``url``（保持旧版 ffmpeg 直连行为）。

    设 ``STREAM_FACTORY_VIDEO_CACHE_STREAMING=0`` 时回退到旧版「全量下载后转流」。

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

        # mp4 直链：无分片，维持旧版「下载完成后返回」（流式仅针对 m3u8 多分片）
        if source_type == "mp4":
            return await _ensure_mp4(url, headers)

        # 回退开关：关闭流式时走旧版全量下载
        if not config.VIDEO_CACHE_STREAMING:
            return await _ensure_legacy_m3u8(url, headers, url_handlers)

        # 流式边下边推：取/建后台任务，等首分片就绪即返回
        job = _inflight.get(url)
        if job is None:
            job = _StreamingCache(url, headers, url_handlers)
            _inflight[url] = job
            job.task = asyncio.create_task(job.run())
        try:
            await asyncio.wait_for(
                job.first_ready.wait(), timeout=config.STREAM_READY_TIMEOUT
            )
        except asyncio.TimeoutError:
            logger.warning("首个分片就绪超时，降级直连：%s", url)
            return url
        if job.degraded:
            return url
        return _local_path(url, source_type)


async def _ensure_mp4(url: str, headers: Optional[Dict[str, str]]) -> str:
    """mp4 直链：下载到本地后返回本地路径；失败降级直连。"""
    try:
        await _cache_mp4(url, headers)
    except Exception as exc:  # noqa: BLE001 - 任何下载失败都降级直连
        logger.warning("mp4 源缓存失败，降级直连：%s（%s）", url, exc)
        return url
    _save_meta(url, "mp4")
    return _local_path(url, "mp4")


async def _ensure_legacy_m3u8(
    url: str,
    headers: Optional[Dict[str, str]],
    url_handlers: Optional[List],
) -> str:
    """旧版 m3u8 缓存（全量下载后返回）；失败 / 嵌套过深降级直连。"""
    try:
        ok = await _cache_m3u8(url, headers, url_handlers)
        if not ok:
            return url
    except Exception as exc:  # noqa: BLE001 - 任何下载失败都降级直连
        logger.warning("源视频缓存失败，降级直连：%s（%s）", url, exc)
        return url
    _save_meta(url, "m3u8")
    return _local_path(url, "m3u8")


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


# ---- 流式边下边推 ----

def _write_playlist(dir_: str, lines: List[str], endlist: bool) -> None:
    """原子写 ``index.m3u8``（重写整个文件 + 可选 ``#EXT-X-ENDLIST``）。

    ffmpeg 正在轮询读取该播放列表，用「写临时文件 + ``os.replace``」原子替换，
    保证其任意时刻读到的是完整一致的内容。
    """
    index = os.path.join(dir_, "index.m3u8")
    tmp = index + ".tmp"
    content = "".join(line + "\n" for line in lines)
    if endlist:
        content += "#EXT-X-ENDLIST\n"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(content)
    os.replace(tmp, index)


async def _download_retry(
    dir_: str, name: str, file_url: str, headers: Optional[Dict[str, str]]
) -> None:
    """下载单个文件到 ``dir_/name``（流式写盘，带重试）；重试耗尽后抛出最后一次异常。"""
    client = _get_client()
    tmp = os.path.join(dir_, name + ".tmp")
    dst = os.path.join(dir_, name)
    last_exc: Optional[Exception] = None
    for attempt in range(config.VIDEO_CACHE_SEGMENT_RETRY + 1):
        try:
            async with client.stream("GET", file_url, headers=headers) as r:
                r.raise_for_status()
                with open(tmp, "wb") as f:
                    async for chunk in r.aiter_bytes():
                        f.write(chunk)
            os.replace(tmp, dst)
            return
        except Exception as exc:  # noqa: BLE001
            last_exc = exc
            if attempt < config.VIDEO_CACHE_SEGMENT_RETRY:
                logger.warning(
                    "分片下载失败（重试 %d/%d）：%s",
                    attempt + 1,
                    config.VIDEO_CACHE_SEGMENT_RETRY,
                    file_url,
                )
    if last_exc is not None:
        raise last_exc
    raise RuntimeError(f"下载失败：{file_url}")


class _StreamingCache:
    """单个源 URL 的流式缓存任务：边下载分片边写 live ``index.m3u8``。

    - ``first_ready``：首个可播分片追加完成（或降级）时置位，``ensure_source`` 据此返回；
    - ``degraded``：首个分片 / key / init 下载失败时为 True，``ensure_source`` 降级直连；
    - ``run()``：后台任务体，按序「下载 → OCR → 追加」，全部完成后写源缓存 meta 并清理。
    """

    def __init__(
        self,
        url: str,
        headers: Optional[Dict[str, str]],
        url_handlers: Optional[List],
    ):
        self.url = url
        self.headers = headers
        self.url_handlers = url_handlers or []
        self.first_ready = asyncio.Event()
        self.degraded = False
        self.task: Optional[asyncio.Task] = None

    async def run(self) -> None:
        """后台任务：流式下载 + 追加分片；成功写 meta，失败置降级标志。"""
        try:
            ok = await self._stream_m3u8(self.url, headers=self.headers, depth=0)
        except Exception as exc:  # noqa: BLE001 - 任何失败降级直连
            logger.warning("源视频流式缓存失败，降级直连：%s（%s）", self.url, exc)
            ok = False
        if ok:
            # 全部完成：写源缓存元数据（标记完整缓存，供后续会话命中）
            _save_meta(self.url, "m3u8")
        else:
            self.degraded = True
        # 无论成败都唤醒等待者并清理：成功时首分片已置位（此处幂等），失败时据此降级
        self.first_ready.set()
        _inflight.pop(self.url, None)

    async def _stream_m3u8(
        self, url: str, headers: Optional[Dict[str, str]], depth: int
    ) -> bool:
        """下载并流式缓存 m3u8，返回是否成功（False 表示首分片/key/init 失败需降级）。

        Master playlist 递归选带宽最高的 variant，缓存目录始终用**原始 url** 的目录；
        Media playlist 则交给 :meth:`_stream_media` 边下边追加。
        """
        if depth > _MAX_DEPTH:
            logger.warning("m3u8 嵌套层级过深，降级直连：%s", url)
            return False

        client = _get_client()
        resp = await client.get(url, headers=headers)
        resp.raise_for_status()
        text = resp.text

        variants = _extract_variants(text.splitlines())
        if variants:
            sub_url = urljoin(url, variants[0])
            resp = await client.get(sub_url, headers=headers)
            resp.raise_for_status()
            return await self._stream_m3u8(sub_url, headers=headers, depth=depth + 1)

        return await self._stream_media(text, url, headers)

    async def _stream_media(
        self, text: str, base_url: str, headers: Optional[Dict[str, str]]
    ) -> bool:
        """解析 Media playlist，先下 key/init，再按序「下载分片 → OCR → 追加」。

        ``#EXTINF`` 恒紧邻其后分片 URI，故以**首个 ``#EXTINF`` 为分界**：之前为全局头
        （``#EXTM3U``/``#EXT-X-TARGETDURATION``/默认 KEY/MAP 等），之后按「标签块 + URI」
        拆成有序分片；``#EXTINF`` 与分片 URI 一起追加，源自带 ``#EXT-X-ENDLIST`` 一律剔除
        （由 :func:`_write_playlist` 按流式进度管理，保证 ffmpeg 以 live 模式边读边推）。
        """
        dir_ = _cache_dir(self.url)
        os.makedirs(dir_, exist_ok=True)

        lines = text.splitlines()

        # 首个 #EXTINF 的位置作为「全局头 / 分片段」的分界
        first_inf = -1
        for i, line in enumerate(lines):
            if line.strip().startswith("#EXTINF"):
                first_inf = i
                break

        header_lines: List[str] = []      # 全局头（写入顺序 = 最终播放列表顺序）
        key_init_jobs: List[tuple] = []   # (本地文件名, 绝对 URL) —— key / init，提前下载
        key_uris: Dict[str, str] = {}
        init_uris: Dict[str, str] = {}

        def rewrite_keymap(line: str) -> str:
            """KEY/MAP 行：URI 重写为本地引用并登记提前下载，返回重写后的行。"""
            if line.strip().startswith("#EXT-X-KEY") and "URI=" in line:
                new_line, local_name, abs_url = _rewrite_uri_attr(
                    line, base_url, key_uris, "key", ".key"
                )
                if local_name:
                    key_init_jobs.append((local_name, abs_url))
                return new_line
            if line.strip().startswith("#EXT-X-MAP"):
                new_line, local_name, abs_url = _rewrite_uri_attr(
                    line, base_url, init_uris, "init", ".mp4"
                )
                if local_name:
                    key_init_jobs.append((local_name, abs_url))
                return new_line
            return line

        # 全局头：首个 #EXTINF 之前的行（KEY/MAP 重写并提前下载；剔除源 ENDLIST、空行）
        header_source = lines if first_inf == -1 else lines[:first_inf]
        for line in header_source:
            stripped = line.strip()
            if not stripped or stripped == "#EXT-X-ENDLIST":
                continue
            header_lines.append(rewrite_keymap(line))

        # 分片段：把「标签块（#EXTINF 及 DISCONTINUITY/KEY/MAP 等） + URI」拆成有序分片
        segments: List[tuple] = []   # (标签行列表, 本地分片名, 绝对 URL) —— 有序
        seg_idx = 0
        tags: List[str] = []
        for line in lines[first_inf:] if first_inf != -1 else []:
            stripped = line.strip()
            if not stripped:
                continue
            if stripped.startswith("#"):
                tags.append(rewrite_keymap(line))
            else:
                abs_url = urljoin(base_url, stripped)
                ext = os.path.splitext(urlparse(abs_url).path)[1] or ".ts"
                name = f"segment_{seg_idx:04d}{ext}"
                segments.append((tags, name, abs_url))
                tags = []
                seg_idx += 1

        # 1) 先下载 key / init（解密先决条件，失败降级直连）
        try:
            await asyncio.gather(
                *[
                    _download_retry(dir_, name, file_url, headers)
                    for name, file_url in key_init_jobs
                ]
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning("key/init 分片下载失败，降级直连：%s（%s）", self.url, exc)
            return False

        # 2) 写初始 index.m3u8（全局头，无分片、无 ENDLIST），ffmpeg 读之为 live
        _write_playlist(dir_, header_lines, endlist=False)

        # 3) 按序处理媒体分片：下载 → OCR → 追加；首个分片追加后置位 first_ready
        first_appended = False
        for tag_lines, name, abs_url in segments:
            if blacklist.is_blacklisted(abs_url):
                continue  # 命中黑名单：不下载、不 OCR、不追加
            try:
                await _download_retry(dir_, name, abs_url, headers)
            except Exception as exc:  # noqa: BLE001 - 分片失败跳过，live 容错
                logger.warning("分片下载失败，跳过：%s（%s）", abs_url, exc)
                continue
            if await self._check_segment(abs_url, os.path.join(dir_, name)):
                continue  # OCR 命中拉黑：不追加
            header_lines.extend(tag_lines)
            header_lines.append(name)
            _write_playlist(dir_, header_lines, endlist=False)
            if not first_appended:
                first_appended = True
                self.first_ready.set()

        # 4) 全部完成：追加 ENDLIST 结束 live（ffmpeg 读到后正常退出）
        _write_playlist(dir_, header_lines, endlist=True)
        if not first_appended:
            # 无任何可用分片（全被黑名单/失败跳过，或源无分片）：告知等待者
            self.first_ready.set()
        return True

    async def _check_segment(self, abs_url: str, local_path: str) -> bool:
        """对已下载分片跑 URL 处理器（OCR），命中返回 True（拉黑跳过）。"""
        for handler in self.url_handlers:
            try:
                if await handler.handle(abs_url, local_path):
                    blacklist.mark(abs_url, handler.name)
                    return True
            except Exception as exc:  # noqa: BLE001 - 处理器异常一律放行
                logger.warning(
                    "URL 处理器 %s 异常，放行分片 %s：%s", handler.name, abs_url, exc
                )
        return False
