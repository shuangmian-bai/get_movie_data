# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 语言约定（必读）

本项目后续的**文档、代码注释、可见的思考（推理过程）以及与用户的交流**均使用**中文**撰写。
新增或修改代码时，注释一律用中文；撰写/更新文档（含本文件）也用中文。

## 开发规范（必读）

1. **文件边界**：所有代码与 Markdown 文档一律写入本项目目录内，不写到项目之外。
2. **虚拟环境**：统一使用 `.venv`（Linux）/ `.venv_win`（Windows）开发，不污染系统 Python。
3. **文档同步**：功能或架构变化后，及时同步更新相关 `.md` 文档。
4. **低耦合 + 应用层汇总**：功能要可复用；各功能模块之间不互相直接调用，统一在应用层（`main.py`）编排汇总。
5. **模块化 + 模块级文档**：每个模块目录附带一个 `.md`，描述该模块的用途与用法；需要了解模块时优先读其 md，而不是通读源码，以减少上下文压力。
6. **禁止 git 操作**：Claude 无权也不应执行任何 git 操作（`commit`/`add`/`restore`/`branch` 等），版本管理一律由用户自行处理，不代劳。

## 项目概览

一个 FastAPI Web 应用，对外暴露**插件化多源影视数据源模块**（`media_source/`）。每个已接入站点（樱花动漫 / yhdm.one、茶杯狐 / cupfox7.com、奇奇影视 / qqll.cc）都是独立 Python 包，实现三段链路：关键词搜索 → 详情获取 → 分集播放地址（m3u8/mp4）。

- `main.py` — 应用入口（应用层），编排各模块：挂载 `web` 路由 + 流工厂路由 + HLS 静态目录 + 前端加载中间件。
- `web/` — Web 服务模块（属应用层：REST API sources/search/info/play + `frontend/` 前端资源）。
- `media_source/` — 可复用的插件框架 + 站点插件 + 文件缓存。
- `frontend_loader/` — 前端静态资源加载引擎（默认从 `web/frontend/` 提供文件）。
- `stream_factory/` — 流工厂模块（去广告转流：FFmpeg 拉流裁剪 + HLS/RTSP 双协议输出 + 内嵌播放器）。
- `requirements.txt` — 运行依赖（`media_source` 数据源 + FastAPI Web 服务）。

## 常用命令

```bash
# 安装 media_source 运行依赖
pip install -r requirements.txt

# 启动 Web 服务（uvicorn，热重载；前端由 frontend_loader 从 web/frontend/ 提供）
python main.py

# RTSP 服务器 mediamtx 由服务启动时自动拉起（无需手动启动；详见 stream_factory/README.md）

# 运行全部测试
python -m unittest discover -s media_source/tests -v

# 运行单个测试文件 / 测试类
python -m unittest media_source.tests.test_mapping -v
python -m unittest media_source.tests.test_mapping.TestMapData -v

# 运行端到端示例（搜索 -> 详情 -> 播放）
python -m media_source.examples.demo_full_flow
```

Python 为 3.13.13（pyenv）。`.venv/` 已存在但被 gitignore；`python3` 解析到 pyenv 的 3.13 解释器，所需依赖均已安装（`fastapi`/`uvicorn` 已纳入 `requirements.txt`）。

## 架构

### 插件框架（`media_source/`）

- **`MediaSourcePlugin`**（`base.py`）为抽象基类。插件需声明 4 个类属性（`base_url`、`source_name`、`source_desc`，以及映射模板 `search_mapping`/`info_mapping`/`play_mapping`/`episode_mapping`），并实现 3 个抽象异步方法 `_raw_search(key)` / `_raw_get_info(search_item)` / `_raw_get_play_url(media_info, episode_index)`。支持分页搜索的站点可额外覆盖可选钩子 `_raw_search_page(key, page)`（page 从 1 开始），基类公开方法 `search_page(key, start, count, page_concurrency)` 据此按「偏移 + 条数」只抓取覆盖区间的分页——先抓第 1 页探明每页条数，再并发抓取所需页码后切片返回；不支持分页的站点降级为抓取全部后切片。
  - **契约**：`_raw_*` 只返回原始 `dict`/`list[dict]`，禁止返回 Pydantic 模型、禁止字段过滤。基类公开方法（`search`/`get_info`/`get_play_url`）把原始数据交给映射引擎，返回标准模型，并自动注入 `base_url`、映射分集列表。
- **字段映射引擎**（`mapping.py`）：模板语法 `"name": "{title} | default:'未知影片'"`。白名单过滤（丢弃所有模板未声明的原始字段）、解析 `{占位符}`、回退到 `default:` 值（经 `ast.literal_eval` 解析），否则交给 Pydantic 模型默认值兜底。
- **`PluginManager`**（`plugin_manager.py`）在导入时通过 `pkgutil` 扫描 `media_source.plugins/*`，跳过 `template` 目录。全局单例 `plugin_manager` 在模块导入时创建并扫描（`from media_source import plugin_manager`）。关键方法：`get_supported_sources()`、`get_plugin_instance(base_url)`、异步 `batch_search(key, base_urls=[], max_concurrency=None)`（信号量限流 + 单插件异常隔离）。
- **数据模型**（Pydantic V2，`models.py`，`coerce_numbers_to_str=True`）：`SourceMeta`、`SearchItem`（name/link/type/year/cover/desc + 注入的 `base_url`）、`EpisodeItem`（name/index/link）、`MediaInfo`（…+ `episodes`）、`PlaySource`（url/type/headers）。
- **横切能力**：`config.py`（环境变量可覆盖的 `MAX_PLUGIN_CONCURRENCY`、`HTTP_TIMEOUT`、`HTTP_USER_AGENT`、自动重联 `HTTP_RETRIES`/`HTTP_RETRY_BACKOFF`、翻页并发 `PAGE_CONCURRENCY`、缓存目录 `CACHE_DIR` 与三级 TTL）、`cache.py`（`FileCache` 文件缓存 + 全局单例 `file_cache`，按 base_url 分区、TTL 过期、并发穿透防护，只存 JSON 可序列化数据）、`exceptions.py`（全部继承 `MediaSourceError`）、`utils/http.py`（`AsyncHttpClient` + `fetch_text`/`fetch_json` 便捷函数，内置自动重联——网络错误/5xx 自动重试，支持 `trust_env=False` 直连绕过代理，网络异常转为 `SourceRequestError`）、`utils/helpers.py`（`normalize_url`、`clean_text`、`strip_html`、`clean_dict`）。

### 新增站点插件

插件是 `media_source/plugins/<site>/` 包，固定 4 个文件 —— `__init__.py`（导出插件类）、`constants.py`（站点 URL/请求头）、`parser.py`（纯异步函数，返回原始字典）、`main.py`（`MediaSourcePlugin` 子类 + 映射模板 + `_raw_*` 方法）。`plugins/yhdm/`、`plugins/cupfox/` 是真实参考实现；`plugins/template/` 为同构骨架（扫描时被跳过）。完整指南：`media_source/docs/PLUGIN_DEV_GUIDE.md`。

### 流工厂（`stream_factory/`）

- 输入 `media_source` 拿到的 m3u8/mp4 播放地址，用 **FFmpeg 子进程**拉流并做**去广告裁剪**（无损 `-c copy` 快路径 / `select` 滤镜重编码路径）。
- 规则模型（`rules.py`）：`StreamSource`（流源 url/type/headers）、`StreamRequest`（source_url/headers/trims/filters/blanks）、`TrimSegment`（删除区间）、`BlankSegment`（周期性空白段）、`FilterRule`（逐帧滤镜，预留）。
- 插件抽象（`frame_plugins/` + `stream_plugins/` 两个子包，每个具体插件一个文件即「孙子模块」）：`FramePlugin`（帧插件，产出 FilterRule）/ `StreamPlugin`（流插件，产出 TrimSegment、可选 BlankSegment 并合成 StreamRequest），**不绑定 base_url**；帧子包内置水印/文字帧插件，流子包内置各站点流插件（示例裁剪）+ 空白插入案例（`BlankInsertStreamPlugin`）+ 组合流插件（`CompositeStreamPlugin`，聚合多个流插件的 trims/blanks）。帧/流两子包运行时解耦（`stream_plugins/base.py` 仅在 `TYPE_CHECKING` 块引用 `FramePlugin`）。
- 编排：`pipeline.py`（规则→ffmpeg 命令）/ `session.py` + `factory.py`（会话子进程生命周期）/ `api.py`（REST + 播放器页）。
- 源视频缓存（`video_cache.py`）：ffmpeg 拉流前先把上游源缓存到本地（保留 HLS 分片结构 + AES-128 key + fMP4 init 分片 / mp4 直链），`factory.create_stream` 自动 `ensure_source` 命中后改读本地；用**长驻 httpx 连接池**复用 TCP、per-key 锁**并发去重**（同源只下载一次）、TTL 过期惰性重下；AES-128 加密分片也一并缓存（key/分片本地化 + URI 重写，ffmpeg 加 `-allowed_extensions ALL` 离线解密）；仅 Master 嵌套过深 / 下载失败**降级直连**。缓存根 `{项目根}/cache/video_cache/`（`VIDEO_CACHE_ROOT`/`VIDEO_CACHE_TTL`/`VIDEO_CACHE_CONCURRENCY` 可配）。
- 处理结果缓存（`process_cache.py`）：源视频缓存之上的第二层缓存，按「源 url + 帧滤镜（filters）+ 流裁剪/空白（trims/blanks）」内容寻址出 `sid`（`md5(规范化 StreamRequest)[:16]`），`factory.create_stream` 命中后直接复用去广告后的 HLS 目录、跳过 ffmpeg 重编码；转流正常结束后写 `meta.json` 登记，TTL 过期惰性重转（`PROCESS_CACHE_TTL`，默认 7 天）；命中时仅提供 HLS（RTSP 实时推流无法缓存）；`stop` 保留完整缓存、只清理转流中的半成品。缓存目录即 `cache/streams/{sid}/`。
- 站点组合在应用层 `main.py` 的 `STREAM_PIPELINES`（`base_url → (流插件, [帧插件])`）自由编排，经 `POST /api/stream/processed`（内部处理入口）按站点触发去广告流；`GET /api/play` 仍无状态返回原始 m3u8。三个站点（cupfox/yhdm/qqll）均已接入「双面酱」文字水印（`ShuangmianTextFramePlugin`）与空白插入案例（`BlankInsertStreamPlugin`，经 `CompositeStreamPlugin` 叠加）。
- 双输出：HLS 写本地磁盘（`/streams/{sid}/index.m3u8`，Web 播放）+ RTSP 推流到 mediamtx（原生播放）。HLS 与 RTSP 各用独立 ffmpeg 子进程：HLS 是主输出，RTSP 是「尽力而为」的附加输出，后者失败不影响前者。mediamtx 由 `main.py` 生命周期自动拉起（`MEDIAMTX_AUTOSTART`），不可用时降级为纯 HLS。

## 已知状态 / 注意事项

- **测试套件当前是坏的**：`test_plugin_manager.py`、`test_plugins.py`、`test_batch_search.py` 引用了已被删除的示例插件 `site_a`/`site_b`（`https://www.site-a.example.com`、`https://www.site-b.example.com`）。现在仅剩 `yhdm`、`cupfox`、`qqll` 三个真实插件，这些文件在更新到当前插件集之前会失败/报错。
- `yhdm` 插件发起**真实网络请求**到 `https://yhdm.one/`（搜索/详情为服务端渲染 HTML；播放地址来自 JSON 接口 `/_get_plays/<vod_id>/<ep_name>`）。无离线/mock 模式，涉及它的单元测试需要网络。
- `cupfox` 插件发起**真实网络请求**到 `https://www.cupfox7.com/`（苹果CMS v10，服务端渲染 HTML；播放地址从播放页内嵌 `var player_xxxx` 的 `url` 字段提取 m3u8）。**需直连（`trust_env=False`）绕过代理**，否则代理对 HTTP/2 处理失败；搜索结果有多页时内部用 asyncio 并发（信号量限流）抓取所有分页。涉及它的单元测试需要网络。
- `plugin_manager.scan_plugins()` 在导入时执行，因此 `get_supported_sources()` 反映的是 `plugins/` 目录下当前实际存在的包，而非静态清单。
- `stream_factory` 依赖系统 `ffmpeg`（转流）与 `mediamtx`（RTSP 服务器，本机位于 `/mnt/4t/linux/huanjing/mediamtx/1.8.1/mediamtx`）。mediamtx 由服务启动时**自动拉起**（`STREAM_FACTORY_MEDIAMTX_AUTOSTART=1`，退出时自动停止本模块拉起的实例）；不可用时 RTSP 降级为纯 HLS（HLS 不受影响）。仅 HLS 可设 `STREAM_FACTORY_RTSP_ENABLED=0` 关闭。
