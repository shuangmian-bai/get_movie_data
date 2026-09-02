"""流会话 —— FFmpeg 子进程的生命周期管理

负责：启动 HLS 主进程（Web 播放）、可选启动 RTSP 推流进程（原生客户端）、
等待 HLS 就绪、后台采集 stderr 日志、停止并清理。

HLS 是主输出，其进程状态决定会话状态；RTSP 是「尽力而为」的独立进程，
即使失败也只记告警，绝不影响 HLS 输出。
状态机：``preparing → running`` 或 ``preparing → error``，停止后为 ``stopped``。
"""
import asyncio
import logging
import os
import shutil
import time
from typing import List, Optional

from stream_factory import config, process_cache
from stream_factory.pipeline import build_hls_command, build_rtsp_command
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
        self.rtsp_process: Optional[asyncio.subprocess.Process] = None
        self._stderr_tail: List[str] = []
        self._stderr_task: Optional[asyncio.Task] = None
        self._rtsp_stderr_task: Optional[asyncio.Task] = None

    @classmethod
    def from_cache(cls, sid: str, req: StreamRequest, hls_dir: str) -> "StreamSession":
        """命中处理结果缓存的轻量会话：HLS 已就绪，无 ffmpeg 进程，无 RTSP。"""
        session = cls(sid, req, hls_dir, rtsp_url=None)
        session.status = "running"
        return session

    async def start(self) -> None:
        """启动 HLS 主进程并等待就绪；随后可选启动 RTSP 推流进程。"""
        os.makedirs(self.hls_dir, exist_ok=True)
        cmd = build_hls_command(self.req, self.hls_dir)
        logger.info("会话 %s 启动 HLS ffmpeg：%s", self.sid, " ".join(cmd))
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
            if self.rtsp_url:
                self._start_rtsp()
        elif self.process.returncode is not None:
            self.status = "error"
            self.error = (
                self._tail_summary() or f"ffmpeg 退出，返回码 {self.process.returncode}"
            )
        else:
            # 超时但进程仍存活（源较慢），乐观标记 running，由调用方自行判定
            logger.warning("会话 %s HLS 就绪超时但仍存活，乐观标记 running", self.sid)
            self.status = "running"
            if self.rtsp_url:
                self._start_rtsp()

    def _start_rtsp(self) -> None:
        """后台启动 RTSP 推流进程（失败只记告警，不改变会话状态）。"""
        cmd = build_rtsp_command(self.req, self.rtsp_url)
        logger.info("会话 %s 启动 RTSP ffmpeg：%s", self.sid, " ".join(cmd))

        async def _run() -> None:
            try:
                self.rtsp_process = await asyncio.create_subprocess_exec(
                    *cmd,
                    stdout=asyncio.subprocess.DEVNULL,
                    stderr=asyncio.subprocess.PIPE,
                )
            except Exception as exc:  # noqa: BLE001
                logger.warning("会话 %s RTSP 推流进程启动失败：%s", self.sid, exc)
                return
            self._rtsp_stderr_task = asyncio.create_task(self._drain_rtsp_stderr())

        asyncio.create_task(_run())

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
        """后台读取 HLS 主进程 stderr，保留最近若干行用于诊断。"""
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
            # 正常转流结束（returncode == 0）→ 登记处理结果缓存，供后续复用
            if self.process.returncode == 0:
                process_cache.mark_complete(self.hls_dir, self.sid, self.req.source_url)

    async def _drain_rtsp_stderr(self) -> None:
        """后台读取 RTSP 进程 stderr，进程异常退出时记录告警（不影响会话状态）。"""
        assert self.rtsp_process and self.rtsp_process.stderr
        tail: List[str] = []
        while True:
            line = await self.rtsp_process.stderr.readline()
            if not line:
                break
            tail.append(line.decode(errors="replace").rstrip())
            if len(tail) > 50:
                tail.pop(0)
        await self.rtsp_process.wait()
        if self.rtsp_process.returncode != 0:
            logger.warning(
                "会话 %s RTSP 推流进程退出（返回码 %s）：%s",
                self.sid,
                self.rtsp_process.returncode,
                " | ".join(tail[-5:]),
            )
        else:
            logger.info("会话 %s RTSP 推流进程正常结束", self.sid)

    def _tail_summary(self) -> str:
        return "\n".join(self._stderr_tail[-20:])

    async def stop(self) -> None:
        """终止 HLS 主进程与 RTSP 进程；仅清理「半成品」HLS 目录（完整缓存保留）。"""
        for proc in (self.rtsp_process, self.process):
            if proc and proc.returncode is None:
                proc.terminate()
                try:
                    await asyncio.wait_for(proc.wait(), timeout=5)
                except asyncio.TimeoutError:
                    proc.kill()
                    await proc.wait()
        for task in (self._rtsp_stderr_task, self._stderr_task):
            if task:
                task.cancel()
        self.status = "stopped"
        # 完整缓存（有未过期 meta）保留复用；转流中的半成品目录清理
        if not process_cache.is_complete(self.hls_dir):
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
