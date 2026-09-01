"""站点私有常量、接口、请求头（模板）

复制后按目标站点实际接口填充。
"""

# 站点唯一标识 URL（必须与 main.py 中的 base_url 保持一致）
BASE_URL = "https://www.example.com"

# 站点接口地址
SEARCH_API = f"{BASE_URL}/api/search"
DETAIL_API = f"{BASE_URL}/api/detail"
PLAY_API = f"{BASE_URL}/api/play"

# 固定请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    "Referer": f"{BASE_URL}/",
}
