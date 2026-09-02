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

前端静态资源位于本模块 `frontend/` 目录，由 `frontend_loader` 引擎加载：

- 访问根路径 `/` 返回 `frontend/index.html`（含搜索框，前端通过 fetch 调用 `/api/*`）；
- 搜索结果**分页展示**：每页 20 条，提供「上一页 / 下一页 / 页码」；翻页时才请求对应页（透传 `start`/`count`），按需爬取、减少引擎压力；翻过头自动停留在上一页并禁用「下一页」；
- 搜索框下方提供**数据源多选**（复选框，默认全选）：勾选后仅请求所选源（透传 `base_urls`），可规避某个慢站点拖慢整体；全不勾选则提示「请至少选择一个数据源」；
- 点选集后前端经 `/api/play` 取原始播放地址，再经 `/api/stream/processed` 转成本地 HLS，用 hls.js 内嵌播放（绕开防盗链、按站点去广告）；
- `.html/.css/.js` 等静态资源同样从 `frontend/` 目录提供。

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
