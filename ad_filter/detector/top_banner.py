"""顶部条幅广告检测器

针对「顶部弹幕 / 跑马灯 / 静态赞助条」这类广告：文字固定在画面**顶部条带**内，
形态有静态居中（如「本节目由 XX 独家赞助播出」）与从右向左滚动（跑马灯）两种。

与通用 ``OcrDetector`` 的区别：

- 只在顶部条带内 OCR，不把整帧文字（含正常字幕）纳入判定；
- 命中后返回**整条顶部条带**作为待处理区域，而不是单帧文字 bbox——
  跑马灯文字逐帧位置变化，单帧 bbox 覆盖不了整段，整条覆盖最稳；
- 顶部条带高度按分辨率比例配置（默认 12%），换分辨率不用改插件。

异常一律放行（``none``），绝不阻断播放链路。
"""
import hashlib
import logging
import os
from typing import List, Optional

from ad_filter import config
from ad_filter._compat import to_thread
from ad_filter.detector.base import Detector
from ad_filter.detector.ocr import _get_engine
from ad_filter.models import Box, DetectionResult

logger = logging.getLogger("ad_filter.detector.top_banner")


def _parse_blockwords(raw: str) -> List[str]:
    """把逗号分隔的词表解析为去空白词列表。"""
    return [w.strip() for w in (raw or "").split(",") if w.strip()]


class TopBannerDetector(Detector):
    """顶部条带 OCR 检测器：命中违规词即判 watermark，返回整条顶部区域。"""

    name = "top_banner"

    def __init__(
        self,
        blockwords: Optional[str] = None,
        top_ratio: Optional[float] = None,
        frame_count: Optional[int] = None,
        score_threshold: Optional[float] = None,
        hit_frame_ratio: Optional[float] = None,
        margin: Optional[int] = None,
    ):
        self.blockwords = _parse_blockwords(
            config.TOP_BANNER_BLOCKWORDS if blockwords is None else blockwords
        )
        self.top_ratio = (
            top_ratio if top_ratio is not None else config.TOP_BANNER_RATIO
        )
        self.frame_count = (
            frame_count if frame_count is not None else config.TOP_BANNER_FRAME_COUNT
        )
        self.score_threshold = (
            score_threshold
            if score_threshold is not None
            else config.OCR_SCORE_THRESHOLD
        )
        self.hit_frame_ratio = (
            hit_frame_ratio
            if hit_frame_ratio is not None
            else config.TOP_BANNER_HIT_RATIO
        )
        self.margin = margin if margin is not None else config.WATERMARK_MARGIN

    def fingerprint(self) -> str:
        """配置指纹：词表 + 条带比例 + 抽帧数 + 命中阈值，任一变化触发重新处理。"""
        digest = hashlib.md5(
            "|".join(sorted(self.blockwords)).encode("utf-8")
        ).hexdigest()[:12]
        return (
            f"{self.name}:{digest}:{self.top_ratio}:{self.frame_count}:"
            f"{self.hit_frame_ratio}:{self.score_threshold}"
        )

    async def detect(self, segment_path: str, segment_url: str) -> DetectionResult:
        """抽帧 + 顶部条带 OCR。任何异常一律放行（none）。"""
        if not self.blockwords or not os.path.exists(segment_path):
            return DetectionResult()
        try:
            return await self._detect(segment_path)
        except Exception as exc:  # noqa: BLE001 - 检测失败一律放行
            logger.warning("顶部条幅检测失败，放行分片 %s：%s", segment_url, exc)
            return DetectionResult()

    async def _detect(self, segment_path: str) -> DetectionResult:
        frames = await to_thread(self._extract_frames, segment_path)
        if not frames:
            return DetectionResult()

        height, width = frames[0].shape[:2]
        band_h = max(1, int(height * self.top_ratio))

        hit_frames = 0
        hits: List[str] = []
        max_y2 = 0.0
        for frame in frames:
            top = frame[0:band_h, :]
            words = await to_thread(self._ocr_top, top)
            frame_hit = False
            for text, score, y2 in words:
                if score < self.score_threshold:
                    continue
                for bw in self.blockwords:
                    if bw and bw in text:
                        frame_hit = True
                        max_y2 = max(max_y2, y2)
                        if bw not in hits:
                            hits.append(bw)
                        break
            if frame_hit:
                hit_frames += 1

        if not hits or hit_frames / len(frames) < self.hit_frame_ratio:
            return DetectionResult()

        # 条带高度取命中文字最低点 + 边距（跑马灯只改 x 不改 y 范围），
        # 避免整条 top_ratio 高度全裁/全糊，减少画面损失。
        band = int(max_y2 + self.margin) if max_y2 > 0 else band_h
        band = max(20, min(band, band_h))
        return DetectionResult(
            ad_type="watermark",
            boxes=[Box(x=0, y=0, w=width, h=band)],
            hits=hits,
        )

    def _extract_frames(self, segment_path: str) -> List:
        """抽帧：统一走 PyAV（opencv 对部分 H.264 分片读不出帧，会导致永远放行）。"""
        from ad_filter.detector.frames import extract_frames

        return extract_frames(segment_path, self.frame_count)

    def _ocr_top(self, image) -> List:
        """对顶部条带做 OCR，返回 [(text, score, y2)]（y2 为文本框底边坐标）。"""
        engine = _get_engine()
        result, _ = engine(image)
        words = []
        for item in result or []:
            # rapidocr 返回 (bbox, text, score)
            if len(item) >= 3:
                box, text, score = item[0], item[1], float(item[2])
                y2 = max(p[1] for p in box)
                words.append((text, score, y2))
        return words
