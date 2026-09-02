"""流工厂 —— 管理多个流会话的生命周期

对外提供创建 / 查询 / 停止会话的编排能力；模块内自洽，应用层只挂载其 API。
"""
import asyncio
import logging
import os
from typing import Dict, List, Optional

from stream_factory import config, process_cache, video_cache
from stream_factory.mediamtx import rtsp_reachable
from stream_factory.rules import StreamRequest
from stream_factory.session import StreamSession

logger = logging.getLogger("stream_factory.factory")


class StreamFactory:
    """流会话工厂，维护 ``sid → StreamSession`` 映射。"""

    def __init__(self):
        self._sessions: Dict[str, StreamSession] = {}
        # per-sid 并发锁（仿 video_cache 的 _locks/_locks_guard），避免同内容并发重复转流
        self._locks: Dict[str, asyncio.Lock] = {}
        self._locks_guard = asyncio.Lock()
        os.makedirs(config.HLS_ROOT, exist_ok=True)

    async def _get_lock(self, sid: str) -> asyncio.Lock:
        """获取 per-sid 并发锁（用 ``_locks_guard`` 保护字典并发读写）。"""
        async with self._locks_guard:
            lock = self._locks.get(sid)
            if lock is None:
                lock = asyncio.Lock()
                self._locks[sid] = lock
            return lock

    async def create_stream(self, req: StreamRequest) -> StreamSession:
        """创建并启动一个流会话。

        先经源视频缓存把上游源缓存到本地（复用连接池 + 并发去重），命中后
        ffmpeg 改读本地文件，减少重复网络请求；再按「源 + 去广告规则」内容寻址出
        ``sid``，命中**处理结果缓存**则跳过 ffmpeg 转流、直接复用 HLS，否则转流，
        转流完成后登记为处理缓存供后续复用。
        """
        local = await video_cache.ensure_source(req.source_url, req.source_type, req.headers)
        if local != req.source_url:
            # 命中本地缓存：改读本地路径，本地读无需防盗链头
            req = req.model_copy(update={"source_url": local, "headers": {}})

        # 内容寻址：同一「源 + 规则」→ 同一 sid → 同一 HLS 目录，天然复用
        sid = process_cache.cache_key(req)
        hls_dir = process_cache.hls_dir(sid)

        lock = await self._get_lock(sid)
        async with lock:
            # 双重检查：等锁期间可能已有会话（转流中/已完成）或已命中缓存
            existing = self._sessions.get(sid)
            if existing is not None:
                return existing
            if process_cache.is_hit(sid):
                # 命中处理结果缓存：直接复用 HLS，不再起 ffmpeg（命中时无 RTSP）
                session = StreamSession.from_cache(sid, req, hls_dir)
                self._sessions[sid] = session
                return session

            # 未命中：正常转流，ffmpeg 输出到 hls_dir，转流完成后即成为处理缓存
            rtsp_url = None
            if config.RTSP_ENABLED and await rtsp_reachable(config.RTSP_SERVER):
                rtsp_url = config.RTSP_SERVER.rstrip("/") + "/" + sid
            session = StreamSession(sid, req, hls_dir, rtsp_url)
            self._sessions[sid] = session
            await session.start()
            return session

    def get(self, sid: str) -> Optional[StreamSession]:
        """按 sid 查询会话。"""
        return self._sessions.get(sid)

    def list_sessions(self) -> List[dict]:
        """列出所有会话（字典形态，供 API 返回）。"""
        return [s.to_dict() for s in self._sessions.values()]

    async def stop(self, sid: str) -> bool:
        """停止并移除会话，返回是否命中。"""
        session = self._sessions.pop(sid, None)
        if session is None:
            return False
        await session.stop()
        return True

    async def shutdown(self) -> None:
        """应用退出时停止所有会话。"""
        for sid in list(self._sessions):
            await self.stop(sid)


# 全局单例
stream_factory = StreamFactory()
