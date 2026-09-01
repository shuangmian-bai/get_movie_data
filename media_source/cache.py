"""文件缓存模块 —— 按 base_url 命名空间划分、TTL 过期、并发穿透防护

用于「网络爬虫 → Web 服务」场景：同一数据在 TTL 内只爬一次，避免重复请求源站，
同时通过 TTL 保证数据不会长期滞后。

本模块仅存 JSON 可序列化数据，不依赖任何业务模型（Pydantic 模型请先 ``model_dump()``，
取回后再 ``Model(**data)`` 还原）。

目录结构::

    {cache_root}/{namespace}/{key_hash}.json

每个缓存文件内容::

    {"ts": 写入时间戳, "expires": 过期时间戳, "data": 缓存数据}
"""
import asyncio
import hashlib
import json
import os
import re
import time
from typing import Any, Awaitable, Callable, Dict, Optional
from urllib.parse import urlparse

from media_source import config

# 目录名 / 文件名安全字符（其余统一替换为下划线）
_SAFE_RE = re.compile(r"[^0-9a-zA-Z._-]+")


class FileCache:
    """基于文件的异步缓存，按 base_url 划分命名空间。

    - ``namespace``：由 base_url 规范化的目录名，不同站点缓存互相隔离；
    - ``key``：业务参数键（如 ``search:仙逆``），内部哈希为文件名；
    - ``ttl``：存活秒数，过期后视为未命中并惰性删除。
    """

    def __init__(self, root: str = "") -> None:
        self.root = root or config.CACHE_DIR
        self._locks: Dict[str, asyncio.Lock] = {}
        self._locks_guard = asyncio.Lock()

    # ---- 命名空间 / 键 规范化 ----
    @staticmethod
    def namespace_of(base_url: str) -> str:
        """把 base_url 规范化为安全的目录名（不同站点隔离）。"""
        parsed = urlparse(base_url)
        raw = (parsed.netloc + parsed.path).strip("/").replace("/", "_")
        raw = _SAFE_RE.sub("_", raw).strip("_")
        return raw or "default"

    @staticmethod
    def _hash(key: str) -> str:
        return hashlib.md5(key.encode("utf-8")).hexdigest()

    def _path(self, namespace: str, key: str) -> str:
        return os.path.join(self.root, namespace, f"{self._hash(key)}.json")

    # ---- 读写 ----
    async def get(self, namespace: str, key: str) -> Optional[Any]:
        """读取缓存；未命中或已过期返回 ``None``（过期文件惰性删除）。"""
        path = self._path(namespace, key)
        try:
            with open(path, "r", encoding="utf-8") as f:
                payload = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError, OSError):
            return None

        if payload.get("expires", 0) <= time.time():
            self._remove(path)
            return None
        return payload.get("data")

    async def set(self, namespace: str, key: str, data: Any, ttl: int) -> None:
        """写入缓存（原子写：先写临时文件再替换）。"""
        path = self._path(namespace, key)
        now = time.time()
        payload = {"ts": now, "expires": now + ttl, "data": data}
        os.makedirs(os.path.dirname(path), exist_ok=True)
        tmp = f"{path}.tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, default=str)
        os.replace(tmp, path)

    async def get_or_fetch(
        self,
        namespace: str,
        key: str,
        ttl: int,
        fetch: Callable[[], Awaitable[Any]],
    ) -> Any:
        """命中缓存直接返回；未命中则调用 ``fetch`` 拉取并回填，返回新数据。

        通过 per-key 锁防止并发穿透：同一 key 同时仅触发一次 ``fetch``。
        """
        cached = await self.get(namespace, key)
        if cached is not None:
            return cached

        lock = await self._get_lock(namespace, key)
        async with lock:
            # 双重检查：拿到锁后可能已被其他协程写入
            cached = await self.get(namespace, key)
            if cached is not None:
                return cached
            data = await fetch()
            await self.set(namespace, key, data, ttl)
            return data

    # ---- 清理 ----
    async def clear(self, namespace: Optional[str] = None) -> int:
        """清空缓存，返回删除的文件数；不传 namespace 时清空全部。"""
        target = os.path.join(self.root, namespace) if namespace else self.root
        removed = 0
        for dirpath, _dirs, files in os.walk(target):
            for name in files:
                if name.endswith((".json", ".tmp")):
                    try:
                        os.remove(os.path.join(dirpath, name))
                        removed += 1
                    except OSError:
                        pass
        return removed

    # ---- 内部 ----
    async def _get_lock(self, namespace: str, key: str) -> asyncio.Lock:
        lock_key = f"{namespace}:{key}"
        async with self._locks_guard:
            lock = self._locks.get(lock_key)
            if lock is None:
                lock = asyncio.Lock()
                self._locks[lock_key] = lock
            return lock

    @staticmethod
    def _remove(path: str) -> None:
        try:
            os.remove(path)
        except OSError:
            pass


# 全局单例，供业务直接复用
file_cache = FileCache()
