"""帧插件抽象基类

定义 ``FramePlugin``：帧插件，逐帧处理单元，产出滤镜规则。
插件不携带 ``base_url``，站点 → 帧插件组合关系由应用层（``main.py``）自由编排。
"""
import abc
from typing import List

from stream_factory.rules import FilterRule


class FramePlugin(abc.ABC):
    """帧插件：逐帧处理单元，产出滤镜规则。可复用、不绑定站点。"""

    name: str = ""

    @abc.abstractmethod
    def filters(self) -> List[FilterRule]:
        """返回该帧处理对应的滤镜规则列表（交由 pipeline 拼入 ``-vf`` 滤镜链）。"""
