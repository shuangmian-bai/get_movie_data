"""ad_filter REST API

渐进式去广告：

- ``POST /api/ad_filter/process``：解析 m3u8 并**秒回**处理后播放列表地址
  （分片指向动态端点，播放时才处理）；
- ``GET /ad_filter/{sid}/index.m3u8``：处理后播放列表；
- ``GET /ad_filter/{sid}/segment/{name}``：动态分片端点（按需检测/去水印/代理，结果缓存）；
- ``GET /ad_filter/{sid}/file/{name}``：旧会话的本地去水印分片（兼容保留）；
- ``GET /ad_filter/{sid}/proxy/{name}``：旧会话的代理分片（兼容保留）。

站点 → 检测器/去水印器组合由应用层通过 :func:`set_pipeline_getter` 注入，
本模块不依赖 ``main.py``（守「低耦合 + 应用层编排」）。
"""
import asyncio
import json
import logging
import os
import time
from typing import Callable, List, Optional, Tuple

from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, Response, StreamingResponse
from pydantic import BaseModel

from ad_filter import config, engine, proxy
from ad_filter.detector.ocr import OcrDetector
from ad_filter.models import ProcessRequest, ProcessResult
from ad_filter.remover.delogo import DelogoRemover

logger = logging.getLogger("ad_filter.api")

api_router = APIRouter(tags=["去广告"])

# 应用层注入的「站点 → 检测器 + 去水印器」解析函数
_pipeline_getter: Optional[Callable[[str], Tuple[List, object]]] = None


def set_pipeline_getter(fn: Callable[[str], Tuple[List, object]]) -> None:
    """由应用层（main.py）注入编排函数：``base_url → (detectors, remover)``。"""
    global _pipeline_getter
    _pipeline_getter = fn


def _resolve(base_url: str) -> Tuple[List, object]:
    """解析站点对应的检测器与去水印器；未注入时用内置默认（OCR + delogo）。"""
    if _pipeline_getter is not None:
        return _pipeline_getter(base_url)
    return [OcrDetector()], DelogoRemover()


@api_router.post("/api/ad_filter/process", response_model=ProcessResult)
async def process(req: ProcessRequest):
    """解析 m3u8 并立即返回播放列表地址（分片按需处理，起播不再等整片）。"""
    if not req.m3u8_url:
        raise HTTPException(status_code=400, detail="m3u8_url 不能为空")
    detectors, remover = _resolve(req.base_url)
    try:
        result = await engine.process(req, detectors, remover)
    except Exception as exc:  # noqa: BLE001 - 统一转 502
        logger.exception("去广告处理失败：%s", exc)
        raise HTTPException(status_code=502, detail=f"去广告处理失败：{exc}")
    # 提前启动后台推进：用户还在初始化播放器时就开始处理前几片，缩短起播等待
    try:
        engine.touch(result.sid)
        engine.ensure_advance(result.sid, detectors, remover)
    except Exception:  # noqa: BLE001 - 预取启动失败不影响返回
        pass
    return result


@api_router.get("/ad_filter/{sid}/index.m3u8")
async def playlist(sid: str):
    """处理后播放列表（按当前缓存状态渲染：已确认的整屏广告分片直接移除）。"""
    meta = engine.load_meta(sid)
    if not meta:
        raise HTTPException(status_code=404, detail="会话不存在")
    detectors, _ = _resolve(meta.get("base_url", ""))
    text = engine.render_playlist(sid, detectors)
    if text is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    return Response(content=text, media_type="application/vnd.apple.mpegurl")


@api_router.get("/ad_filter/{sid}/segment/{name}")
async def dynamic_segment(sid: str, name: str):
    """动态分片端点：已处理命中缓存；未处理则同步处理；失败降级代理。"""
    name = os.path.basename(name)  # 防目录穿越
    meta = engine.load_meta(sid)
    if not meta:
        raise HTTPException(status_code=404, detail="会话不存在")
    detectors, remover = _resolve(meta.get("base_url", ""))
    try:
        result = await engine.handle_segment(sid, name, detectors, remover)
    except Exception as exc:  # noqa: BLE001 - 任何异常降级代理，不阻断播放
        logger.warning("分片处理异常，降级代理：%s（%s）", name, exc)
        result = ("none", None)

    if result is None:
        raise HTTPException(status_code=404, detail="分片不存在")

    kind, path = result
    if kind == "full":
        # 全广告：返回与主视频同分辨率的 1 帧黑帧（几乎无感；已确认的分片会从 playlist 移除）
        if path and os.path.exists(path):
            return FileResponse(path, media_type="video/mp2t")
        return FileResponse(engine.blank_ts_path(), media_type="video/mp2t")
    if kind == "watermark" and path and os.path.exists(path):
        return FileResponse(path, media_type="video/mp2t")

    # none / 降级代理：透传上游（带防盗链头）
    seg_map = {
        item.get("name"): item.get("url")
        for item in (meta.get("segments") or [])
        if isinstance(item, dict)
    }
    url = seg_map.get(name)
    if url is None:
        # 旧格式 meta：segments 是 {name: url}
        legacy = meta.get("segments")
        if isinstance(legacy, dict):
            url = legacy.get(name)
    if not url:
        raise HTTPException(status_code=404, detail="分片映射不存在")
    return StreamingResponse(
        proxy.stream_bytes(url, meta.get("headers") or {}), media_type="video/mp2t"
    )


class ProgressReport(BaseModel):
    """播放进度上报：当前播放时间（秒），用于 seek 优先调度预取。"""

    time: float = 0.0


@api_router.post("/ad_filter/{sid}/progress", summary="上报播放位置")
async def report_progress(sid: str, body: ProgressReport):
    """前端上报播放时间：更新预取位置（seek 后立即从新位置开始预取）。"""
    meta = engine.load_meta(sid)
    if not meta:
        raise HTTPException(status_code=404, detail="会话不存在")
    detectors, remover = _resolve(meta.get("base_url", ""))
    idx = engine.set_position(sid, body.time, detectors, remover)
    return {"sid": sid, "index": idx, "time": body.time}


@api_router.websocket("/ad_filter/{sid}/ws")
async def ad_heartbeat_ws(websocket: WebSocket, sid: str):
    """去广告播放心跳（WebSocket）。

    - 连接即视为用户在场：刷新心跳并启动/恢复后台推进；
    - 收到 ``{"type":"progress","time":秒}`` 时更新播放位置（seek 优先）；
    - 30 秒无消息或连接断开 → 取消该会话后台处理（避免用户关闭页面后继续耗资源）。
    """
    await websocket.accept()
    meta = engine.load_meta(sid)
    if not meta:
        await websocket.close(code=4404)
        return
    detectors, remover = _resolve(meta.get("base_url", ""))
    engine.touch(sid)
    engine.ensure_advance(sid, detectors, remover)
    last_stats = 0.0

    async def _push_stats() -> None:
        nonlocal last_stats
        now = time.time()
        if now - last_stats < 5:
            return
        last_stats = now
        stats = engine.skip_stats(sid, detectors)
        if stats is not None:
            try:
                await websocket.send_text(json.dumps({"type": "stats", **stats}))
            except Exception:  # noqa: BLE001 - 连接已断
                pass

    await _push_stats()
    try:
        while True:
            try:
                raw = await asyncio.wait_for(websocket.receive_text(), timeout=30)
            except asyncio.TimeoutError:
                # 心跳超时：用户可能已关闭页面
                break
            engine.touch(sid)
            try:
                data = json.loads(raw)
            except (TypeError, ValueError):
                continue
            if isinstance(data, dict) and data.get("type") == "progress":
                try:
                    engine.set_position(sid, float(data.get("time", 0)), detectors, remover)
                except (TypeError, ValueError):
                    pass
            await _push_stats()
    except WebSocketDisconnect:
        pass
    finally:
        engine.cancel_advance(sid)
        try:
            await websocket.close()
        except Exception:  # noqa: BLE001 - 已断开
            pass


@api_router.get("/ad_filter/{sid}/stats", summary="广告处理统计")
async def session_stats(sid: str):
    """返回已确认的广告分片统计（整屏跳过数量/秒数、去水印数量）。"""
    meta = engine.load_meta(sid)
    if not meta:
        raise HTTPException(status_code=404, detail="会话不存在")
    detectors, _ = _resolve(meta.get("base_url", ""))
    stats = engine.skip_stats(sid, detectors)
    if stats is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    return stats


@api_router.get("/ad_filter/{sid}/file/{name}")
async def local_file(sid: str, name: str):
    """本地去水印后的分片（旧会话兼容）。"""
    name = os.path.basename(name)
    path = os.path.join(config.OUTPUT_ROOT, sid, name)
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="分片不存在")
    return FileResponse(path, media_type="video/mp2t")


@api_router.get("/ad_filter/{sid}/proxy/{name}")
async def proxy_segment(sid: str, name: str):
    """代理上游分片（旧会话兼容）。"""
    name = os.path.basename(name)
    meta = engine.load_meta(sid)
    if not meta:
        raise HTTPException(status_code=404, detail="会话不存在")
    segments = meta.get("segments") or {}
    if isinstance(segments, dict):
        url = segments.get(name)
    else:
        url = next(
            (item.get("url") for item in segments if isinstance(item, dict) and item.get("name") == name),
            None,
        )
    if not url:
        raise HTTPException(status_code=404, detail="分片映射不存在")
    headers = meta.get("headers", {})
    return StreamingResponse(proxy.stream_bytes(url, headers), media_type="video/mp2t")
