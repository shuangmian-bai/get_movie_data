"""ad_filter —— 插件化去广告处理模块

输入上游 m3u8，逐分片抽帧检测广告：全广告丢弃、水印广告去水印落盘、
正常分片经本服务代理，最终产出一份处理后的 m3u8 返回前端播放。

检测器（Detector）与去水印器（Remover）可插拔；站点 → 插件组合关系由应用层
（``main.py``）编排。本模块不依赖 ``stream_factory`` / ``media_source``，
仅依赖系统 ffmpeg/ffprobe（可选 tesseract）与 httpx。
"""
from ad_filter import config, engine
from ad_filter.api import api_router, set_pipeline_getter
from ad_filter.detector import Detector, OcrDetector
from ad_filter.proxy import close as close_proxy
from ad_filter.remover import DelogoRemover, Remover

__all__ = [
    "config",
    "engine",
    "api_router",
    "set_pipeline_getter",
    "Detector",
    "OcrDetector",
    "Remover",
    "DelogoRemover",
    "close_proxy",
]
