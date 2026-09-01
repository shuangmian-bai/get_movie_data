"""站点 A 插件主类、映射模板、抽象方法实现"""
from typing import Any, Dict, List

from media_source.base import MediaSourcePlugin
from media_source.models import MediaInfo, PlaySource, SearchItem


class SiteAPlugin(MediaSourcePlugin):
    """示例站点 A（原始字段：title/href/type/year/cover/intro）"""

    # ---- 站点元信息 ----
    base_url = "https://www.site-a.example.com"
    source_name = "示例站点A"
    source_desc = "示例站点A：演示搜索/详情/播放完整链路（m3u8/mp4）"

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
        from media_source.plugins.site_a import parser

        return await parser.parse_search(key)

    async def _raw_get_info(self, search_item: SearchItem) -> Dict[str, Any]:
        from media_source.plugins.site_a import parser

        return await parser.parse_info(search_item.link)

    async def _raw_get_play_url(
        self, media_info: MediaInfo, episode_index: int
    ) -> Dict[str, Any]:
        from media_source.plugins.site_a import parser

        # 根据集数序号找到对应分集的链接
        episode = next(
            (ep for ep in media_info.episodes if ep.index == episode_index),
            None,
        )
        if episode is None:
            return {}
        return await parser.parse_play_url(episode.link)
