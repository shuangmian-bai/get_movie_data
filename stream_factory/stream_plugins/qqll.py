"""奇奇影视流插件（孙子模块）

``QqllStreamPlugin``：奇奇影视（qqll.cc）流插件，示例裁剪区间（占位）。
"""
from typing import List

from stream_factory.rules import StreamSource, TrimSegment
from stream_factory.stream_plugins.base import StreamPlugin


class QqllStreamPlugin(StreamPlugin):
    """奇奇影视（qqll.cc）流插件：示例裁剪区间（占位）。"""

    name = "qqll"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return []  # 示例：暂不裁剪
