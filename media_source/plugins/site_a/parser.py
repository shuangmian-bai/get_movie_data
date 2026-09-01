"""站点 A 专属解析逻辑（原始数据提取）

说明：本项目为演示 / 模板工程，站点数据以本地模拟数据代替真实网络请求，
以便示例与测试可离线运行。实际接入时，将下方 MOCK 数据替换为真实
HTTP 请求 + 解析逻辑即可。

本文件仅输出原始字典，不做字段过滤与格式化。
"""
from typing import Any, Dict, List

from media_source.plugins.site_a.constants import abs_url

# ---- 模拟数据（实际开发替换为真实接口返回）----
_MOCK_SEARCH = [
    {
        "title": "三体",
        "href": "/detail/2001",
        "type": "剧集",
        "year": 2023,
        "cover": "https://img.site-a.com/santi.jpg",
        "intro": "纳米科学家汪淼与刑警史强共同调查科学家离奇自杀事件……",
        "score": "8.7",          # 多余脏字段，映射后应被丢弃
        "internal_id": 2001,     # 多余脏字段，映射后应被丢弃
    },
    {
        "title": "流浪地球",
        "href": "/detail/1001",
        "type": "电影",
        "year": 2019,
        "cover": "https://img.site-a.com/wandering.jpg",
        "intro": "太阳急速衰老膨胀，人类推动地球逃离太阳系……",
        "score": "9.2",
        "internal_id": 1001,
    },
]

_MOCK_INFO = {
    "2001": {
        "title": "三体",
        "href": "/detail/2001",
        "type": "剧集",
        "year": 2023,
        "cover": "https://img.site-a.com/santi.jpg",
        "intro": "改编自刘慈欣同名科幻小说。",
        "director": "杨磊",      # 脏字段，应被过滤
        "episodes": [
            {"name": "第1集", "url": "/play/2001-1"},
            {"name": "第2集", "url": "/play/2001-2"},
            {"name": "第3集", "url": "/play/2001-3"},
        ],
    },
    "1001": {
        "title": "流浪地球",
        "href": "/detail/1001",
        "type": "电影",
        "year": 2019,
        "cover": "https://img.site-a.com/wandering.jpg",
        "intro": "改编自刘慈欣同名小说。",
        "director": "郭帆",
        "episodes": [{"name": "正片", "url": "/play/1001-1"}],
    },
}

_MOCK_PLAY = {
    "2001-1": {
        "play_url": "https://cdn.site-a.com/santi/01.m3u8",
        "play_type": "m3u8",
        "headers": {"Referer": "https://www.site-a.example.com/"},
    },
    "2001-2": {
        "play_url": "https://cdn.site-a.com/santi/02.m3u8",
        "play_type": "m3u8",
        "headers": {},
    },
    "1001-1": {
        "play_url": "https://cdn.site-a.com/wandering/01.mp4",
        "play_type": "mp4",
        "headers": {},
    },
}


async def parse_search(key: str) -> List[Dict[str, Any]]:
    """模拟搜索：返回匹配关键词的原始结果列表。"""
    # 真实实现示例：
    #   from media_source.utils.http import fetch_json
    #   data = await fetch_json(SEARCH_API, params={"wd": key})
    #   return [ ... ]
    return [
        {
            **item,
            "href": abs_url(item["href"]),
        }
        for item in _MOCK_SEARCH
        if key in item["title"]
    ]


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """模拟详情：根据详情页链接返回原始详情字典。"""
    # 从链接中提取资源 ID（演示用，实际以真实接口为准）
    resource_id = detail_url.rstrip("/").split("/")[-1]
    data = _MOCK_INFO.get(resource_id)
    if data is None:
        return {}
    return {
        **data,
        "href": abs_url(data["href"]),
        "episodes": [
            {"name": ep["name"], "url": abs_url(ep["url"])}
            for ep in data["episodes"]
        ],
    }


async def parse_play_url(play_url: str) -> Dict[str, Any]:
    """模拟播放地址：根据分集链接返回原始播放地址字典。"""
    resource_id = play_url.rstrip("/").split("/")[-1]
    return _MOCK_PLAY.get(resource_id, {})
