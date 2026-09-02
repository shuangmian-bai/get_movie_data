"""流处理插件抽象基类

定义两个可复用、不绑定站点的插件基类：

- ``FramePlugin``：帧插件，逐帧处理单元，产出滤镜规则；
- ``StreamPlugin``：流插件，流级裁剪策略，产出裁剪区间并合成 ``StreamRequest``。

插件不携带 ``base_url``，站点 → 插件组合关系由应用层（``main.py``）自由编排。
"""
import abc
from typing import List, Optional

from stream_factory.rules import BlankSegment, FilterRule, StreamRequest, StreamSource, TrimSegment


class FramePlugin(abc.ABC):
    """帧插件：逐帧处理单元，产出滤镜规则。可复用、不绑定站点。"""

    name: str = ""

    @abc.abstractmethod
    def filters(self) -> List[FilterRule]:
        """返回该帧处理对应的滤镜规则列表（交由 pipeline 拼入 ``-vf`` 滤镜链）。"""


class StreamPlugin(abc.ABC):
    """流插件：流级裁剪策略。可复用、不绑定站点。"""

    name: str = ""

    @abc.abstractmethod
    def trims(self, source: StreamSource) -> List[TrimSegment]:
        """返回要删除的广告区间（裁剪区间，单位秒）。"""

    def blanks(self, source: StreamSource) -> List[BlankSegment]:
        """返回周期性空白段（默认无）。需要「插入空白」的流插件覆盖此方法。"""
        return []

    def build_request(
        self,
        source: StreamSource,
        frame_plugins: Optional[List[FramePlugin]] = None,
    ) -> StreamRequest:
        """把源 + 裁剪区间 + 帧滤镜 合成 ``StreamRequest``。

        :param source:        上游流源描述（url/type/headers）
        :param frame_plugins: 帧插件列表（由应用层自由组合传入），其滤镜合并到 filters
        """
        frames = frame_plugins or []
        return StreamRequest(
            source_url=source.url,
            source_type=source.type,
            headers=source.headers,
            trims=self.trims(source),
            filters=[f for fp in frames for f in fp.filters()],
            blanks=self.blanks(source),
        )
