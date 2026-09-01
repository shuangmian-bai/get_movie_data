# 数据源插件开发指南

本文档介绍如何为一个新的影视站点编写插件包，并接入 `media_source` 模块。
新增站点**无需修改任何核心框架代码**，只需在 `plugins/` 下新建一个独立包并实现约定接口。

---

## 1. 插件是什么

- 一个插件 = 一个独立 Python 模块包，对应一个影视站点。
- 插件**只做两件事**：请求站点、解析出**原始脏数据**（字典 / 字典列表）。
- 数据格式化（字段映射、白名单过滤、默认值兜底）由基类 `MediaSourcePlugin` 统一完成，插件无感知。

```
上层业务层 → 插件管理器层 → 插件基类层 → 站点插件实现层 → 工具层
```

---

## 2. 插件目录结构

```
plugins/
└── site_xxx/              # 站点标识名称（新站点复制 template 后重命名）
    ├── __init__.py        # 导出插件主类
    ├── constants.py       # 站点私有常量、接口、请求头
    ├── parser.py          # 站点专属解析逻辑（输出原始数据）
    └── main.py            # 插件主类 + 映射模板 + 抽象方法实现
```

---

## 3. 核心概念：原始数据 vs 标准数据

| 概念 | 说明 | 由谁产生 |
| --- | --- | --- |
| 原始数据 | 站点返回的脏数据，字段名各异、含大量无用字段 | 插件的 `_raw_*` 方法 |
| 标准数据 | 映射后的统一结构（`SearchItem`/`MediaInfo`/`PlaySource`） | 基类自动生成 |

**插件永远只输出原始数据**，禁止返回标准模型、禁止字段过滤。

---

## 4. 五步新建插件

### 步骤 1：复制模板

```bash
cp -r media_source/plugins/template media_source/plugins/site_xxx
```

### 步骤 2：修改 `__init__.py` 导出主类

```python
# plugins/site_xxx/__init__.py
from media_source.plugins.site_xxx.main import SiteXxxPlugin

__all__ = ["SiteXxxPlugin"]
```

### 步骤 3：填写 `constants.py`

```python
# plugins/site_xxx/constants.py
from media_source.utils.helpers import normalize_url

BASE_URL = "https://www.site-xxx.com"          # 站点唯一标识 URL
SEARCH_API = f"{BASE_URL}/api/search"           # 搜索接口
DETAIL_API = f"{BASE_URL}/api/detail"           # 详情接口
PLAY_API = f"{BASE_URL}/api/play"               # 播放地址接口

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    "Referer": f"{BASE_URL}/",
}


def abs_url(path: str) -> str:
    """把相对路径标准化为绝对地址。"""
    return normalize_url(path, BASE_URL)
```

### 步骤 4：编写 `parser.py`（站点解析逻辑）

```python
# plugins/site_xxx/parser.py
from typing import Any, Dict, List
from media_source.utils.http import fetch_json
from media_source.plugins.site_xxx.constants import SEARCH_API, DETAIL_API, abs_url


async def parse_search(key: str) -> List[Dict[str, Any]]:
    """请求搜索接口并解析为原始字典列表。"""
    data = await fetch_json(SEARCH_API, params={"wd": key})
    return [
        {
            "title": item["name"],      # 原始字段，可能叫 name/标题 等
            "href": abs_url(item["url"]),
            "type": item["category"],
            "year": item["pub_year"],
            "cover": item["poster"],
            "intro": item["summary"],
        }
        for item in data.get("list", [])
    ]


async def parse_info(detail_url: str) -> Dict[str, Any]:
    """请求详情页并解析为原始字典。"""
    data = await fetch_json(detail_url)
    return {
        "title": data["name"],
        "type": data["category"],
        "year": data["pub_year"],
        "cover": data["poster"],
        "intro": data["summary"],
        "episodes": [
            {"name": ep["ep_name"], "url": abs_url(ep["ep_url"])}
            for ep in data.get("eps", [])
        ],
    }


async def parse_play_url(play_url: str) -> Dict[str, Any]:
    """请求播放页并解析出原始播放地址字典。"""
    data = await fetch_json(play_url)
    return {
        "play_url": data["url"],
        "play_type": data.get("type", "m3u8"),
        "headers": data.get("headers", {}),
    }
```

> 说明：上述示例使用真实 HTTP 请求。若本地离线开发，可先返回硬编码的模拟数据，
> 待接口就绪后再替换为真实请求（参考 `plugins/site_a/parser.py`）。

### 步骤 5：编写 `main.py`（主类 + 映射模板 + 抽象方法）

```python
# plugins/site_xxx/main.py
from typing import Any, Dict, List

from media_source.base import MediaSourcePlugin
from media_source.models import MediaInfo, PlaySource, SearchItem


class SiteXxxPlugin(MediaSourcePlugin):
    # ---- 站点元信息（必须自定义）----
    base_url = "https://www.site-xxx.com"
    source_name = "站点 XXX"
    source_desc = "站点 XXX 描述信息"

    # ---- 三套映射模板（白名单）----
    # 标准字段: {原始字段} | default:默认值
    search_mapping: Dict[str, str] = {
        "name": "{title} | default:'未知影片'",
        "link": "{href} | default:''",
        "type": "{type} | default:''",
        "year": "{year} | default:''",
        "cover": "{cover} | default:''",
        "desc": "{intro} | default:''",
    }

    info_mapping: Dict[str, str] = {
        "name": "{title} | default:'未知影片'",
        "link": "{href} | default:''",
        "type": "{type} | default:''",
        "year": "{year} | default:''",
        "cover": "{cover} | default:''",
        "desc": "{intro} | default:''",
    }

    # 分集映射模板（可选，用于统一分集列表）
    episode_mapping: Dict[str, str] = {
        "name": "{name} | default:''",
        "index": "{index} | default:0",
        "link": "{url} | default:''",
    }

    play_mapping: Dict[str, str] = {
        "url": "{play_url} | default:''",
        "type": "{play_type} | default:'m3u8'",
        "headers": "{headers} | default:{}",
    }

    # ---- 抽象方法实现（仅输出原始数据）----
    async def _raw_search(self, key: str) -> List[Dict[str, Any]]:
        from media_source.plugins.site_xxx import parser

        return await parser.parse_search(key)

    async def _raw_get_info(self, search_item: SearchItem) -> Dict[str, Any]:
        from media_source.plugins.site_xxx import parser

        return await parser.parse_info(search_item.link)

    async def _raw_get_play_url(
        self, media_info: MediaInfo, episode_index: int
    ) -> Dict[str, Any]:
        from media_source.plugins.site_xxx import parser

        episode = next(
            (ep for ep in media_info.episodes if ep.index == episode_index),
            None,
        )
        if episode is None:
            return {}
        return await parser.parse_play_url(episode.link)
```

重启项目后，管理器会自动扫描并加载新插件，无需任何额外注册。

---

## 5. 字段映射模板详解

### 5.1 语法

```
标准输出字段: {站点原始字段} | default:默认值
```

示例：`"name": "{title} | default:'未知影片'"`

### 5.2 取值规则

| 情况 | 结果 |
| --- | --- |
| 原始字段存在且非 `None` | 取原始字段值 |
| 原始字段不存在 / 为 `None` | 取 `default` 默认值 |
| 无 `default` 且字段缺失 | 跳过该 key，由模型默认值兜底 |

### 5.3 默认值类型

`default` 支持任意 Python 字面量：

- 字符串：`default:'未知影片'`、`default:''`
- 数字：`default:0`
- 空字典 / 列表：`default:{}`、`default:[]`

### 5.4 白名单过滤

最终输出字段**仅为模板定义的 key**，站点原始数据中所有多余字段全部丢弃。

```python
raw = {"title": "三体", "href": "/x", "score": "8.7", "internal_id": 2001}
mapping = {"name": "{title} | default:''", "link": "{href} | default:''"}
# 输出：{"name": "三体", "link": "/x"}  —— score / internal_id 被丢弃
```

---

## 6. 标准数据模型字段

| 模型 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| `SearchItem` | `name` | str | 影视标题 |
| | `base_url` | str | 来源站点标识（基类自动注入） |
| | `link` | str | 详情页链接 |
| | `type` | str | 类型（电影/剧集/综艺） |
| | `year` | str | 年份 |
| | `cover` | str | 封面图 |
| | `desc` | str | 简介 |
| `MediaInfo` | 同上 | | 另含 `episodes` |
| | `episodes` | List[EpisodeItem] | 分集列表 |
| `EpisodeItem` | `name` | str | 分集标题 |
| | `index` | int | 集数序号（从 1 开始） |
| | `link` | str | 分集播放页链接 |
| `PlaySource` | `url` | str | 播放地址 |
| | `type` | str | 播放类型（m3u8/mp4） |
| | `headers` | dict | 自定义请求头 |

> `base_url` 由基类在格式化后自动注入，插件无需在映射模板中声明。

---

## 7. 异常处理约定

| 模式 | 策略 | 示例 |
| --- | --- | --- |
| 批量模式（`batch_search`） | 容错：单插件失败仅记日志，不中断整体 | 管理器自动处理 |
| 单源模式（插件实例直接调用） | 严格：异常直接抛出，业务自行处理 | `plugin.search(key)` |

插件解析失败时可主动抛出框架异常：

```python
from media_source.exceptions import SourceParseError

async def parse_info(url):
    data = await fetch_json(url)
    if not data:
        raise SourceParseError("详情解析失败：返回数据为空")
    return data
```

---

## 8. 验证新插件

```bash
# 查询数据源列表，确认新插件已加载
python -m media_source.examples.demo_get_sources

# 运行单元测试
python -m unittest discover -s media_source/tests -v
```

新增插件后，建议为 `search` / `get_info` / `get_play_url` 三条链路各写一个用例，参考 `media_source/tests/test_plugins.py`。

---

## 9. 开发约束（必须遵守）

1. 插件**禁止导入**核心框架业务逻辑，仅依赖基类 `MediaSourcePlugin` 和 `utils` 工具方法。
2. `_raw_*` 方法**仅返回原始字典**，禁止返回标准模型、禁止字段过滤。
3. 站点私有配置、加密逻辑、解析逻辑**全部封装在插件包内**。
4. **必须配置完整三套映射模板**（`search_mapping`/`info_mapping`/`play_mapping`），保证输出字段统一。
