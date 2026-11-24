"""顶部条幅广告去除器

配合 ``TopBannerDetector``：对检测器给出的顶部条带逐帧处理，再用 PyAV 重编码写回
（视频重编码 + 音频 copy）。四种模式：

- ``inpaint``（默认）：用周边内容修复**文字像素**，画面完整、不裁剪不模糊；
- ``crop``：裁掉顶部条带后缩放回原尺寸，广告彻底消失，画面轻微纵向拉伸；
- ``blur``：高斯模糊顶部条带，保留构图但有残影；
- ``black``：纯色填充顶部条带，最彻底但视觉上是黑色硬边。

工程约束（实测 av 12.3.0）：

- 同一容器 ``decode()`` 后再 ``demux()`` 会在关闭时触发段错误，因此视频与音频
  分别用两个输入容器打开；
- 先写完所有视频包再写音频包会让 MPEG-TS 时间戳乱序、产物无法解码，
  因此音视频包按秒级 dts **交错 mux**。

不依赖系统 ffmpeg 二进制（PyAV 自带 ffmpeg 库）。
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

logger = logging.getLogger("ad_filter.remover.top_banner")


def _pkt_seconds(pkt) -> float:
    """把包 dts 换算成秒，用于音视频交错排序（无 dts 时按 0）。"""
    if pkt is None or pkt.dts is None or pkt.time_base is None:
        return 0.0
    try:
        return float(pkt.dts * pkt.time_base)
    except (TypeError, ValueError):
        return 0.0


class TopBannerRemover(Remover):
    """顶部条带去除器：按 boxes 区域逐帧处理并重编码写回 .ts。"""

    name = "top_banner"

    def __init__(self, mode: str = None, blur_strength: float = None):
        self.mode = (mode or config.TOP_BANNER_REMOVE_MODE or "blur").lower()
        self.blur_strength = (
            blur_strength
            if blur_strength is not None
            else config.TOP_BANNER_BLUR_STRENGTH
        )

    async def remove(
        self, segment_path: str, boxes: List[Box], out_path: str
    ) -> str:
        if not boxes:
            return segment_path  # 无区域，原样返回
        return await to_thread(self._remove, segment_path, boxes, out_path)

    def _remove(self, segment_path: str, boxes: List[Box], out_path: str) -> str:
        """同步处理体（在线程池中执行）：逐帧处理顶部条带 + 重编码。"""
        import av  # PyAV 仅在本方法内按需导入

        av.logging.set_level(av.logging.ERROR)
        # 视频与音频分别打开两个容器：同一容器 decode 后再 demux 会触发 av 12.3.0 段错误
        vin = av.open(segment_path)
        ain = av.open(segment_path)
        out = av.open(out_path, "w", format="mpegts")
        try:
            in_v = vin.streams.video[0]
            width, height = in_v.width, in_v.height
            in_a = ain.streams.audio[0] if ain.streams.audio else None

            rate = in_v.average_rate or in_v.base_rate or 25
            out_v = out.add_stream("libx264", rate=rate)
            out_v.width = width
            out_v.height = height
            out_v.pix_fmt = "yuv420p"
            out_v.options = {
                "preset": config.OUTPUT_PRESET,
                "crf": config.OUTPUT_CRF,
            }

            band_h = self._band_height(boxes, height)

            # 1) 视频编码并收集包（带秒级 dts，供交错 mux）
            video_packets = []
            for frame in vin.decode(in_v):
                img = frame.to_ndarray(format="bgr24")
                img = self._process_frame(img, boxes, band_h)
                new_frame = av.VideoFrame.from_ndarray(img, format="bgr24")
                new_frame.pts = frame.pts
                new_frame.time_base = frame.time_base
                for pkt in out_v.encode(new_frame):
                    video_packets.append((_pkt_seconds(pkt), pkt))
            for pkt in out_v.encode(None):
                video_packets.append((_pkt_seconds(pkt), pkt))

            # 2) 音频 remux（copy）并收集包
            out_a = None
            audio_packets = []
            if in_a is not None:
                out_a = out.add_stream(template=in_a)
                for pkt in ain.demux(in_a):
                    if pkt.dts is None:
                        continue
                    audio_packets.append((_pkt_seconds(pkt), pkt))

            # 3) 音视频按时间交错 mux（先全部视频再全部音频会时间戳乱序，产物不可解码）
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
            raise RuntimeError("顶部条幅去除写回失败")
        return out_path

    def _band_height(self, boxes: List[Box], height: int) -> int:
        """取 boxes 覆盖的最大 y 下边界，作为裁切/处理条带高度。"""
        y2 = max((b.y + b.h for b in boxes), default=0)
        return max(1, min(int(y2), height))

    def _process_frame(self, img: np.ndarray, boxes: List[Box], band_h: int) -> np.ndarray:
        """按模式处理一帧。"""
        h, w = img.shape[:2]
        if self.mode == "crop":
            # 裁掉顶部条带后缩放回原尺寸（广告彻底消失，画面轻微纵向拉伸）
            cropped = img[band_h:, :]
            if cropped.size == 0:
                return img
            return cv2.resize(cropped, (w, h), interpolation=cv2.INTER_LINEAR)

        for b in boxes:
            x1 = max(0, min(b.x, w))
            y1 = max(0, min(b.y, h))
            x2 = max(0, min(b.x + b.w, w))
            y2 = max(0, min(b.y + b.h, h))
            if x2 <= x1 or y2 <= y1:
                continue
            region = img[y1:y2, x1:x2]
            if self.mode == "black":
                img[y1:y2, x1:x2] = 0
            elif self.mode == "inpaint":
                # 只把文字像素用周边内容修复：画面完整、不裁不糊
                mask = self._text_mask(region)
                if mask is not None and int(mask.sum()) > 0:
                    img[y1:y2, x1:x2] = cv2.inpaint(
                        region, mask, config.TOP_BANNER_INPAINT_RADIUS, cv2.INPAINT_TELEA
                    )
            else:
                # blur：核大小随区域高度自适应，保证奇数且不小于 21
                k = max(21, int((y2 - y1) * self.blur_strength) | 1)
                img[y1:y2, x1:x2] = cv2.GaussianBlur(region, (k, k), 0)
        return img

    def _text_mask(self, region: np.ndarray) -> np.ndarray:
        """从顶部条带提取广告文字掩码：白字（高亮低饱和）+ 红字（红色区间），
        再膨胀覆盖白字黑描边。背景大面积高亮/红色可能被纳入，但 inpaint 后仍自然。"""
        hsv = cv2.cvtColor(region, cv2.COLOR_BGR2HSV)
        h, s, v = cv2.split(hsv)
        white = ((v > 200) & (s < 90)).astype(np.uint8) * 255
        red = (((h < 12) | (h > 168)) & (s > 120) & (v > 120)).astype(np.uint8) * 255
        mask = cv2.bitwise_or(white, red)
        k = max(3, int(config.TOP_BANNER_TEXT_DILATE) | 1)
        mask = cv2.dilate(
            mask, cv2.getStructuringElement(cv2.MORPH_RECT, (k, k)), iterations=2
        )
        return mask
