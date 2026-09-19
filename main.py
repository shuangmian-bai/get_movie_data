"""应用入口 —— 编排汇总各功能模块

组装 FastAPI 应用：挂载 web 模块的 API 路由 + 去广告模块路由 + 前端中间件。
各功能模块（media_source / web / ad_filter）互不直接调用，统一在此编排。

去广告规则是系统内部知识：此处按 ``base_url`` 把站点映射到「检测器 + 去水印器」组合，
调用方只传 ``base_url`` 与源，无需关心检测/去水印细节。
"""
from contextlib import asynccontextmanager
from typing import Dict, List, Tuple

import uvicorn
from fastapi import FastAPI

from ad_filter import api as ad_api
from ad_filter import close_proxy
from ad_filter.detector.ocr import OcrDetector
from ad_filter.remover.delogo import DelogoRemover
from frontend_loader import FrontendStaticLoader
from web import api_router

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

# 前端静态资源（web/frontend/ 目录，由 frontend_loader 引擎加载）
app.add_middleware(FrontendStaticLoader)


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
    parser.add_argument("--port", type=int, default=8000, help="监听端口")
    args = parser.parse_args()

    if args.proxy:
        # 把显式代理下发到各模块（环境变量在 uvicorn reload 子进程中同样生效）
        os.environ["MEDIA_SOURCE_HTTP_PROXY"] = args.proxy
        os.environ["AD_FILTER_HTTP_PROXY"] = args.proxy

    uvicorn.run("main:app", host=args.host, port=args.port, reload=True)
