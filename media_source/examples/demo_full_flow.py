"""示例：完整业务链路演示

关键词搜索 -> 影视详情获取 -> 分集播放地址解析（m3u8/mp4）

运行方式（在项目根目录执行）::

    python -m media_source.examples.demo_full_flow
"""
import asyncio

from media_source import plugin_manager


async def main() -> None:
    key = "流浪"

    # 1. 全源批量搜索
    results = await plugin_manager.batch_search(key, [])
    print(f"搜索到 {len(results)} 条结果：")
    for idx, item in enumerate(results):
        print(f"  [{idx}] {item.name} ({item.base_url}) {item.year}")

    if not results:
        return

    # 2. 取第一条，按 base_url 路由回对应插件
    target = results[0]
    plugin = plugin_manager.get_plugin_instance(target.base_url)

    # 3. 获取详情
    info = await plugin.get_info(target)
    print(f"\n详情: {info.name}，共 {len(info.episodes)} 集")

    # 4. 解析第一集播放地址
    if info.episodes:
        play = await plugin.get_play_url(info, info.episodes[0].index)
        print(f"播放地址: {play.url} ({play.type})")


if __name__ == "__main__":
    asyncio.run(main())
