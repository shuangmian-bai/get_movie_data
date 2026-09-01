"""Web 服务模块 —— 基于 FastAPI 的 REST API 路由

通过 media_source 的公开接口（plugin_manager / file_cache / 模型）提供服务，
对外暴露影视数据源查询能力。业务在应用层（main.py）挂载本模块的 ``api_router``。
"""
from web.api import api_router

__all__ = ["api_router"]
