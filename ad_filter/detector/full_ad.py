"""整屏广告检测器

针对「帧帧都是广告」的整段插播：对整帧 OCR，命中广告词且命中区域面积大 → 判 ``full``，
由引擎在动态分片端点返回极短空白 ts（播放器无感跳过，等效从 m3u8 移除该分片，
但不动 m3u8 结构，避免时间轴跳变）。

复用 ``OcrDetector`` 的抽帧 / OCR / 面积判定，只保留 ``full``：
非 full 一律返回 ``none``，把顶部条幅等交给其它检测器（如 ``TopBannerDetector``）。
"""
from ad_filter.detector.ocr import OcrDetector
from ad_filter.models import DetectionResult


class FullAdDetector(OcrDetector):
    """整屏广告检测器：只判 full，其余放行。"""

    name = "full_ad"

    def fingerprint(self) -> str:
        """指纹沿用 OcrDetector 的配置指纹，前缀改为 full_ad（与顶部条幅检测器区分）。"""
        base = super().fingerprint()
        return base.replace("ocr:", "full_ad:", 1)

    async def _detect(self, segment_path: str) -> DetectionResult:
        result = await super()._detect(segment_path)
        if result.ad_type == "full":
            return result
        return DetectionResult()
