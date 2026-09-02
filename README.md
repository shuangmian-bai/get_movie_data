# get_movie_data

一个基于 `FastAPI` 的影视数据源聚合项目，核心目标是把多个站点插件统一成一套检索、详情和播放地址接口。

## 项目做什么

- 插件化接入多个影视数据源
- 统一输出搜索结果、详情信息、分集列表和播放地址
- 提供 `FastAPI` Web 接口，便于直接对外调用
- 内置文件缓存，减少重复抓取
- 提供前端静态页面加载入口

当前仓库里已经接入的示例站点包括：

- `yhdm.one`
- `cupfox7.com`
- `qqll.cc`

## 主要功能

- 关键词搜索影视资源
- 获取影视详情
- 获取指定集数的播放地址
- 批量并发搜索多个数据源
- 前端搜索分页（每页 20 条，按需翻页爬取，减少引擎压力）
- 数据源多选（前端勾选数据源，仅请求所选站点）
- 文件缓存与过期控制
- 插件自动扫描与加载
- 去广告转流（stream_factory，HLS + RTSP 双协议输出）
- 违规内容过滤（stream_factory URL 处理器：OCR 识别「澳门新葡京」等违规词，拉黑对应分片跳过推流）

## 目录导航

### 文档

- [媒体数据源模块说明](./media_source/README.md)
- [缓存模块说明](./media_source/cache.md)
- [插件开发指南](./media_source/docs/PLUGIN_DEV_GUIDE.md)
- [Web 服务说明](./web/README.md)
- [流工厂模块说明](./stream_factory/README.md)

### 代码

- `main.py`：应用入口
- `web/`：HTTP 接口层
- `media_source/`：插件框架、模型、缓存和数据源实现
- `frontend_loader/`：前端静态资源加载中间件
- `stream_factory/`：流工厂（去广告转流，HLS + RTSP 双协议输出）
- `view/`：演示页面
- `cache/`：统一运行时缓存目录（文件缓存 / HLS 输出 / 源视频缓存）

## 缓存目录约定

所有运行时缓存统一放在项目根的 `cache/` 目录下（以 `cache` 为基础路径），不再散落在根目录：

```
cache/
├── {站点}/          # media_source 文件缓存（FileCache，按 base_url 分区，JSON）
├── streams/         # stream_factory HLS 输出 + 处理结果缓存（内容寻址 sid，去广告后 HLS 复用）
├── video_cache/     # stream_factory 源视频缓存（按 source_url 哈希，m3u8/mp4）
└── blacklist/       # stream_factory 黑名单（命中违规的 ts 源 URL，跳过推流）
```

- 各模块缓存目录均可通过环境变量覆盖：`MEDIA_SOURCE_CACHE_DIR`（media_source 文件缓存）、`STREAM_FACTORY_CACHE_ROOT`（流工厂统一缓存根），以及细分的 `STREAM_FACTORY_HLS_ROOT` / `STREAM_FACTORY_VIDEO_CACHE_ROOT`。
- **新增缓存时同样放入 `cache/` 下**，保持「所有缓存以 cache 为基础路径」这条约定。

## 环境要求

- **Python 3.8+**：本项目依赖 `Pydantic V2` / `FastAPI` / `httpx` 等库，需 Python 3.8 及以上版本。
- **FFmpeg**：去广告转流（`stream_factory`）依赖系统 `ffmpeg`，需**单独安装**（非 Python 包），如 `apt install ffmpeg` / `brew install ffmpeg`。
- **mediamtx**（可选）：RTSP 推流服务器，服务启动时自动拉起；仅用 HLS 可省略（设 `STREAM_FACTORY_RTSP_ENABLED=0`）。
- **tesseract**（可选）：OCR 违规词过滤（`stream_factory` 的 URL 处理器）依赖系统 `tesseract` 与中文语言包 `chi_sim`（`apt install tesseract-ocr tesseract-ocr-chi-sim` / `dnf install tesseract tesseract-langpack-chi_sim`）；不启用 OCR 过滤可省略。

## 快速开始

```bash
pip install -r requirements.txt
python main.py
```

启动后可访问：

- `http://127.0.0.1:8000/docs`

## 常用接口

- `GET /api/sources`
- `GET /api/search?key=关键词`（可选 `base_url` 单源 / `base_urls` 多源 / `start`+`count` 分页）
- `GET /api/info?base_url=...&link=...`
- `GET /api/play?base_url=...&link=...&episode_index=1`
- `POST /api/stream`（创建流）、`POST /api/stream/processed`（按站点去广告建流）、`GET /api/stream/{sid}/player`（内嵌播放器）

## 开发提示

- 新增站点时，优先参考 `media_source/plugins/template`
- 插件实现只负责输出原始数据
- 字段映射、默认值和统一结构由基础类完成
- 去广告转流由 `stream_factory/` 模块提供（FFmpeg 拉流裁剪 + HLS/RTSP 输出），需系统依赖 ffmpeg；mediamtx 由服务启动时自动拉起，无需手动启动；去广告规则按站点在 `main.py` 的 `STREAM_PIPELINES` 里自由组合流/帧插件

## 友情链接

- [隼目安全](https://sumsafe.org.cn/)
- [双面的小窝](https://blog.shuangmian.top/)
