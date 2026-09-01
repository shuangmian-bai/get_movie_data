import os
from fastapi import Request
from fastapi.responses import FileResponse

# 静态文件目录，view文件夹，相对于项目根
VIEW_FOLDER = os.path.join(os.path.dirname(os.path.dirname(__file__)), "view")


class FrontendStaticLoader:
    def __init__(self, app):
        # ✅第一个参数接收FastAPI内部asgi app对象
        self.app = app
        self.frontend_root = VIEW_FOLDER
        # 确保view目录存在
        os.makedirs(self.frontend_root, exist_ok=True)

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)

        request = Request(scope, receive)
        path = request.url.path

        ext_list = (".html", ".css", ".js")
        if path.endswith(ext_list):
            file_path = os.path.join(self.frontend_root, path.lstrip("/"))
            if os.path.isfile(file_path):
                resp = FileResponse(file_path)
                await resp(scope, receive, send)
                return

        await self.app(scope, receive, send)
