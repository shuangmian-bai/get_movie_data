"""内置流处理插件（示例）

- 帧插件：``WatermarkFramePlugin``（去水印/打标，复用 drawtext）；
- 流插件：``PassthroughStreamPlugin``（透传）+ 各站点裁剪策略（区间为占位/示例）。

插件不绑定 ``base_url``，由应用层 ``main.py`` 自由组合。
"""
from typing import List

from stream_factory.base import FramePlugin, StreamPlugin
from stream_factory.rules import BlankSegment, FilterRule, StreamSource, TrimSegment


# ---- 帧插件 ----
class WatermarkFramePlugin(FramePlugin):
    """去水印/打标帧插件（示例，drawtext）。

    复用 pipeline 已有的 drawtext 实现；text 含特殊字符需自行转义。
    """

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


class CompositeStreamPlugin(StreamPlugin):
    """组合流插件：合并多个流插件的裁剪区间与空白段，供应用层自由组合。

    用于把「站点裁剪插件」与「空白插入案例」等能力叠加到同一站点，例如
    ``CompositeStreamPlugin([CupfoxStreamPlugin(), BlankInsertStreamPlugin()])``。
    自身不绑定 ``base_url``，也不改变各子插件，仅聚合 ``trims`` 与 ``blanks``。
    """

    name = "composite"

    def __init__(self, plugins: List[StreamPlugin]):
        self.plugins = plugins

    def trims(self, source: StreamSource) -> List[TrimSegment]:
        return [t for p in self.plugins for t in p.trims(source)]

    def blanks(self, source: StreamSource) -> List[BlankSegment]:
        return [b for p in self.plugins for b in p.blanks(source)]


# ---- 开发案例：自定义帧插件 / 流插件 ----
class ShuangmianTextFramePlugin(FramePlugin):
    """帧插件开发案例：在视频画面上叠加「双面酱」文字水印。

    展示如何自定义 ``FramePlugin``：只需实现 ``filters()`` 返回滤镜规则列表，
    pipeline 会自动把规则拼入 ``-vf`` 滤镜链（存在滤镜时视频走重编码）。

    用法：在 ``main.py`` 的 ``STREAM_PIPELINES`` 里把它加入帧插件列表即可。
    """

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


class BlankInsertStreamPlugin(StreamPlugin):
    """流插件开发案例：每隔 ``interval`` 秒插入 ``duration`` 秒空白段（黑屏 + 静音 + 提示文字）。

    展示如何自定义 ``StreamPlugin`` 的流级时间操作：覆盖 ``blanks()`` 返回空白段规则，
    pipeline 翻译为 ``drawbox``（视频盖黑）+ ``volume``（音频静音）滤镜，并在空白段
    居中叠加 ``text`` 提示文字（避免纯黑屏被误解为故障），触发重编码。

    注意：本案例以「覆盖为黑屏/静音」呈现空白（总时长不变）；若需真正延长总时长，
    可改用 ``filter_complex`` + ``concat`` 切分拼接，留作进阶练习。
    """

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
