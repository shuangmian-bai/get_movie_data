"""示例：单源详情、播放地址查询

运行方式（在项目根目录执行）::

    python -m media_source.examples.demo_single_plugin
"""
import asyncio

from media_source import plugin_manager
from media_source.exceptions import PluginNotFoundError


async def main() -> None:
    base_url = "https://www.site-a.example.com"
    try:
        plugin = plugin_manager.get_plugin_instance(base_url)
    except PluginNotFoundError as exc:
        print(f"获取插件失败: {exc}")
        return

    # 单源搜索（异常直接抛出，业务自行处理）
    items = await plugin.search("三体")
    if not items:
        print("未搜索到结果")
        return

    item = items[0]
    print(f"搜索结果: {item.name} / {item.type} / {item.year}")

    # 获取详情
    info = await plugin.get_info(item)
    print(f"详情: {info.name}，共 {len(info.episodes)} 集")

    # 获取第一集播放地址
    if info.episodes:
        play = await plugin.get_play_url(info, info.episodes[0].index)
        print(f"第 {info.episodes[0].index} 集播放地址: {play.url} ({play.type})")
        print(f"请求头: {play.headers}")


if __name__ == "__main__":
    asyncio.run(main())
