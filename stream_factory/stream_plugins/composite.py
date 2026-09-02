"""组合流插件（孙子模块）

``CompositeStreamPlugin``：合并多个流插件的裁剪区间与空白段，供应用层自由组合。
用于把「站点裁剪插件」与「空白插入案例」等能力叠加到同一站点，例如
``CompositeStreamPlugin([CupfoxStreamPlugin(), BlankInsertStreamPlugin()])``。
自身不绑定 ``base_url``，也不改变各子插件，仅聚合 ``trims`` 与 ``blanks``。
"""
from typing import List

from stream_factory.rules import BlankSegment, StreamSource, TrimSegment
from stream_factory.stream_plugins.base import StreamPlugin


class CompositeStreamPlugin(StreamPlugin):
    """组合流插件：合并多个流插件的裁剪区间与空白段，供应用层自由组合。"""

    name = "composite"

    def __init__(self, plugins: List[StreamPlugin]):
        self.plugins = plugins

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return [t for p in self.plugins for t in p.trims(source)]

    def blanks(self, source: StreamSource) -> List[BlankSegment]:
        return [b for p in self.plugins for b in p.blanks(source)]
