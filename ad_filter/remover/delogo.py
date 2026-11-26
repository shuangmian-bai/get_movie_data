"""去水印插件（内置实现）

``DelogoRemover``：用 opencv ``inpaint`` 把水印区域用周边像素插值填补，再用 PyAV
重编码写回 .ts 分片（视频重编码 + 音频 copy）。不依赖系统 ffmpeg 二进制（PyAV
自带 ffmpeg 库），去水印为通用近似（非还原原画），作为内置实现可替换。

工程约束（实测 av 12.3.0）：同一容器 ``decode()`` 后再 ``demux()`` 会在关闭时段错误，
且先写完视频包再写音频包会让 MPEG-TS 时间戳乱序、产物无法解码。因此视频与音频
分两个容器打开，音视频包按秒级 dts 交错 mux。
"""
import logging
import os
from typing import List

import cv2
import numpy as np

from ad_filter import config
from ad_filter._compat import to_thread
from ad_filter.models import Box
from ad_filter.remover.base import Remover

logger = logging.getLogger("ad_filter.remover.delogo")


def _build_mask(boxes: List[Box], width: int, height: int) -> np.ndarray:
    """由水印区域列表生成单通道掩码（白=待填补区域）。"""
    mask = np.zeros((height, width), dtype=np.uint8)
    for b in boxes:
        x1, y1 = max(0, b.x), max(0, b.y)
        x2, y2 = min(width, b.x + b.w), min(height, b.y + b.h)
        if x2 > x1 and y2 > y1:
            mask[y1:y2, x1:x2] = 255
    return mask


def _pkt_seconds(pkt) -> float:
    """把包 dts 换算成秒，用于音视频交错排序（无 dts 时按 0）。"""
    if pkt is None or pkt.dts is None or pkt.time_base is None:
        return 0.0
    try:
        return float(pkt.dts * pkt.time_base)
    except (TypeError, ValueError):
        return 0.0


class DelogoRemover(Remover):
    """opencv inpaint 去水印 + PyAV 重编码写回 .ts。"""

    name = "delogo"

    async def remove(
        self, segment_path: str, boxes: List[Box], out_path: str
    ) -> str:
        if not boxes:
            return segment_path  # 无水印区域，原样返回
        return await to_thread(self._remove, segment_path, boxes, out_path)

    def _remove(self, segment_path: str, boxes: List[Box], out_path: str) -> str:
        """同步处理体（在线程池中执行）：视频 inpaint 重编码 + 音频 copy。"""
        import av  # PyAV 仅在本方法内按需导入，避免无此库时影响模块加载
        av.logging.set_level(av.logging.ERROR)  # 抑制 h264 解码的警告日志（POCs/mmco 等）

        # 视频与音频分别打开两个容器：同一容器 decode 后再 demux 会触发 av 12.3.0 段错误
        vin = av.open(segment_path)
        ain = av.open(segment_path)
        out = av.open(out_path, "w", format="mpegts")
        try:
            in_v = vin.streams.video[0]
            width, height = in_v.width, in_v.height
            mask = _build_mask(boxes, width, height)
            in_a = ain.streams.audio[0] if ain.streams.audio else None

            rate = in_v.average_rate or in_v.base_rate or 25
            out_v = out.add_stream("libx264", rate=rate)
            out_v.thread_count = max(1, config.OUTPUT_THREADS)
            out_v.width = width
            out_v.height = height
            out_v.pix_fmt = "yuv420p"
            out_v.options = {
                "preset": config.OUTPUT_PRESET,
                "crf": config.OUTPUT_CRF,
            }

            # 1) 视频：逐帧 inpaint 后重编码并收集包（带秒级 dts）
            video_packets = []
            for frame in vin.decode(in_v):
                img = frame.to_ndarray(format="bgr24")
                img = cv2.inpaint(
                    img, mask, config.INPAINT_RADIUS, cv2.INPAINT_TELEA
                )
                new_frame = av.VideoFrame.from_ndarray(img, format="bgr24")
                new_frame.pts = frame.pts
                new_frame.time_base = frame.time_base
                for pkt in out_v.encode(new_frame):
                    video_packets.append((_pkt_seconds(pkt), pkt))
            for pkt in out_v.encode(None):
                video_packets.append((_pkt_seconds(pkt), pkt))

            # 2) 音频：remux（copy）并收集包
            out_a = None
            audio_packets = []
            if in_a is not None:
                out_a = out.add_stream(template=in_a)
                for pkt in ain.demux(in_a):
                    if pkt.dts is None:
                        continue
                    audio_packets.append((_pkt_seconds(pkt), pkt))

            # 3) 音视频按时间交错 mux（先视频后音频会时间戳乱序，产物不可解码）
            merged = [(t, p, out_v) for t, p in video_packets]
            if out_a is not None:
                merged += [(t, p, out_a) for t, p in audio_packets]
            merged.sort(key=lambda item: item[0])
            for _, pkt, stream in merged:
                pkt.stream = stream
                out.mux(pkt)
        finally:
            out.close()
            vin.close()
            ain.close()

        if not os.path.exists(out_path) or os.path.getsize(out_path) == 0:
            raise RuntimeError("去水印写回失败")
        return out_path
