"""流工厂模块 —— 第三方流去广告转流 + 多会话管理 + 双协议输出

把上游 m3u8/mp4 拉流转码（去广告裁剪），以流工厂形式管理会话，
对外输出 HLS（Web 播放）与 RTSP（原生客户端），供开发者内嵌调用。

去广告规则由可复用的流/帧插件承载，站点 → 插件组合关系在应用层 ``main.py`` 自由编排。
"""
from stream_factory.config import HLS_ROOT
from stream_factory.factory import StreamFactory, stream_factory
from stream_factory.mediamtx import ensure_mediamtx, stop_mediamtx
from stream_factory.api import api_router
from stream_factory.rules import (
    BlankSegment,
    FilterRule,
    StreamRequest,
    StreamSource,
    TrimSegment,
)
from stream_factory.frame_plugins import (
    FramePlugin,
    ShuangmianTextFramePlugin,
    WatermarkFramePlugin,
)
from stream_factory.stream_plugins import (
    BlankInsertStreamPlugin,
    CompositeStreamPlugin,
    CupfoxStreamPlugin,
    PassthroughStreamPlugin,
    QqllStreamPlugin,
    StreamPlugin,
    YhdmStreamPlugin,
)
from stream_factory.video_cache import close_video_cache, ensure_source

__all__ = [
    "StreamFactory",
    "stream_factory",
    "ensure_mediamtx",
    "stop_mediamtx",
    "api_router",
    "HLS_ROOT",
    "StreamRequest",
    "StreamSource",
    "TrimSegment",
    "BlankSegment",
    "FilterRule",
    "FramePlugin",
    "StreamPlugin",
    "WatermarkFramePlugin",
    "ShuangmianTextFramePlugin",
    "PassthroughStreamPlugin",
    "BlankInsertStreamPlugin",
    "CompositeStreamPlugin",
    "CupfoxStreamPlugin",
    "YhdmStreamPlugin",
    "QqllStreamPlugin",
    "ensure_source",
    "close_video_cache",
]
