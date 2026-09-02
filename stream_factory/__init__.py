"""流工厂模块 —— 第三方流去广告转流 + 多会话管理 + 双协议输出

把上游 m3u8/mp4 拉流转码（去广告裁剪），以流工厂形式管理会话，
对外输出 HLS（Web 播放）与 RTSP（原生客户端），供开发者内嵌调用。

去广告规则由可复用的流/帧插件承载，站点 → 插件组合关系在应用层 ``main.py`` 自由编排。
"""
from stream_factory.config import HLS_ROOT
from stream_factory.factory import StreamFactory, stream_factory
from stream_factory.api import api_router
from stream_factory.base import FramePlugin, StreamPlugin
from stream_factory.rules import FilterRule, StreamRequest, StreamSource, TrimSegment
from stream_factory.plugins import (
    CupfoxStreamPlugin,
    PassthroughStreamPlugin,
    QqllStreamPlugin,
    WatermarkFramePlugin,
    YhdmStreamPlugin,
)

__all__ = [
    "StreamFactory",
    "stream_factory",
    "api_router",
    "HLS_ROOT",
    "StreamRequest",
    "StreamSource",
    "TrimSegment",
    "FilterRule",
    "FramePlugin",
    "StreamPlugin",
    "WatermarkFramePlugin",
    "PassthroughStreamPlugin",
    "CupfoxStreamPlugin",
    "YhdmStreamPlugin",
    "QqllStreamPlugin",
]
