"""空白插入流插件开发案例（孙子模块）

``BlankInsertStreamPlugin``：每隔 ``interval`` 秒插入 ``duration`` 秒空白段（黑屏 + 静音 + 提示文字）。
展示如何自定义 ``StreamPlugin`` 的流级时间操作：覆盖 ``blanks()`` 返回空白段规则，
pipeline 翻译为 ``drawbox``（视频盖黑）+ ``volume``（音频静音）滤镜，并在空白段
居中叠加 ``text`` 提示文字（避免纯黑屏被误解为故障），触发重编码。

注意：本案例以「覆盖为黑屏/静音」呈现空白（总时长不变）；若需真正延长总时长，
可改用 ``filter_complex`` + ``concat`` 切分拼接，留作进阶练习。
"""
from typing import List

from stream_factory.rules import BlankSegment, StreamSource, TrimSegment
from stream_factory.stream_plugins.base import StreamPlugin


class BlankInsertStreamPlugin(StreamPlugin):
    """流插件开发案例：每隔 N 秒插入 M 秒空白（黑屏 + 静音 + 提示文字）。"""

    name = "blank_insert"

    def __init__(
        self, interval: float = 10.0, duration: float = 2.0, text: str = "出发双面酱的流处理模块，黑屏几秒"
    ):
        self.interval = interval
        self.duration = duration
        self.text = text

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return []  # 本案例不做裁剪，只插入空白

    def blanks(self, source: StreamSource) -> List[BlankSegment]:
        return [
            BlankSegment(interval=self.interval, duration=self.duration, text=self.text)
        ]
