"""流工厂 REST API —— 基于 FastAPI 的路由

对外暴露流创建 / 查询 / 停止能力，以及内嵌播放器页。
本模块仅依赖 ``stream_factory`` 内部能力，保持低耦合；应用层 ``main.py`` 挂载本路由。
"""
import os

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from stream_factory.factory import stream_factory
from stream_factory.rules import StreamRequest

api_router = APIRouter(prefix="/api", tags=["流工厂"])

# 内嵌播放器页路径
_PLAYER_HTML = os.path.join(os.path.dirname(__file__), "frontend", "player.html")


def _find(sid: str):
    """按 sid 查会话，未命中转 404。"""
    session = stream_factory.get(sid)
    if session is None:
        raise HTTPException(status_code=404, detail=f"流会话不存在：{sid}")
    return session


@api_router.post("/stream", summary="创建流（去广告转流）")
async def create_stream(req: StreamRequest):
    """根据源地址与裁剪/滤镜规则创建流，返回会话信息（含 HLS / RTSP 地址）。"""
    if not req.source_url:
        raise HTTPException(status_code=400, detail="source_url 不能为空")
    session = await stream_factory.create_stream(req)
    return session.to_dict()


@api_router.get("/stream", summary="列出所有流会话")
async def list_streams():
    return stream_factory.list_sessions()


@api_router.get("/stream/{sid}", summary="查询流会话状态")
async def get_stream(sid: str):
    return _find(sid).to_dict()


@api_router.delete("/stream/{sid}", summary="停止流会话")
async def stop_stream(sid: str):
    if not await stream_factory.stop(sid):
        raise HTTPException(status_code=404, detail=f"流会话不存在：{sid}")
    return {"ok": True, "sid": sid}


@api_router.get("/stream/{sid}/player", summary="内嵌播放器页")
async def stream_player(sid: str):
    """返回内嵌播放器页（hls.js 播放该会话的 HLS 地址）。"""
    _find(sid)  # 校验会话存在
    return FileResponse(_PLAYER_HTML, media_type="text/html")
