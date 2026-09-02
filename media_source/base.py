"""插件抽象基类（核心规范）

定义统一接口、映射模板规范、原始数据标准化流程与异常边界。

职责划分
--------
- 插件实现层（子类）：仅负责请求站点、解析原始数据，输出原始脏数据字典；
- 基类核心层：统一调用映射引擎完成白名单过滤、占位符取值、默认值兜底，
  并注入 ``base_url`` 等路由字段，最终返回 Pydantic 标准对象。

子类必须自定义 6 个基础类属性 + 实现 3 个 ``_raw_*`` 抽象方法。
"""
import abc
import asyncio
import logging
from typing import Any, Dict, List, Optional

from media_source import config
from media_source.mapping import map_data
from media_source.models import EpisodeItem, MediaInfo, PlaySource, SearchItem

logger = logging.getLogger("media_source.base")


class MediaSourcePlugin(abc.ABC):
    """影视站点插件抽象基类。"""

    # ---- 基础类属性（各插件必须自定义）----
    base_url: str = ""            # 站点唯一标识 URL
    source_name: str = ""         # 站点友好名称
    source_desc: str = ""         # 站点描述信息
    search_mapping: Dict[str, str] = {}   # 搜索结果映射模板
    info_mapping: Dict[str, str] = {}     # 详情数据映射模板
    play_mapping: Dict[str, str] = {}     # 播放地址映射模板
    episode_mapping: Dict[str, str] = {}  # 分集映射模板（可选，用于统一分集列表）

    # ---- 子类必须实现的抽象方法（仅输出原始数据）----
    @abc.abstractmethod
    async def _raw_search(self, key: str) -> List[Dict[str, Any]]:
        """返回站点原始搜索字典列表（禁止字段过滤、禁止返回标准模型）。"""

    async def _raw_search_page(self, key: str, page: int) -> List[Dict[str, Any]]:
        """返回站点指定页码的原始搜索字典列表（page 从 1 开始）。

        支持分页搜索的站点应覆盖此方法；不支持分页的站点保留默认实现
        （抛出 :class:`NotImplementedError`），框架将降级为抓取全部后切片。
        """
        raise NotImplementedError(f"站点 {self.source_name} 不支持分页搜索")

    @abc.abstractmethod
    async def _raw_get_info(self, search_item: SearchItem) -> Dict[str, Any]:
        """返回站点原始详情字典（禁止字段过滤、禁止返回标准模型）。"""

    @abc.abstractmethod
    async def _raw_get_play_url(
        self, media_info: MediaInfo, episode_index: int
    ) -> Dict[str, Any]:
        """返回原始播放地址字典（禁止字段过滤、禁止返回标准模型）。"""

    # ---- 对外公开方法（统一封装，自动格式化）----
    async def search(self, key: str) -> List[SearchItem]:
        """搜索影视（全量），自动格式化返回标准数据。"""
        raw_list = await self._raw_search(key)
        return self._format_search_items(raw_list)

    async def search_page(
        self,
        key: str,
        start: int = 0,
        count: Optional[int] = None,
        page_concurrency: Optional[int] = None,
    ) -> List[SearchItem]:
        """分页搜索：只抓取覆盖 [start, start+count) 的站点分页，并发抓取后切片返回。

        - ``start``：起始偏移（0-based，第 1 条的序号为 0）；
        - ``count``：期望返回条数；``None`` 表示全量搜索（等价 :meth:`search`）；
        - ``page_concurrency``：并发抓取页数，默认取 ``config.PAGE_CONCURRENCY``。

        站点支持分页时，先抓第 1 页探明每页条数，再并发抓取覆盖区间的页码，
        最后切片返回；站点不支持分页时，降级为抓取全部后按区间切片。
        """
        start = max(start, 0)
        if count is None:
            return await self.search(key)
        if count <= 0:
            return []

        try:
            first_raw = await self._raw_search_page(key, 1)
        except NotImplementedError:
            # 站点不支持分页：抓全部后切片
            return (await self.search(key))[start : start + count]

        page_size = len(first_raw)
        if page_size == 0:
            return []

        # 覆盖区间对应的页码（1-based）
        start_page = start // page_size + 1
        end_page = (start + count - 1) // page_size + 1

        pages: Dict[int, List[Dict[str, Any]]] = {}
        if start_page <= 1 <= end_page:
            pages[1] = first_raw

        pending = [p for p in range(start_page, end_page + 1) if p not in pages]
        if pending:
            concurrency = page_concurrency or config.PAGE_CONCURRENCY
            sem = asyncio.Semaphore(concurrency)

            async def fetch_page(page: int):
                async with sem:
                    try:
                        return page, await self._raw_search_page(key, page)
                    except Exception as exc:  # noqa: BLE001 - 单页失败不中断整体
                        logger.warning(
                            "插件 %s 抓取第 %d 页失败: %s", self.source_name, page, exc
                        )
                        return page, []

            results = await asyncio.gather(*(fetch_page(p) for p in pending))
            for p, items in results:
                pages[p] = items

        # 按页码顺序合并后切片
        merged: List[Dict[str, Any]] = []
        for p in sorted(pages):
            merged.extend(pages[p])

        offset = start - (start_page - 1) * page_size
        return self._format_search_items(merged[offset : offset + count])

    async def get_info(self, search_item: SearchItem) -> MediaInfo:
        """获取影视详情，自动格式化返回标准数据。"""
        raw = await self._raw_get_info(search_item)
        mapped = map_data(raw, self.info_mapping)
        mapped["base_url"] = self.base_url
        mapped["episodes"] = self._map_episodes(raw)
        return MediaInfo.model_validate(mapped)

    async def get_play_url(self, media_info: MediaInfo, episode_index: int) -> PlaySource:
        """获取指定集数播放地址，自动格式化返回标准数据。"""
        raw = await self._raw_get_play_url(media_info, episode_index)
        mapped = map_data(raw, self.play_mapping)
        return PlaySource.model_validate(mapped)

    # ---- 内部辅助 ----
    def _format_search_items(self, raw_list: Any) -> List[SearchItem]:
        """把原始搜索字典列表映射为标准 SearchItem 列表（复用映射引擎）。"""
        if not isinstance(raw_list, list):
            raw_list = [raw_list] if raw_list else []

        items: List[SearchItem] = []
        for raw in raw_list:
            if not isinstance(raw, dict):
                continue
            mapped = map_data(raw, self.search_mapping)
            mapped["base_url"] = self.base_url  # 注入来源标识，用于单源路由
            items.append(SearchItem.model_validate(mapped))
        return items

    def _map_episodes(self, raw: Dict[str, Any]) -> List[EpisodeItem]:
        """从原始详情数据中提取并格式化分集列表。"""
        raw_episodes = (
            raw.get("episodes")
            or raw.get("play_list")
            or raw.get("episode_list")
        )
        if not isinstance(raw_episodes, list):
            return []

        result: List[EpisodeItem] = []
        for i, ep in enumerate(raw_episodes, start=1):
            if isinstance(ep, dict):
                if self.episode_mapping:
                    ep_mapped = map_data(ep, self.episode_mapping)
                else:
                    # 未配置分集映射时，做通用字段兜底
                    ep_mapped = {
                        "name": ep.get("name") or ep.get("title") or "",
                        "link": ep.get("link") or ep.get("url") or "",
                    }
                if not ep_mapped.get("index"):
                    ep_mapped["index"] = i
                result.append(EpisodeItem.model_validate(ep_mapped))
            elif isinstance(ep, str):
                result.append(EpisodeItem(name=ep, index=i))
        return result
