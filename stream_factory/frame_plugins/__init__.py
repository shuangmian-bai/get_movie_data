"""帧插件子包 —— FramePlugin 基类 + 各帧插件（孙子模块）

帧插件是逐帧处理单元，产出 ``FilterRule`` 列表，交由 pipeline 拼入 ``-vf`` 滤镜链。
每个具体帧插件一个文件（孙子模块），站点 → 帧插件组合在应用层 ``main.py`` 编排。
"""
from stream_factory.frame_plugins.base import FramePlugin
from stream_factory.frame_plugins.shuangmian_text import ShuangmianTextFramePlugin
from stream_factory.frame_plugins.watermark import WatermarkFramePlugin

__all__ = ["FramePlugin", "WatermarkFramePlugin", "ShuangmianTextFramePlugin"]
