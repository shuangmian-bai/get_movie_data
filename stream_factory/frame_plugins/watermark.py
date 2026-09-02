"""水印帧插件（孙子模块）

``WatermarkFramePlugin``：去水印/打标帧插件（示例，drawtext）。
复用 pipeline 已有的 drawtext 实现；text 含特殊字符需自行转义。
"""
from typing import List

from stream_factory.frame_plugins.base import FramePlugin
from stream_factory.rules import FilterRule


class WatermarkFramePlugin(FramePlugin):
    """去水印/打标帧插件（示例，drawtext）。"""

    name = "watermark"

    def __init__(
        self,
        text: str = "双面酱帧处理",
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
