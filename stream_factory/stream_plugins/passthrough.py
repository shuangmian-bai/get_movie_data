"""透传流插件（孙子模块）

``PassthroughStreamPlugin``：透传流插件，不裁剪，原样转发。
"""
from typing import List

from stream_factory.rules import StreamSource, TrimSegment
from stream_factory.stream_plugins.base import StreamPlugin


class PassthroughStreamPlugin(StreamPlugin):
    """透传流插件：不裁剪，原样转发。"""

    name = "passthrough"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return []
