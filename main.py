"""应用入口 —— 编排汇总各功能模块

组装 FastAPI 应用：挂载 web 模块的 API 路由 + 流工厂路由 + HLS 静态目录 + 前端中间件。
各功能模块（media_source / web / stream_factory）互不直接调用，统一在此编排。

去广告规则是系统内部知识：此处按 ``base_url`` 把站点映射到「流插件 + 帧插件」组合，
调用方只传 ``base_url`` 与源，无需关心裁剪区间与滤镜细节。
"""
from typing import Dict, List, Tuple

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from frontend_loader import FrontendStaticLoader
from stream_factory import (
    HLS_ROOT,
    FramePlugin,
    StreamPlugin,
    StreamRequest,
    StreamSource,
    api_router as stream_api_router,
    stream_factory,
)
from stream_factory.plugins import (
    CupfoxStreamPlugin,
    PassthroughStreamPlugin,
    QqllStreamPlugin,
    WatermarkFramePlugin,
    YhdmStreamPlugin,
)
from web import api_router

# ---- 流处理自由组合（应用层汇总：base_url → 流插件 + 帧插件）----
# 新增/调整站点只需改这一张表；未匹配的 base_url 走透传（不裁剪）。
STREAM_PIPELINES: Dict[str, Tuple[StreamPlugin, List[FramePlugin]]] = {
    "https://www.cupfox7.com": (CupfoxStreamPlugin(), [WatermarkFramePlugin(text="去广告")]),
    "https://yhdm.one": (YhdmStreamPlugin(), []),
    "https://www.qqll.cc": (QqllStreamPlugin(), []),
}
DEFAULT_PIPELINE: Tuple[StreamPlugin, List[FramePlugin]] = (PassthroughStreamPlugin(), [])


def build_stream_request(base_url: str, source: StreamSource) -> StreamRequest:
    """按 ``base_url`` 组合流插件 + 帧插件，合成去广告后的 ``StreamRequest``。"""
    stream_plugin, frame_plugins = STREAM_PIPELINES.get(base_url, DEFAULT_PIPELINE)
    return stream_plugin.build_request(source, frame_plugins)


class ProcessedStreamRequest(BaseModel):
    """内部处理入口请求体：``base_url`` 关联站点组合规则；``source`` 为上游播放源。"""

    base_url: str
    source: StreamSource


app = FastAPI(title="影视数据源服务", docs_url="/docs")

# 挂载 API 路由（web 数据源模块 + 流工厂模块）
app.include_router(api_router)
app.include_router(stream_api_router)

# HLS 静态文件（流工厂输出目录；目录由 StreamFactory 单例在导入时创建）
app.mount("/streams", StaticFiles(directory=HLS_ROOT), name="streams")

# 前端静态资源（web/frontend/ 目录，由 frontend_loader 引擎加载）
app.add_middleware(FrontendStaticLoader)


# 内部处理入口：按 base_url 组合去广告后建流（/api/play 保持不变，此接口为新增内部能力）
@app.post("/api/stream/processed", tags=["流工厂"])
async def create_processed_stream(req: ProcessedStreamRequest):
    """按站点（``base_url``）内化的去广告规则建流，返回会话信息（含 HLS / RTSP 地址）。"""
    if not req.source.url:
        raise HTTPException(status_code=400, detail="source.url 不能为空")
    stream_req = build_stream_request(req.base_url, req.source)
    session = await stream_factory.create_stream(stream_req)
    return session.to_dict()


if __name__ == "__main__":
    uvicorn.run("main:app", reload=True)
