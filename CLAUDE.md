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

一个 FastAPI Web 应用，对外暴露**插件化多源影视数据源模块**（`media_source/`）。每个已接入站点（目前仅樱花动漫 / yhdm.one）都是独立 Python 包，实现三段链路：关键词搜索 → 详情获取 → 分集播放地址（m3u8/mp4）。

- `main.py` — FastAPI 入口（Web 服务，经 `frontend_loader/` 提供静态文件）。
- `media_source/` — 可复用的插件框架 + 站点插件。
- `frontend_loader/` + `view/` — 中间件，从 `view/` 提供 `.html/.css/.js`。
- `requirements.txt` — 仅固定 `media_source` 运行依赖。

## 常用命令

```bash
# 安装 media_source 运行依赖
pip install -r requirements.txt

# 启动 Web 服务（uvicorn，热重载；从 view/ 提供静态文件）
python main.py

# 运行全部测试
python -m unittest discover -s media_source/tests -v

# 运行单个测试文件 / 测试类
python -m unittest media_source.tests.test_mapping -v
python -m unittest media_source.tests.test_mapping.TestMapData -v

# 运行端到端示例（搜索 -> 详情 -> 播放）
python -m media_source.examples.demo_full_flow
```

Python 为 3.13.13（pyenv）。`.venv/` 已存在但被 gitignore；`python3` 解析到 pyenv 的 3.13 解释器，所需依赖均已安装。注意 `requirements.txt` 不含 `fastapi`/`uvicorn` —— `main.py` 需要它们，当前环境已装但未固定版本。

## 架构

### 插件框架（`media_source/`）

- **`MediaSourcePlugin`**（`base.py`）为抽象基类。插件需声明 4 个类属性（`base_url`、`source_name`、`source_desc`，以及映射模板 `search_mapping`/`info_mapping`/`play_mapping`/`episode_mapping`），并实现 3 个抽象异步方法 `_raw_search(key)` / `_raw_get_info(search_item)` / `_raw_get_play_url(media_info, episode_index)`。
  - **契约**：`_raw_*` 只返回原始 `dict`/`list[dict]`，禁止返回 Pydantic 模型、禁止字段过滤。基类公开方法（`search`/`get_info`/`get_play_url`）把原始数据交给映射引擎，返回标准模型，并自动注入 `base_url`、映射分集列表。
- **字段映射引擎**（`mapping.py`）：模板语法 `"name": "{title} | default:'未知影片'"`。白名单过滤（丢弃所有模板未声明的原始字段）、解析 `{占位符}`、回退到 `default:` 值（经 `ast.literal_eval` 解析），否则交给 Pydantic 模型默认值兜底。
- **`PluginManager`**（`plugin_manager.py`）在导入时通过 `pkgutil` 扫描 `media_source.plugins/*`，跳过 `template` 目录。全局单例 `plugin_manager` 在模块导入时创建并扫描（`from media_source import plugin_manager`）。关键方法：`get_supported_sources()`、`get_plugin_instance(base_url)`、异步 `batch_search(key, base_urls=[], max_concurrency=None)`（信号量限流 + 单插件异常隔离）。
- **数据模型**（Pydantic V2，`models.py`，`coerce_numbers_to_str=True`）：`SourceMeta`、`SearchItem`（name/link/type/year/cover/desc + 注入的 `base_url`）、`EpisodeItem`（name/index/link）、`MediaInfo`（…+ `episodes`）、`PlaySource`（url/type/headers）。
- **横切能力**：`config.py`（环境变量可覆盖的 `MAX_PLUGIN_CONCURRENCY`、`HTTP_TIMEOUT`、`HTTP_USER_AGENT`）、`exceptions.py`（全部继承 `MediaSourceError`）、`utils/http.py`（`AsyncHttpClient` + `fetch_text`/`fetch_json` 便捷函数，网络异常转为 `SourceRequestError`）、`utils/helpers.py`（`normalize_url`、`clean_text`、`strip_html`、`clean_dict`）。

### 新增站点插件

插件是 `media_source/plugins/<site>/` 包，固定 4 个文件 —— `__init__.py`（导出插件类）、`constants.py`（站点 URL/请求头）、`parser.py`（纯异步函数，返回原始字典）、`main.py`（`MediaSourcePlugin` 子类 + 映射模板 + `_raw_*` 方法）。`plugins/yhdm/` 是真实参考实现；`plugins/template/` 为同构骨架（扫描时被跳过）。完整指南：`media_source/docs/PLUGIN_DEV_GUIDE.md`。

## 已知状态 / 注意事项

- **测试套件当前是坏的**：`test_plugin_manager.py`、`test_plugins.py`、`test_batch_search.py` 引用了已被删除的示例插件 `site_a`/`site_b`（`https://www.site-a.example.com`、`https://www.site-b.example.com`）。现在仅剩 `yhdm` 一个真实插件，这些文件在更新到当前插件集之前会失败/报错。
- `yhdm` 插件发起**真实网络请求**到 `https://yhdm.one/`（搜索/详情为服务端渲染 HTML；播放地址来自 JSON 接口 `/_get_plays/<vod_id>/<ep_name>`）。无离线/mock 模式，涉及它的单元测试需要网络。
- `plugin_manager.scan_plugins()` 在导入时执行，因此 `get_supported_sources()` 反映的是 `plugins/` 目录下当前实际存在的包，而非静态清单。
