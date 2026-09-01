"""示例：查询可用数据源列表

运行方式（在项目根目录执行）::

    python -m media_source.examples.demo_get_sources
"""
from media_source import plugin_manager


def main() -> None:
    sources = plugin_manager.get_supported_sources()
    print(f"当前可用数据源共 {len(sources)} 个：")
    for source in sources:
        print(f"  - {source.source_name} ({source.base_url})")
        print(f"      描述: {source.source_desc}")


if __name__ == "__main__":
    main()
