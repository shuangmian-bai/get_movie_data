# get_movie_data

一个基于 `FastAPI` 的影视数据源聚合项目，核心目标是把多个站点插件统一成一套检索、详情和播放地址接口，并对播放源做**插件化去广告处理**。

## 项目做什么

- 插件化接入多个影视数据源
- 统一输出搜索结果、详情信息、分集列表和播放地址
- 提供 `FastAPI` Web 接口，便于直接对外调用
- 内置文件缓存，减少重复抓取
- 提供前端静态页面加载入口
- 插件化去广告处理（逐分片检测广告：全广告丢弃 / 水印广告去水印 / 正常分片代理）

当前仓库里已经接入的示例站点包括：

- `cupfox7.com`
- `qqll.cc`

> 过期不再使用的数据源（如 `yhdm.one`）已移入 `media_source/plugins/_deprecated/` 废弃区，扫描时自动跳过。

## 主要功能

- 关键词搜索影视资源
- 获取影视详情
- 获取指定集数的播放地址
- 批量并发搜索多个数据源
- 前端搜索分页（每页按需翻页爬取，减少引擎压力）
- 数据源多选（前端勾选数据源，仅请求所选站点）
- 文件缓存与过期控制
- 插件自动扫描与加载
- 插件化去广告处理（ad_filter：逐分片抽帧检测，全广告丢弃 / 水印广告去水印 / 正常分片代理）

## 目录导航

### 文档

- [媒体数据源模块说明](./media_source/README.md)
- [缓存模块说明](./media_source/cache.md)
- [插件开发指南](./media_source/docs/PLUGIN_DEV_GUIDE.md)
- [Web 服务说明](./web/README.md)
- [去广告处理模块说明](./ad_filter/README.md)

### 代码

- `main.py`：应用入口
- `web/`：HTTP 接口层
- `media_source/`：插件框架、模型、缓存和数据源实现
- `ad_filter/`：插件化去广告处理（逐分片检测 → 丢弃/去水印/代理 → 处理后 m3u8）
- `frontend_loader/`：前端静态资源加载中间件
- `view/`：演示页面
- `cache/`：统一运行时缓存目录

## 缓存目录约定

所有运行时缓存统一放在项目根的 `cache/` 目录下（以 `cache` 为基础路径）：

```
cache/
├── {站点}/          # media_source 文件缓存（FileCache，按 base_url 分区，JSON）
└── ad_filter/       # ad_filter 处理结果（处理后的 m3u8 + 去水印分片 + 会话元数据）
```

- 各模块缓存目录均可通过环境变量覆盖：`MEDIA_SOURCE_CACHE_DIR`（media_source 文件缓存）、`AD_FILTER_OUTPUT_ROOT`（ad_filter 处理结果根目录）。
- **新增缓存时同样放入 `cache/` 下**，保持「所有缓存以 cache 为基础路径」这条约定。

## 环境要求

- **Python 3.8+**（本项目用 pyenv 3.13.13 开发）。
- **纯 Python 依赖**：视频处理用 PyAV（`av`，自带 ffmpeg 库）、去水印用 `opencv-python-headless`、OCR 用 `rapidocr_onnxruntime`，全部 `pip install -r requirements.txt` 即可，**无需手动安装系统 ffmpeg / tesseract / mediamtx**。

## 快速开始

```bash
pip install -r requirements.txt
python main.py
```

- 默认**自动检测系统代理**（读 `HTTP_PROXY` / `HTTPS_PROXY` / `ALL_PROXY` 环境变量）。
- 需要显式指定代理时，用启动参数：

```bash
python main.py --proxy http://127.0.0.1:7890
```

代理支持 http / https / socks5（socks5 由 `httpx[socks]` 提供）；显式代理优先于系统代理。

启动后可访问：

- `http://127.0.0.1:8000/docs`

## 常用接口

- `GET /api/sources`
- `GET /api/search?key=关键词`（可选 `base_url` 单源 / `base_urls` 多源 / `start`+`count` 分页）
- `GET /api/info?base_url=...&link=...`
- `GET /api/play?base_url=...&link=...&episode_index=1`
- `POST /api/ad_filter/process`（去广告处理：返回处理后 m3u8 地址）

## 开发提示

- 新增站点时，优先参考 `media_source/plugins/template`
- 插件实现只负责输出原始数据
- 字段映射、默认值和统一结构由基础类完成
- 去广告处理由 `ad_filter/` 模块提供（opencv 抽帧 + rapidocr 检测 + opencv inpaint 去水印 + PyAV 写回），站点 → 检测器/去水印器组合在 `main.py` 的 `AD_FILTER_PIPELINES` 里编排

## 友情链接

- [隼目安全](https://sumsafe.org.cn/)
- [双面的小窝](https://blog.shuangmian.top/)
