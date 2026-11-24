"""投稿 REST 路由

- ``POST /api/contribute/source``：投稿数据源；
- ``POST /api/contribute/ad``：投稿广告信息；
- ``GET  /api/contribute/list?type=source|ad&limit=N``：查看最近投稿（管理/前端回显用）。

投稿只落盘为 ``pending``，不直接生效；是否接入由人工审核后转成插件配置。
本模块不依赖 media_source / ad_filter，仅做收集与存储。
"""
import logging
from typing import List, Literal

from fastapi import APIRouter, Query

from contribute import store
from contribute.models import (
    AdContribution,
    ContributionResult,
    SourceContribution,
)

logger = logging.getLogger("contribute.api")

api_router = APIRouter(prefix="/api/contribute", tags=["投稿"])


def _result(record: dict, kind: str) -> ContributionResult:
    return ContributionResult(
        id=record["id"],
        type=kind,
        status=record.get("status", "pending"),
        created_at=record.get("created_at", ""),
    )


@api_router.post(
    "/source", response_model=ContributionResult, summary="投稿数据源"
)
async def contribute_source(payload: SourceContribution) -> ContributionResult:
    """接收数据源投稿（站点名/根地址/说明/样本链接），落盘为 pending。"""
    record = store.append("source", payload.model_dump())
    logger.info("收到数据源投稿 %s：%s", record["id"], payload.base_url)
    return _result(record, "source")


@api_router.post("/ad", response_model=ContributionResult, summary="投稿广告信息")
async def contribute_ad(payload: AdContribution) -> ContributionResult:
    """接收广告信息投稿（类型/位置/样本时间点/词表），落盘为 pending。"""
    record = store.append("ad", payload.model_dump())
    logger.info("收到广告投稿 %s：%s %s", record["id"], payload.base_url, payload.ad_type)
    return _result(record, "ad")


@api_router.get("/list", summary="查看最近投稿")
async def list_contributions(
    type: Literal["source", "ad"] = Query("source", description="投稿类型"),
    limit: int = Query(50, ge=1, le=200, description="返回条数上限"),
) -> List[dict]:
    """返回最近投稿（倒序），供前端回显与人工审核。"""
    return store.list_recent(type, limit=limit)
