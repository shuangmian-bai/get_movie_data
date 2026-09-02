# stream_factory —— 流工厂模块

## 用途

把第三方影视源（`media_source` 拿到的 m3u8/mp4 播放地址）转成**统一流**，在服务端做**去广告裁剪**，
以**流工厂**形式管理多个流会话，对外输出**双协议**：

- **HLS**：浏览器 Web 播放（内嵌 `hls.js` 播放器）；
- **RTSP**：原生客户端播放（VLC / ffplay / ijkplayer，经 mediamtx 分发）。

本模块只依赖 `stream_factory` 内部能力，不侵入 `media_source` / `web`；应用层 `main.py` 挂载其路由。

## 系统依赖

| 组件 | 作用 | 安装 |
| --- | --- | --- |
| `ffmpeg` / `ffprobe` | 拉流、裁剪、转码、推流 | `apt install ffmpeg` / `brew install ffmpeg` |
| `mediamtx` | RTSP 服务器（接收 FFmpeg 推流并分发 RTSP/HLS；服务启动时自动拉起） | 单二进制，见 [mediamtx](https://github.com/bluenviron/mediamtx) |

> 本机已装：`ffmpeg 7.1.5`、`mediamtx 1.8.1`（`/mnt/4t/linux/huanjing/mediamtx/1.8.1/mediamtx`）。
> `python main.py` 启动服务时会**自动拉起 mediamtx**（`MEDIAMTX_AUTOSTART=1` 时），无需手动启动；
> 只需 HLS、不要 RTSP 时，设 `STREAM_FACTORY_RTSP_ENABLED=0` 关闭推流（不拉起、无需 mediamtx）。

## 目录结构

```
stream_factory/
├── __init__.py          # 导出单例、路由、HLS_ROOT、规则模型、插件基类与内置插件
├── config.py            # FFmpeg 路径、HLS 目录、RTSP 地址等（环境变量可覆盖）
├── rules.py             # 规则模型：StreamSource / TrimSegment / FilterRule / StreamRequest
├── base.py              # 抽象基类：FramePlugin（帧插件）/ StreamPlugin（流插件）
├── plugins.py           # 内置插件：水印/文字帧插件 + 各站点流插件 + 空白插入案例
├── pipeline.py          # FFmpeg 命令行构建器（规则 → ffmpeg 参数）
├── session.py           # StreamSession：单会话子进程生命周期
├── factory.py           # StreamFactory：会话集合管理（单例 stream_factory）
├── api.py               # FastAPI 路由（创建/查询/停止 + 播放器页）
└── frontend/player.html # 内嵌播放器（hls.js）
```

## 裁剪档位

把「删除广告区间」翻译为 ffmpeg 参数，能无损则无损：

| 场景 | 手段 | 是否重编码 |
| --- | --- | --- |
| 无裁剪 | 整段转发 | 否（`-c copy`） |
| 掐头（删 `[0, end)`） | `-ss end` | 否 |
| 去尾（删 `[start, 结尾)`） | `-to start` | 否 |
| 中间段 / 多段 | `select`/`aselect` 滤镜 | 是（`libx264` + `aac`） |

- 无损快路径（copy）裁剪精度受关键帧/GOP 限制，去广告可接受；精确到帧需走重编码路径。
- `filters`（逐帧滤镜）为**预留扩展**，当前实现水印示例 `drawtext`；存在滤镜时视频强制重编码。

## 流/帧插件与站点组合

去广告规则是**系统内部知识**，封装为两类可复用、不绑定站点的插件：

- **`FramePlugin`（帧插件）**：逐帧处理单元，`filters()` 产出 `FilterRule` 列表（如去水印 `drawtext`）。
- **`StreamPlugin`（流插件）**：流级裁剪策略，`trims(source)` 产出 `TrimSegment` 列表，`build_request(source, frame_plugins)` 合成 `StreamRequest`。

内置插件（`stream_factory/plugins.py`）：

| 类型 | 插件 | 说明 |
| --- | --- | --- |
| 帧 | `WatermarkFramePlugin` | 去水印/打标（`drawtext`，示例） |
| 帧 | `ShuangmianTextFramePlugin` | **开发案例**：叠加「双面酱」文字水印 |
| 流 | `PassthroughStreamPlugin` | 透传，不裁剪 |
| 流 | `CupfoxStreamPlugin` / `YhdmStreamPlugin` / `QqllStreamPlugin` | 各站点裁剪策略（区间为占位/示例） |
| 流 | `BlankInsertStreamPlugin` | **开发案例**：每隔 N 秒插入 M 秒空白（黑屏+静音+提示文字） |
| 流 | `CompositeStreamPlugin` | 组合流插件：聚合多个流插件的 trims/blanks，供应用层叠加能力 |

插件不携带 `base_url`，站点 → 插件组合关系在**应用层 `main.py`** 的 `STREAM_PIPELINES` 里自由编排：

```python
STREAM_PIPELINES = {
    "https://www.cupfox7.com": (
        CompositeStreamPlugin([CupfoxStreamPlugin(), BlankInsertStreamPlugin()]),
        [WatermarkFramePlugin(text="去广告"), ShuangmianTextFramePlugin()],
    ),
    "https://yhdm.one": (
        CompositeStreamPlugin([YhdmStreamPlugin(), BlankInsertStreamPlugin()]),
        [ShuangmianTextFramePlugin()],
    ),
    "https://www.qqll.cc": (
        CompositeStreamPlugin([QqllStreamPlugin(), BlankInsertStreamPlugin()]),
        [ShuangmianTextFramePlugin()],
    ),
}
```

调用方只需传 `base_url` 与源，经 `POST /api/stream/processed` 触发去广告流；新增/调整站点组合只改 `STREAM_PIPELINES` 一处。

### 自定义插件开发案例

`plugins.py` 内附两个可直接照抄的开发案例：

- **`ShuangmianTextFramePlugin`**（帧插件）：实现 `filters()` 返回一条 `drawtext` 规则，即可在画面上叠加「双面酱」文字。
- **`BlankInsertStreamPlugin`**（流插件）：覆盖 `blanks()` 返回 `BlankSegment`，实现「每隔 `interval` 秒插入 `duration` 秒空白（黑屏 + 静音 + 居中提示文字，默认「广告已跳过」）」。

> 以上两个开发案例已接入三个真实站点（cupfox / yhdm / qqll）的 `STREAM_PIPELINES`：
> `ShuangmianTextFramePlugin` 加入各站点的帧插件列表，`BlankInsertStreamPlugin` 经 `CompositeStreamPlugin` 与各站点裁剪插件叠加。

自定义流插件时，`trims()`（裁剪）与 `blanks()`（插入空白）是两种流级时间操作，可只实现其中一种；帧插件只需实现 `filters()`。

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/stream` | 创建流，body 见 `StreamRequest` |
| POST | `/api/stream/processed` | 按站点（`base_url`）内化规则建流，body 见 `ProcessedStreamRequest` |
| GET | `/api/stream` | 列出所有会话 |
| GET | `/api/stream/{sid}` | 查询会话状态 |
| DELETE | `/api/stream/{sid}` | 停止并清理会话 |
| GET | `/api/stream/{sid}/player` | 内嵌播放器页 |

HLS 分片由 `main.py` 的 `app.mount("/streams", StaticFiles(...))` 提供（`/streams/{sid}/index.m3u8`）。

### 请求体（StreamRequest）

```json
{
  "source_url": "https://example.com/movie/index.m3u8",
  "source_type": "m3u8",
  "headers": {"Referer": "https://example.com/"},
  "trims": [{"start": 60, "end": 90}],
  "filters": [{"name": "drawtext", "params": {"text": "去广告", "fontsize": 24}}]
}
```

- `source_url`：上游播放地址（m3u8/mp4），必填；
- `headers`：透传给 ffmpeg 的 `-headers`（防盗链 Referer 等）；
- `trims`：要删除的广告区间（秒）；`end` 省略表示删到结尾；
- `filters`：逐帧滤镜（预留），`drawtext` 已实现。

### 使用示例

```bash
# 启动服务（RTSP 依赖的 mediamtx 会自动拉起，无需手动启动）
python main.py

# 创建流（无裁剪）
curl -X POST "http://127.0.0.1:8000/api/stream" \
  -H "Content-Type: application/json" \
  -d '{"source_url":"https://example.com/movie/index.m3u8"}'

# 创建流（掐头 30 秒去广告）
curl -X POST "http://127.0.0.1:8000/api/stream" \
  -H "Content-Type: application/json" \
  -d '{"source_url":"https://example.com/movie/index.m3u8","trims":[{"start":0,"end":30}]}'

# 浏览器内嵌播放器
# http://127.0.0.1:8000/api/stream/{sid}/player

# 原生客户端拉 RTSP
# ffplay rtsp://127.0.0.1:8554/{sid}
```

## 配置项（环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `STREAM_FACTORY_FFMPEG_BIN` | `ffmpeg` | ffmpeg 路径 |
| `STREAM_FACTORY_HLS_ROOT` | `{项目根}/streams` | HLS 输出根目录 |
| `STREAM_FACTORY_HLS_TIME` | `2` | 分片时长（秒） |
| `STREAM_FACTORY_HLS_LIST_SIZE` | `0` | 播放列表长度（0 = 全部分片，适合点播） |
| `STREAM_FACTORY_RTSP_SERVER` | `rtsp://127.0.0.1:8554` | RTSP 推流目标 |
| `STREAM_FACTORY_RTSP_ENABLED` | `1` | 是否启用 RTSP 双输出 |
| `STREAM_FACTORY_MEDIAMTX_AUTOSTART` | `1` | 服务启动时是否自动拉起 mediamtx（RTSP 启用时生效） |
| `STREAM_FACTORY_MEDIAMTX_BIN` | `/mnt/4t/linux/huanjing/mediamtx/1.8.1/mediamtx` | mediamtx 可执行文件路径 |
| `STREAM_FACTORY_MEDIAMTX_CONFIG` | `{BIN 同级}/mediamtx.yml` | mediamtx 配置文件（自动拉起时显式传入） |
| `STREAM_FACTORY_MEDIAMTX_STARTUP_TIMEOUT` | `10` | 拉起后等待端口就绪超时（秒） |
| `STREAM_FACTORY_READY_TIMEOUT` | `30` | HLS 就绪探测超时（秒） |

## 注意事项

- 会话就绪判定以 **HLS 索引文件出现** 为准；HLS 是主输出，RTSP 是「尽力而为」的独立进程，mediamtx 不可用时只降级为纯 HLS，RTSP 推流失败不影响 HLS 输出。
- RTSP 无损 copy 依赖源为 H.264/AAC；H.265 等编码需走重编码（`filters` 或中间段裁剪会触发）。
- `streams/` 为运行时产物，建议加入 `.gitignore`。
- `main.py` 挂载 `api_router` 与 `/streams` 静态目录，应用退出时建议调用 `await stream_factory.shutdown()` 清理子进程。
