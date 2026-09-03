"""索尼采集站（suonizy.net）站点私有常量、接口、请求头"""
from media_source.utils.helpers import normalize_url

# 站点唯一标识 URL（与 main.py 中 base_url 保持一致）
BASE_URL = "https://suonizy.net"

# 索尼官方采集接口（站点页脚公布的 json 采集地址）
SEARCH_API = "https://suoniapi.com/api.php/provide/vod/"

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Referer": f"{BASE_URL}/",
}


def detail_api_url(vod_id: str) -> str:
    """构造详情接口地址。"""
    return f"{SEARCH_API}?ac=detail&ids={vod_id}"


def detail_page_url(vod_id: str) -> str:
    """构造详情页地址（站点 URL 重写格式 /voddetail/<id>.html）。"""
    return f"{BASE_URL}/voddetail/{vod_id}.html"


def abs_url(path: str) -> str:
    """把接口返回的相对路径标准化为绝对地址。"""
    return normalize_url(path, BASE_URL)
