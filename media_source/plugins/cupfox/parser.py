"""茶杯狐（cupfox7.com）站点专属解析逻辑（原始数据提取）

站点为苹果CMS v10，服务端渲染，无需浏览器执行 JS：

- 搜索：``GET /vodsearch/<关键词>----------<页码>---.html``，结果在 ``ul.stui-vodlist li``
- 详情：``GET /vos/<id>.html``，信息在 ``.stui-content__detail``，分集在 ``ul.stui-content__playlist a``
- 播放：``GET /play/<id>-<sid>-<nid>.html``，地址在 ``var player_xxxx`` 的 ``url`` 字段

特性：
- **翻页并发**：搜索命中多页时用 ``asyncio`` + 信号量并发抓取所有分页；
- **直连**：cupfox 需绕过代理访问（``trust_env=False``）；
- **自动重联**：复用带重试的 :class:`AsyncHttpClient`，网络错误 / 5xx 自动重试。

本文件仅输出原始字典，不做字段过滤与格式化。
"""
import asyncio
import json
import logging
import re
from typing import Any, Dict, List, Optional

from parsel import Selector

from media_source import config
from media_source.utils.http import AsyncHttpClient
from media_source.plugins.cupfox.constants import BASE_URL, HEADERS, abs_url, search_page_url

logger = logging.getLogger(__name__)

# 分页链接中的页码：----------<页码>---.html
_PAGE_RE = re.compile(r"----------(\d+)---\.html")
# 播放页内嵌播放器变量：var player_xxxx = { ... }
_PLAYER_RE = re.compile(r"var\s+player_\w+\s*=\s*(\{.*?\})\s*;?\s*</script>", re.S)

# 复用直连客户端（cupfox 需绕过代理）；带自动重联
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


def _extract_total_pages(selector: Selector) -> int:
    """从搜索页分页链接中解析最大页码（无分页时为 1）。"""
    max_page = 1
    for a in selector.css("a[href*='vodsearch']"):
        m = _PAGE_RE.search(a.attrib.get("href", ""))
        if m:
            max_page = max(max_page, int(m.group(1)))
    return max_page


def _parse_search_items(selector: Selector) -> List[Dict[str, Any]]:
    """解析单页搜索结果列表为原始字典。"""
    results: List[Dict[str, Any]] = []
    for li in selector.css("ul.stui-vodlist li"):
        a = li.css("a.stui-vodlist__thumb")
        if not a:
            continue
        title = (a.attrib.get("title", "") or "").strip()
        href = a.attrib.get("href", "") or ""
        cover = a.attrib.get("data-original", "") or ""
        type_str = (a.css("span.pic-text1 b::text").get() or "").strip()
        if not title and not href:
            continue
        results.append(
            {
                "title": title,
                "href": abs_url(href),
                "cover": abs_url(cover),
                "type": type_str,
                "year": "",
                "intro": "",
            }
        )
    return results


async def parse_search(key: str) -> List[Dict[str, Any]]:
    """请求搜索接口，并发抓取所有分页，解析为原始字典列表。"""
    first_html = await _fetch_text(search_page_url(key, 1))
    first_sel = Selector(first_html)
    total_pages = _extract_total_pages(first_sel)
    results = _parse_search_items(first_sel)

    if total_pages > 1:
        sem = asyncio.Semaphore(config.PAGE_CONCURRENCY)

        async def fetch_page(page: int) -> List[Dict[str, Any]]:
            try:
                async with sem:
                    html = await _fetch_text(search_page_url(key, page))
                return _parse_search_items(Selector(html))
            except Exception as exc:  # 单页失败不影响整体
                logger.warning("cupfox 抓取第 %d 页失败: %s", page, exc)
                return []

        rest = await asyncio.gather(*(fetch_page(p) for p in range(2, total_pages + 1)))
        for items in rest:
            results.extend(items)

    return results


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """请求详情页并解析为原始字典（含分集列表）。"""
    html = await _fetch_text(detail_url)
    selector = Selector(html)

    title = (selector.css("h1.title::text").get() or "").strip()
    cover = selector.css(".stui-content__thumb img::attr(data-original)").get() or ""

    # 信息区：p.data，形如 "类型：国产动漫 / 地区：大陆 / 年份：2025"
    info_text = re.sub(r"\s+", " ", "".join(selector.css("p.data ::text").getall()))
    year_match = re.search(r"年份：\s*(\d+)", info_text)
    type_match = re.search(r"类型：\s*([^\s/]+)", info_text)

    # 简介
    desc = re.sub(r"\s+", " ", "".join(selector.css(".detail-content ::text").getall())).strip()

    # 分集列表：/play/<id>-<sid>-<nid>.html，集数为末段数字
    episodes: List[Dict[str, Any]] = []
    for a in selector.css("ul.stui-content__playlist a"):
        name = (a.css("::text").get() or "").strip()
        link = a.attrib.get("href", "") or ""
        tail = link.rstrip(".html").rsplit("/", 1)[-1]
        parts = tail.split("-")
        idx = int(parts[-1]) if parts and parts[-1].isdigit() else 0
        episodes.append({"name": name, "url": abs_url(link), "index": idx})

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
    """请求播放页并解析出原始播放地址字典（m3u8/mp4）。"""
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
