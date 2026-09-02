# 废弃数据源区

本目录专门收纳「过期不再使用」的数据源插件。

`plugin_manager.scan_plugins()` 扫描时**跳过**本目录（同 `template` 目录），因此这里的内容不会被加载，也不会出现在 `get_supported_sources()` 的返回里。

## 如何废弃一个数据源

把 `media_source/plugins/<site>/` 整个目录移动到本目录下即可：

```bash
mv media_source/plugins/<site> media_source/plugins/_deprecated/<site>
```

插件内部 import 路径（`media_source.plugins.<site>.*`）**无需修改**——废弃时不会被加载，日后恢复（移回 `plugins/`）时路径自动复原。

> 注意：若该站点还有对应的流插件（`stream_factory/stream_plugins/`），需一并移入
> `stream_factory/stream_plugins/_deprecated/`，并取消其在 `stream_plugins/__init__.py`、
> `stream_factory/__init__.py`、`main.py` 中的导出与引用。

## 如何恢复

把本目录下的 `<site>/` 移回 `media_source/plugins/<site>/`，重启服务即重新扫描加载。

## 当前收纳

| 站点 | base_url | 说明 |
| --- | --- | --- |
| yhdm | `https://yhdm.one` | 樱花动漫（暂废弃） |
