"""樱花动漫（yhdm.one）站点专属解析逻辑（原始数据提取）

站点为服务端渲染，无需浏览器执行 JS，直接 httpx 请求 + parsel 解析即可：

- 搜索：``GET /search?q=<关键词>``，结果在 ``ul.list-unstyled li`` 中
- 详情：``GET /vod/<id>.html``，信息在 ``.detail-left .small``，分集在 ``a[href*=vod-play]``
- 播放：``GET /_get_plays/<vod_id>/<ep_name>``，返回 JSON 的 ``video_plays[].play_data``

本文件仅输出原始字典，不做字段过滤与格式化。
"""
import re
from typing import Any, Dict, List

from parsel import Selector

from media_source.utils.helpers import normalize_url
from media_source.utils.http import fetch_json, fetch_text
from media_source.plugins.yhdm.constants import HEADERS, PLAY_API_TMPL, SEARCH_URL, abs_url

# 分集链接：/vod-play/<vod_id>/<ep_name>.html
_EP_LINK_RE = re.compile(r"/vod-play/(\d+)/(.+?)\.html")


async def parse_search(key: str) -> List[Dict[str, Any]]:
    """请求搜索接口并解析为原始字典列表。"""
    html = await fetch_text(SEARCH_URL, params={"q": key}, headers=HEADERS)
    selector = Selector(html)

    results: List[Dict[str, Any]] = []
    for li in selector.css("ul.list-unstyled li"):
        name = (
            (li.css("h6 a::attr(title)").get() or "").strip()
            or (li.css("h6 a::text").get() or "").strip()
        )
        link = li.css("h6 a::attr(href)").get() or ""
        cover = li.css("img::attr(src)").get() or ""

        text = re.sub(r"\s+", " ", "".join(li.css("::text").getall()))
        year = re.search(r"年份：\s*(\d+)", text)
        type_match = re.search(r"类型：\s*(\S+)", text)
        intro_match = re.search(r"剧情简介：\s*(.*)", text)

        results.append(
            {
                "title": name,
                "href": abs_url(link),
                "cover": abs_url(cover),
                "year": year.group(1) if year else "",
                "type": type_match.group(1) if type_match else "",
                "intro": (intro_match.group(1) if intro_match else "").strip(),
            }
        )
    return results


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """请求详情页并解析为原始字典（含分集列表）。"""
    html = await fetch_text(detail_url, headers=HEADERS)
    selector = Selector(html)

    title = (selector.css("h1.names::text").get() or "").strip()
    cover = selector.css(".detail-poster img::attr(src)").get() or ""

    # 信息区（原名/年代/类型等）与简介分别是两个 .small 容器
    info_text = ""
    desc = ""
    for small in selector.css(".detail-left .small"):
        text = re.sub(r"\s+", " ", "".join(small.css("::text").getall())).strip()
        if "原名" in text and "年代" in text:
            info_text = text
        else:
            desc = text

    year = ""
    type_str = ""
    if info_text:
        year_match = re.search(r"年代：\s*(\d+)", info_text)
        year = year_match.group(1) if year_match else ""
        type_match = re.search(r"类型：\s*(.*?)\s*(?:标签|更新至|$)", info_text)
        type_str = type_match.group(1).strip() if type_match else ""

    # 分集列表（站点默认倒序，这里统一为正序）
    episodes: List[Dict[str, Any]] = []
    for a in selector.css("a[href*=vod-play]"):
        ep_name = (a.css("::text").get() or "").strip()
        ep_link = a.css("::attr(href)").get() or ""
        idx = re.search(r"(\d+)", ep_name)
        episodes.append(
            {
                "name": ep_name,
                "url": abs_url(ep_link),
                "index": int(idx.group(1)) if idx else 0,
            }
        )
    episodes.sort(key=lambda x: x["index"])

    return {
        "title": title,
        "href": detail_url,
        "type": type_str,
        "year": year,
        "cover": abs_url(cover),
        "intro": desc,
        "episodes": episodes,
    }


async def parse_play_url(play_url: str) -> Dict[str, Any]:
    """请求播放源接口并解析出原始播放地址字典。

    入参 ``play_url`` 为分集链接（如 ``/vod-play/2023684335/ep156.html``），
    从链接提取 vod_id 与 ep_name 后请求 ``/_get_plays/<vod_id>/<ep_name>``。
    """
    match = _EP_LINK_RE.search(play_url)
    if not match:
        return {}

    vod_id, ep_name = match.group(1), match.group(2)
    data = await fetch_json(PLAY_API_TMPL.format(vod_id, ep_name), headers=HEADERS)

    video_plays = data.get("video_plays") or []
    if not video_plays:
        return {}

    url = video_plays[0].get("play_data", "")
    if url.endswith(".mp4"):
        play_type = "mp4"
    else:
        play_type = "m3u8"

    return {
        "play_url": normalize_url(url),
        "play_type": play_type,
        "headers": {"Referer": "https://yhdm.one/"},
    }
