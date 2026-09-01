"""樱花动漫（yhdm.one）插件主类、映射模板、抽象方法实现"""
from typing import Any, Dict, List

from media_source.base import MediaSourcePlugin
from media_source.models import MediaInfo, PlaySource, SearchItem


class YhdmPlugin(MediaSourcePlugin):
    """樱花动漫（yhdm.one）站点插件。

    原始字段：title/href/cover/year/type/intro + episodes（name/url/index）。
    """

    # ---- 站点元信息 ----
    base_url = "https://yhdm.one"
    source_name = "樱花动漫"
    source_desc = "樱花动漫（yhdm.one）：动漫搜索 / 详情 / m3u8 播放地址解析"

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

    # ---- 抽象方法实现（仅输出原始数据）----
    async def _raw_search(self, key: str) -> List[Dict[str, Any]]:
        from media_source.plugins.yhdm import parser

        return await parser.parse_search(key)

    async def _raw_get_info(self, search_item: SearchItem) -> Dict[str, Any]:
        from media_source.plugins.yhdm import parser

        return await parser.parse_info(search_item.link)

    async def _raw_get_play_url(
        self, media_info: MediaInfo, episode_index: int
    ) -> Dict[str, Any]:
        from media_source.plugins.yhdm import parser

        episode = next(
            (ep for ep in media_info.episodes if ep.index == episode_index),
            None,
        )
        if episode is None:
            return {}
        return await parser.parse_play_url(episode.link)
