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

一个 FastAPI Web 应用，对外暴露**插件化多源影视数据源模块**（`media_source/`）。每个已接入站点（茶杯狐 / cupfox7.com、奇奇影视 / qqll.cc）都是独立 Python 包，实现三段链路：关键词搜索 → 详情获取 → 分集播放地址（m3u8/mp4）。

- `main.py` — 应用入口（应用层），编排各模块：挂载 `web` 路由 + 去广告路由 + 前端加载中间件。
- `web/` — Web 服务模块（属应用层：REST API sources/search/info/play + `frontend/` 前端资源）。
- `media_source/` — 可复用的插件框架 + 站点插件 + 文件缓存。
- `ad_filter/` — 插件化去广告处理模块（逐分片抽帧检测广告：全广告丢弃 / 水印广告去水印 / 正常分片代理，产出处理后 m3u8）。
- `frontend_loader/` — 前端静态资源加载引擎（默认从 `web/frontend/` 提供文件）。
- `requirements.txt` — 运行依赖（`media_source` 数据源 + FastAPI Web 服务 + PyAV/opencv/rapidocr）。

## 常用命令

```bash
# 安装运行依赖（含视频处理 / OCR，全 pip 安装，无需系统 ffmpeg / tesseract）
pip install -r requirements.txt

# 启动 Web 服务（uvicorn，热重载；前端由 frontend_loader 从 web/frontend/ 提供）
python main.py

# 运行全部测试
python -m unittest discover -s media_source/tests -v

# 运行单个测试文件 / 测试类
python -m unittest media_source.tests.test_mapping -v
python -m unittest media_source.tests.test_mapping.TestMapData -v

# 运行端到端示例（搜索 -> 详情 -> 播放）
python -m media_source.examples.demo_full_flow
```

Python 为 3.13.13（pyenv）。`.venv/` 已存在但被 gitignore。

## 架构

### 插件框架（`media_source/`）

- **`MediaSourcePlugin`**（`base.py`）为抽象基类。插件需声明 4 个类属性（`base_url`、`source_name`、`source_desc`，以及映射模板 `search_mapping`/`info_mapping`/`play_mapping`/`episode_mapping`），并实现 3 个抽象异步方法 `_raw_search(key)` / `_raw_get_info(search_item)` / `_raw_get_play_url(media_info, episode_index)`。支持分页搜索的站点可额外覆盖可选钩子 `_raw_search_page(key, page)`（page 从 1 开始），基类公开方法 `search_page(key, start, count, page_concurrency)` 据此按「偏移 + 条数」只抓取覆盖区间的分页——先抓第 1 页探明每页条数，再并发抓取所需页码后切片返回；不支持分页的站点降级为抓取全部后切片。
  - **契约**：`_raw_*` 只返回原始 `dict`/`list[dict]`，禁止返回 Pydantic 模型、禁止字段过滤。基类公开方法（`search`/`get_info`/`get_play_url`）把原始数据交给映射引擎，返回标准模型，并自动注入 `base_url`、映射分集列表。
- **字段映射引擎**（`mapping.py`）：模板语法 `"name": "{title} | default:'未知影片'"`。白名单过滤（丢弃所有模板未声明的原始字段）、解析 `{占位符}`、回退到 `default:` 值（经 `ast.literal_eval` 解析），否则交给 Pydantic 模型默认值兜底。
- **`PluginManager`**（`plugin_manager.py`）在导入时通过 `pkgutil` 扫描 `media_source.plugins/*`，跳过 `template` 与 `_deprecated` 目录。全局单例 `plugin_manager` 在模块导入时创建并扫描（`from media_source import plugin_manager`）。关键方法：`get_supported_sources()`、`get_plugin_instance(base_url)`、异步 `batch_search(key, base_urls=[], max_concurrency=None, start=0, count=None)`（信号量限流 + 单插件异常隔离；多源时 `start`/`count` 为**全局分页**——各源先抓前 `start+count` 条按 base_url 字典序合并后整体切片，每页是整合总数而非每源各 `count` 条）。
- **数据模型**（Pydantic V2，`models.py`，`coerce_numbers_to_str=True`）：`SourceMeta`、`SearchItem`（name/link/type/year/cover/desc + 注入的 `base_url`）、`EpisodeItem`（name/index/link）、`MediaInfo`（…+ `episodes`）、`PlaySource`（url/type/headers）。
- **横切能力**：`config.py`（环境变量可覆盖的 `MAX_PLUGIN_CONCURRENCY`、`HTTP_TIMEOUT`、`HTTP_USER_AGENT`、自动重联 `HTTP_RETRIES`/`HTTP_RETRY_BACKOFF`、翻页并发 `PAGE_CONCURRENCY`、缓存目录 `CACHE_DIR` 与三级 TTL）、`cache.py`（`FileCache` 文件缓存 + 全局单例 `file_cache`，按 base_url 分区、TTL 过期、并发穿透防护，只存 JSON 可序列化数据）、`exceptions.py`（全部继承 `MediaSourceError`）、`utils/http.py`（`AsyncHttpClient` + `fetch_text`/`fetch_json` 便捷函数，内置自动重联——网络错误/5xx 自动重试，支持 `trust_env=False` 直连绕过代理，网络异常转为 `SourceRequestError`）、`utils/helpers.py`（`normalize_url`、`clean_text`、`strip_html`、`clean_dict`）。

### 新增站点插件

插件是 `media_source/plugins/<site>/` 包，固定 4 个文件 —— `__init__.py`（导出插件类）、`constants.py`（站点 URL/请求头）、`parser.py`（纯异步函数，返回原始字典）、`main.py`（`MediaSourcePlugin` 子类 + 映射模板 + `_raw_*` 方法）。`plugins/cupfox/`、`plugins/qqll/` 是真实参考实现；`plugins/template/` 为同构骨架、`plugins/_deprecated/` 收纳过期数据源（二者扫描时均被跳过）。完整指南：`media_source/docs/PLUGIN_DEV_GUIDE.md`。

### 去广告处理（`ad_filter/`）

独立模块（不 import `media_source`/其它模块，仅依赖 Python 库 av / opencv / rapidocr / httpx）。核心链路：传入 m3u8 → 解析（相对/绝对/协议相对 URI 统一 `urljoin`）→ 逐分片下载到临时目录 → 检测器抽帧分类 → 重写 m3u8 → 返回处理结果。

- 插件抽象：`Detector`（检测器，`detect() -> DetectionResult`，判定 `none`/`full`/`watermark` 并给出水印区域）与 `Remover`（去水印器，`remove(segment_path, boxes, out_path)`）。内置 `OcrDetector`（opencv 抽帧 + rapidocr 识别 + 分类 + 水印坐标）与 `DelogoRemover`（opencv inpaint 去水印 + PyAV 写回 .ts）。插件不绑定站点，站点 → 组合在应用层 `main.py` 的 `AD_FILTER_PIPELINES`（`base_url → (检测器列表, 去水印器)`）编排，经 `ad_filter.api.set_pipeline_getter(...)` 注入。
- 分片动作：`full`（全广告）→ 丢弃（不输出该分片块）；`watermark`（水印广告）→ 去水印后落盘 `{OUTPUT_ROOT}/{sid}/`，引用 `file/{name}`；`none`（正常）→ 不落盘，引用 `proxy/{name}`（`proxy.py` 长驻 httpx 连接池带防盗链头转发上游）。**本地只保存修改了画面的分片**。
- 编排（`engine.py`）：`sid = md5(m3u8_url + headers + 检测器指纹)[:16]` 内容寻址幂等复用；写 `index.m3u8` + `meta.json`（分片名→上游 URL 映射，供代理查询）。REST：`POST /api/ad_filter/process`、`GET /ad_filter/{sid}/index.m3u8`、`GET /ad_filter/{sid}/file/{name}`、`GET /ad_filter/{sid}/proxy/{name}`。
- 失败容错：检测器/OCR 异常一律放行（`none`），分片下载失败降级代理，绝不阻断播放链路。rapidocr 缺失或识别失败时一律放行（仅失去检测能力）；加密分片（AES-128）抽帧失败自动放行（走代理）。前端播放页提供「原生直连 / 去广告播放」两种方式。
- 依赖：视频处理用 PyAV（`av`，自带 ffmpeg 库）+ opencv（`cv2`），OCR 用 rapidocr，**均 pip 安装，不依赖系统 ffmpeg / tesseract / mediamtx**。

## 已知状态 / 注意事项

- **测试套件当前是坏的**：`test_plugin_manager.py`、`test_plugins.py`、`test_batch_search.py` 引用了已被删除的示例插件 `site_a`/`site_b`（`https://www.site-a.example.com`、`https://www.site-b.example.com`）。现在仅剩 `cupfox`、`qqll` 两个真实插件（`yhdm` 已移入 `plugins/_deprecated/` 废弃区），这些文件在更新到当前插件集之前会失败/报错。
- `cupfox` 插件发起**真实网络请求**到 `https://www.cupfox7.com/`（苹果CMS v10，服务端渲染 HTML；播放地址从播放页内嵌 `var player_xxxx` 的 `url` 字段提取 m3u8）。**需直连（`trust_env=False`）绕过代理**，否则代理对 HTTP/2 处理失败；搜索结果有多页时内部用 asyncio 并发（信号量限流）抓取所有分页。涉及它的单元测试需要网络。
- `plugin_manager.scan_plugins()` 在导入时执行，因此 `get_supported_sources()` 反映的是 `plugins/` 目录下当前实际存在的包，而非静态清单。
- `ad_filter` 依赖 Python 库 `av`（PyAV，内置 ffmpeg 库）、`opencv-python-headless`、`rapidocr_onnxruntime`（中文 OCR），全部 pip 安装、无需系统二进制；rapidocr 首次加载会初始化模型（较慢），识别为「尽力而为」（失败放行）。配置前缀 `AD_FILTER_*`（详见 `ad_filter/README.md`）。
