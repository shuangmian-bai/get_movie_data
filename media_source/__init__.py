"""媒体数据源插件化模块 —— 对外统一导出入口

插件化、可扩展、多源聚合影视数据源解析模块。
"""
from media_source import config
from media_source.base import MediaSourcePlugin
from media_source.cache import FileCache, file_cache
from media_source.exceptions import (
    MediaSourceError,
    PlayUrlNotFoundError,
    PluginNotFoundError,
    SourceParseError,
    SourceRequestError,
)
from media_source.mapping import map_data, map_data_list, parse_template
from media_source.models import (
    EpisodeItem,
    MediaInfo,
    PlaySource,
    SearchItem,
    SourceMeta,
)
from media_source.plugin_manager import PluginManager, plugin_manager

__all__ = [
    "config",
    "MediaSourcePlugin",
    "FileCache",
    "file_cache",
    "MediaSourceError",
    "PluginNotFoundError",
    "SourceRequestError",
    "SourceParseError",
    "PlayUrlNotFoundError",
    "map_data",
    "map_data_list",
    "parse_template",
    "SourceMeta",
    "SearchItem",
    "EpisodeItem",
    "MediaInfo",
    "PlaySource",
    "PluginManager",
    "plugin_manager",
]
