"""优质资源库（1080zyk6.com）站点专属解析逻辑（原始数据提取）

站点为苹果CMS，前端实际调用 macapi1.com 的 JSON 数据接口
（slug=youzhi），因此本插件直接复用该接口：

- 搜索：``GET {SEARCH_API}?ac=videolist&wd=<关键词>&pg=<页码>``；
- 详情：``GET {SEARCH_API}?ac=detail&ids=<vod_id>``；
- 播放：接口返回的 ``vod_play_url`` 为 ``名称$m3u8直链#名称$m3u8直链``，
  无需再请求播放页。

特性：
- **翻页并发**：搜索命中多页时用 ``asyncio`` + 信号量并发抓取；
- **直连**：绕过代理访问（``trust_env=False``）；
- **自动重联**：复用带重试的 :class:`AsyncHttpClient`，网络错误 / 5xx 自动重试。

> 本文件仅输出原始字典，不做字段过滤与格式化。
"""
import asyncio
import logging
import re
from typing import Any, Dict, List, Optional

from media_source import config
from media_source.utils.helpers import clean_text, strip_html
from media_source.utils.http import AsyncHttpClient

from .constants import (
    BASE_URL,
    HEADERS,
    abs_url,
    detail_api_url,
    detail_page_url,
    search_page_url,
)

logger = logging.getLogger(__name__)

# 详情链接中的 vod_id：/index.php/vod/detail/id/<id>.html
_VOD_ID_RE = re.compile(r"/vod/detail/id/(\d+)")
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


async def _fetch_json(url: str) -> Dict[str, Any]:
    """GET 请求并返回 JSON 字典（自动重联）。"""
    data = await _get_client().get_json(url)
    if not isinstance(data, dict) or data.get("code") != 1:
        logger.warning("1080zyk6 接口返回异常: %s", str(data)[:200])
        return {}
    return data


def _record_to_search_dict(record: Dict[str, Any]) -> Dict[str, Any]:
    """把接口单条记录映射为搜索原始字典。"""
    vod_id = str(record.get("vod_id") or "")
    content = clean_text(
        strip_html(record.get("vod_content") or record.get("vod_blurb") or "")
    )
    return {
        "title": clean_text(record.get("vod_name")),
        "href": detail_page_url(vod_id) if vod_id else "",
        "type": clean_text(record.get("type_name")),
        "year": clean_text(record.get("vod_year")),
        "cover": abs_url(record.get("vod_pic") or ""),
        "intro": content,
    }


def _parse_episodes(record: Dict[str, Any]) -> List[Dict[str, Any]]:
    """解析播放串为分集原始字典（名称$地址#名称$地址……）。"""
    play_froms = re.split(r"\$\$\$", record.get("vod_play_from") or "")
    play_urls = re.split(r"\$\$\$", record.get("vod_play_url") or "")

    prefer = 0
    for i, source in enumerate(play_froms):
        if "m3u8" in source.lower():
            prefer = i
            break
    play_url = play_urls[prefer] if prefer < len(play_urls) else ""
    if not play_url:
        return []

    episodes: List[Dict[str, Any]] = []
    seen: set = set()
    for seg in play_url.split("#"):
        seg = seg.strip()
        if not seg:
            continue
        name, sep, url = seg.partition("$")
        if not sep:
            continue
        name = clean_text(name)
        url = abs_url(clean_text(url))
        if not url or url in seen:
            continue

        index_m = _EP_INDEX_RE.search(name)
        seen.add(url)
        episodes.append(
            {
                "name": name,
                "url": url,
                "index": int(index_m.group(1)) if index_m else 0,
            }
        )
    return episodes


def _record_to_info_dict(
    record: Dict[str, Any], detail_url: Optional[str] = None
) -> Dict[str, Any]:
    """把接口单条记录映射为详情原始字典（含分集）。"""
    vod_id = str(record.get("vod_id") or "")
    content = clean_text(
        strip_html(record.get("vod_content") or record.get("vod_blurb") or "")
    )
    return {
        "title": clean_text(record.get("vod_name")),
        "href": detail_url or (detail_page_url(vod_id) if vod_id else ""),
        "type": clean_text(record.get("type_name")),
        "year": clean_text(record.get("vod_year")),
        "cover": abs_url(record.get("vod_pic") or ""),
        "intro": content,
        "episodes": _parse_episodes(record),
    }


async def parse_search_page(key: str, page: int) -> List[Dict[str, Any]]:
    """请求搜索接口，抓取指定页码并解析为原始字典列表（page 从 1 开始）。"""
    data = await _fetch_json(search_page_url(key, page))
    records = data.get("list") or []
    return [_record_to_search_dict(r) for r in records if isinstance(r, dict)]


async def parse_search(key: str) -> List[Dict[str, Any]]:
    """请求搜索接口，并发抓取所有分页，解析为原始字典列表。"""
    first = await _fetch_json(search_page_url(key, 1))
    total_pages = int(first.get("pagecount") or 1)
    results = [
        _record_to_search_dict(r)
        for r in (first.get("list") or [])
        if isinstance(r, dict)
    ]

    if total_pages > 1:
        sem = asyncio.Semaphore(config.PAGE_CONCURRENCY)

        async def fetch_page(page: int) -> List[Dict[str, Any]]:
            try:
                async with sem:
                    data = await _fetch_json(search_page_url(key, page))
                return [
                    _record_to_search_dict(r)
                    for r in (data.get("list") or [])
                    if isinstance(r, dict)
                ]
            except Exception as exc:  # 单页失败不影响整体
                logger.warning("1080zyk6 抓取第 %d 页失败: %s", page, exc)
                return []

        rest = await asyncio.gather(*(fetch_page(p) for p in range(2, total_pages + 1)))
        for items in rest:
            results.extend(items)

    return results


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """请求详情接口并解析为原始字典（含 m3u8 直链分集列表）。"""
    vod_id_m = _VOD_ID_RE.search(detail_url)
    if not vod_id_m:
        return {}

    data = await _fetch_json(detail_api_url(vod_id_m.group(1)))
    records = data.get("list") or []
    if not records or not isinstance(records[0], dict):
        return {}
    return _record_to_info_dict(records[0], detail_url)


async def parse_play_url(play_url: str) -> Dict[str, Any]:
    """返回原始播放地址字典（详情接口返回 m3u8 直链，直接透传）。"""
    url = clean_text(play_url)
    if not url:
        return {}
    return {
        "play_url": url,
        "play_type": "m3u8",
        "headers": {"Referer": f"{BASE_URL}/", "User-Agent": HEADERS["User-Agent"]},
    }
