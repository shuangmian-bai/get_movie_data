"""樱花动漫流插件（孙子模块）

``YhdmStreamPlugin``：樱花动漫（yhdm.one）流插件，示例裁剪区间（占位）。
"""
from typing import List

from stream_factory.rules import StreamSource, TrimSegment
from stream_factory.stream_plugins.base import StreamPlugin


class YhdmStreamPlugin(StreamPlugin):
    """樱花动漫（yhdm.one）流插件：示例裁剪区间（占位）。"""

    name = "yhdm"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return [TrimSegment(start=0, end=15)]  # 示例：片头 15 秒
