"""插件管理器（扫描、加载、并发、对外接口）

模块核心调度中心，所有对外能力均由此提供：
- 插件自动扫描与注册；
- 数据源列表查询；
- 单插件实例获取；
- 多源批量并发搜索（限流 + 异常隔离）。
"""
import asyncio
import importlib
import logging
import pkgutil
from typing import Dict, List, Optional

from media_source import config
from media_source.base import MediaSourcePlugin
from media_source.exceptions import PluginNotFoundError
from media_source.models import SearchItem, SourceMeta

logger = logging.getLogger("media_source.plugin_manager")

# 扫描时跳过的目录名（插件开发模板，非真实站点）
_TEMPLATE_DIR = "template"


class PluginManager:
    """插件管理器。"""

    def __init__(self) -> None:
        self._plugins: Dict[str, MediaSourcePlugin] = {}  # base_url -> 插件实例

    # ---- 插件扫描与加载 ----
    def scan_plugins(self) -> int:
        """自动扫描 ``media_source.plugins`` 下所有站点包并注册插件。

        返回成功加载的插件数量；单个插件加载失败仅记录日志，不影响其他插件。
        """
        import media_source.plugins as plugins_pkg

        loaded = 0
        for _finder, name, ispkg in pkgutil.iter_modules(plugins_pkg.__path__):
            if not ispkg or name == _TEMPLATE_DIR:
                continue
            try:
                module = importlib.import_module(f"media_source.plugins.{name}")
                plugin_cls = self._find_plugin_class(module)
                if plugin_cls is None:
                    logger.warning("插件包 %s 未发现插件主类，跳过", name)
                    continue

                instance = plugin_cls()
                if not instance.base_url:
                    logger.warning("插件 %s 缺少 base_url，跳过", name)
                    continue

                self._plugins[instance.base_url] = instance
                loaded += 1
                logger.info("已加载插件: %s (%s)", instance.source_name, instance.base_url)
            except Exception as exc:  # noqa: BLE001 - 插件隔离，不阻断扫描
                logger.exception("加载插件包 %s 失败: %s", name, exc)
        return loaded

    @staticmethod
    def _find_plugin_class(module):
        """在插件包模块中查找 ``MediaSourcePlugin`` 的具体子类。"""
        for attr in dir(module):
            obj = getattr(module, attr)
            if (
                isinstance(obj, type)
                and issubclass(obj, MediaSourcePlugin)
                and obj is not MediaSourcePlugin
            ):
                return obj
        return None

    # ---- 数据源查询 ----
    def get_supported_sources(self) -> List[SourceMeta]:
        """同步获取全部可用数据源列表（对外展示 / 合法性校验）。"""
        return [
            SourceMeta(
                base_url=p.base_url,
                source_name=p.source_name,
                source_desc=p.source_desc,
            )
            for p in self._plugins.values()
        ]

    def get_plugin_instance(self, base_url: str) -> MediaSourcePlugin:
        """获取单个插件实例，用于单源精准调用。

        无匹配插件时抛出 :class:`PluginNotFoundError`。
        """
        plugin = self._plugins.get(base_url)
        if plugin is None:
            raise PluginNotFoundError(f"未找到 base_url={base_url} 对应的站点插件")
        return plugin

    # ---- 批量并发搜索 ----
    async def batch_search(
        self,
        key: str,
        base_urls: Optional[List[str]] = None,
        max_concurrency: Optional[int] = None,
        start: int = 0,
        count: Optional[int] = None,
        page_concurrency: Optional[int] = None,
    ) -> List[SearchItem]:
        """批量并发搜索核心接口。

        - ``base_urls=[]`` 或 ``None``：全量已加载插件并发搜索；
        - ``base_urls=[url1, url2]``：指定多源 / 单源搜索；
        - 列表内无效 URL 自动过滤，日志告警，不影响整体任务；
        - 单插件失败仅记录日志，聚合有效结果返回；
        - ``start``/``count``：分页参数，透传给各插件的分页搜索；
        - ``page_concurrency``：单插件翻页并发抓取页数（默认取配置）。
        """
        base_urls = base_urls or []
        if base_urls:
            plugins: List[MediaSourcePlugin] = []
            for url in base_urls:
                plugin = self._plugins.get(url)
                if plugin is not None:
                    plugins.append(plugin)
                else:
                    logger.warning("无效站点 URL，自动跳过: %s", url)
        else:
            plugins = list(self._plugins.values())

        if not plugins:
            logger.warning("无可用插件执行搜索")
            return []

        concurrency = max_concurrency or config.MAX_PLUGIN_CONCURRENCY
        semaphore = asyncio.Semaphore(concurrency)

        async def _run(plugin: MediaSourcePlugin) -> List[SearchItem]:
            async with semaphore:
                try:
                    return await plugin.search_page(
                        key, start=start, count=count, page_concurrency=page_concurrency
                    )
                except Exception as exc:  # noqa: BLE001 - 异常隔离，不中断整体任务
                    logger.error(
                        "插件 %s 搜索失败: %s", plugin.source_name, exc, exc_info=True
                    )
                    return []

        results = await asyncio.gather(*[_run(p) for p in plugins])
        return [item for sub in results for item in sub]


# 全局单例，便于业务直接调用（导入时自动扫描插件）
plugin_manager = PluginManager()
plugin_manager.scan_plugins()
