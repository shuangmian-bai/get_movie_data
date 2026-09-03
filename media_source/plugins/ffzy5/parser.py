"""非凡采集站（ffzy5.tv）站点专属解析逻辑（原始数据提取）

站点为苹果CMS，服务端渲染，无需浏览器执行 JS：

- 搜索：``GET /index.php/vod/search/page/<页码>/wd/<关键词>.html``，
  结果在 ``ul.videoContent li``；
- 详情：``GET /index.php/vod/detail/id/<id>.html``，信息在 ``div.people``，
  简介在 ``.vod_content``；
- 播放：详情页内嵌多条播放线路，``div.playlist.ffm3u8`` 一行为一个
  可直接访问的 m3u8 地址，无需再跳播放页提取。

特性：
- **翻页并发**：搜索命中多页时用 ``asyncio`` + 信号量并发抓取所有分页；
- **直连**：绕过代理访问（``trust_env=False``）；
- **自动重联**：复用带重试的 :class:`AsyncHttpClient`，网络错误 / 5xx 自动重试。

> 站点页面可能存在异常注入的重复片段，分集统一按播放地址去重。
> 本文件仅输出原始字典，不做字段过滤与格式化。
"""
import asyncio
import logging
import re
from typing import Any, Dict, List, Optional

from parsel import Selector

from media_source import config
from media_source.plugins.ffzy5.constants import (
    BASE_URL,
    HEADERS,
    abs_url,
    search_page_url,
)
from media_source.utils.http import AsyncHttpClient

logger = logging.getLogger(__name__)

# 分页链接中的页码：/index.php/vod/search/page/<页码>/wd/
_PAGE_RE = re.compile(r"/index.php/vod/search/page/(\d+)/wd/")
# 分集序号：第01集 / 第20260902期 / HD国语（无序号）
_EP_INDEX_RE = re.compile(r"第(\d+)(?:集|期)")

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


def _clean(text: Any) -> str:
    """压缩连续空白并去除首尾空白。"""
    return re.sub(r"\s+", " ", str(text or "")).strip()


def _parse_search_items(selector: Selector) -> List[Dict[str, Any]]:
    """解析单页搜索结果列表为原始字典。

    非凡搜索页无封面/简介/年份，仅提供标题、详情链接、地区、分类。
    """
    results: List[Dict[str, Any]] = []
    seen: set = set()

    for li in selector.css("ul.videoContent li"):
        a = li.css("a.videoName")
        if not a:
            continue
        a = a[0]
        href = a.attrib.get("href", "") or ""
        if not href:
            continue
        href = abs_url(href)
        if href in seen:
            continue

        # 标题链接内嵌 <i> 状态标签（更新至第xx集 / HD国语），仅取直接文本节点
        title = _clean(a.xpath("text()").get() or a.attrib.get("title"))
        if not title:
            continue

        type_str = _clean(li.css("span.category::text").get())
        region = _clean(li.css("span.region::text").get())

        seen.add(href)
        results.append(
            {
                "title": title,
                "href": href,
                "type": type_str,
                "year": "",
                "region": region,
                "intro": "",
            }
        )
    return results


def _extract_total_pages(selector: Selector) -> int:
    """从搜索页分页链接中解析最大页码（无分页时为 1）。"""
    max_page = 1
    for href in selector.css("a[href*='vod/search/page/']::attr(href)").getall():
        m = _PAGE_RE.search(href or "")
        if m:
            max_page = max(max_page, int(m.group(1)))
    return max_page


async def parse_search_page(key: str, page: int) -> List[Dict[str, Any]]:
    """请求搜索接口，抓取指定页码并解析为原始字典列表（page 从 1 开始）。"""
    html = await _fetch_text(search_page_url(key, page))
    return _parse_search_items(Selector(html))


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
                logger.warning("ffzy5 抓取第 %d 页失败: %s", page, exc)
                return []

        rest = await asyncio.gather(*(fetch_page(p) for p in range(2, total_pages + 1)))
        for items in rest:
            results.extend(items)

    return results


def _parse_detail_meta(selector: Selector) -> Dict[str, str]:
    """解析详情页信息区 ``div.people .right p`` 中的键值字段。"""
    fields: Dict[str, str] = {}
    for p in selector.css("div.people .right p"):
        text = _clean("".join(p.css("::text").getall()))
        key, sep, value = text.partition("：")
        if not sep:
            key, sep, value = text.partition(":")
        if sep:
            fields[key.strip()] = _clean(value)
    return fields


def _parse_episodes(selector: Selector) -> List[Dict[str, Any]]:
    """从详情页播放区提取分集原始字典（优先 ffm3u8 直链线路）。

    站点页面可能注入重复片段，按播放地址去重；无序号条目（如电影
    的 ``HD国语``）index 置 0，由基类按位置自动补号。
    """
    containers = selector.css("div.playlist.wbox.ffm3u8")
    if not containers:
        containers = selector.css("div.playlist.wbox")

    episodes: List[Dict[str, Any]] = []
    seen: set = set()
    for a in containers.css("li a[href]"):
        href = _clean(a.attrib.get("href"))
        if not href or href in seen:
            continue
        link = abs_url(href)
        if link in seen:
            continue
        # 只保留可直接访问的 m3u8 播放地址
        if not re.search(r"\.m3u8($|\?)", link):
            continue

        name = _clean(a.attrib.get("title") or a.xpath("text()").get())
        if "$" in name:  # 兜底：部分模板把 "名称$地址" 一起写进文本
            name = _clean(name.split("$", 1)[0])
        index_m = _EP_INDEX_RE.search(name)

        seen.add(link)
        episodes.append(
            {
                "name": name,
                "url": link,
                "index": int(index_m.group(1)) if index_m else 0,
            }
        )
    return episodes


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """请求详情页并解析为原始字典（含 m3u8 直链分集列表）。"""
    html = await _fetch_text(detail_url)
    selector = Selector(html)
    fields = _parse_detail_meta(selector)

    cover = (
        selector.css("div.people .left img::attr(src)").get()
        or selector.css("div.people .left img::attr(data-original)").get()
        or ""
    )

    type_raw = fields.get("类型", "")
    year_raw = fields.get("年代", "")
    year_match = re.search(r"\d{4}", year_raw)

    desc = _clean("".join(selector.css(".vod_content ::text").getall()))

    return {
        "title": fields.get("片名", ""),
        "href": detail_url,
        "type": re.split(r"[,，]", type_raw, maxsplit=1)[0].strip() if type_raw else "",
        "year": year_match.group(0) if year_match else "",
        "cover": abs_url(cover),
        "intro": desc,
        "episodes": _parse_episodes(selector),
    }


async def parse_play_url(play_url: str) -> Dict[str, Any]:
    """返回原始播放地址字典。

    非凡详情页的 ffm3u8 线路本身即为可直接播放的 m3u8 直链，
    无需再请求播放页解析，因此这里直接透传分集链接。
    """
    url = _clean(play_url)
    if not url:
        return {}
    return {
        "play_url": url,
        "play_type": "m3u8",
        "headers": {"Referer": f"{BASE_URL}/", "User-Agent": HEADERS["User-Agent"]},
    }
