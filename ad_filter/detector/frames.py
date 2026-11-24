"""抽帧工具：统一用 PyAV 解码。

opencv 的 ``VideoCapture`` 对部分 H.264 ts 分片（参考帧缺失、非关键帧起头等）
读不出帧（``read()`` 返回 False），检测器就会拿到空帧列表 → 永远放行。
PyAV 对同类分片能正常解码，因此抽帧统一走 PyAV；帧数优先用 opencv 的元数据，
拿不到时按 100 帧估算。
"""
import logging
from typing import List

logger = logging.getLogger("ad_filter.detector.frames")


def extract_frames(segment_path: str, count: int) -> List:
    """从分片均匀抽 ``count`` 帧，返回 BGR numpy 数组列表（失败返回空列表）。"""
    import av  # 按需导入

    av.logging.set_level(av.logging.ERROR)
    total = 0
    try:
        import cv2

        cap = cv2.VideoCapture(segment_path)
        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        cap.release()
    except Exception:  # noqa: BLE001 - 帧数拿不到不影响后续
        total = 0
    if total <= 0:
        total = 100

    n = min(max(1, int(count)), total)
    targets = {int(total * (i + 1) / (n + 1)) for i in range(n)}

    frames = []
    try:
        with av.open(segment_path) as container:
            stream = container.streams.video[0]
            for idx, frame in enumerate(container.decode(stream)):
                if idx in targets:
                    frames.append(frame.to_ndarray(format="bgr24"))
                    if len(frames) >= n:
                        break
    except Exception as exc:  # noqa: BLE001 - 解码失败返回已抽到的帧
        logger.warning("PyAV 抽帧失败 %s：%s", segment_path, exc)
    return frames
