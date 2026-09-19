"""ad_filter REST API

- ``POST /api/ad_filter/process``：去广告处理，返回处理后 m3u8 地址；
- ``GET /ad_filter/{sid}/index.m3u8``：处理后播放列表；
- ``GET /ad_filter/{sid}/file/{name}``：本地去水印后的分片；
- ``GET /ad_filter/{sid}/proxy/{name}``：代理上游分片（带防盗链头）。

站点 → 检测器/去水印器组合由应用层通过 :func:`set_pipeline_getter` 注入，
本模块不依赖 ``main.py``（守「低耦合 + 应用层编排」）。
"""
import logging
import os
from typing import Callable, List, Optional, Tuple

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, StreamingResponse

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
    """去广告处理：返回处理后 m3u8 地址与统计。"""
    if not req.m3u8_url:
        raise HTTPException(status_code=400, detail="m3u8_url 不能为空")
    detectors, remover = _resolve(req.base_url)
    try:
        return await engine.process(req, detectors, remover)
    except Exception as exc:  # noqa: BLE001 - 统一转 502
        logger.exception("去广告处理失败：%s", exc)
        raise HTTPException(status_code=502, detail=f"去广告处理失败：{exc}")


@api_router.get("/ad_filter/{sid}/index.m3u8")
async def playlist(sid: str):
    """处理后播放列表。"""
    path = os.path.join(config.OUTPUT_ROOT, sid, "index.m3u8")
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="会话不存在或尚未处理完成")
    return FileResponse(path, media_type="application/vnd.apple.mpegurl")


@api_router.get("/ad_filter/{sid}/file/{name}")
async def local_file(sid: str, name: str):
    """本地去水印后的分片。"""
    name = os.path.basename(name)  # 防目录穿越
    path = os.path.join(config.OUTPUT_ROOT, sid, name)
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="分片不存在")
    return FileResponse(path, media_type="video/mp2t")


@api_router.get("/ad_filter/{sid}/proxy/{name}")
async def proxy_segment(sid: str, name: str):
    """代理上游分片（带防盗链头）。"""
    name = os.path.basename(name)
    meta = engine.load_meta(sid)
    if not meta:
        raise HTTPException(status_code=404, detail="会话不存在")
    url = meta.get("segments", {}).get(name)
    if not url:
        raise HTTPException(status_code=404, detail="分片映射不存在")
    headers = meta.get("headers", {})
    return StreamingResponse(
        proxy.stream_bytes(url, headers), media_type="video/mp2t"
    )
