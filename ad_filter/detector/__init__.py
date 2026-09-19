"""检测器插件子包"""
from ad_filter.detector.base import Detector
from ad_filter.detector.ocr import OcrDetector

__all__ = ["Detector", "OcrDetector"]
