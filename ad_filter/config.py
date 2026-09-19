"""ad_filter 配置模块

集中管理 ffmpeg/ffprobe 路径、OCR 参数、HTTP 连接、输出目录等参数。
参数优先从环境变量读取（前缀 ``AD_FILTER_``），风格对齐项目其它模块。
"""
import os
from pathlib import Path

# 项目根目录（ad_filter 的上一级）
_PROJECT_ROOT = Path(__file__).resolve().parent.parent

# ---- 系统二进制 ----
FFMPEG_BIN: str = os.getenv("AD_FILTER_FFMPEG_BIN", "ffmpeg")
FFPROBE_BIN: str = os.getenv("AD_FILTER_FFPROBE_BIN", "ffprobe")

# ---- 输出目录 ----

# 处理结果根目录：{项目根}/cache/ad_filter/{sid}/（处理后的 m3u8 + 去水印分片 + meta.json）
OUTPUT_ROOT: str = os.getenv(
    "AD_FILTER_OUTPUT_ROOT", str(_PROJECT_ROOT / "cache" / "ad_filter")
)

# ---- HTTP 连接 ----

HTTP_TIMEOUT: float = float(os.getenv("AD_FILTER_HTTP_TIMEOUT", "15"))
HTTP_USER_AGENT: str = os.getenv(
    "AD_FILTER_HTTP_USER_AGENT",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
)
# 是否信任环境代理（HTTP_PROXY 等）；需直连的源可由请求头/站点自行决定
HTTP_TRUST_ENV: bool = os.getenv("AD_FILTER_HTTP_TRUST_ENV", "1") == "1"

# ---- 分片处理 ----

# 分片「下载 + 检测 + 处理」并发数
SEGMENT_CONCURRENCY: int = int(os.getenv("AD_FILTER_SEGMENT_CONCURRENCY", "4"))

# ---- OCR 检测 ----

OCR_TESSERACT_BIN: str = os.getenv("AD_FILTER_OCR_TESSERACT_BIN", "tesseract")
OCR_LANG: str = os.getenv("AD_FILTER_OCR_LANG", "chi_sim")
# 违规词表（逗号分隔），命中任一即判为广告
OCR_BLOCKWORDS: str = os.getenv(
    "AD_FILTER_OCR_BLOCKWORDS", "澳门新葡京,新葡京,博彩"
)
# 每个 ts 分片抽帧数（均匀取点）
OCR_FRAME_COUNT: int = int(os.getenv("AD_FILTER_OCR_FRAME_COUNT", "3"))
# OCR 并发数（tesseract 较重，默认低并发）
OCR_CONCURRENCY: int = int(os.getenv("AD_FILTER_OCR_CONCURRENCY", "2"))

# ---- 广告分类阈值 ----

# 全广告判定：命中帧占比超过该值且命中区域面积占比超过 FULL_AREA_RATIO → full
FULL_FRAME_RATIO: float = float(os.getenv("AD_FILTER_FULL_FRAME_RATIO", "0.6"))
FULL_AREA_RATIO: float = float(os.getenv("AD_FILTER_FULL_AREA_RATIO", "0.3"))
# 水印区域向外扩展的边距（像素），避免 delogo 遮不完整
WATERMARK_MARGIN: int = int(os.getenv("AD_FILTER_WATERMARK_MARGIN", "10"))
