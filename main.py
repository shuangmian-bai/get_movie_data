"""应用入口 —— 编排汇总各功能模块

组装 FastAPI 应用：挂载 web 模块的 API 路由 + 去广告模块路由 + 前端中间件。
各功能模块（media_source / web / ad_filter）互不直接调用，统一在此编排。

去广告规则是系统内部知识：此处按 ``base_url`` 把站点映射到「检测器 + 去水印器」组合，
调用方只传 ``base_url`` 与源，无需关心检测/去水印细节。
"""
import os
import sys
from contextlib import asynccontextmanager
from typing import Dict, List, Tuple

import uvicorn
from fastapi import FastAPI

from ad_filter import api as ad_api
from ad_filter import close_proxy
from ad_filter.detector.ocr import OcrDetector
from ad_filter.remover.delogo import DelogoRemover
from web import api_router

# ---- 前端加载引擎（fastapi-ui，git submodule 引入）----
# 包声明 requires-python>=3.9，而本项目运行环境为 Python 3.8，pip 安装会被元数据检查拒绝；
# 源码实测在 3.8 可正常导入（类型注解全部惰性化），故按子模块方式直引 src 目录，不走安装。
_VENDOR_UI_SRC = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "vendor", "fastapi-ui", "src"
)
if _VENDOR_UI_SRC not in sys.path:
    sys.path.insert(0, _VENDOR_UI_SRC)

from fastapi_ui import mount_webui, view_dir_of  # noqa: E402  # 依赖上面的 sys.path 注入

# ---- 去广告处理自由组合（base_url → 检测器列表 + 去水印器）----
# 不同资源广告形式/内容不同，站点 → 去广告插件组合在此编排；未匹配走默认（OCR + delogo）。
AD_FILTER_PIPELINES: Dict[str, Tuple[List, object]] = {
    "https://www.cupfox7.com": ([OcrDetector()], DelogoRemover()),
    "https://www.qqll.cc": ([OcrDetector()], DelogoRemover()),
}
AD_FILTER_DEFAULT: Tuple[List, object] = ([OcrDetector()], DelogoRemover())


def get_ad_pipeline(base_url: str) -> Tuple[List, object]:
    """按 ``base_url`` 取该站点的检测器 + 去水印器组合。"""
    return AD_FILTER_PIPELINES.get(base_url, AD_FILTER_DEFAULT)


# 注入编排函数，供 ad_filter.api 按站点解析插件组合
ad_api.set_pipeline_getter(get_ad_pipeline)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """服务生命周期：退出时关闭去广告模块的长驻连接池。"""
    yield
    await close_proxy()


app = FastAPI(title="影视数据源服务", docs_url="/docs", lifespan=lifespan)

# 挂载 API 路由（web 数据源模块 + 去广告模块）
app.include_router(api_router)
app.include_router(ad_api.api_router)

# 前端静态资源（web/frontend/ 目录，由 fastapi-ui 前端加载引擎托管）
# view_dir 按模块文件定位，不受启动时工作目录影响；spa_fallback 供前端路由深链刷新使用。
mount_webui(
    app,
    view_dir_of(__file__, "web", "frontend"),
    spa_fallback=True,
)


if __name__ == "__main__":
    import argparse
    import os

    parser = argparse.ArgumentParser(description="影视数据源服务")
    parser.add_argument(
        "--proxy",
        default="",
        help="代理地址（http/https/socks5），如 http://127.0.0.1:7890；留空则自动检测系统代理",
    )
    parser.add_argument("--host", default="127.0.0.1", help="监听地址")
    parser.add_argument("--port", type=int, default=9999, help="监听端口")
    args = parser.parse_args()

    if args.proxy:
        # 把显式代理下发到各模块（环境变量在 uvicorn reload 子进程中同样生效）
        os.environ["MEDIA_SOURCE_HTTP_PROXY"] = args.proxy
        os.environ["AD_FILTER_HTTP_PROXY"] = args.proxy

    uvicorn.run("main:app", host=args.host, port=args.port, reload=True)
