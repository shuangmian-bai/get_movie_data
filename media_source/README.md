# media_source —— 插件化多源影视数据源模块

插件化、可扩展、多源聚合影视数据源解析模块，基于 Python 异步开发。
实现多影视站点统一接入、统一字段格式化、并发批量检索能力，彻底解耦框架与业务站点解析逻辑。

## 核心能力

- **插件化架构**：每个影视站点为独立 Python 模块包，支持插拔式接入
- **统一数据格式化**：字段映射引擎 + 白名单过滤机制，剔除站点多余脏字段，统一输出标准 JSON
- **多源批量并发搜索**：支持传入站点列表、全站点扫描，内置最大并发数限流
- **分页搜索**：按偏移 + 条数只抓取覆盖区间的站点分页，并发抓页，避免一次性抓取全部结果
- **完整业务链路**：关键词搜索 → 影视详情获取 → 分集播放地址解析（m3u8/mp4）
- **数据源查询接口**：实时获取当前已加载、可使用的全部数据源信息
- **异常隔离机制**：批量搜索单源失败不影响整体任务，日志记录错误，聚合有效结果

## 目录结构

```
media_source/
├── __init__.py            # 对外统一导出入口
├── base.py                # 插件抽象基类（核心规范）
├── mapping.py             # 字段映射引擎、白名单过滤核心逻辑
├── models.py              # Pydantic 标准数据模型
├── plugin_manager.py      # 插件管理器（扫描、加载、并发、对外接口）
├── exceptions.py          # 全局自定义异常
├── config.py              # 全局配置（并发数、超时、默认参数）
├── utils/                 # 通用工具包（http / helpers）
├── plugins/               # 站点插件目录
│   ├── template/          # 插件开发模板（新增站点直接复制，扫描时跳过）
│   ├── cupfox/            # 茶杯狐
│   ├── qqll/              # 奇奇影视
│   └── _deprecated/       # 废弃数据源区（过期源收纳，扫描时跳过）
├── examples/              # 业务使用示例
├── tests/                 # 单元测试
├── docs/                  # 开发文档
└── README.md
```

> `requirements.txt` 位于项目根目录。

## 快速开始

```bash
pip install -r requirements.txt
```

```python
import asyncio
from media_source import plugin_manager

async def main():
    # 1. 查询可用数据源
    sources = plugin_manager.get_supported_sources()

    # 2. 全源批量搜索（base_urls=[] 表示全源扫描）
    results = await plugin_manager.batch_search("流浪地球", [])

    # 3. 按 base_url 路由到单个插件，获取详情与播放地址
    item = results[0]
    plugin = plugin_manager.get_plugin_instance(item.base_url)
    info = await plugin.get_info(item)
    play = await plugin.get_play_url(info, info.episodes[0].index)

asyncio.run(main())
```

## 对外接口

| 接口 | 类型 | 说明 |
| --- | --- | --- |
| `plugin_manager.get_supported_sources()` | 同步 | 返回 `List[SourceMeta]` 可用数据源列表 |
| `await plugin_manager.batch_search(key, base_urls, max_concurrency=None, start=0, count=None, page_concurrency=None)` | 异步 | 批量多源搜索，返回 `List[SearchItem]`，内部异常隔离，支持分页 |
| `plugin_manager.get_plugin_instance(base_url)` | 同步 | 获取单插件实例，无匹配抛出 `PluginNotFoundError` |
| `await plugin.search(key)` | 异步 | 单源搜索（全量） |
| `await plugin.search_page(key, start=0, count=None, page_concurrency=None)` | 异步 | 单源分页搜索（按偏移 + 条数） |
| `await plugin.get_info(search_item)` | 异步 | 获取影视详情 |
| `await plugin.get_play_url(media_info, episode_index)` | 异步 | 获取指定集数播放地址 |

### 入参规则（batch_search）

- `base_urls = []`：全量已加载插件并发搜索
- `base_urls = [url1, url2]`：指定多源 / 单源搜索
- 列表内无效 URL 自动过滤，日志告警，不影响整体任务
- `start` / `count`：分页参数（偏移从 0 开始 + 条数），透传给各插件；`count=None` 表示全量
- `page_concurrency`：单插件翻页并发抓取页数（默认取 `config.PAGE_CONCURRENCY`）

### 双层异常策略

1. **批量模式**（`batch_search`）：容错模式，单源失败不中断，只记录日志，返回有效数据
2. **单源模式**（插件实例直接调用）：严格模式，异常直接抛出，业务精准处理错误

### 分页搜索

搜索支持按偏移 + 条数分页，避免一次性抓取全部结果：

```python
results = await plugin.search_page("仙逆", start=0, count=20, page_concurrency=5)
```

- `start`：起始偏移（0-based，第 1 条序号为 0）
- `count`：期望返回条数；`None` 表示全量搜索（等价 `search`）
- `page_concurrency`：并发抓取页数，默认取 `config.PAGE_CONCURRENCY`

**机制**：支持分页的站点（覆盖 `_raw_search_page(key, page)` 钩子）会先抓第 1 页探明每页条数，
再并发抓取覆盖 `[start, start+count)` 的页码，合并后切片返回；不支持分页的站点降级为抓取全部后切片。

## 字段映射引擎

模板语法：`标准输出字段: {站点原始字段} | default:默认值`

```python
search_mapping = {
    "name": "{title} | default:'未知影片'",
    "link": "{href} | default:''",
    "type": "{type} | default:''",
    "year": "{year} | default:''",
    "cover": "{cover} | default:''",
    "desc": "{intro} | default:''",
}
```

- **白名单过滤**：最终输出字段仅为模板定义的 key，原始数据多余字段全部丢弃
- **取值优先级**：原始字段存在则取值，不存在则使用默认值
- **格式统一**：所有站点数据经过映射后，结构完全一致

## 新增站点插件

1. 复制 `plugins/template` 完整文件夹，重命名为站点标识名称
2. 修改 `__init__.py`，导出插件主类
3. 在 `constants.py` 定义站点接口、请求头、固定参数
4. 在 `parser.py` 编写站点专属解析逻辑，输出原始字典数据
5. 在 `main.py` 定义站点元信息 + 三套映射模板
6. 实现三个 `_raw_*` 抽象方法，调用内部解析工具
7. 重启项目，管理器自动扫描加载新插件

> 完整的分步教程（含代码示例、映射模板详解、字段对照表）见
> [数据源插件开发指南](docs/PLUGIN_DEV_GUIDE.md)。

### 插件开发强制约束

- 插件禁止导入核心框架业务逻辑，仅依赖基类和工具方法
- `_raw_*` 方法仅返回原始字典，禁止返回标准模型、禁止字段过滤
- 所有站点私有配置、加密逻辑、解析逻辑，全部封装在当前插件包内
- 必须配置完整三套 mapping 模板，保证输出字段统一

## 运行示例

```bash
# 查询可用数据源列表
python -m media_source.examples.demo_get_sources

# 多源 / 全源批量搜索
python -m media_source.examples.demo_batch_search

# 单源详情、播放地址查询
python -m media_source.examples.demo_single_plugin

# 完整业务链路演示
python -m media_source.examples.demo_full_flow
```

## 运行测试

```bash
python -m unittest discover -s media_source/tests -v
```

## 依赖

```
httpx>=0.27.0
pydantic>=2.0
lxml>=5.0
parsel>=1.8.1
```
