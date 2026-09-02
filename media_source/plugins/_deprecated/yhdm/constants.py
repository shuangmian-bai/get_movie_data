"""樱花动漫（yhdm.one）站点私有常量、接口、请求头"""
from media_source.utils.helpers import normalize_url

# 站点唯一标识 URL（与 main.py 中 base_url 保持一致）
BASE_URL = "https://yhdm.one"

# 站点接口地址
SEARCH_URL = f"{BASE_URL}/search"                    # 搜索：GET ?q=关键词
PLAY_API_TMPL = f"{BASE_URL}/_get_plays/{{0}}/{{1}}"  # 播放源：GET /_get_plays/<vod_id>/<ep_name>

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Referer": f"{BASE_URL}/",
}


def abs_url(path: str) -> str:
    """把站点返回的相对路径标准化为绝对地址。"""
    return normalize_url(path, BASE_URL)
