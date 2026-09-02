"""流工厂 —— 管理多个流会话的生命周期

对外提供创建 / 查询 / 停止会话的编排能力；模块内自洽，应用层只挂载其 API。
"""
import logging
import os
import uuid
from typing import Dict, List, Optional

from stream_factory import config
from stream_factory.mediamtx import rtsp_reachable
from stream_factory.rules import StreamRequest
from stream_factory.session import StreamSession

logger = logging.getLogger("stream_factory.factory")


class StreamFactory:
    """流会话工厂，维护 ``sid → StreamSession`` 映射。"""

    def __init__(self):
        self._sessions: Dict[str, StreamSession] = {}
        os.makedirs(config.HLS_ROOT, exist_ok=True)

    async def create_stream(self, req: StreamRequest) -> StreamSession:
        """创建并启动一个流会话。"""
        sid = uuid.uuid4().hex[:12]
        hls_dir = os.path.join(config.HLS_ROOT, sid)
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
