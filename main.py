"""应用入口 —— 编排汇总各功能模块

组装 FastAPI 应用：挂载 web 模块的 API 路由 + 静态文件中间件。
各功能模块（media_source / cache / web）互不直接调用，统一在此编排。
"""
import uvicorn
from fastapi import FastAPI

from frontend_loader import FrontendStaticLoader
from web import api_router

app = FastAPI(title="影视数据源服务", docs_url="/docs")

# 挂载 API 路由（web 模块）
app.include_router(api_router)

# 前端静态资源（web/frontend/ 目录，由 frontend_loader 引擎加载）
app.add_middleware(FrontendStaticLoader)


if __name__ == "__main__":
    uvicorn.run("main:app", reload=True)
