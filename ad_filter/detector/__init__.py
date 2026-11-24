"""检测器插件子包"""
from ad_filter.detector.base import Detector
from ad_filter.detector.ocr import OcrDetector
from ad_filter.detector.top_banner import TopBannerDetector

__all__ = ["Detector", "OcrDetector", "TopBannerDetector"]
