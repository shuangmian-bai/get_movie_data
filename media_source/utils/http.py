"""异步 HTTP 统一封装

所有插件共用的网络请求能力，基于 httpx 异步客户端。
网络异常统一转换为 :class:`SourceRequestError`，便于业务精准捕获。
"""
from typing import Any, Dict, Optional

import httpx

from media_source import config
from media_source.exceptions import SourceRequestError


class AsyncHttpClient:
    """异步 HTTP 客户端，插件可直接实例化复用。

    示例::

        client = AsyncHttpClient()
        html = await client.get_text(url)
    """

    def __init__(
        self,
        timeout: Optional[float] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> None:
        self.timeout = timeout or config.HTTP_TIMEOUT
        self.headers = headers or {"User-Agent": config.HTTP_USER_AGENT}
        self._client: Optional[httpx.AsyncClient] = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                timeout=self.timeout,
                headers=self.headers,
                follow_redirects=True,
            )
        return self._client

    async def get_text(
        self,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        **kwargs: Any,
    ) -> str:
        """GET 请求，返回响应文本。"""
        client = await self._get_client()
        try:
            resp = await client.get(url, params=params, headers=headers, **kwargs)
            resp.raise_for_status()
            return resp.text
        except httpx.HTTPError as exc:
            raise SourceRequestError(f"请求失败: {url}, 原因: {exc}") from exc

    async def get_json(
        self,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        **kwargs: Any,
    ) -> Any:
        """GET 请求，返回解析后的 JSON。"""
        client = await self._get_client()
        try:
            resp = await client.get(url, params=params, headers=headers, **kwargs)
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as exc:
            raise SourceRequestError(f"请求失败: {url}, 原因: {exc}") from exc

    async def post_text(
        self,
        url: str,
        data: Optional[Dict[str, Any]] = None,
        json: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        **kwargs: Any,
    ) -> str:
        """POST 请求，返回响应文本。"""
        client = await self._get_client()
        try:
            resp = await client.post(
                url, data=data, json=json, headers=headers, **kwargs
            )
            resp.raise_for_status()
            return resp.text
        except httpx.HTTPError as exc:
            raise SourceRequestError(f"请求失败: {url}, 原因: {exc}") from exc

    async def close(self) -> None:
        """关闭底层连接。"""
        if self._client is not None and not self._client.is_closed:
            await self._client.aclose()


async def fetch_text(
    url: str,
    params: Optional[Dict[str, Any]] = None,
    headers: Optional[Dict[str, str]] = None,
    **kwargs: Any,
) -> str:
    """便捷函数：一次性 GET 请求返回文本。"""
    client = AsyncHttpClient()
    try:
        return await client.get_text(url, params=params, headers=headers, **kwargs)
    finally:
        await client.close()


async def fetch_json(
    url: str,
    params: Optional[Dict[str, Any]] = None,
    headers: Optional[Dict[str, str]] = None,
    **kwargs: Any,
) -> Any:
    """便捷函数：一次性 GET 请求返回 JSON。"""
    client = AsyncHttpClient()
    try:
        return await client.get_json(url, params=params, headers=headers, **kwargs)
    finally:
        await client.close()
