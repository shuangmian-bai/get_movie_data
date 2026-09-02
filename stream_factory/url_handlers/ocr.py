"""OCR URL 处理器（孙子模块）

``OcrUrlHandler``：对每个 ts 分片抽帧并 OCR 识别文字，命中违规词表（如
「澳门新葡京」）则拉黑该分片（跳过推流）。

- 抽帧：ffmpeg 抽 ts 中间若干帧为 PNG（``OCR_FRAME_COUNT``）；
- 识别：tesseract 命令行读 stdout 文本（``OCR_LANG``，默认 chi_sim）；
- 判断：文本命中词表任一词即拉黑；
- **失败容错**：抽帧 / tesseract 报错、语言包缺失 → 返回 False（放行），绝不阻断转流。

依赖系统 ``tesseract`` 与对应语言包（中文需 ``tesseract-langpack-chi_sim``）。
"""
import asyncio
import hashlib
import logging
import os
import shutil
import tempfile
from typing import List, Optional

from stream_factory import config
from stream_factory.url_handlers.base import UrlHandler

logger = logging.getLogger("stream_factory.url_handlers.ocr")

# 模块级 OCR 信号量（tesseract 较重，限流避免打满 CPU）
_ocr_sem: Optional[asyncio.Semaphore] = None


def _get_sem() -> asyncio.Semaphore:
    """懒加载全局 OCR 信号量（首次在事件循环内创建）。"""
    global _ocr_sem
    if _ocr_sem is None:
        _ocr_sem = asyncio.Semaphore(config.OCR_CONCURRENCY)
    return _ocr_sem


def _parse_blockwords(raw: str) -> List[str]:
    """把逗号分隔的词表解析为去空白词列表。"""
    return [w.strip() for w in (raw or "").split(",") if w.strip()]


class OcrUrlHandler(UrlHandler):
    """OCR URL 处理器：抽帧识别文字，命中违规词则拉黑分片。"""

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
        self.frame_count = frame_count if frame_count is not None else config.OCR_FRAME_COUNT

    def fingerprint(self) -> str:
        """配置指纹：词表 + 语言 + 抽帧数。任一变化 → sid 变化 → 重新转流。"""
        digest = hashlib.md5(
            "|".join(sorted(self.blockwords)).encode("utf-8")
        ).hexdigest()[:12]
        return f"{self.name}:{digest}:{self.lang}:{self.frame_count}"

    async def handle(self, segment_url: str, segment_path: str) -> bool:
        """抽帧 + OCR，命中任一违规词则返回 True（拉黑）。失败一律放行。"""
        if not self.blockwords or not os.path.exists(segment_path):
            return False
        async with _get_sem():
            try:
                text = await self._ocr_text(segment_path)
            except Exception as exc:  # noqa: BLE001 - OCR 失败一律放行
                logger.warning("OCR 失败，放行分片 %s：%s", segment_url, exc)
                return False
        if not text:
            return False
        return any(w in text for w in self.blockwords)

    async def _ocr_text(self, segment_path: str) -> str:
        """抽帧 + 逐帧 OCR，返回拼接后的识别文本（失败返回空串）。"""
        tmpdir = tempfile.mkdtemp(prefix="ocr_")
        try:
            frames = await self._extract_frames(segment_path, tmpdir)
            parts = []
            for frame in frames:
                t = await self._run_tesseract(frame)
                if t:
                    parts.append(t)
            return "\n".join(parts)
        finally:
            shutil.rmtree(tmpdir, ignore_errors=True)

    async def _extract_frames(self, segment_path: str, tmpdir: str) -> List[str]:
        """用 ffmpeg 在时长范围内均匀抽若干帧为临时 PNG，返回文件路径列表。"""
        duration = await self._probe_duration(segment_path)
        out_paths: List[str] = []
        for i in range(self.frame_count):
            # 均匀取点（frame_count=1 时取中间帧）
            t = duration * (i + 1) / (self.frame_count + 1)
            out = os.path.join(tmpdir, f"frame_{i:02d}.png")
            cmd = [
                config.FFMPEG_BIN, "-ss", f"{t:.3f}", "-i", segment_path,
                "-frames:v", "1", "-y", out,
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

    async def _run_tesseract(self, image_path: str) -> str:
        """tesseract OCR 一张图，返回识别文本（失败返回空串）。"""
        try:
            cmd = [
                config.OCR_TESSERACT_BIN, image_path, "stdout",
                "-l", self.lang, "--psm", "6",
            ]
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
            )
            out, _ = await proc.communicate()
            return out.decode(errors="replace").strip()
        except Exception:  # noqa: BLE001
            return ""
