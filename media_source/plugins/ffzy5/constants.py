"""非凡采集站（ffzy5.tv）站点私有常量、接口、请求头"""
from urllib.parse import quote

from media_source.utils.helpers import normalize_url

# 站点唯一标识 URL（与 main.py 中 base_url 保持一致）
BASE_URL = "http://ffzy5.tv"

# 搜索分页 URL 模板：/index.php/vod/search/page/<页码>/wd/<关键词>.html（页码从 1 开始）
SEARCH_PAGE_TMPL = f"{BASE_URL}/index.php/vod/search/page/{{1}}/wd/{{0}}.html"

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Referer": f"{BASE_URL}/",
}


def search_page_url(key: str, page: int) -> str:
    """构造搜索分页地址（关键词 URL 编码，页码从 1 开始）。"""
    return SEARCH_PAGE_TMPL.format(quote(key), page)


def abs_url(path: str) -> str:
    """把站点返回的相对路径标准化为绝对地址。"""
    return normalize_url(path, BASE_URL)
