"""URL 处理器子包 —— UrlHandler 基类 + 各 URL 处理器（孙子模块）

URL 处理器是分片级内容处理器，对已下载的单个 ts 分片做内容检测（如 OCR 识别违规词），
命中则拉黑该分片（从重写的 index.m3u8 移除，跳过推流）。
每个具体处理器一个文件（孙子模块），站点 → 处理器组合在应用层 ``main.py`` 编排。
"""
from stream_factory.url_handlers.base import UrlHandler
from stream_factory.url_handlers.ocr import OcrUrlHandler

__all__ = ["UrlHandler", "OcrUrlHandler"]
