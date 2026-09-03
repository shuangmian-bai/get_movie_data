"""量子采集站（lzizy.net）站点私有常量、接口、请求头"""
from urllib.parse import quote

from media_source.utils.helpers import normalize_url

# 站点唯一标识 URL（与 main.py 中 base_url 保持一致）
BASE_URL = "http://lzizy.net"

# 站点前端搜索页（/lz/lz.html）实际调用的 JSON 数据接口
SEARCH_API = "https://macapi1.com/maccms/json/liangzi/"

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Referer": f"{BASE_URL}/",
}


def search_page_url(key: str, page: int) -> str:
    """构造搜索接口地址（关键词 URL 编码，页码从 1 开始）。"""
    return f"{SEARCH_API}?ac=videolist&wd={quote(key)}&pg={page}"


def detail_api_url(vod_id: str) -> str:
    """构造详情接口地址。"""
    return f"{SEARCH_API}?ac=detail&ids={vod_id}"


def detail_page_url(vod_id: str) -> str:
    """构造详情页地址（对外暴露的站点链接）。"""
    return f"{BASE_URL}/index.php/vod/detail/id/{vod_id}.html"


def abs_url(path: str) -> str:
    """把接口返回的相对路径标准化为绝对地址。"""
    return normalize_url(path, BASE_URL)
