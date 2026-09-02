"""流插件子包 —— StreamPlugin 基类 + 各流插件（孙子模块）

流插件是流级裁剪策略，产出 ``TrimSegment``（裁剪区间）、可选 ``BlankSegment``（空白段）
并合成 ``StreamRequest``。每个具体流插件一个文件（孙子模块），站点 → 流插件组合在应用层 ``main.py`` 编排。
"""
from stream_factory.stream_plugins.base import StreamPlugin
from stream_factory.stream_plugins.blank_insert import BlankInsertStreamPlugin
from stream_factory.stream_plugins.composite import CompositeStreamPlugin
from stream_factory.stream_plugins.cupfox import CupfoxStreamPlugin
from stream_factory.stream_plugins.passthrough import PassthroughStreamPlugin
from stream_factory.stream_plugins.qqll import QqllStreamPlugin

__all__ = [
    "StreamPlugin",
    "PassthroughStreamPlugin",
    "CupfoxStreamPlugin",
    "QqllStreamPlugin",
    "BlankInsertStreamPlugin",
    "CompositeStreamPlugin",
]
