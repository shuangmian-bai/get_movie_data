"""contribute —— 用户投稿模块

收集两类投稿：数据源（新站点接入线索）与广告信息（站点上广告的类型/位置/样本），
存储为 JSONL（``cache/contributions/``），人工审核后转成插件配置或新站点插件。

对外只导出路由与前缀常量，存储细节不外泄。
"""
from contribute.api import api_router

__all__ = ["api_router"]
