"""ad_filter 数据模型（Pydantic V2）

定义矩形区域（Box）、分片检测结果（DetectionResult）、处理请求/结果
（ProcessRequest / ProcessResult）等标准结构。
"""
from typing import Dict, List

from pydantic import BaseModel, Field


class Box(BaseModel):
    """一个矩形区域（像素坐标，相对视频分辨率）。"""

    x: int = 0
    y: int = 0
    w: int = 0
    h: int = 0


class DetectionResult(BaseModel):
    """单个 ts 分片的检测结果。

    ``ad_type``：``none``（正常）/ ``full``（全广告，丢弃）/ ``watermark``（水印广告，去水印）。
    ``boxes``：水印区域列表（仅 ``watermark`` 有意义）。
    ``hits``：命中的违规词。
    """

    ad_type: str = "none"
    boxes: List[Box] = Field(default_factory=list)
    hits: List[str] = Field(default_factory=list)


class ProcessRequest(BaseModel):
    """去广告处理请求体。

    ``headers``：透传给上游的请求头（防盗链 Referer 等）。
    ``base_url``：站点标识，用于应用层选择检测器/去水印器组合。
    """

    m3u8_url: str
    headers: Dict[str, str] = Field(default_factory=dict)
    base_url: str = ""


class ProcessStats(BaseModel):
    """处理统计。"""

    total: int = 0       # 总分片数
    full: int = 0        # 全广告（丢弃）
    watermark: int = 0   # 水印广告（去水印落盘）
    passed: int = 0      # 正常（代理透传）


class ProcessResult(BaseModel):
    """去广告处理结果。"""

    sid: str
    playlist_url: str   # 处理后 m3u8 的相对路径（同源可直播）
    stats: ProcessStats
