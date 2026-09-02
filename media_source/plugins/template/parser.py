"""站点专属解析逻辑（模板）

本文件仅负责请求站点并提取原始数据，输出原始字典，不做任何字段过滤与格式化。
所有站点私有配置、加密逻辑、解析逻辑全部封装在当前插件包内。
"""
from typing import Any, Dict, List


async def parse_search(key: str) -> List[Dict[str, Any]]:
    """请求搜索接口并解析为原始字典列表。

    真实实现示例::

        from media_source.utils.http import fetch_json
        data = await fetch_json(SEARCH_API, params={"wd": key})
        return [{"title": item["name"], "href": item["url"], ...} for item in data["list"]]
    """
    raise NotImplementedError("请实现站点搜索解析逻辑")


async def parse_search_page(key: str, page: int) -> List[Dict[str, Any]]:
    """请求搜索接口，抓取指定页码并解析为原始字典列表（page 从 1 开始）。

    仅当站点支持分页时实现；不实现则插件保留基类默认 ``_raw_search_page``
    （抛 ``NotImplementedError``），框架将降级为抓取全部后切片。
    """
    raise NotImplementedError("请实现站点分页搜索解析逻辑")


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """请求详情页并解析为原始字典。"""
    raise NotImplementedError("请实现站点详情解析逻辑")


async def parse_play_url(play_url: str) -> Dict[str, Any]:
    """请求播放页并解析出原始播放地址字典。"""
    raise NotImplementedError("请实现站点播放地址解析逻辑")
