"""流工厂配置模块

集中管理 FFmpeg 路径、HLS 输出目录、RTSP 服务器地址等流处理参数。
参数优先从环境变量读取，便于后续扩展为动态配置；风格对齐 ``media_source/config.py``。
"""
import os
from pathlib import Path

# 项目根目录（stream_factory 的上一级）
_PROJECT_ROOT = Path(__file__).resolve().parent.parent

# FFmpeg / ffprobe 可执行文件路径
FFMPEG_BIN: str = os.getenv("STREAM_FACTORY_FFMPEG_BIN", "ffmpeg")
FFPROBE_BIN: str = os.getenv("STREAM_FACTORY_FFPROBE_BIN", "ffprobe")

# HLS 输出根目录（每个流会话一个子目录），默认位于项目根 streams/
HLS_ROOT: str = os.getenv("STREAM_FACTORY_HLS_ROOT", str(_PROJECT_ROOT / "streams"))

# HLS 分片时长（秒）与播放列表长度（0 = 保留全部分片，适合点播）
HLS_TIME: int = int(os.getenv("STREAM_FACTORY_HLS_TIME", "2"))
HLS_LIST_SIZE: int = int(os.getenv("STREAM_FACTORY_HLS_LIST_SIZE", "0"))

# RTSP 服务器地址（mediamtx 默认 8554）；FFmpeg 推流目标为 {RTSP_SERVER}/{sid}
RTSP_SERVER: str = os.getenv("STREAM_FACTORY_RTSP_SERVER", "rtsp://127.0.0.1:8554")

# 是否启用 RTSP 双输出（关闭后仅输出 HLS，便于无 mediamtx 环境调试）
RTSP_ENABLED: bool = os.getenv("STREAM_FACTORY_RTSP_ENABLED", "1") == "1"

# 是否在服务启动时自动拉起 mediamtx（RTSP_ENABLED 为真时生效）
MEDIAMTX_AUTOSTART: bool = os.getenv("STREAM_FACTORY_MEDIAMTX_AUTOSTART", "1") == "1"

# 拉起 mediamtx 后等待其端口就绪的超时（秒）
MEDIAMTX_STARTUP_TIMEOUT: float = float(
    os.getenv("STREAM_FACTORY_MEDIAMTX_STARTUP_TIMEOUT", "10")
)

# 流就绪探测超时（秒）：等待 HLS 索引文件出现
STREAM_READY_TIMEOUT: float = float(os.getenv("STREAM_FACTORY_READY_TIMEOUT", "30"))

# mediamtx 可执行文件路径（服务启动时由 mediamtx 模块按需自动拉起）
MEDIAMTX_BIN: str = os.getenv(
    "STREAM_FACTORY_MEDIAMTX_BIN",
    "/mnt/4t/linux/huanjing/mediamtx/1.8.1/mediamtx",
)

# mediamtx 配置文件路径（自动拉起时显式传入，确保用标准默认配置）
MEDIAMTX_CONFIG: str = os.getenv(
    "STREAM_FACTORY_MEDIAMTX_CONFIG",
    str(Path(MEDIAMTX_BIN).parent / "mediamtx.yml"),
)
