"""mediamtx 进程管理 —— 服务启动时自动拉起 RTSP 服务器

应用层 ``main.py`` 在 FastAPI 生命周期里调用 :func:`ensure_mediamtx` / :func:`stop_mediamtx`，
从而无需用户手动启动 mediamtx。

- 仅当 ``RTSP_ENABLED`` 且 ``MEDIAMTX_AUTOSTART`` 为真时才自动拉起；
- 端口已可连（外部已启动或先前已拉起）则复用，不重复拉起；
- 退出时只停止本模块拉起的实例，不动外部进程；
- 拉起失败时降级为仅 HLS，由 ``factory`` 在建流时按端口可达性兜底判定。
"""
import asyncio
import logging
from typing import Optional
from urllib.parse import urlparse

from stream_factory import config

logger = logging.getLogger("stream_factory.mediamtx")

# 由本模块拉起的 mediamtx 子进程（外部启动的进程不在此列）
_process: Optional[asyncio.subprocess.Process] = None


async def rtsp_reachable(server_url: str, timeout: float = 1.0) -> bool:
    """探测 RTSP 服务器 TCP 端口是否可达。"""
    try:
        parsed = urlparse(server_url)
        host = parsed.hostname or "127.0.0.1"
        port = parsed.port or 8554
        _, writer = await asyncio.wait_for(
            asyncio.open_connection(host, port), timeout=timeout
        )
        writer.close()
        try:
            await writer.wait_closed()
        except Exception:  # noqa: BLE001
            pass
        return True
    except Exception:  # noqa: BLE001
        return False


async def ensure_mediamtx() -> bool:
    """确保 mediamtx 在运行：已可连则复用，否则拉起并等待端口就绪。

    返回 ``True`` 表示 RTSP 可用（外部已运行或自动拉起成功）；``False`` 表示不可用，
    此时建流会降级为仅 HLS。
    """
    global _process
    if not config.RTSP_ENABLED or not config.MEDIAMTX_AUTOSTART:
        return False

    if await rtsp_reachable(config.RTSP_SERVER):
        return True  # 已在运行（外部或先前拉起），直接复用

    try:
        cmd = [config.MEDIAMTX_BIN]
        if config.MEDIAMTX_CONFIG:
            cmd.append(config.MEDIAMTX_CONFIG)
        _process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.DEVNULL,
            start_new_session=True,
        )
    except FileNotFoundError:
        logger.warning("找不到 mediamtx（%s），RTSP 降级为仅 HLS", config.MEDIAMTX_BIN)
        _process = None
        return False
    except Exception as exc:  # noqa: BLE001
        logger.warning("拉起 mediamtx 失败：%s，RTSP 降级为仅 HLS", exc)
        _process = None
        return False

    # 等待端口就绪
    loop = asyncio.get_running_loop()
    deadline = loop.time() + config.MEDIAMTX_STARTUP_TIMEOUT
    while loop.time() < deadline:
        if await rtsp_reachable(config.RTSP_SERVER, timeout=0.5):
            logger.info(
                "已自动拉起 mediamtx（pid=%s），监听 %s", _process.pid, config.RTSP_SERVER
            )
            return True
        if _process.returncode is not None:
            logger.warning(
                "mediamtx 提前退出（返回码 %s），RTSP 降级为仅 HLS", _process.returncode
            )
            _process = None
            return False
        await asyncio.sleep(0.5)

    logger.warning("等待 mediamtx 就绪超时，RTSP 降级为仅 HLS")
    return False


async def stop_mediamtx() -> None:
    """停止由本模块拉起的 mediamtx 进程（外部启动的进程不处理）。"""
    global _process
    if _process is None:
        return
    proc = _process
    _process = None
    if proc.returncode is None:
        proc.terminate()
        try:
            await asyncio.wait_for(proc.wait(), timeout=5)
        except asyncio.TimeoutError:
            proc.kill()
            await proc.wait()
