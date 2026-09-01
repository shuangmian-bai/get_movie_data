"""站点 A 私有常量、接口、请求头"""
from media_source.utils.helpers import normalize_url

# 站点唯一标识 URL（与 main.py 中 base_url 保持一致）
BASE_URL = "https://www.site-a.example.com"

# 站点接口地址
SEARCH_API = f"{BASE_URL}/api/search"
DETAIL_API = f"{BASE_URL}/api/detail"
PLAY_API = f"{BASE_URL}/api/play"

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    "Referer": f"{BASE_URL}/",
}


def abs_url(path: str) -> str:
    """把站点返回的相对路径标准化为绝对地址。"""
    return normalize_url(path, BASE_URL)
