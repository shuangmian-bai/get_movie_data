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

# 统一缓存根目录（所有缓存产物——HLS 输出 / 源视频缓存——都归到其下，不散落在项目根）
CACHE_ROOT: str = os.getenv("STREAM_FACTORY_CACHE_ROOT", str(_PROJECT_ROOT / "cache"))

# HLS 输出根目录（每个流会话一个子目录），默认位于统一缓存根 streams/
HLS_ROOT: str = os.getenv("STREAM_FACTORY_HLS_ROOT", str(Path(CACHE_ROOT) / "streams"))

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

# ---- 源视频缓存配置 ----

# 源视频缓存根目录（按 source_url 哈希建子目录），默认位于统一缓存根 video_cache/
VIDEO_CACHE_ROOT: str = os.getenv(
    "STREAM_FACTORY_VIDEO_CACHE_ROOT", str(Path(CACHE_ROOT) / "video_cache")
)

# 源视频缓存 TTL（秒）：过期后重新下载（惰性删除）
VIDEO_CACHE_TTL: int = int(os.getenv("STREAM_FACTORY_VIDEO_CACHE_TTL", "86400"))

# m3u8 分片并发下载数（流式边下边推模式下，key/init 与分片按序处理，此项主要影响旧回退路径）
VIDEO_CACHE_CONCURRENCY: int = int(
    os.getenv("STREAM_FACTORY_VIDEO_CACHE_CONCURRENCY", "5")
)

# 是否启用流式边下边推（1=首个分片就绪即起 ffmpeg；0=回退到旧的全量下载后转流）
VIDEO_CACHE_STREAMING: bool = (
    os.getenv("STREAM_FACTORY_VIDEO_CACHE_STREAMING", "1") == "1"
)

# 单个分片下载重试次数（流式下分片下载失败先重试，仍失败则跳过该分片继续）
VIDEO_CACHE_SEGMENT_RETRY: int = int(
    os.getenv("STREAM_FACTORY_VIDEO_CACHE_SEGMENT_RETRY", "3")
)

# ---- 处理结果缓存配置 ----

# 处理结果缓存 TTL（秒）：去广告转流后的 HLS 目录复用有效期，过期后重新转流（惰性）
PROCESS_CACHE_TTL: int = int(
    os.getenv("STREAM_FACTORY_PROCESS_CACHE_TTL", str(7 * 24 * 3600))
)

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

# ---- 黑名单配置 ----

# 黑名单根目录（按 ts 源 URL 哈希建文件），默认位于统一缓存根 blacklist/
BLACKLIST_ROOT: str = os.getenv(
    "STREAM_FACTORY_BLACKLIST_ROOT", str(Path(CACHE_ROOT) / "blacklist")
)

# 黑名单 TTL（秒）：命中违规的 ts 在此时长内直接跳过（不下载、不 OCR、不推流）
BLACKLIST_TTL: int = int(os.getenv("STREAM_FACTORY_BLACKLIST_TTL", str(7 * 24 * 3600)))

# ---- OCR 检测配置 ----

# tesseract 可执行文件路径
OCR_TESSERACT_BIN: str = os.getenv("STREAM_FACTORY_OCR_TESSERACT_BIN", "tesseract")

# OCR 识别语言（中文简体 chi_sim，需安装对应语言包）
OCR_LANG: str = os.getenv("STREAM_FACTORY_OCR_LANG", "chi_sim")

# 每个 ts 分片抽帧数（默认抽中间 1 帧）
OCR_FRAME_COUNT: int = int(os.getenv("STREAM_FACTORY_OCR_FRAME_COUNT", "1"))

# 违规词表（逗号分隔），命中任一即拉黑该 ts
OCR_BLOCKWORDS: str = os.getenv(
    "STREAM_FACTORY_OCR_BLOCKWORDS", "澳门新葡京,新葡京"
)

# OCR 并发数（tesseract 较重，默认低并发）
OCR_CONCURRENCY: int = int(os.getenv("STREAM_FACTORY_OCR_CONCURRENCY", "1"))

# ---- 水印字体配置 ----

# drawtext 水印字体文件路径：服务器精简环境常无 fontconfig/中文字体，drawtext 不指定字体
# 来源会报「No font filename provided」，故默认指向项目内置中文字体（跨环境稳定）；
# 设为空串则回退 ffmpeg 的系统字体探测（本地开发可保持原行为）。
DRAWTEXT_FONT: str = os.getenv(
    "STREAM_FACTORY_DRAWTEXT_FONT",
    str(_PROJECT_ROOT / "stream_factory" / "assets" / "fonts" / "DroidSansFallbackFull.ttf"),
)
