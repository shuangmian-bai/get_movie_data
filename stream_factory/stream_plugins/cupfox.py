"""茶杯狐流插件（孙子模块）

``CupfoxStreamPlugin``：茶杯狐（cupfox7.com）流插件，示例裁剪区间（占位，待真实站点数据填充）。
"""
from typing import List

from stream_factory.rules import StreamSource, TrimSegment
from stream_factory.stream_plugins.base import StreamPlugin


class CupfoxStreamPlugin(StreamPlugin):
    """茶杯狐（cupfox7.com）流插件：示例裁剪区间（占位，待真实站点数据填充）。"""

    name = "cupfox"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return [TrimSegment(start=0, end=30)]  # 示例：片头 30 秒
