"""示例：多源 / 全源批量并发搜索

运行方式（在项目根目录执行）::

    python -m media_source.examples.demo_batch_search
"""
import asyncio

from media_source import plugin_manager


async def main() -> None:
    key = "流浪"

    print("== 全源扫描（base_urls=[]）==")
    results = await plugin_manager.batch_search(key, [])
    for item in results:
        print(f"  [{item.base_url}] {item.name} / {item.type} / {item.year}")

    print("\n== 指定单源扫描 ==")
    results = await plugin_manager.batch_search(
        key, ["https://www.site-a.example.com"]
    )
    for item in results:
        print(f"  [{item.base_url}] {item.name} / {item.year}")

    print("\n== 指定多源 + 自定义并发数 ==")
    results = await plugin_manager.batch_search(
        key,
        [
            "https://www.site-a.example.com",
            "https://www.site-b.example.com",
            "https://www.invalid-site.example.com",  # 无效 URL，自动跳过
        ],
        max_concurrency=3,
    )
    for item in results:
        print(f"  [{item.base_url}] {item.name} / {item.type}")


if __name__ == "__main__":
    asyncio.run(main())
