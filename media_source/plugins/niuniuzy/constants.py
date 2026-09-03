"""牛牛资源（niuniuzy.cc）站点私有常量、接口、请求头"""
from media_source.utils.helpers import normalize_url

# 站点唯一标识 URL（与 main.py 中 base_url 保持一致）
BASE_URL = "https://niuniuzy.cc"

# 牛牛真实资源站域名（niuniuzy.cc 仅是入口页，真实 CMS 在 niuniuzy4.com）
DETAIL_HOST = "https://niuniuzy4.com"

# 官方采集接口
SEARCH_API = f"{DETAIL_HOST}/api.php/provide/vod/"

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Referer": f"{DETAIL_HOST}/",
}


def detail_api_url(vod_id: str) -> str:
    """构造详情接口地址。"""
    return f"{SEARCH_API}?ac=detail&ids={vod_id}"


def detail_page_url(vod_id: str) -> str:
    """构造详情页地址（真实资源站详情链接）。"""
    return f"{DETAIL_HOST}/index.php/vod/detail/id/{vod_id}.html"


def abs_url(path: str) -> str:
    """把接口返回的相对路径标准化为绝对地址。"""
    return normalize_url(path, DETAIL_HOST)
