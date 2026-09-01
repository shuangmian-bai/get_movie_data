"""Pydantic 标准数据模型

基于 Pydantic V2，强制统一输出结构、类型与默认值。
所有站点经过字段映射引擎处理后，最终都会被校验为下述标准对象。
"""
from typing import Dict, List

from pydantic import BaseModel, ConfigDict, Field


class SourceMeta(BaseModel):
    """数据源元信息（用于查询可用站点接口）"""

    model_config = ConfigDict(coerce_numbers_to_str=True)

    base_url: str = ""       # 站点唯一标识 URL
    source_name: str = ""    # 站点友好名称
    source_desc: str = ""    # 站点描述信息


class SearchItem(BaseModel):
    """搜索结果标准条目"""

    model_config = ConfigDict(coerce_numbers_to_str=True)

    name: str = ""           # 影视标题
    base_url: str = ""       # 来源站点标识（自动注入，用于单源路由）
    link: str = ""           # 详情页链接
    type: str = ""           # 类型：电影 / 剧集 / 综艺 ...
    year: str = ""           # 年份
    cover: str = ""          # 封面图
    desc: str = ""           # 简介


class EpisodeItem(BaseModel):
    """分集信息条目"""

    model_config = ConfigDict(coerce_numbers_to_str=True)

    name: str = ""           # 分集标题
    index: int = 0           # 集数序号（从 1 开始）
    link: str = ""           # 分集播放页链接


class MediaInfo(BaseModel):
    """影视详情标准结构"""

    model_config = ConfigDict(coerce_numbers_to_str=True)

    name: str = ""                                       # 影视标题
    base_url: str = ""                                   # 来源站点标识
    link: str = ""                                       # 详情页链接
    type: str = ""                                       # 类型
    year: str = ""                                       # 年份
    cover: str = ""                                      # 封面图
    desc: str = ""                                       # 简介
    episodes: List[EpisodeItem] = Field(default_factory=list)  # 分集列表


class PlaySource(BaseModel):
    """播放地址标准结构（支持 m3u8 / mp4、自定义请求头）"""

    model_config = ConfigDict(coerce_numbers_to_str=True)

    url: str = ""                                        # 播放地址
    type: str = "m3u8"                                   # 播放类型：m3u8 / mp4
    headers: Dict[str, str] = Field(default_factory=dict)  # 自定义请求头
