"""OCR 检测器（内置实现）

``OcrDetector``：用 opencv 均匀抽帧，rapidocr（onnxruntime）识别文字，命中违规词则判为广告，
并按命中范围分类：

- **full**（全屏广告）：命中帧占比与命中区域面积占比都超过阈值 → 整段丢弃；
- **watermark**（水印广告）：命中局部小区域 → 返回水印区域，交去水印器处理；
- **none**：未命中 / 抽帧或 OCR 失败 → 放行（宁可漏报不可误杀，绝不阻断链路）。

不依赖系统 tesseract 二进制，全由 Python 库（opencv / rapidocr）完成。
"""
import asyncio
import hashlib
import logging
import os
import threading
from typing import List, Optional, Tuple

import cv2
import numpy as np

from ad_filter import config
from ad_filter.detector.base import Detector
from ad_filter.models import Box, DetectionResult

logger = logging.getLogger("ad_filter.detector.ocr")

# rapidocr 引擎（懒加载单例：模型加载较重，只初始化一次，线程安全）
_engine = None
_engine_lock = threading.Lock()

# 模块级 OCR 信号量（onnxruntime 推理较重，限流避免打满 CPU）
_ocr_sem: Optional[asyncio.Semaphore] = None


def _get_sem() -> asyncio.Semaphore:
    global _ocr_sem
    if _ocr_sem is None:
        _ocr_sem = asyncio.Semaphore(config.OCR_CONCURRENCY)
    return _ocr_sem


def _get_engine():
    """懒加载 rapidocr 引擎（双重检查 + 线程锁，避免并发重复初始化）。"""
    global _engine
    if _engine is None:
        with _engine_lock:
            if _engine is None:
                from rapidocr_onnxruntime import RapidOCR

                _engine = RapidOCR()
    return _engine


def _parse_blockwords(raw: str) -> List[str]:
    """把逗号分隔的词表解析为去空白词列表。"""
    return [w.strip() for w in (raw or "").split(",") if w.strip()]


def _boxes_area_ratio(boxes: List[Box], width: int, height: int) -> float:
    """命中区域面积占比（用包围盒近似，简化矩形并集计算）。"""
    if not boxes or width <= 0 or height <= 0:
        return 0.0
    x1 = min(b.x for b in boxes)
    y1 = min(b.y for b in boxes)
    x2 = max(b.x + b.w for b in boxes)
    y2 = max(b.y + b.h for b in boxes)
    area = max(0, x2 - x1) * max(0, y2 - y1)
    return min(1.0, area / (width * height))


def _overlap(a: Box, b: Box) -> bool:
    """判断两矩形是否重叠（含相切）。"""
    return not (
        a.x + a.w <= b.x
        or b.x + b.w <= a.x
        or a.y + a.h <= b.y
        or b.y + b.h <= a.y
    )


def _merge_boxes(boxes: List[Box], margin: int) -> List[Box]:
    """合并重叠/相近的水印矩形（贪心），并向外扩展 margin。"""
    merged: List[Box] = []
    for b in boxes:
        rx, ry = max(0, b.x - margin), max(0, b.y - margin)
        rw, rh = b.w + 2 * margin, b.h + 2 * margin
        cand = Box(x=rx, y=ry, w=rw, h=rh)
        placed = False
        for m in merged:
            if _overlap(m, cand):
                nx1, ny1 = min(m.x, rx), min(m.y, ry)
                nx2, ny2 = max(m.x + m.w, rx + rw), max(m.y + m.h, ry + rh)
                m.x, m.y, m.w, m.h = nx1, ny1, nx2 - nx1, ny2 - ny1
                placed = True
                break
        if not placed:
            merged.append(cand)
    return merged


class OcrDetector(Detector):
    """OCR 检测器：opencv 抽帧 + rapidocr 识别，命中违规词则分类为 full / watermark。"""

    name = "ocr"

    def __init__(
        self,
        blockwords: Optional[str] = None,
        frame_count: Optional[int] = None,
        score_threshold: Optional[float] = None,
    ):
        self.blockwords = _parse_blockwords(
            config.OCR_BLOCKWORDS if blockwords is None else blockwords
        )
        self.frame_count = (
            frame_count if frame_count is not None else config.OCR_FRAME_COUNT
        )
        self.score_threshold = (
            score_threshold if score_threshold is not None else config.OCR_SCORE_THRESHOLD
        )

    def fingerprint(self) -> str:
        """配置指纹：词表 + 抽帧数 + 置信度阈值，任一变化触发重新处理。"""
        digest = hashlib.md5(
            "|".join(sorted(self.blockwords)).encode("utf-8")
        ).hexdigest()[:12]
        return f"{self.name}:{digest}:{self.frame_count}:{self.score_threshold}"

    async def detect(self, segment_path: str, segment_url: str) -> DetectionResult:
        """抽帧 + OCR 分类。任何异常一律放行（none），绝不阻断链路。"""
        if not self.blockwords or not os.path.exists(segment_path):
            return DetectionResult()
        try:
            return await self._detect(segment_path)
        except Exception as exc:  # noqa: BLE001 - OCR 失败一律放行
            logger.warning("OCR 检测失败，放行分片 %s：%s", segment_url, exc)
            return DetectionResult()

    async def _detect(self, segment_path: str) -> DetectionResult:
        frames = await self._extract_frames(segment_path)
        if not frames:
            return DetectionResult()

        width, height = await self._probe_size(segment_path)

        hit_frames = 0
        boxes: List[Box] = []
        hits: List[str] = []
        for frame in frames:
            words = await self._ocr_words(frame)
            frame_hit = False
            for text, x, y, w, h in words:
                for bw in self.blockwords:
                    if bw and bw in text:
                        frame_hit = True
                        boxes.append(Box(x=x, y=y, w=w, h=h))
                        if bw not in hits:
                            hits.append(bw)
                        break
            if frame_hit:
                hit_frames += 1

        if not boxes:
            return DetectionResult()

        frame_ratio = hit_frames / len(frames)
        area_ratio = _boxes_area_ratio(boxes, width, height)
        if (
            frame_ratio >= config.FULL_FRAME_RATIO
            and area_ratio >= config.FULL_AREA_RATIO
        ):
            return DetectionResult(ad_type="full", boxes=boxes, hits=hits)

        return DetectionResult(
            ad_type="watermark",
            boxes=_merge_boxes(boxes, config.WATERMARK_MARGIN),
            hits=hits,
        )

    async def _extract_frames(self, segment_path: str) -> List[np.ndarray]:
        """opencv 均匀抽若干帧，返回 BGR numpy 数组列表。"""

        def _run() -> List[np.ndarray]:
            cap = cv2.VideoCapture(segment_path)
            try:
                total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
                if total <= 0:
                    return []
                n = min(self.frame_count, max(1, total))
                frames: List[np.ndarray] = []
                for i in range(n):
                    idx = int(total * (i + 1) / (n + 1))
                    cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
                    ok, frame = cap.read()
                    if ok and frame is not None:
                        frames.append(frame)
                return frames
            finally:
                cap.release()

        return await asyncio.to_thread(_run)

    async def _ocr_words(
        self, frame: np.ndarray
    ) -> List[Tuple[str, int, int, int, int]]:
        """rapidocr 识别一帧，返回 [(text, x, y, w, h)]。"""

        def _run():
            engine = _get_engine()
            result, _ = engine(frame)
            return result

        async with _get_sem():
            try:
                result = await asyncio.to_thread(_run)
            except Exception as exc:  # noqa: BLE001
                logger.debug("OCR 识别失败：%s", exc)
                return []
        words: List[Tuple[str, int, int, int, int]] = []
        if not result:
            return words
        for item in result:
            try:
                box, text, score = item[0], item[1], float(item[2])
            except (IndexError, TypeError, ValueError):
                continue
            if not text or score < self.score_threshold:
                continue
            xs = [int(p[0]) for p in box]
            ys = [int(p[1]) for p in box]
            x, y = min(xs), min(ys)
            w, h = max(xs) - x, max(ys) - y
            words.append((str(text), x, y, w, h))
        return words

    async def _probe_size(self, segment_path: str) -> Tuple[int, int]:
        """opencv 探视频宽高，失败返回 (0, 0)。"""

        def _run() -> Tuple[int, int]:
            cap = cv2.VideoCapture(segment_path)
            try:
                w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                return w, h
            finally:
                cap.release()

        try:
            return await asyncio.to_thread(_run)
        except Exception:  # noqa: BLE001
            return 0, 0
