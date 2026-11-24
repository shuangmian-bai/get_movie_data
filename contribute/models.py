"""投稿数据模型（Pydantic V2）

用户可投稿两类信息：

- **数据源**（``SourceContribution``）：新影视站点的接入线索；
- **广告信息**（``AdContribution``）：已接入站点上广告的类型、位置与样本时间点，
  便于扩展去广告插件（检测器/去除器的词表、区域、时机）。

投稿先落盘为 ``pending`` 状态，人工审核后再转成插件配置或新站点插件。
"""
from typing import List, Literal, Optional

from pydantic import BaseModel, Field, field_validator


def _check_url(value: str) -> str:
    value = (value or "").strip()
    if not value:
        raise ValueError("URL 不能为空")
    if not (value.startswith("http://") or value.startswith("https://")):
        raise ValueError("URL 必须以 http:// 或 https:// 开头")
    if len(value) > 500:
        raise ValueError("URL 过长（上限 500 字符）")
    return value


class SourceContribution(BaseModel):
    """数据源投稿：新站点接入线索。"""

    name: str = Field(..., min_length=1, max_length=100, description="站点名称")
    base_url: str = Field(..., description="站点根地址（如 https://example.com）")
    desc: str = Field("", max_length=2000, description="站点说明（类型、资源种类等）")
    sample_urls: List[str] = Field(
        default_factory=list,
        max_length=10,
        description="样本链接（搜索结果页/详情页/播放页，最多 10 条）",
    )
    contact: str = Field("", max_length=200, description="联系方式（可选）")

    @field_validator("base_url")
    @classmethod
    def _v_base_url(cls, v: str) -> str:
        return _check_url(v)

    @field_validator("sample_urls")
    @classmethod
    def _v_samples(cls, v: List[str]) -> List[str]:
        return [_check_url(u) for u in v]


class AdContribution(BaseModel):
    """广告信息投稿：某站点广告的类型、位置与样本时间点。"""

    base_url: str = Field(..., description="所属站点根地址（须与已接入源一致）")
    ad_type: Literal[
        "top_marquee", "top_static", "watermark", "pre_roll", "mid_roll", "other"
    ] = Field(
        ...,
        description=(
            "广告类型：top_marquee 顶部跑马灯 / top_static 顶部静态条 / "
            "watermark 画面水印 / pre_roll 片头插播 / mid_roll 片中插播 / other 其它"
        ),
    )
    position: Literal["top", "bottom", "center", "corner", "full"] = Field(
        "top", description="广告位置：top/bottom/center/corner/full"
    )
    timestamps: List[str] = Field(
        default_factory=list,
        max_length=20,
        description="样本时间点（如 4:50、13:22，最多 20 条）",
    )
    blockwords: str = Field(
        "", max_length=500, description="广告文字/关键词（逗号分隔，用于词表）"
    )
    note: str = Field("", max_length=2000, description="补充说明（如广告样式、出现规律）")
    contact: str = Field("", max_length=200, description="联系方式（可选）")

    @field_validator("base_url")
    @classmethod
    def _v_base_url(cls, v: str) -> str:
        return _check_url(v)

    @field_validator("timestamps")
    @classmethod
    def _v_timestamps(cls, v: List[str]) -> List[str]:
        cleaned = []
        for item in v:
            item = (item or "").strip()
            if not item:
                continue
            if len(item) > 20:
                raise ValueError("时间点格式过长")
            cleaned.append(item)
        return cleaned


class ContributionResult(BaseModel):
    """投稿提交结果。"""

    id: str
    type: Literal["source", "ad"]
    status: str = "pending"
    created_at: str
