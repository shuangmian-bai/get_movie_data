"""上游 TS 代理

未命中广告的分片经本服务转发（带防盗链头），避免前端直拉源站被防盗链拦截。
使用模块级长驻 httpx 连接池复用 TCP 连接；模块不 import ``stream_factory`` / ``media_source``。
"""
import logging
from typing import AsyncIterator, Dict, Optional

import httpx

from ad_filter import config

logger = logging.getLogger("ad_filter.proxy")

# 长驻连接池：所有会话共享同一 httpx 客户端（懒加载）
_client: Optional[httpx.AsyncClient] = None


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            timeout=config.HTTP_TIMEOUT,
            headers={"User-Agent": config.HTTP_USER_AGENT},
            follow_redirects=True,
            trust_env=config.HTTP_TRUST_ENV,
        )
    return _client


async def close() -> None:
    """关闭长驻连接池（服务退出时调用）。"""
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
        _client = None


async def stream_bytes(url: str, headers: Dict[str, str]) -> AsyncIterator[bytes]:
    """流式拉取上游字节（异步生成器，供 ``StreamingResponse`` 逐块转发）。"""
    client = _get_client()
    async with client.stream("GET", url, headers=headers) as resp:
        resp.raise_for_status()
        async for chunk in resp.aiter_bytes():
            yield chunk
