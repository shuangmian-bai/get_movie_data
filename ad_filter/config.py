"""ad_filter 配置模块

集中管理去水印编码、OCR 参数、HTTP 连接、输出目录等参数。
参数优先从环境变量读取（前缀 ``AD_FILTER_``），风格对齐项目其它模块。
不依赖系统 ffmpeg / tesseract 二进制，全部由 Python 库（PyAV / opencv / rapidocr）完成。
"""
import os
from pathlib import Path

# 项目根目录（ad_filter 的上一级）
_PROJECT_ROOT = Path(__file__).resolve().parent.parent

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
# 是否信任环境代理（HTTP_PROXY 等）
HTTP_TRUST_ENV: bool = os.getenv("AD_FILTER_HTTP_TRUST_ENV", "1") == "1"
# 显式代理地址（http/https/socks5），空则回退 trust_env 自动检测系统代理
HTTP_PROXY: str = os.getenv("AD_FILTER_HTTP_PROXY", "")

# ---- 分片处理 ----

# 分片「下载 + 检测 + 处理」并发数
SEGMENT_CONCURRENCY: int = int(os.getenv("AD_FILTER_SEGMENT_CONCURRENCY", "4"))

# ---- 去水印（opencv inpaint + PyAV 重编码）----

# opencv inpaint 邻域半径（越大填补越「平滑」，但可能糊掉细节）
INPAINT_RADIUS: int = int(os.getenv("AD_FILTER_INPAINT_RADIUS", "3"))
# 输出视频编码参数（PyAV/libx264）
OUTPUT_CRF: str = os.getenv("AD_FILTER_OUTPUT_CRF", "23")
OUTPUT_PRESET: str = os.getenv("AD_FILTER_OUTPUT_PRESET", "veryfast")

# ---- OCR 检测（rapidocr）----

# 违规词表（逗号分隔），命中任一即判为广告
OCR_BLOCKWORDS: str = os.getenv(
    "AD_FILTER_OCR_BLOCKWORDS", "澳门新葡京,新葡京,博彩"
)
# 每个 ts 分片抽帧数（均匀取点）
OCR_FRAME_COUNT: int = int(os.getenv("AD_FILTER_OCR_FRAME_COUNT", "3"))
# OCR 识别置信度阈值（低于此值的文字忽略）
OCR_SCORE_THRESHOLD: float = float(os.getenv("AD_FILTER_OCR_SCORE_THRESHOLD", "0.5"))
# OCR 并发数（onnxruntime 推理较重，默认低并发）
OCR_CONCURRENCY: int = int(os.getenv("AD_FILTER_OCR_CONCURRENCY", "2"))

# ---- 广告分类阈值 ----

# 全广告判定：命中帧占比超过该值且命中区域面积占比超过 FULL_AREA_RATIO → full
FULL_FRAME_RATIO: float = float(os.getenv("AD_FILTER_FULL_FRAME_RATIO", "0.6"))
FULL_AREA_RATIO: float = float(os.getenv("AD_FILTER_FULL_AREA_RATIO", "0.3"))
# 水印区域向外扩展的边距（像素），避免 inpaint 遮不完整
WATERMARK_MARGIN: int = int(os.getenv("AD_FILTER_WATERMARK_MARGIN", "10"))
