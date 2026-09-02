"""流会话 —— 单个 FFmpeg 子进程的生命周期管理

负责：启动 ffmpeg 子进程、等待 HLS 就绪、后台采集 stderr 日志、停止并清理。
状态机：``preparing → running`` 或 ``preparing → error``，停止后为 ``stopped``。
"""
import asyncio
import logging
import os
import shutil
import time
from typing import List, Optional

from stream_factory import config
from stream_factory.pipeline import build_command
from stream_factory.rules import StreamRequest

logger = logging.getLogger("stream_factory.session")


class StreamSession:
    """单个流会话。"""

    def __init__(
        self, sid: str, req: StreamRequest, hls_dir: str, rtsp_url: Optional[str]
    ):
        self.sid = sid
        self.req = req
        self.hls_dir = hls_dir
        self.rtsp_url = rtsp_url
        self.hls_url = f"/streams/{sid}/index.m3u8"  # 由 /streams 静态目录提供
        self.status = "preparing"
        self.created_at = time.time()
        self.error: Optional[str] = None
        self.process: Optional[asyncio.subprocess.Process] = None
        self._stderr_tail: List[str] = []
        self._stderr_task: Optional[asyncio.Task] = None

    async def start(self) -> None:
        """启动 ffmpeg 子进程并等待 HLS 就绪。"""
        os.makedirs(self.hls_dir, exist_ok=True)
        cmd = build_command(self.req, self.sid, self.hls_dir)
        logger.info("会话 %s 启动 ffmpeg：%s", self.sid, " ".join(cmd))
        try:
            self.process = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.PIPE,
            )
        except FileNotFoundError:
            self.status = "error"
            self.error = f"找不到可执行文件 {config.FFMPEG_BIN}，请确认 ffmpeg 已安装"
            return
        except Exception as exc:  # noqa: BLE001
            self.status = "error"
            self.error = f"启动 ffmpeg 失败：{exc}"
            return

        self._stderr_task = asyncio.create_task(self._drain_stderr())

        ready = await self._wait_hls_ready()
        if ready:
            self.status = "running"
        elif self.process.returncode is not None:
            self.status = "error"
            self.error = (
                self._tail_summary() or f"ffmpeg 退出，返回码 {self.process.returncode}"
            )
        else:
            # 超时但进程仍存活（源较慢），乐观标记 running，由调用方自行判定
            logger.warning("会话 %s HLS 就绪超时但仍存活，乐观标记 running", self.sid)
            self.status = "running"

    async def _wait_hls_ready(self) -> bool:
        """轮询等待 HLS 索引文件出现；进程提前退出则判定失败。"""
        index = os.path.join(self.hls_dir, "index.m3u8")
        deadline = time.time() + config.STREAM_READY_TIMEOUT
        while time.time() < deadline:
            if os.path.exists(index):
                return True
            if self.process is not None and self.process.returncode is not None:
                return False
            await asyncio.sleep(0.3)
        return os.path.exists(index)

    async def _drain_stderr(self) -> None:
        """后台读取 stderr，保留最近若干行用于诊断。"""
        assert self.process and self.process.stderr
        while True:
            line = await self.process.stderr.readline()
            if not line:
                break
            text = line.decode(errors="replace").rstrip()
            self._stderr_tail.append(text)
            if len(self._stderr_tail) > 200:
                self._stderr_tail.pop(0)
        # stderr 读到 EOF，等待进程退出以便 returncode 就绪
        if self.process:
            await self.process.wait()

    def _tail_summary(self) -> str:
        return "\n".join(self._stderr_tail[-20:])

    async def stop(self) -> None:
        """终止子进程并清理 HLS 目录。"""
        if self.process and self.process.returncode is None:
            self.process.terminate()
            try:
                await asyncio.wait_for(self.process.wait(), timeout=5)
            except asyncio.TimeoutError:
                self.process.kill()
                await self.process.wait()
        if self._stderr_task:
            self._stderr_task.cancel()
        self.status = "stopped"
        shutil.rmtree(self.hls_dir, ignore_errors=True)

    def is_alive(self) -> bool:
        return self.process is not None and self.process.returncode is None

    def to_dict(self) -> dict:
        return {
            "sid": self.sid,
            "status": self.status,
            "hls_url": self.hls_url,
            "rtsp_url": self.rtsp_url,
            "source_url": self.req.source_url,
            "source_type": self.req.source_type,
            "created_at": self.created_at,
            "error": self.error,
        }
