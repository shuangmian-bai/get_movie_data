"""插件主类、映射模板、抽象方法实现（模板）

复制本文件夹后，按目标站点修改下述内容：
1. ``base_url`` / ``source_name`` / ``source_desc`` 三个元信息属性；
2. ``search_mapping`` / ``info_mapping`` / ``play_mapping`` 三套映射模板；
3. 三个 ``_raw_*`` 方法，调用 ``parser.py`` 中的解析逻辑。
"""
from typing import Any, Dict, List

from media_source.base import MediaSourcePlugin
from media_source.models import MediaInfo, PlaySource, SearchItem


class TemplatePlugin(MediaSourcePlugin):
    """站点插件模板类。"""

    # ---- 站点元信息 ----
    base_url = "https://www.example.com"
    source_name = "示例站点"
    source_desc = "站点描述信息"

    # ---- 字段映射模板（白名单）----
    search_mapping: Dict[str, str] = {
        "name": "{title} | default:''",
        "link": "{href} | default:''",
        "type": "{type} | default:''",
        "year": "{year} | default:''",
        "cover": "{cover} | default:''",
        "desc": "{intro} | default:''",
    }

    info_mapping: Dict[str, str] = {
        "name": "{title} | default:''",
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
        from media_source.plugins.template import parser

        return await parser.parse_search(key)

    async def _raw_get_info(self, search_item: SearchItem) -> Dict[str, Any]:
        from media_source.plugins.template import parser

        return await parser.parse_info(search_item.link)

    async def _raw_get_play_url(
        self, media_info: MediaInfo, episode_index: int
    ) -> Dict[str, Any]:
        from media_source.plugins.template import parser

        return await parser.parse_play_url(str(episode_index))
