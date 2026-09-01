"""全局配置模块

集中管理插件并发数、HTTP 超时、默认 UA 等全局可调参数。
参数优先从环境变量读取，便于后续扩展为动态配置。
"""
import os

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
