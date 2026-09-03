"""鸭鸭资源库（yayazy.com）站点私有常量、接口、请求头"""
from urllib.parse import quote

from media_source.utils.helpers import normalize_url

# 站点唯一标识 URL（与 main.py 中 base_url 保持一致）
BASE_URL = "https://yayazy.com"

# 鸭鸭真实资源站域名（yayazy.com 仅是入口页，站方轮换 yayazy1/2/3.com 镜像）
DETAIL_HOST = "https://yayazy1.com"

# 站点前端实际调用的 JSON 数据接口（macapi1 聚合，slug=yaya）
SEARCH_API = "https://macapi1.com/maccms/json/yaya/"

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Referer": f"{DETAIL_HOST}/",
}


def search_page_url(key: str, page: int) -> str:
    """构造搜索接口地址（关键词 URL 编码，页码从 1 开始）。"""
    return f"{SEARCH_API}?ac=videolist&wd={quote(key)}&pg={page}"


def detail_api_url(vod_id: str) -> str:
    """构造详情接口地址。"""
    return f"{SEARCH_API}?ac=detail&ids={vod_id}"


def detail_page_url(vod_id: str) -> str:
    """构造详情页地址（真实资源站详情链接）。"""
    return f"{DETAIL_HOST}/index.php/vod/detail/id/{vod_id}.html"


def abs_url(path: str) -> str:
    """把接口返回的相对路径标准化为绝对地址。"""
    return normalize_url(path, DETAIL_HOST)
