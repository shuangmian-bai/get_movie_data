"""检测器抽象基类

``Detector``：检测器插件，对单个 ts 分片判定广告类型（none / full / watermark）。
不同资源有不同的广告形式与内容，站点 → 检测器组合关系由应用层（``main.py``）
自由编排；插件不携带 ``base_url``。
"""
import abc

from ad_filter.models import DetectionResult


class Detector(abc.ABC):
    """检测器插件：分片级广告检测。可复用、不绑定站点。"""

    name: str = ""

    @abc.abstractmethod
    async def detect(self, segment_path: str, segment_url: str) -> DetectionResult:
        """检测一个已下载到本地的 ts 分片。

        :param segment_path: 本地分片路径
        :param segment_url:  分片源绝对 URL（供日志 / 后续扩展）
        :return: 检测结果（none / full / watermark + 水印区域）
        """

    def fingerprint(self) -> str:
        """配置指纹：纳入结果复用键（sid），配置变化触发重新处理。默认返回 ``name``。"""
        return self.name
