"""去水印器抽象基类

``Remover``：去水印插件，对命中水印的分片做画面处理（去水印），输出处理后的
分片文件。可复用、不绑定站点；站点 → 去水印器组合由应用层（``main.py``）编排。
"""
import abc
from typing import List

from ad_filter.models import Box


class Remover(abc.ABC):
    """去水印插件：对单个分片去水印，输出处理后的分片。"""

    name: str = ""

    @abc.abstractmethod
    async def remove(
        self, segment_path: str, boxes: List[Box], out_path: str
    ) -> str:
        """处理一个命中水印的分片。

        :param segment_path: 源分片本地路径
        :param boxes:        检测器给出的水印区域列表
        :param out_path:     输出路径（处理后分片落盘位置）
        :return: 输出路径（处理成功返回 ``out_path``）
        """
