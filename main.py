"""应用入口 —— 编排汇总各功能模块

组装 FastAPI 应用：挂载 web 模块的 API 路由 + 去广告模块路由 + 投稿模块路由，
并由 fastapi-ui 托管 React 构建产物（项目根 ``view/``）。
各功能模块（media_source / web / ad_filter / contribute）互不直接调用，统一在此编排。

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
from ad_filter.detector.full_ad import FullAdDetector
from ad_filter.detector.ocr import OcrDetector
from ad_filter.detector.top_banner import TopBannerDetector
from ad_filter.remover.delogo import DelogoRemover
from ad_filter.remover.top_banner import TopBannerRemover
from contribute import api_router as contribute_router
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
# 优质资源库（1080zyk6）：广告是顶部弹幕/跑马灯/静态赞助条 → 顶部条带检测 + 条带清除。
AD_FILTER_PIPELINES: Dict[str, Tuple[List, object]] = {
    "https://www.cupfox7.com": ([OcrDetector()], DelogoRemover()),
    "https://www.qqll.cc": ([OcrDetector()], DelogoRemover()),
    # 1080zyk6：整屏广告（帧帧广告）先判 full 丢弃，顶部条幅再判 watermark 去水印
    "https://1080zyk6.com": (
        [FullAdDetector(), TopBannerDetector()],
        TopBannerRemover(),
    ),
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

# 挂载 API 路由（web 数据源模块 + 去广告模块 + 投稿模块）
app.include_router(api_router)
app.include_router(ad_api.api_router)
app.include_router(contribute_router)

# 前端静态资源（项目根 view/，React 构建产物由 fastapi-ui 托管）
# view_dir 按模块文件定位，不受启动时工作目录影响；spa_fallback 供前端路由深链刷新使用。
# 注意：/ad_filter/ 与 /api/ 必须排除在 SPA 回退之外——“.m3u8/.ts” 不在静态扩展名白名单里，
# 不排除会被当成页面路由回退成 index.html（接口链路静默失效）。
mount_webui(
    app,
    view_dir_of(__file__, "view"),
    spa_fallback=True,
    spa_exclude=("/api/", "/docs", "/redoc", "/openapi.json", "/ad_filter/"),
)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="影视数据源服务")
    parser.add_argument(
        "--proxy",
        default="",
        help="代理地址（http/https/socks5），如 http://127.0.0.1:7890；留空则自动检测系统代理",
    )
    parser.add_argument("--host", default="127.0.0.1", help="监听地址")
    parser.add_argument("--port", type=int, default=9999, help="监听端口")
    parser.add_argument(
        "--reload",
        action="store_true",
        help="开发模式：文件变更自动重载（生产不要开，重启窗口会导致反代 502）",
    )
    args = parser.parse_args()

    if args.proxy:
        # 把显式代理下发到各模块（环境变量在 uvicorn reload 子进程中同样生效）
        os.environ["MEDIA_SOURCE_HTTP_PROXY"] = args.proxy
        os.environ["AD_FILTER_HTTP_PROXY"] = args.proxy

    # 热重载仅开发用：reload 会在文件变化/worker 退出时重启进程，窗口内端口无监听，
    # nginx 反代会返回 502；生产默认关闭，需要时显式加 --reload。
    uvicorn.run("main:app", host=args.host, port=args.port, reload=args.reload)
