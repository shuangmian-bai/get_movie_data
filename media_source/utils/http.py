"""异步 HTTP 统一封装

所有插件共用的网络请求能力，基于 httpx 异步客户端。
内置**自动重联**（重试）机制：网络错误 / 超时 / 5xx 自动重试，4xx 不重试；
网络异常统一转换为 :class:`SourceRequestError`，便于业务精准捕获。
支持按插件关闭代理（``trust_env=False`` 直连），应对需要绕过代理的站点。
"""
import asyncio
from typing import Any, Dict, Optional

import httpx

from media_source import config
from media_source.exceptions import SourceRequestError


class AsyncHttpClient:
    """异步 HTTP 客户端，插件可直接实例化复用。

    示例::

        client = AsyncHttpClient(trust_env=False)  # 直连，不走代理
        html = await client.get_text(url)
    """

    def __init__(
        self,
        timeout: Optional[float] = None,
        headers: Optional[Dict[str, str]] = None,
        retries: Optional[int] = None,
        trust_env: Optional[bool] = None,
    ) -> None:
        self.timeout = timeout or config.HTTP_TIMEOUT
        self.headers = headers or {"User-Agent": config.HTTP_USER_AGENT}
        self.retries = config.HTTP_RETRIES if retries is None else retries
        self.trust_env = config.HTTP_TRUST_ENV if trust_env is None else trust_env
        self._client: Optional[httpx.AsyncClient] = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                timeout=self.timeout,
                headers=self.headers,
                follow_redirects=True,
                trust_env=self.trust_env,
            )
        return self._client

    @staticmethod
    def _should_retry(exc: httpx.HTTPError) -> bool:
        """判断异常是否值得重试：网络层错误 / 超时 / 5xx 重试，4xx 不重试。"""
        if isinstance(exc, httpx.HTTPStatusError):
            return exc.response.status_code >= 500
        return isinstance(exc, httpx.TransportError)

    def _backoff(self, attempt: int) -> float:
        """指数退避：0.5s、1s、2s ……"""
        return config.HTTP_RETRY_BACKOFF * (2 ** attempt)

    async def _request(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        """发起请求，带自动重联。"""
        last_exc: Optional[httpx.HTTPError] = None
        for attempt in range(self.retries + 1):
            client = await self._get_client()
            try:
                resp = await client.request(method, url, **kwargs)
                resp.raise_for_status()
                return resp
            except httpx.HTTPError as exc:
                last_exc = exc
                if attempt >= self.retries or not self._should_retry(exc):
                    break
                await asyncio.sleep(self._backoff(attempt))
        raise SourceRequestError(f"请求失败: {url}, 原因: {last_exc}") from last_exc

    async def get_text(
        self,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        **kwargs: Any,
    ) -> str:
        """GET 请求，返回响应文本。"""
        resp = await self._request("GET", url, params=params, headers=headers, **kwargs)
        return resp.text

    async def get_json(
        self,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        **kwargs: Any,
    ) -> Any:
        """GET 请求，返回解析后的 JSON。"""
        resp = await self._request("GET", url, params=params, headers=headers, **kwargs)
        return resp.json()

    async def post_text(
        self,
        url: str,
        data: Optional[Dict[str, Any]] = None,
        json: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        **kwargs: Any,
    ) -> str:
        """POST 请求，返回响应文本。"""
        resp = await self._request(
            "POST", url, data=data, json=json, headers=headers, **kwargs
        )
        return resp.text

    async def close(self) -> None:
        """关闭底层连接。"""
        if self._client is not None and not self._client.is_closed:
            await self._client.aclose()


async def fetch_text(
    url: str,
    params: Optional[Dict[str, Any]] = None,
    headers: Optional[Dict[str, str]] = None,
    retries: Optional[int] = None,
    trust_env: Optional[bool] = None,
    **kwargs: Any,
) -> str:
    """便捷函数：一次性 GET 请求返回文本。"""
    client = AsyncHttpClient(retries=retries, trust_env=trust_env)
    try:
        return await client.get_text(url, params=params, headers=headers, **kwargs)
    finally:
        await client.close()


async def fetch_json(
    url: str,
    params: Optional[Dict[str, Any]] = None,
    headers: Optional[Dict[str, str]] = None,
    retries: Optional[int] = None,
    trust_env: Optional[bool] = None,
    **kwargs: Any,
) -> Any:
    """便捷函数：一次性 GET 请求返回 JSON。"""
    client = AsyncHttpClient(retries=retries, trust_env=trust_env)
    try:
        return await client.get_json(url, params=params, headers=headers, **kwargs)
    finally:
        await client.close()
