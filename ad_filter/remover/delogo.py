"""delogo 去水印插件（内置实现）

``DelogoRemover``：用 ffmpeg ``delogo`` 滤镜把水印区域用周边像素插值模糊覆盖，
输出重编码后的 ts 分片。delogo 是通用「去水印」近似（非还原原画），作为内置
实现，可替换为其它去水印算法。
"""
import asyncio
import logging
import os
from typing import List

from ad_filter import config
from ad_filter.models import Box
from ad_filter.remover.base import Remover

logger = logging.getLogger("ad_filter.remover.delogo")


class DelogoRemover(Remover):
    """ffmpeg delogo 去水印：模糊覆盖水印区域，重编码输出。"""

    name = "delogo"

    async def remove(
        self, segment_path: str, boxes: List[Box], out_path: str
    ) -> str:
        if not boxes:
            return segment_path  # 无水印区域，原样返回
        vf = ",".join(
            f"delogo=x={b.x}:y={b.y}:w={b.w}:h={b.h}" for b in boxes
        )
        cmd = [
            config.FFMPEG_BIN, "-y",
            "-i", segment_path,
            "-vf", vf,
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-c:a", "copy",
            out_path,
        ]
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.DEVNULL,
        )
        rc = await proc.wait()
        if rc != 0 or not os.path.exists(out_path):
            raise RuntimeError("delogo 处理失败")
        return out_path
