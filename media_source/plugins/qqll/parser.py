"""奇奇影视（qqll.cc）站点专属解析逻辑（原始数据提取）

站点为苹果CMS v10，服务端渲染，无需浏览器执行 JS：

- 搜索：``GET /vsearch/<关键词>----------<页码>---.html``，结果在 ``ul.stui-vodlist__media li``
- 详情：``GET /yh/<id>.html``，信息在 ``.stui-content__detail``，分集在 ``a[href*='/vplay/']``
- 播放：``GET /vplay/<vod_id>-<sid>-<nid>.html``，地址在 ``var player_xxxx`` 的 ``url`` 字段

特性：
- **直连**：复用带重试的 :class:`AsyncHttpClient`（``trust_env=False`` 绕过代理）；
- **自动重联**：网络错误 / 5xx 自动重试。

> 选择器基于苹果CMS v10 默认模板；如站点改用自定义模板，需按实际 HTML 微调。
> 本文件仅输出原始字典，不做字段过滤与格式化。
"""
import json
import logging
import re
from typing import Any, Dict, List, Optional

from parsel import Selector

from media_source.utils.http import AsyncHttpClient
from media_source.plugins.qqll.constants import (
    BASE_URL,
    HEADERS,
    abs_url,
    search_page_url,
)

logger = logging.getLogger(__name__)

# 播放页内嵌播放器变量：var player_xxxx = { ... }
_PLAYER_RE = re.compile(r"var\s+player_\w+\s*=\s*(\{.*?\})\s*;?\s*</script>", re.S)
# 分集链接：/vplay/<vod_id>-<sid>-<nid>.html，nid 为集数序号
_EP_LINK_RE = re.compile(r"/vplay/\d+-\d+-(\d+)\.html")
# 年份（4 位数字）
_YEAR_RE = re.compile(r"(20\d{2}|19\d{2})")
# 类型词（覆盖常见分类）
_TYPE_RE = re.compile(
    r"(电影|电视剧|动漫|综艺|国产动漫|日韩动漫|欧美动漫|海外动漫|国产剧|韩剧|日剧|美剧|港剧|泰剧|台剧)"
)

# 复用直连客户端（绕过代理）；带自动重联
_client: Optional[AsyncHttpClient] = None


def _get_client() -> AsyncHttpClient:
    """返回复用的直连客户端（trust_env=False + 自动重联）。"""
    global _client
    if _client is None:
        _client = AsyncHttpClient(headers=HEADERS, trust_env=False)
    return _client


async def _fetch_text(url: str) -> str:
    """GET 请求返回文本（自动重联）。"""
    return await _get_client().get_text(url)


def _parse_search_items(selector: Selector) -> List[Dict[str, Any]]:
    """解析搜索结果列表为原始字典。

    选择器基于苹果CMS v10 默认搜索模板（``ul.stui-vodlist__media li``）；
    如站点改用自定义模板，需按实际 HTML 微调。
    """
    results: List[Dict[str, Any]] = []
    seen: set = set()

    items = selector.css("ul.stui-vodlist__media li, ul.stui-vodlist li")
    # 兜底：默认 li 选择器无结果时，扫所有 li
    if not items:
        items = selector.css("li")

    for li in items:
        # 标题链接：详情页 /yh/<id>.html
        anchors = li.css("a[href*='/yh/']")
        if not anchors:
            continue
        a = anchors[0]
        href = a.attrib.get("href", "") or ""
        if not href:
            continue
        href = abs_url(href)
        if href in seen:
            continue
        title = (a.attrib.get("title", "") or a.css("::text").get() or "").strip()

        # 封面：maccms 惯用 data-original 懒加载
        cover = (
            li.css("img::attr(data-original)").get()
            or li.css("img::attr(src)").get()
            or ""
        )

        # 元信息文本：类型 · 年份 · 地区 · 分类
        text = re.sub(r"\s+", " ", " ".join(li.css("::text").getall())).strip()
        year_match = _YEAR_RE.search(text)
        type_match = _TYPE_RE.search(text)

        # 简介：取 li 内最长的一段描述文本
        intro = ""
        for p_text in li.css("p::text, .intro::text, .desc::text").getall():
            if len(p_text.strip()) > len(intro):
                intro = p_text.strip()

        seen.add(href)
        results.append(
            {
                "title": title,
                "href": href,
                "cover": abs_url(cover),
                "type": type_match.group(1) if type_match else "",
                "year": year_match.group(1) if year_match else "",
                "intro": intro,
            }
        )
    return results


async def parse_search(key: str) -> List[Dict[str, Any]]:
    """请求搜索接口并解析为原始字典列表（单页）。

    站点搜索分页 URL 形如 ``/vsearch/<key>----------<page>---.html``；
    本实现只抓第 1 页，如需多页并发可参考 ``cupfox`` 插件。
    """
    html = await _fetch_text(search_page_url(key, 1))
    selector = Selector(html)
    return _parse_search_items(selector)


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """请求详情页并解析为原始字典（含分集列表）。"""
    html = await _fetch_text(detail_url)
    selector = Selector(html)

    # 标题：maccms 详情页常见 h1.title
    title = (
        selector.css("h1.title::text").get()
        or selector.css("h1::text").get()
        or selector.css(".stui-content__detail .title::text").get()
        or ""
    ).strip()

    # 封面
    cover = (
        selector.css(".stui-content__thumb img::attr(data-original)").get()
        or selector.css(".stui-content__thumb img::attr(src)").get()
        or selector.css("img::attr(data-original)").get()
        or ""
    )

    # 信息区文本：提取年份/类型
    info_text = re.sub(
        r"\s+",
        " ",
        "".join(
            selector.css(".stui-content__detail ::text, .data ::text").getall()
        ),
    )
    year_match = re.search(r"年份[：:]\s*(\d+)", info_text) or _YEAR_RE.search(
        info_text
    )
    type_match = re.search(r"类型[：:]\s*([^\s/,，]+)", info_text) or _TYPE_RE.search(
        info_text
    )

    # 简介
    desc = re.sub(
        r"\s+",
        " ",
        "".join(
            selector.css(
                ".stui-content__desc ::text, .detail-content ::text, .intro ::text"
            ).getall()
        ),
    ).strip()

    # 分集列表：/vplay/<vod_id>-<sid>-<nid>.html，nid 为集数序号
    # 多线路时同一集数会有多个链接，按 index 去重保留第一个
    by_index: Dict[int, Dict[str, Any]] = {}
    for a in selector.css("a[href*='/vplay/']"):
        link = a.attrib.get("href", "") or ""
        name = (a.css("::text").get() or "").strip()
        m = _EP_LINK_RE.search(link)
        idx = int(m.group(1)) if m else 0
        if idx in by_index:
            continue
        by_index[idx] = {"name": name, "url": abs_url(link), "index": idx}
    episodes = sorted(by_index.values(), key=lambda x: x["index"])

    return {
        "title": title,
        "href": detail_url,
        "type": type_match.group(1).strip() if type_match else "",
        "year": year_match.group(1) if year_match else "",
        "cover": abs_url(cover),
        "intro": desc,
        "episodes": episodes,
    }


async def parse_play_url(play_url: str) -> Dict[str, Any]:
    """请求播放页并解析出原始播放地址字典（m3u8/mp4）。

    入参 ``play_url`` 为分集播放页链接（如 ``/vplay/945-1-1.html``），
    从页面内嵌 ``var player_xxxx = {...}`` 提取 ``url`` 字段。
    """
    html = await _fetch_text(play_url)
    m = _PLAYER_RE.search(html)
    if not m:
        return {}

    try:
        data = json.loads(m.group(1))
    except ValueError:
        return {}

    url = (data.get("url") or "").strip()
    if not url:
        return {}

    play_type = "mp4" if url.endswith(".mp4") else "m3u8"
    return {
        "play_url": url,
        "play_type": play_type,
        "headers": {"Referer": f"{BASE_URL}/"},
    }
