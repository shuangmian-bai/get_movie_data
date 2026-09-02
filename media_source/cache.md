# cache —— 文件缓存模块

## 用途

在「网络爬虫 → Web 服务」场景下，同一数据在 TTL 内只爬取一次，避免重复请求源站；
通过分级 TTL 保证数据不会长期滞后。按 `base_url` 划分命名空间，不同站点缓存互相隔离。

## 目录结构

```
{项目根}/cache/                     # 缓存根目录（可用环境变量 MEDIA_SOURCE_CACHE_DIR 覆盖）
└── yhdm.one/                       # 命名空间 = base_url 规范化
      ├── <md5>.json                # 搜索 / 详情 / 播放 的缓存条目
      └── ...
```

每个缓存文件内容为 JSON：`{"ts": 写入时间戳, "expires": 过期时间戳, "data": 缓存数据}`。

## 核心接口

```python
from media_source.cache import file_cache  # 全局单例
```

| 方法 | 说明 |
| --- | --- |
| `FileCache.namespace_of(base_url)` | 把 base_url 规范化为目录名 |
| `await cache.get(namespace, key)` | 读取；未命中或过期返回 `None`（过期文件惰性删除） |
| `await cache.set(namespace, key, data, ttl)` | 写入（原子写：`.tmp` → `os.replace`） |
| `await cache.get_or_fetch(namespace, key, ttl, fetch)` | 命中直接返回；未命中调用 `fetch()` 并回填 |
| `await cache.clear(namespace=None)` | 清空缓存（不传则清空全部），返回删除文件数 |

`key` 为业务参数键（如 `search:仙逆:0:20`、`info:<详情链接>`、`play:<分集链接>`），内部 MD5 成文件名，
避免路径非法字符问题。搜索 key 含分页维度（`start:count`），不同分页参数不共享缓存。

## TTL 分级

| 数据类型 | 默认 TTL | 配置项 |
| --- | --- | --- |
| 搜索结果 | 10 分钟 | `MEDIA_SOURCE_SEARCH_TTL` |
| 详情信息 | 1 小时 | `MEDIA_SOURCE_INFO_TTL` |
| 播放地址 | 10 分钟 | `MEDIA_SOURCE_PLAY_TTL` |

播放地址（m3u8/CDN）时效性最强，默认取最短 TTL；各值均可通过环境变量覆盖（见 `config.py`）。

## 并发穿透防护

`get_or_fetch` 内部用 **per-key 的 `asyncio.Lock` + 双重检查**：同一 key 的并发请求，
只有一个真正调用 `fetch()` 去爬源站，其余协程等待锁后直接复用结果，从根本上压掉并发重复爬虫。

## 接入方式（应用层编排）

按开发规范「低耦合 + 应用层汇总」，缓存模块不侵入插件框架，统一在应用层编排。
封装模式如下（以搜索为例）：

```python
from media_source import config, plugin_manager
from media_source.cache import file_cache
from media_source.models import SearchItem

async def search_with_cache(key: str, base_url: str, start: int = 0, count: int | None = None) -> list[SearchItem]:
    plugin = plugin_manager.get_plugin_instance(base_url)
    namespace = file_cache.namespace_of(plugin.base_url)
    cache_key = f"search:{key}:{start}:{'all' if count is None else count}"

    async def fetch():
        items = await plugin.search_page(key, start=start, count=count)
        return [item.model_dump() for item in items]  # 模型 -> dict（可 JSON 化）

    data = await file_cache.get_or_fetch(
        namespace, cache_key, config.SEARCH_CACHE_TTL, fetch
    )
    return [SearchItem(**d) for d in data]  # dict -> 模型还原
```

完整可运行示例见 `examples/demo_cache.py`。

## 注意事项

- 缓存只存 **JSON 可序列化数据**（dict/list/str/int/float/bool/None）；Pydantic 模型先 `model_dump()`、取回后 `Model(**data)` 还原。
- 文件读写为同步阻塞 IO，但缓存文件极小、耗时可忽略；若后续要承载高并发大对象，可替换为 `aiofiles` 或 Redis，接口保持一致。
- 缓存目录属于运行时产物，建议加入 `.gitignore`（默认位于项目根 `cache/`）。
