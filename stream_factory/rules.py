"""流处理规则模型

定义流源描述（StreamSource）、去广告裁剪区间（TrimSegment）、
逐帧滤镜规则（FilterRule）与完整请求体（StreamRequest）。
基于 Pydantic V2，风格对齐 ``media_source/models.py``。
"""
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class TrimSegment(BaseModel):
    """一个要被删除的广告区间（单位：秒）。

    ``start`` 为广告起始秒；``end`` 为广告结束秒，``None`` 表示一直删到结尾。
    """

    start: float = 0.0
    end: Optional[float] = None


class BlankSegment(BaseModel):
    """周期性空白段：每隔 ``interval`` 秒，覆盖 ``duration`` 秒为黑屏 + 静音（视觉/听觉空白）。

    由流插件（``StreamPlugin.blanks``）产出，pipeline 翻译为 ``drawbox``（视频盖黑）
    + ``volume``（音频静音）滤镜。
    """

    interval: float  # 每隔多少秒插入一段空白
    duration: float  # 每段空白的时长（秒）


class FilterRule(BaseModel):
    """预留：逐帧滤镜规则（插入帧 / 删除帧 / 水印等）。

    ``name`` 为 FFmpeg 滤镜名（drawtext / tpad / select / overlay ...），
    ``params`` 为该滤镜的参数，由 ``pipeline`` 拼接为 ``-vf`` 滤镜链。
    """

    name: str
    params: Dict[str, Any] = Field(default_factory=dict)


class StreamSource(BaseModel):
    """流源描述（与 ``media_source.PlaySource`` 解耦，避免 stream_factory 依赖 media_source）。

    ``base_url`` 只是两边各自声明的字符串，由应用层用同一字符串把二者关联起来。
    """

    url: str = ""                                          # 上游播放地址（m3u8/mp4）
    type: str = "m3u8"                                     # m3u8 | mp4
    headers: Dict[str, str] = Field(default_factory=dict)  # 透传给 ffmpeg 的 -headers


class StreamRequest(BaseModel):
    """创建流请求体。"""

    source_url: str                                    # 上游播放地址（m3u8/mp4）
    source_type: str = "m3u8"                          # m3u8 | mp4
    headers: Dict[str, str] = Field(default_factory=dict)  # 透传给 ffmpeg 的 -headers
    trims: List[TrimSegment] = Field(default_factory=list)   # 去广告区间
    filters: List[FilterRule] = Field(default_factory=list)  # 预留：逐帧滤镜
    blanks: List[BlankSegment] = Field(default_factory=list)  # 周期性空白段（黑屏+静音）
