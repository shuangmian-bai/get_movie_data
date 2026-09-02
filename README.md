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

## 主要功能

- 关键词搜索影视资源
- 获取影视详情
- 获取指定集数的播放地址
- 批量并发搜索多个数据源
- 文件缓存与过期控制
- 插件自动扫描与加载

## 目录导航

### 文档

- [媒体数据源模块说明](./media_source/README.md)
- [缓存模块说明](./media_source/cache.md)
- [插件开发指南](./media_source/docs/PLUGIN_DEV_GUIDE.md)
- [Web 服务说明](./web/README.md)

### 代码

- `main.py`：应用入口
- `web/`：HTTP 接口层
- `media_source/`：插件框架、模型、缓存和数据源实现
- `frontend_loader/`：前端静态资源加载中间件
- `view/`：演示页面
- `cache/`：运行时缓存目录

## 快速开始

```bash
pip install -r requirements.txt
python main.py
```

启动后可访问：

- `http://127.0.0.1:8000/docs`

## 常用接口

- `GET /api/sources`
- `GET /api/search?key=关键词`
- `GET /api/info?base_url=...&link=...`
- `GET /api/play?base_url=...&link=...&episode_index=1`

## 开发提示

- 新增站点时，优先参考 `media_source/plugins/template`
- 插件实现只负责输出原始数据
- 字段映射、默认值和统一结构由基础类完成

## 友情链接

- [隼目安全](https://sumsafe.org.cn/)
