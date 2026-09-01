"""前端静态资源加载引擎（ASGI 中间件）

从 ``frontend_root`` 加载前端资源；根路径 ``/`` 返回 ``index.html``；
其余路径透传给下游路由（如 ``/api/*``）。前端目录默认位于 ``web/frontend``。
"""
import os

from fastapi import Request
from fastapi.responses import FileResponse

# 项目根目录
_PROJECT_ROOT = os.path.dirname(os.path.dirname(__file__))

# 默认前端目录：位于 web 模块内部（应用层前端资源）
DEFAULT_FRONTEND_ROOT = os.path.join(_PROJECT_ROOT, "web", "frontend")

# 静态资源扩展名白名单
_STATIC_EXTS = (".html", ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico")


class FrontendStaticLoader:
    """前端静态资源加载引擎。

    作为 ASGI 中间件挂载：命中静态资源则直接返回文件，否则透传给下游 FastAPI。
    """

    def __init__(self, app, frontend_root=None):
        # 第一个参数接收 FastAPI 内部 asgi app 对象；frontend_root 可自定义前端目录
        self.app = app
        self.frontend_root = frontend_root or DEFAULT_FRONTEND_ROOT
        os.makedirs(self.frontend_root, exist_ok=True)

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)

        request = Request(scope, receive)
        path = request.url.path

        # 首页：/ 或空路径 -> index.html
        if path in ("", "/"):
            path = "/index.html"

        # 仅放行白名单扩展名，其余（如 /api/*、/docs）透传
        if not path.endswith(_STATIC_EXTS):
            return await self.app(scope, receive, send)

        target = os.path.join(self.frontend_root, path.lstrip("/"))

        # 防目录穿越：解析后仍须位于前端目录内
        root_real = os.path.realpath(self.frontend_root)
        target_real = os.path.realpath(target)
        if not (target_real == root_real or target_real.startswith(root_real + os.sep)):
            return await self.app(scope, receive, send)

        if os.path.isfile(target_real):
            resp = FileResponse(target_real)
            await resp(scope, receive, send)
            return

        await self.app(scope, receive, send)
