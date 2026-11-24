"""去广告处理引擎（分片级按需处理）

渐进式：``process`` **秒回** m3u8（所有分片指向动态端点 ``segment/{name}``），
播放器拉哪个分片就处理哪个；已处理分片按内容寻址缓存，跨会话复用。
不再等整片处理完才起播。

- 全广告（full）→ 动态端点返回一段极短空白 ts（播放器无缝跳过）；
- 水印（watermark）→ 去水印后落盘分片缓存，动态端点返回该文件；
- 正常（none）→ 动态端点代理透传上游；
- 处理失败 → 降级代理（不阻断播放）。

分片缓存键 = ``md5(分片绝对 URL + 检测器指纹)``，目录
``{OUTPUT_ROOT}/segments/{key}/``（``result.json`` + 去水印文件）。
"""
import asyncio
import hashlib
import json
import logging
import os
import shutil
import tempfile
import time
from dataclasses import dataclass, field
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


# ---------------------------------------------------------------------------
# 会话结构
# ---------------------------------------------------------------------------


@dataclass
class _Segment:
    """一个分片：名称、上游绝对 URL、原始 m3u8 标签行。"""

    name: str
    abs_url: str
    tags: List[str] = field(default_factory=list)


@dataclass
class _Session:
    """会话内存态（可随时从 meta.json 重建）。"""

    sid: str
    segments: List[_Segment]
    headers: Dict[str, str]
    position: int = 0  # 播放位置（当前分片索引），由前端进度上报驱动预取


# 会话缓存（重启后靠 meta.json 重建）
_sessions: Dict[str, _Session] = {}
_sessions_guard = asyncio.Lock()

# per-sid 锁（同一内容并发只解析一次）
_locks: Dict[str, asyncio.Lock] = {}
_locks_guard = asyncio.Lock()

# per-分片锁（同一分片并发只处理一次）
_seg_locks: Dict[str, asyncio.Lock] = {}
_seg_locks_guard = asyncio.Lock()

# 空白 ts（full 分片占位）缓存路径
_blank_path: Optional[str] = None


# ---------------------------------------------------------------------------
# 基础工具
# ---------------------------------------------------------------------------


def compute_sid(req: ProcessRequest, detectors: List) -> str:
    """内容寻址 sid：源 m3u8 + headers + 检测器指纹 → md5 前 16 位。"""
    fp = "|".join(d.fingerprint() for d in detectors)
    raw = f"{req.m3u8_url}\n{json.dumps(req.headers, sort_keys=True)}\n{fp}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()[:16]


def sid_dir(sid: str) -> str:
    """会话目录：``{OUTPUT_ROOT}/{sid}/``。"""
    return os.path.join(config.OUTPUT_ROOT, sid)


def load_meta(sid: str) -> Optional[dict]:
    """读取会话元数据（分片清单 / headers / base_url）。"""
    try:
        with open(os.path.join(sid_dir(sid), "meta.json"), "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None


def segment_cache_dir(key: str) -> str:
    """分片缓存目录：``{OUTPUT_ROOT}/segments/{key}/``。"""
    return os.path.join(config.OUTPUT_ROOT, "segments", key)


def segment_cache_key(abs_url: str, detectors: List) -> str:
    """分片缓存键：分片绝对 URL + 检测器配置指纹。"""
    fp = "|".join(d.fingerprint() for d in detectors)
    raw = f"{abs_url}\n{fp}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


async def _get_lock(sid: str) -> asyncio.Lock:
    async with _locks_guard:
        lock = _locks.get(sid)
        if lock is None:
            lock = asyncio.Lock()
            _locks[sid] = lock
        return lock


async def _get_seg_lock(key: str) -> asyncio.Lock:
    async with _seg_locks_guard:
        lock = _seg_locks.get(key)
        if lock is None:
            lock = asyncio.Lock()
            _seg_locks[key] = lock
        return lock


def blank_ts_path(width: int = 0, height: int = 0) -> str:
    """生成（并缓存）与主视频同分辨率的 1 帧黑帧 ts。

    full（整屏广告）分片用它占位：**分辨率与主视频一致**，播放器不会因尺寸变化
    重建解码器；1 帧（约 0.04 秒）视觉上几乎无感。首次播放兜底用；
    已检测出 full 的分片在 playlist 渲染时会被直接移除（见 render_playlist）。
    """
    w = max(2, int(width or 320))
    h = max(2, int(height or 180))
    # 编码要求宽高为偶数
    w -= w % 2
    h -= h % 2
    path = os.path.join(config.OUTPUT_ROOT, f"_blank_{w}x{h}.ts")
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    import av  # 按需导入
    import numpy as np

    av.logging.set_level(av.logging.ERROR)
    os.makedirs(config.OUTPUT_ROOT, exist_ok=True)
    out = av.open(path, "w", format="mpegts")
    try:
        stream = out.add_stream("libx264", rate=25)
        stream.width, stream.height, stream.pix_fmt = w, h, "yuv420p"
        stream.options = {"preset": "ultrafast", "crf": "51", "tune": "zerolatency"}
        frame = av.VideoFrame.from_ndarray(
            np.zeros((h, w, 3), dtype=np.uint8), format="bgr24"
        )
        frame.pts = 0
        for packet in stream.encode(frame):
            out.mux(packet)
        for packet in stream.encode(None):
            out.mux(packet)
    finally:
        out.close()
    return path


def _probe_video_size(segment_path: str) -> Tuple[int, int]:
    """探测分片宽高（失败返回 0,0）。"""
    try:
        import av

        av.logging.set_level(av.logging.ERROR)
        with av.open(segment_path) as c:
            st = c.streams.video[0]
            return int(st.width or 0), int(st.height or 0)
    except Exception:  # noqa: BLE001
        return 0, 0


def skip_stats(sid: str, detectors: List) -> Optional[dict]:
    """统计已确认的广告分片：full（整屏跳过）数量与秒数、watermark（去水印）数量。

    用于前端提示「已跳过 N 段广告（X 秒）」，让用户理解进度为何前移。
    """
    meta = load_meta(sid)
    if not meta:
        return None
    total = int(meta.get("total", 0))
    full = 0
    full_seconds = 0.0
    watermark = 0
    for item in meta.get("segments") or []:
        if not isinstance(item, dict):
            continue
        url = item.get("url", "")
        if not url:
            continue
        cached = _read_result(segment_cache_dir(segment_cache_key(url, detectors)))
        if cached is None:
            continue
        if cached[0] == "full":
            full += 1
            full_seconds += _extinf_seconds(list(item.get("tags") or []))
        elif cached[0] == "watermark":
            watermark += 1
    return {
        "total": total,
        "full": full,
        "full_seconds": round(full_seconds, 1),
        "watermark": watermark,
    }


def render_playlist(sid: str, detectors: List) -> Optional[str]:
    """按当前缓存状态渲染 playlist：已确认 full（整屏广告）的分片直接移除。

    首次播放时多数分片还没检测，playlist 含全部分片（播放到广告片由端点兜底）；
    第二次播放（或后台推进检测完成后）再请求，广告分片已被剔除，播放器直接跳过，
    没有黑帧、没有时间轴跳变。
    """
    meta = load_meta(sid)
    if not meta:
        return None
    raw = meta.get("segments") or []
    header_lines: List[str] = []
    # 保留原 m3u8 头（EXT-X-VERSION/TARGETDURATION 等），从旧 index 里取；取不到用默认
    index = os.path.join(sid_dir(sid), "index.m3u8")
    try:
        with open(index, "r", encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                if line.startswith("#EXT-X-ENDLIST"):
                    break
                if line.startswith("#EXTINF") or line.startswith("segment/"):
                    break
                header_lines.append(line)
    except OSError:
        header_lines = ["#EXTM3U", "#EXT-X-VERSION:3", "#EXT-X-PLAYLIST-TYPE:VOD"]

    out_lines = list(header_lines)
    # 去掉可能重复的 PLAYLIST-TYPE（保留一份）
    for item in raw:
        if not isinstance(item, dict):
            continue
        name = item.get("name", "")
        url = item.get("url", "")
        tags = list(item.get("tags") or [])
        if not name or not url:
            continue
        key = segment_cache_key(url, detectors)
        cached = _read_result(segment_cache_dir(key))
        if cached is not None and cached[0] == "full":
            continue  # 已确认整屏广告：从 playlist 移除
        out_lines.extend(tags)
        out_lines.append(f"segment/{name}")
    out_lines.append("#EXT-X-ENDLIST")
    return "".join(line + "\n" for line in out_lines)


# ---------------------------------------------------------------------------
# 会话准备（秒回）
# ---------------------------------------------------------------------------


async def process(req: ProcessRequest, detectors: List, remover) -> ProcessResult:
    """解析 m3u8 并**立即返回**播放列表地址；分片处理延迟到播放时按需进行。"""
    sid = compute_sid(req, detectors)
    dir_ = sid_dir(sid)
    os.makedirs(dir_, exist_ok=True)

    lock = await _get_lock(sid)
    async with lock:
        meta = load_meta(sid)
        index = os.path.join(dir_, "index.m3u8")
        if not (meta and os.path.exists(index)):
            meta = await _prepare(req, sid, dir_)

    stats = ProcessStats(total=int(meta.get("total", 0)))
    return ProcessResult(
        sid=sid,
        playlist_url=f"/ad_filter/{sid}/index.m3u8",
        stats=stats,
    )


async def _prepare(req: ProcessRequest, sid: str, dir_: str) -> dict:
    """下载播放列表、生成会话与动态分片 index.m3u8，写 meta。"""
    client = _get_client()
    playlist_url = req.m3u8_url
    text = await _fetch_text(client, playlist_url, req.headers)
    if m3u8_parser.is_master(text):
        variants = m3u8_parser.extract_variants(text.splitlines())
        if not variants:
            raise RuntimeError("Master 播放列表无可用 variant")
        playlist_url = urljoin(playlist_url, variants[0])
        text = await _fetch_text(client, playlist_url, req.headers)

    header_lines, segments = m3u8_parser.parse_media(text, playlist_url)

    segs: List[_Segment] = []
    out_lines = list(header_lines)
    for idx, (tags, abs_url) in enumerate(segments):
        ext = os.path.splitext(abs_url.split("?")[0])[1] or ".ts"
        name = f"segment_{idx:04d}{ext}"
        segs.append(_Segment(name=name, abs_url=abs_url, tags=list(tags)))
        out_lines.extend(tags)
        out_lines.append(f"segment/{name}")

    _write_playlist(dir_, out_lines)
    meta = {
        "sid": sid,
        "m3u8_url": req.m3u8_url,
        "base_url": req.base_url,
        "headers": req.headers,
        "total": len(segs),
        "segments": [
            {"name": s.name, "url": s.abs_url, "tags": s.tags} for s in segs
        ],
        "ts": time.time(),
    }
    _write_meta(dir_, meta)

    async with _sessions_guard:
        _sessions[sid] = _Session(sid=sid, segments=segs, headers=req.headers)
    return meta


async def _ensure_session(sid: str, meta: dict) -> _Session:
    """从 meta 重建会话（重启后/多进程场景）。"""
    async with _sessions_guard:
        sess = _sessions.get(sid)
        if sess is not None:
            return sess
        raw = meta.get("segments") or []
        segs: List[_Segment] = []
        if isinstance(raw, dict):
            # 旧格式：{name: url}（无 tags）
            for name, url in raw.items():
                segs.append(_Segment(name=name, abs_url=url, tags=[]))
        else:
            for item in raw:
                if not isinstance(item, dict):
                    continue
                segs.append(
                    _Segment(
                        name=item.get("name", ""),
                        abs_url=item.get("url", ""),
                        tags=list(item.get("tags") or []),
                    )
                )
        sess = _Session(sid=sid, segments=segs, headers=meta.get("headers") or {})
        _sessions[sid] = sess
        return sess


# ---------------------------------------------------------------------------
# 分片按需处理与缓存
# ---------------------------------------------------------------------------


async def handle_segment(
    sid: str, name: str, detectors: List, remover, prefetch: bool = False
) -> Optional[Tuple[str, Optional[str]]]:
    """处理（或复用缓存）单个分片。

    :param prefetch: True 表示由预取任务调用（不再级联触发新的预取）。
    :return: ``("full", None)`` / ``("watermark", 本地路径)`` / ``("none", None)``；
             会话或分片不存在返回 ``None``。
    """
    meta = load_meta(sid)
    if not meta:
        return None
    sess = await _ensure_session(sid, meta)
    seg = next((s for s in sess.segments if s.name == name), None)
    if seg is None:
        return None

    key = segment_cache_key(seg.abs_url, detectors)
    cdir = segment_cache_dir(key)
    cached = _read_result(cdir)
    if cached is not None:
        if not prefetch:
            idx = _index_of(sess, name)
            if idx is not None and sess.position < idx:
                sess.position = idx
            ensure_advance(sid, detectors, remover)
        return cached

    lock = await _get_seg_lock(key)
    async with lock:
        cached = _read_result(cdir)
        if cached is not None:
            result = cached
        else:
            result = await _process_segment(seg, sess.headers, detectors, remover, cdir)
    if not prefetch:
        idx = _index_of(sess, name)
        if idx is not None and sess.position < idx:
            sess.position = idx  # 用户直接拉到这里：从该片往后推进
        ensure_advance(sid, detectors, remover)
    return result


# 预取任务（避免同一分片重复排队）
_prefetch_tasks: Dict[str, asyncio.Task] = {}

# 全局分片处理闸门：主请求与预取共享，防止并发处理打满 CPU
_process_sem: Optional[asyncio.Semaphore] = None


def _get_process_sem() -> asyncio.Semaphore:
    global _process_sem
    if _process_sem is None:
        _process_sem = asyncio.Semaphore(max(1, config.SEGMENT_CONCURRENCY))
    return _process_sem


async def _prefetch_one(sid: str, name: str, detectors: List, remover) -> None:
    """后台预取单个分片（受全局处理闸门限制，失败静默）。"""
    try:
        await handle_segment(sid, name, detectors, remover, prefetch=True)
    except Exception as exc:  # noqa: BLE001 - 预取失败不影响播放
        logger.debug("预取失败 %s：%s", name, exc)


def _extinf_seconds(tags: List[str]) -> float:
    """从分片标签里解析 #EXTINF 时长（秒），解析不到按 4 秒估算。"""
    for tag in tags:
        if tag.startswith("#EXTINF:"):
            try:
                return float(tag.split(":", 1)[1].split(",", 1)[0])
            except (ValueError, IndexError):
                return 4.0
    return 4.0


def segment_index_by_time(sess: _Session, seconds: float) -> int:
    """把播放时间（秒）换算成分片索引（按 #EXTINF 累计时长）。"""
    if not sess.segments:
        return 0
    t = 0.0
    for idx, seg in enumerate(sess.segments):
        dur = _extinf_seconds(seg.tags)
        if seconds < t + dur:
            return idx
        t += dur
    return len(sess.segments) - 1


def _index_of(sess: _Session, name: str) -> Optional[int]:
    return next((i for i, s in enumerate(sess.segments) if s.name == name), None)


_advance_tasks: Dict[str, asyncio.Task] = {}

# 会话最后心跳时间（WS 保活）；长时间无心跳视为用户已离开
_last_seen: Dict[str, float] = {}


def touch(sid: str) -> None:
    """刷新会话心跳时间。"""
    _last_seen[sid] = time.time()


def is_alive(sid: str, timeout: float = 30.0) -> bool:
    """最近 timeout 秒内是否有心跳。"""
    return (time.time() - _last_seen.get(sid, 0.0)) < timeout


def cancel_advance(sid: str) -> None:
    """取消该会话的后台推进任务（用户离开/心跳超时）。"""
    task = _advance_tasks.pop(sid, None)
    if task is not None and not task.done():
        task.cancel()
    _last_seen.pop(sid, None)
    logger.info("会话 %s 停止后台处理（无用户心跳）", sid)


def ensure_advance(sid: str, detectors: List, remover) -> None:
    """确保该会话有一个“从当前播放位置继续往后处理”的后台任务。"""
    task = _advance_tasks.get(sid)
    if task is not None and not task.done():
        return
    _advance_tasks[sid] = asyncio.create_task(_advance_loop(sid, detectors, remover))


async def _advance_loop(sid: str, detectors: List, remover) -> None:
    """持续从会话位置往后处理，直到末尾。

    用户播到哪，后台就从哪往后逐片处理（不拉进度条就一路处理到底）；
    seek 时 set_position 更新位置，循环下一轮自动从新位置继续。
    处理速度受全局闸门限制，不会打满 CPU。
    """
    try:
        meta = load_meta(sid)
        if not meta:
            return
        sess = await _ensure_session(sid, meta)
        total = len(sess.segments)
        while True:
            # 用户已离开（心跳超时）→ 停止处理，避免浪费资源
            if not is_alive(sid):
                logger.info("会话 %s 心跳超时，停止推进", sid)
                return
            idx = sess.position
            if idx >= total:
                logger.info("会话 %s 已推进到末尾（%d 片）", sid, total)
                return
            seg = sess.segments[idx]
            key = segment_cache_key(seg.abs_url, detectors)
            if _read_result(segment_cache_dir(key)) is None:
                try:
                    await handle_segment(sid, seg.name, detectors, remover, prefetch=True)
                except asyncio.CancelledError:
                    raise
                except Exception as exc:  # noqa: BLE001 - 单分片失败不中断推进
                    logger.debug("推进处理失败 %s：%s", seg.name, exc)
            # 若期间发生 seek（位置被改），按新位置继续；否则前进一格
            if sess.position == idx:
                sess.position = idx + 1
            await asyncio.sleep(0.05)
    except asyncio.CancelledError:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.warning("推进任务异常 %s：%s", sid, exc)


def set_position(
    sid: str, seconds: float, detectors: List, remover
) -> Optional[int]:
    """前端上报播放时间：更新位置并按 seek 优先调度预取。返回换算的分片索引。"""
    sess = _sessions.get(sid)
    if sess is None:
        meta = load_meta(sid)
        if not meta:
            return None
        # 同步重建（事件循环内，开销可忽略）
        raw = meta.get("segments") or []
        segs = []
        for item in raw:
            if isinstance(item, dict):
                segs.append(_Segment(item.get("name", ""), item.get("url", ""), list(item.get("tags") or [])))
        sess = _Session(sid=sid, segments=segs, headers=meta.get("headers") or {})
        _sessions[sid] = sess
    idx = segment_index_by_time(sess, max(0.0, float(seconds)))
    sess.position = idx
    touch(sid)
    ensure_advance(sid, detectors, remover)
    return idx


async def _process_segment(
    seg: _Segment, headers: Dict[str, str], detectors: List, remover, cdir: str
) -> Tuple[str, Optional[str]]:
    """下载 → 检测 → 按类型处理 → 写分片缓存。失败降级代理。

    全局处理闸门（SEGMENT_CONCURRENCY）限制同时处理的分片数：多用户/爬虫并发
    播放时即使请求量很大，也只会排队，不会把 CPU/内存打满导致整机卡死。
    """
    async with _get_process_sem():
        return await _process_segment_inner(seg, headers, detectors, remover, cdir)


async def _process_segment_inner(
    seg: _Segment, headers: Dict[str, str], detectors: List, remover, cdir: str
) -> Tuple[str, Optional[str]]:
    client = _get_client()
    os.makedirs(cdir, exist_ok=True)
    suffix = os.path.splitext(seg.name)[1] or ".ts"
    fd, tmp = tempfile.mkstemp(prefix="adseg_", suffix=suffix)
    os.close(fd)
    try:
        try:
            await _download(client, seg.abs_url, headers, tmp)
        except Exception as exc:  # noqa: BLE001 - 下载失败降级代理
            logger.warning("分片下载失败，降级代理：%s（%s）", seg.abs_url, exc)
            return ("none", None)  # 不写缓存，下次重试

        try:
            result = await _detect_any(detectors, tmp, seg.abs_url)
        except Exception as exc:  # noqa: BLE001 - 检测异常放行
            logger.warning("分片检测异常，放行：%s（%s）", seg.abs_url, exc)
            result = DetectionResult()

        if result.ad_type == "full":
            _write_result(cdir, {"ad_type": "full"})
            w, h = _probe_video_size(tmp)
            return ("full", blank_ts_path(w, h))

        if result.ad_type == "watermark":
            out = os.path.join(cdir, seg.name)
            try:
                await remover.remove(tmp, result.boxes, out)
            except Exception as exc:  # noqa: BLE001 - 去除失败降级代理
                logger.warning("去水印失败，降级代理：%s（%s）", seg.abs_url, exc)
                return ("none", None)
            _write_result(cdir, {"ad_type": "watermark", "file": seg.name})
            return ("watermark", out)

        _write_result(cdir, {"ad_type": "none"})
        return ("none", None)
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass


def _read_result(cdir: str) -> Optional[Tuple[str, Optional[str]]]:
    """读分片缓存；水印文件缺失时视为未命中（重处理）。"""
    path = os.path.join(cdir, "result.json")
    if not os.path.exists(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, json.JSONDecodeError):
        return None
    ad_type = data.get("ad_type")
    if ad_type == "watermark":
        fname = data.get("file") or ""
        fpath = os.path.join(cdir, fname)
        if fname and os.path.exists(fpath):
            return ("watermark", fpath)
        return None
    if ad_type in ("full", "none"):
        return (ad_type, None)
    return None


def _write_result(cdir: str, data: dict) -> None:
    tmp = os.path.join(cdir, "result.json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    os.replace(tmp, os.path.join(cdir, "result.json"))


# ---------------------------------------------------------------------------
# 下载与检测辅助
# ---------------------------------------------------------------------------


async def _fetch_text(client: httpx.AsyncClient, url: str, headers: Dict[str, str]) -> str:
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
    tmp = os.path.join(dir_, "meta.json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False)
    os.replace(tmp, os.path.join(dir_, "meta.json"))
