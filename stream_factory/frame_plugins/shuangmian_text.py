"""文字帧插件开发案例（孙子模块）

``ShuangmianTextFramePlugin``：在视频画面上叠加「双面酱」文字水印。
展示如何自定义 ``FramePlugin``：只需实现 ``filters()`` 返回滤镜规则列表，
pipeline 会自动把规则拼入 ``-vf`` 滤镜链（存在滤镜时视频走重编码）。

用法：在 ``main.py`` 的 ``STREAM_PIPELINES`` 里把它加入帧插件列表即可。
"""
from typing import List

from stream_factory.frame_plugins.base import FramePlugin
from stream_factory.rules import FilterRule


class ShuangmianTextFramePlugin(FramePlugin):
    """帧插件开发案例：在视频画面上叠加「双面酱」文字水印。"""

    name = "shuangmian_text"

    def __init__(
        self,
        text: str = "双面酱",
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
