"""索尼采集站（suonizy.net）插件主类、映射模板、抽象方法实现"""
from typing import Any, Dict, List

from media_source.base import MediaSourcePlugin
from media_source.models import MediaInfo, PlaySource, SearchItem


class SuonizyPlugin(MediaSourcePlugin):
    """索尼采集站（suonizy.net）站点插件。

    站点为苹果CMS；官方采集接口（suoniapi.com）已开放详情与播放，
    但关键词搜索被站方关闭（返回“暂不支持搜索”），搜索方法返回空列表。
    """

    # ---- 站点元信息 ----
    base_url = "https://suonizy.net"
    source_name = "索尼采集站"
    source_desc = (
        "索尼采集站（suonizy.net）：苹果CMS 采集接口（suoniapi.com）；"
        "站方暂未开放关键词搜索，详情 / m3u8 播放可用"
    )

    # ---- 字段映射模板（白名单）----
    search_mapping: Dict[str, str] = {
        "name": "{title} | default:'未知影片'",
        "link": "{href} | default:''",
        "type": "{type} | default:''",
        "year": "{year} | default:''",
        "cover": "{cover} | default:''",
        "desc": "{intro} | default:''",
    }

    info_mapping: Dict[str, str] = {
        "name": "{title} | default:'未知影片'",
        "link": "{href} | default:''",
        "type": "{type} | default:''",
        "year": "{year} | default:''",
        "cover": "{cover} | default:''",
        "desc": "{intro} | default:''",
    }

    episode_mapping: Dict[str, str] = {
        "name": "{name} | default:''",
        "index": "{index} | default:0",
        "link": "{url} | default:''",
    }

    play_mapping: Dict[str, str] = {
        "url": "{play_url} | default:''",
        "type": "{play_type} | default:'m3u8'",
        "headers": "{headers} | default:{}",
    }

    # ---- 抽象方法实现 ----
    async def _raw_search(self, key: str) -> List[Dict[str, Any]]:
        from . import parser

        return await parser.parse_search(key)

    async def _raw_search_page(self, key: str, page: int) -> List[Dict[str, Any]]:
        from . import parser

        return await parser.parse_search_page(key, page)

    async def _raw_get_info(self, search_item: SearchItem) -> Dict[str, Any]:
        from . import parser

        return await parser.parse_info(search_item.link)

    async def _raw_get_play_url(
        self, media_info: MediaInfo, episode_index: int
    ) -> Dict[str, Any]:
        from . import parser

        episode = next(
            (ep for ep in media_info.episodes if ep.index == episode_index),
            None,
        )
        if episode is None:
            return {}
        return await parser.parse_play_url(episode.link)
