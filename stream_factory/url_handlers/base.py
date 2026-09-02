"""URL 处理器抽象基类（第三类插件）

``UrlHandler``：分片级内容处理器，对已下载到本地的单个 ts 分片做内容检测，
命中违规则返回 ``True``（拉黑该分片：从重写的 ``index.m3u8`` 移除，跳过推流）。

与 ``FramePlugin``（静态帧滤镜）、``StreamPlugin``（流级裁剪）并列；插件不携带
``base_url``，站点 → 处理器组合关系由应用层（``main.py``）自由编排。
"""
import abc


class UrlHandler(abc.ABC):
    """URL 处理器：分片级内容处理器。可复用、不绑定站点。"""

    name: str = ""

    @abc.abstractmethod
    async def handle(self, segment_url: str, segment_path: str) -> bool:
        """检测一个已下载的本地分片。

        :param segment_url:  该分片的源绝对 URL（黑名单键，同 URL 同内容）
        :param segment_path: 本地已下载分片路径
        :return: True 表示拉黑（从 index.m3u8 移除，跳过推流），False 放行
        """

    def fingerprint(self) -> str:
        """配置指纹：参与内容寻址 sid，配置变化触发重新转流。默认返回 ``name``。"""
        return self.name
