"""全局配置模块

集中管理插件并发数、HTTP 超时、默认 UA、缓存目录与 TTL 等全局可调参数。
参数优先从环境变量读取，便于后续扩展为动态配置。
"""
import os
from pathlib import Path

# 项目根目录（media_source 的上一级）
_PROJECT_ROOT = Path(__file__).resolve().parent.parent

# 插件批量搜索最大并发数（仅作用于多插件批量任务，单插件内部请求不受限制）
MAX_PLUGIN_CONCURRENCY: int = int(os.getenv("MEDIA_SOURCE_MAX_CONCURRENCY", "5"))

# 全局 HTTP 请求超时时间（秒）
HTTP_TIMEOUT: float = float(os.getenv("MEDIA_SOURCE_HTTP_TIMEOUT", "10"))

# 默认请求 User-Agent
HTTP_USER_AGENT: str = os.getenv(
    "MEDIA_SOURCE_HTTP_USER_AGENT",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
)

# ---- 自动重联（HTTP 重试）配置 ----

# 单次请求失败后的最大重试次数（0 表示不重试）
HTTP_RETRIES: int = int(os.getenv("MEDIA_SOURCE_HTTP_RETRIES", "3"))

# 重试基础退避时间（秒），按 2^n 指数递增
HTTP_RETRY_BACKOFF: float = float(os.getenv("MEDIA_SOURCE_HTTP_RETRY_BACKOFF", "0.5"))

# 是否默认信任环境代理（HTTP_PROXY/ALL_PROXY 等）；需直连的站点由插件自行关闭
HTTP_TRUST_ENV: bool = os.getenv("MEDIA_SOURCE_HTTP_TRUST_ENV", "1") == "1"

# 单插件翻页并发抓取上限（搜索有多页时，同时发起的请求数）
PAGE_CONCURRENCY: int = int(os.getenv("MEDIA_SOURCE_PAGE_CONCURRENCY", "5"))

# ---- 文件缓存配置 ----

# 缓存根目录（按 base_url 划分命名空间），默认位于项目根 cache/
CACHE_DIR: str = os.getenv("MEDIA_SOURCE_CACHE_DIR", str(_PROJECT_ROOT / "cache"))

# 各类数据缓存 TTL（秒）：在「防止重复爬虫」与「数据不过于落后」之间取平衡
SEARCH_CACHE_TTL: int = int(os.getenv("MEDIA_SOURCE_SEARCH_TTL", "600"))   # 搜索结果 10 分钟
INFO_CACHE_TTL: int = int(os.getenv("MEDIA_SOURCE_INFO_TTL", "3600"))      # 详情信息 1 小时
PLAY_CACHE_TTL: int = int(os.getenv("MEDIA_SOURCE_PLAY_TTL", "600"))       # 播放地址 10 分钟
