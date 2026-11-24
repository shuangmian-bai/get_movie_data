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
SEGMENT_CONCURRENCY: int = int(os.getenv("AD_FILTER_SEGMENT_CONCURRENCY", "2"))

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

# ---- 顶部条幅广告（弹幕 / 跑马灯 / 静态赞助条）----

# 顶部条带高度占画面高度的比例（1280x720 下 0.12 约 86px，覆盖 y=15~55 的广告文字）
TOP_BANNER_RATIO: float = float(os.getenv("AD_FILTER_TOP_BANNER_RATIO", "0.12"))
# 每个分片抽帧数（顶部条带 OCR）
TOP_BANNER_FRAME_COUNT: int = int(os.getenv("AD_FILTER_TOP_BANNER_FRAME_COUNT", "3"))
# 命中帧占比阈值：达到即判为水印广告（顶部条带）
TOP_BANNER_HIT_RATIO: float = float(os.getenv("AD_FILTER_TOP_BANNER_HIT_RATIO", "0.5"))
# 顶部条幅违规词表（逗号分隔；专指顶部赞助/博彩类文字，与全屏 OCR 词表分开）
TOP_BANNER_BLOCKWORDS: str = os.getenv(
    "AD_FILTER_TOP_BANNER_BLOCKWORDS",
    "澳门,威尼斯人,116.am,新葡京,博彩,娱乐城,独家赞助",
)
# 顶部条幅去除模式：inpaint（文字像素内容修复，画面完整，默认）/ crop（裁切）/ blur / black
TOP_BANNER_REMOVE_MODE: str = os.getenv("AD_FILTER_TOP_BANNER_REMOVE_MODE", "inpaint")
# blur 强度：核大小 = 条带高度 * 该系数（取奇数，最小 21）
TOP_BANNER_BLUR_STRENGTH: float = float(
    os.getenv("AD_FILTER_TOP_BANNER_BLUR_STRENGTH", "0.5")
)
# inpaint 模式：修复邻域半径（opencv Telea）
TOP_BANNER_INPAINT_RADIUS: int = int(os.getenv("AD_FILTER_TOP_BANNER_INPAINT_RADIUS", "5"))
# inpaint 模式：文字掩码膨胀核大小（覆盖白字黑描边）
TOP_BANNER_TEXT_DILATE: int = int(os.getenv("AD_FILTER_TOP_BANNER_TEXT_DILATE", "9"))

# ---- 分片按需处理（渐进式）----

# 预取窗口：播放某分片时，后台提前处理其后 N 个分片（让播放跟上，减少卡顿）
PREFETCH_LOOKAHEAD: int = int(os.getenv("AD_FILTER_PREFETCH_LOOKAHEAD", "3"))
# 预取并发数（OCR/去水印较重，默认低并发避免打满 CPU）
PREFETCH_CONCURRENCY: int = int(os.getenv("AD_FILTER_PREFETCH_CONCURRENCY", "2"))
