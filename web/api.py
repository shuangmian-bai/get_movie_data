"""Web 服务路由 —— 基于 FastAPI 的 REST API

通过 media_source 的公开接口提供服务，并在本层完成缓存编排（防止重复爬虫）。
本模块不依赖 media_source 内部实现，仅使用其稳定公开能力，保持低耦合。
"""
from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query

from media_source import config, plugin_manager
from media_source.cache import file_cache
from media_source.exceptions import MediaSourceError, PluginNotFoundError
from media_source.models import MediaInfo, PlaySource, SearchItem, SourceMeta

api_router = APIRouter(prefix="/api", tags=["影视数据源"])


# ---- 缓存编排辅助（应用层汇总：数据源 + 缓存 组合） ----
def _get_plugin(base_url: str):
    """按站点标识获取插件实例，无匹配转为 404。"""
    try:
        return plugin_manager.get_plugin_instance(base_url)
    except PluginNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc))


def _search_cache_key(key: str, start: int, count: Optional[int]) -> str:
    """构造搜索缓存 key（含分页维度，不同分页参数不共享缓存）。"""
    count_str = "all" if count is None else str(count)
    return f"search:{key}:{start}:{count_str}"


async def _search_one(
    plugin,
    key: str,
    start: int = 0,
    count: Optional[int] = None,
    page_concurrency: Optional[int] = None,
) -> List[SearchItem]:
    """单源搜索（带缓存，按分页参数区分缓存）。"""
    namespace = file_cache.namespace_of(plugin.base_url)
    cache_key = _search_cache_key(key, start, count)

    async def fetch():
        items = await plugin.search_page(
            key, start=start, count=count, page_concurrency=page_concurrency
        )
        return [item.model_dump() for item in items]

    data = await file_cache.get_or_fetch(
        namespace, cache_key, config.SEARCH_CACHE_TTL, fetch
    )
    return [SearchItem(**d) for d in data]


async def _search_batch(
    key: str,
    base_urls: Optional[List[str]] = None,
    start: int = 0,
    count: Optional[int] = None,
    page_concurrency: Optional[int] = None,
) -> List[SearchItem]:
    """批量搜索（带缓存）：``base_urls`` 为空则全源，非空则只搜指定源（按源组合区分缓存）。"""
    async def fetch():
        items = await plugin_manager.batch_search(
            key, base_urls or [], start=start, count=count, page_concurrency=page_concurrency
        )
        return [item.model_dump() for item in items]

    srcs = ",".join(sorted(base_urls)) if base_urls else "_all"
    cache_key = f"{_search_cache_key(key, start, count)}:{srcs}"
    data = await file_cache.get_or_fetch("_all", cache_key, config.SEARCH_CACHE_TTL, fetch)
    return [SearchItem(**d) for d in data]


async def _get_info(plugin, link: str) -> MediaInfo:
    """详情获取（带缓存）。"""
    namespace = file_cache.namespace_of(plugin.base_url)
    item = SearchItem(base_url=plugin.base_url, link=link)

    async def fetch():
        info = await plugin.get_info(item)
        return info.model_dump()

    data = await file_cache.get_or_fetch(
        namespace, f"info:{link}", config.INFO_CACHE_TTL, fetch
    )
    return MediaInfo(**data)


async def _get_play(plugin, link: str, episode_index: int) -> PlaySource:
    """播放地址获取（带缓存；详情复用 info 缓存，不重复爬详情页）。"""
    namespace = file_cache.namespace_of(plugin.base_url)
    info = await _get_info(plugin, link)

    async def fetch():
        play = await plugin.get_play_url(info, episode_index)
        return play.model_dump()

    data = await file_cache.get_or_fetch(
        namespace, f"play:{link}:{episode_index}", config.PLAY_CACHE_TTL, fetch
    )
    return PlaySource(**data)


# ---- 路由 ----
@api_router.get("/sources", response_model=List[SourceMeta], summary="可用数据源列表")
async def list_sources() -> List[SourceMeta]:
    """返回当前已加载的可用数据源。"""
    return plugin_manager.get_supported_sources()


@api_router.get("/search", response_model=List[SearchItem], summary="搜索影视")
async def search(
    key: str = Query(..., min_length=1, description="关键词"),
    base_url: str = Query("", description="站点标识（单源），空则按 base_urls / 全源"),
    base_urls: Optional[List[str]] = Query(None, description="站点标识列表（多源），空则全源"),
    start: int = Query(0, ge=0, description="分页起始偏移（从 0 开始）"),
    count: Optional[int] = Query(None, ge=1, description="返回条数，空则返回全部"),
    page_concurrency: Optional[int] = Query(None, ge=1, description="分页并发抓取页数"),
) -> List[SearchItem]:
    try:
        if base_url:
            return await _search_one(
                _get_plugin(base_url), key, start, count, page_concurrency
            )
        return await _search_batch(key, base_urls, start, count, page_concurrency)
    except MediaSourceError as exc:
        raise HTTPException(status_code=502, detail=str(exc))


@api_router.get("/info", response_model=MediaInfo, summary="影视详情")
async def info(
    base_url: str = Query(..., description="站点标识"),
    link: str = Query(..., description="详情页链接"),
) -> MediaInfo:
    try:
        return await _get_info(_get_plugin(base_url), link)
    except MediaSourceError as exc:
        raise HTTPException(status_code=502, detail=str(exc))


@api_router.get("/play", response_model=PlaySource, summary="播放地址")
async def play(
    base_url: str = Query(..., description="站点标识"),
    link: str = Query(..., description="详情页链接"),
    episode_index: int = Query(..., ge=1, description="集数序号（从 1 开始）"),
) -> PlaySource:
    try:
        return await _get_play(_get_plugin(base_url), link, episode_index)
    except MediaSourceError as exc:
        raise HTTPException(status_code=502, detail=str(exc))
