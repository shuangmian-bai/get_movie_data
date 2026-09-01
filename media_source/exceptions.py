"""全局自定义异常体系

统一异常层级，便于业务层精准捕获与处理各类错误。
所有异常均继承自 ``MediaSourceError``，可一次性捕获模块内全部异常。
"""


class MediaSourceError(Exception):
    """媒体数据源模块基础异常"""


class PluginNotFoundError(MediaSourceError):
    """无匹配站点插件"""


class SourceRequestError(MediaSourceError):
    """站点网络请求失败"""


class SourceParseError(MediaSourceError):
    """数据解析失败、字段缺失"""


class PlayUrlNotFoundError(MediaSourceError):
    """无有效播放地址"""
