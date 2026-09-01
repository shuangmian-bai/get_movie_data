"""文件缓存接入示例：在应用层用 FileCache 包装插件调用，防止重复爬虫

演示：同一关键词连续搜索两次，第一次走网络，第二次命中缓存（耗时明显更短）。
"""
import asyncio
import time
from typing import List

from media_source import config, plugin_manager
from media_source.cache import file_cache
from media_source.models import MediaInfo, PlaySource, SearchItem


async def search_with_cache(key: str, base_url: str) -> List[SearchItem]:
    """带缓存的搜索（应用层编排：先查缓存，未命中才调用插件）。"""
    plugin = plugin_manager.get_plugin_instance(base_url)
    namespace = file_cache.namespace_of(plugin.base_url)

    async def fetch():
        items = await plugin.search(key)
        return [item.model_dump() for item in items]

    data = await file_cache.get_or_fetch(
        namespace, f"search:{key}", config.SEARCH_CACHE_TTL, fetch
    )
    return [SearchItem(**d) for d in data]


async def info_with_cache(item: SearchItem) -> MediaInfo:
    """带缓存的详情获取。"""
    plugin = plugin_manager.get_plugin_instance(item.base_url)
    namespace = file_cache.namespace_of(plugin.base_url)

    async def fetch():
        info = await plugin.get_info(item)
        return info.model_dump()

    data = await file_cache.get_or_fetch(
        namespace, f"info:{item.link}", config.INFO_CACHE_TTL, fetch
    )
    return MediaInfo(**data)


async def play_with_cache(info: MediaInfo, episode_index: int) -> PlaySource:
    """带缓存的播放地址获取。"""
    plugin = plugin_manager.get_plugin_instance(info.base_url)
    namespace = file_cache.namespace_of(plugin.base_url)
    episode = next((e for e in info.episodes if e.index == episode_index), None)
    cache_key = f"play:{episode.link if episode else episode_index}"

    async def fetch():
        play = await plugin.get_play_url(info, episode_index)
        return play.model_dump()

    data = await file_cache.get_or_fetch(
        namespace, cache_key, config.PLAY_CACHE_TTL, fetch
    )
    return PlaySource(**data)


async def main() -> None:
    base_url = "https://yhdm.one"

    # 第一次：未命中，走网络
    t0 = time.perf_counter()
    items = await search_with_cache("仙逆", base_url)
    t1 = time.perf_counter()
    print(f"第 1 次搜索「仙逆」：{len(items)} 条，耗时 {t1 - t0:.3f}s（走网络）")

    # 第二次：命中缓存
    t2 = time.perf_counter()
    items2 = await search_with_cache("仙逆", base_url)
    t3 = time.perf_counter()
    print(f"第 2 次搜索「仙逆」：{len(items2)} 条，耗时 {t3 - t2:.3f}s（命中缓存）")

    # 详情 + 播放（同样带缓存）
    info = await info_with_cache(items[0])
    print(f"详情：{info.name}，共 {len(info.episodes)} 集")

    play = await play_with_cache(info, info.episodes[-1].index)
    print(f"播放：{play.type} -> {play.url}")


if __name__ == "__main__":
    asyncio.run(main())
