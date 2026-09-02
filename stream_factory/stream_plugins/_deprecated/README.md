# 废弃流插件区

专门收纳「过期不再使用」的流插件源码。

流插件经 `stream_plugins/__init__.py` 显式导出，废弃时需：

1. 把对应 `xxx.py` 移到本目录；
2. 从 `stream_plugins/__init__.py`、`stream_factory/__init__.py` 移除导入与 `__all__` 导出；
3. 若 `main.py` 的 `STREAM_PIPELINES` 引用了该插件，一并移除。

## 当前收纳

| 插件 | 说明 |
| --- | --- |
| `YhdmStreamPlugin` | 樱花动漫（yhdm）流插件（暂废弃） |
