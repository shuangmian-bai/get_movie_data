"""OCR 检测器（内置实现）

``OcrDetector``：对本地 ts 分片抽帧，用 tesseract 识别文字，命中违规词则判为广告，
并按命中范围分类：

- **full**（全屏广告）：命中帧占比与命中区域面积占比都超过阈值 → 整段丢弃；
- **watermark**（水印广告）：命中局部小区域 → 返回水印区域，交去水印器处理；
- **none**：未命中 / 抽帧或 OCR 失败 → 放行（宁可漏报不可误杀，绝不阻断链路）。
"""
import asyncio
import csv
import hashlib
import io
import logging
import os
import shutil
import tempfile
from typing import List, Optional, Tuple

from ad_filter import config
from ad_filter.detector.base import Detector
from ad_filter.models import Box, DetectionResult

logger = logging.getLogger("ad_filter.detector.ocr")

# 模块级 OCR 信号量（tesseract 较重，限流避免打满 CPU）
_ocr_sem: Optional[asyncio.Semaphore] = None


def _get_sem() -> asyncio.Semaphore:
    global _ocr_sem
    if _ocr_sem is None:
        _ocr_sem = asyncio.Semaphore(config.OCR_CONCURRENCY)
    return _ocr_sem


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
    """OCR 检测器：抽帧 + 文字识别，命中违规词则分类为 full / watermark。"""

    name = "ocr"

    def __init__(
        self,
        blockwords: Optional[str] = None,
        lang: Optional[str] = None,
        frame_count: Optional[int] = None,
    ):
        self.blockwords = _parse_blockwords(
            config.OCR_BLOCKWORDS if blockwords is None else blockwords
        )
        self.lang = lang or config.OCR_LANG
        self.frame_count = (
            frame_count if frame_count is not None else config.OCR_FRAME_COUNT
        )

    def fingerprint(self) -> str:
        """配置指纹：词表 + 语言 + 抽帧数，任一变化触发重新处理。"""
        digest = hashlib.md5(
            "|".join(sorted(self.blockwords)).encode("utf-8")
        ).hexdigest()[:12]
        return f"{self.name}:{digest}:{self.lang}:{self.frame_count}"

    async def detect(self, segment_path: str, segment_url: str) -> DetectionResult:
        """抽帧 + OCR 分类。任何异常一律放行（none），绝不阻断链路。"""
        if not self.blockwords or not os.path.exists(segment_path):
            return DetectionResult()
        async with _get_sem():
            try:
                return await self._detect(segment_path)
            except Exception as exc:  # noqa: BLE001 - OCR 失败一律放行
                logger.warning("OCR 检测失败，放行分片 %s：%s", segment_url, exc)
                return DetectionResult()

    async def _detect(self, segment_path: str) -> DetectionResult:
        tmpdir = tempfile.mkdtemp(prefix="adfilter_ocr_")
        try:
            frames = await self._extract_frames(segment_path, tmpdir)
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
        finally:
            shutil.rmtree(tmpdir, ignore_errors=True)

    async def _extract_frames(self, segment_path: str, tmpdir: str) -> List[str]:
        """均匀抽若干帧为临时 PNG，返回文件路径列表。"""
        duration = await self._probe_duration(segment_path)
        out_paths: List[str] = []
        for i in range(self.frame_count):
            t = duration * (i + 1) / (self.frame_count + 1)
            out = os.path.join(tmpdir, f"frame_{i:02d}.png")
            cmd = [
                config.FFMPEG_BIN, "-ss", f"{t:.3f}",
                "-i", segment_path, "-frames:v", "1", "-y", out,
            ]
            try:
                proc = await asyncio.create_subprocess_exec(
                    *cmd,
                    stdout=asyncio.subprocess.DEVNULL,
                    stderr=asyncio.subprocess.DEVNULL,
                )
                rc = await proc.wait()
            except Exception:  # noqa: BLE001
                continue
            if rc == 0 and os.path.exists(out):
                out_paths.append(out)
        return out_paths

    async def _ocr_words(self, image_path: str) -> List[Tuple[str, int, int, int, int]]:
        """tesseract TSV 识别一张图，返回 [(text, left, top, width, height)]。"""
        try:
            cmd = [
                config.OCR_TESSERACT_BIN, image_path, "stdout",
                "-l", self.lang, "--psm", "6", "tsv",
            ]
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
            )
            out, _ = await proc.communicate()
        except Exception:  # noqa: BLE001
            return []
        words: List[Tuple[str, int, int, int, int]] = []
        reader = csv.reader(io.StringIO(out.decode(errors="replace")), delimiter="\t")
        for row in reader:
            if len(row) < 12:
                continue
            text = row[11].strip()
            if not text:
                continue
            try:
                left, top, width, height = (
                    int(row[6]), int(row[7]), int(row[8]), int(row[9])
                )
            except (ValueError, IndexError):
                continue
            words.append((text, left, top, width, height))
        return words

    async def _probe_duration(self, segment_path: str) -> float:
        """ffprobe 探时长（秒），失败返回 2.0。"""
        try:
            cmd = [
                config.FFPROBE_BIN, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                segment_path,
            ]
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
            )
            out, _ = await proc.communicate()
            return float(out.decode().strip()) or 2.0
        except Exception:  # noqa: BLE001
            return 2.0

    async def _probe_size(self, segment_path: str) -> Tuple[int, int]:
        """ffprobe 探视频宽高，失败返回 (0, 0)。"""
        try:
            cmd = [
                config.FFPROBE_BIN, "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=p=0",
                segment_path,
            ]
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
            )
            out, _ = await proc.communicate()
            parts = out.decode().strip().split(",")
            if len(parts) >= 2:
                return int(parts[0]), int(parts[1])
        except Exception:  # noqa: BLE001
            pass
        return 0, 0
