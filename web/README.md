# web —— Web 服务模块

## 用途

基于 FastAPI 的 REST API 模块，把 `media_source` 数据源能力暴露为 HTTP 接口，
并在本层完成**缓存编排**（复用 `media_source.cache`），防止重复爬虫。

本模块只依赖 `media_source` 的公开接口（`plugin_manager` / `file_cache` / 模型），
不侵入其内部实现；应用层 `main.py` 负责挂载本模块的 `api_router`。

## 端点

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/sources` | 无 | 可用数据源列表 |
| GET | `/api/search` | `key`（必填）、`base_url`（可选单源）、`base_urls`（可选多源，重复参数）、`start`（可选，默认 0）、`count`（可选，空则全部）、`page_concurrency`（可选） | 搜索影视（支持分页，单源优先） |
| GET | `/api/info` | `base_url`、`link`（均必填） | 影视详情 |
| GET | `/api/play` | `base_url`、`link`、`episode_index`（均必填） | 播放地址 |

参数一律走 query string。`base_url` 为站点唯一标识（如 `https://www.cupfox7.com`），
`link` 为详情页链接，`episode_index` 为集数序号（从 1 开始）。

## 缓存行为

- 搜索 / 详情 / 播放三类结果均写入文件缓存，TTL 见 `media_source/config.py`
  （默认搜索 7 天、详情 30 天、播放 7 天，可环境变量覆盖）。
- 搜索缓存 key 含分页维度（`start`/`count`），不同分页参数不共享缓存；多源搜索还按源组合（`base_urls`）区分缓存。
- 播放接口内部复用详情缓存，不会因取播放地址而重复爬详情页。
- 同一 key 并发请求只触发一次爬取（见 `media_source.cache` 的并发穿透防护）。

## 前端

前端源码在独立工作区 `$DISK_ROOT/react_data/get_movie_data_web`（React + Vite + TypeScript + Sass），
构建产物 `view/` 由 fastapi-ui 托管（`mount_webui(app, "view")`）。

- 访问根路径 `/` 返回 `view/index.html`，React 应用包含三个功能：搜索、详情播放、投稿；
- 构建：在 react_data 工作区执行 `npm run build`（先 `tsc -b` 再 `vite build`），
  产物通过 `build.outDir` 直接输出到本项目 `view/`，不手工拷文件；
- `view/` 是构建产物，已 gitignore，**不手工修改**（改了下次构建就被覆盖）；
- 前端功能目录见 `react_data/get_movie_data_web/src/features/`，每个功能含 `README.md` 与统一出口
  `index.ts`；跨功能只从出口 import；
- 前端功能与旧内联页面对齐：搜索分页（每页 5 条）、数据源多选、详情选集、
  播放器支持「原生直连 / 去广告播放」；新增「投稿」页（数据源与广告信息）。

## 启动

```bash
python main.py
```

启动后访问 `http://127.0.0.1:8000/docs` 查看交互式 API 文档。

## 示例

```bash
# 数据源列表
curl "http://127.0.0.1:8000/api/sources"

# 搜索（单源）
curl "http://127.0.0.1:8000/api/search?key=仙逆&base_url=https://www.cupfox7.com"

# 搜索（全源）
curl "http://127.0.0.1:8000/api/search?key=仙逆"

# 搜索（多源指定，重复 base_urls 参数）
curl "http://127.0.0.1:8000/api/search?key=仙逆&base_urls=https://www.cupfox7.com&base_urls=https://www.qqll.cc"

# 搜索（分页：从第 0 条起取 20 条，并发抓页 5）
curl "http://127.0.0.1:8000/api/search?key=仙逆&start=0&count=20&page_concurrency=5"

# 详情
curl "http://127.0.0.1:8000/api/info?base_url=https://www.cupfox7.com&link=https://www.cupfox7.com/voddetail/xxxx.html"

# 播放地址
curl "http://127.0.0.1:8000/api/play?base_url=https://www.cupfox7.com&link=https://www.cupfox7.com/voddetail/xxxx.html&episode_index=1"
```
