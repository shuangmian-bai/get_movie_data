"""内置流处理插件（示例）

- 帧插件：``WatermarkFramePlugin``（去水印/打标，复用 drawtext）；
- 流插件：``PassthroughStreamPlugin``（透传）+ 各站点裁剪策略（区间为占位/示例）。

插件不绑定 ``base_url``，由应用层 ``main.py`` 自由组合。
"""
from typing import List

from stream_factory.base import FramePlugin, StreamPlugin
from stream_factory.rules import FilterRule, StreamSource, TrimSegment


# ---- 帧插件 ----
class WatermarkFramePlugin(FramePlugin):
    """去水印/打标帧插件（示例，drawtext）。

    复用 pipeline 已有的 drawtext 实现；text 含特殊字符需自行转义。
    """

    name = "watermark"

    def __init__(
        self,
        text: str = "去广告",
        x: int = 10,
        y: int = 10,
        fontsize: int = 24,
        color: str = "white",
    ):
        self.text = text
        self.x = x
        self.y = y
        self.fontsize = fontsize
        self.color = color

    def filters(self) -> List[FilterRule]:
        return [
            FilterRule(
                name="drawtext",
                params={
                    "text": self.text,
                    "x": self.x,
                    "y": self.y,
                    "fontsize": self.fontsize,
                    "color": self.color,
                },
            )
        ]


# ---- 流插件 ----
class PassthroughStreamPlugin(StreamPlugin):
    """透传流插件：不裁剪，原样转发。"""

    name = "passthrough"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return []


class CupfoxStreamPlugin(StreamPlugin):
    """茶杯狐（cupfox7.com）流插件：示例裁剪区间（占位，待真实站点数据填充）。"""

    name = "cupfox"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return [TrimSegment(start=0, end=30)]  # 示例：片头 30 秒


class YhdmStreamPlugin(StreamPlugin):
    """樱花动漫（yhdm.one）流插件：示例裁剪区间（占位）。"""

    name = "yhdm"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return [TrimSegment(start=0, end=15)]  # 示例：片头 15 秒


class QqllStreamPlugin(StreamPlugin):
    """奇奇影视（qqll.cc）流插件：示例裁剪区间（占位）。"""

    name = "qqll"

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return []  # 示例：暂不裁剪
