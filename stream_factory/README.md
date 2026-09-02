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
| `ffmpeg` / `ffprobe` | 拉流、裁剪、转码、推流、抽帧 | `apt install ffmpeg` / `brew install ffmpeg` |
| `mediamtx` | RTSP 服务器（接收 FFmpeg 推流并分发 RTSP/HLS；服务启动时自动拉起） | 单二进制，见 [mediamtx](https://github.com/bluenviron/mediamtx) |
| `tesseract` | OCR 违规词识别（URL 处理器 `OcrUrlHandler`，可选） | `apt install tesseract-ocr tesseract-ocr-chi-sim` / `dnf install tesseract tesseract-langpack-chi_sim` |

> 本机已装：`ffmpeg 7.1.5`、`mediamtx 1.8.1`（`/mnt/4t/linux/huanjing/mediamtx/1.8.1/mediamtx`）。
> `python main.py` 启动服务时会**自动拉起 mediamtx**（`MEDIAMTX_AUTOSTART=1` 时），无需手动启动；
> 只需 HLS、不要 RTSP 时，设 `STREAM_FACTORY_RTSP_ENABLED=0` 关闭推流（不拉起、无需 mediamtx）。
> OCR 违规词过滤需另装 tesseract 中文语言包（本机缺 `chi_sim`，Fedora：`sudo dnf install tesseract-langpack-chi_sim`）；不装则 OCR 一律放行、不影响转流。

## 目录结构

```
stream_factory/
├── __init__.py          # 导出单例、路由、HLS_ROOT、规则模型、插件基类与内置插件
├── config.py            # FFmpeg 路径、HLS 目录、RTSP 地址等（环境变量可覆盖）
├── rules.py             # 规则模型：StreamSource / TrimSegment / FilterRule / StreamRequest
├── frame_plugins/       # 帧模块（子模块）：FramePlugin 基类 + 水印/文字帧插件
│   ├── __init__.py      # 导出 FramePlugin / WatermarkFramePlugin / ShuangmianTextFramePlugin
│   ├── base.py          # FramePlugin 抽象基类
│   ├── watermark.py     # WatermarkFramePlugin（去水印/打标，示例）
│   └── shuangmian_text.py  # ShuangmianTextFramePlugin（「双面酱」文字水印）
├── stream_plugins/      # 流模块（子模块）：StreamPlugin 基类 + 各流插件
│   ├── __init__.py      # 导出 StreamPlugin + 5 个流插件
│   ├── base.py          # StreamPlugin 抽象基类
│   ├── passthrough.py   # PassthroughStreamPlugin（透传）
│   ├── cupfox.py        # CupfoxStreamPlugin（站点裁剪）
│   ├── qqll.py          # QqllStreamPlugin（站点裁剪）
│   ├── blank_insert.py  # BlankInsertStreamPlugin（空白插入案例）
│   ├── composite.py     # CompositeStreamPlugin（组合流插件）
│   └── _deprecated/     # 废弃流插件区（过期源收纳，不再导出）
├── url_handlers/        # URL 处理器（子模块）：UrlHandler 基类 + OCR 违规词处理器
│   ├── __init__.py      # 导出 UrlHandler / OcrUrlHandler
│   ├── base.py          # UrlHandler 抽象基类（分片级内容处理器）
│   └── ocr.py           # OcrUrlHandler（抽帧 + OCR 识别违规词，命中拉黑分片）
├── blacklist.py         # 黑名单持久化：命中违规的 ts 源 URL 记录 + TTL
├── pipeline.py          # FFmpeg 命令行构建器（规则 → ffmpeg 参数）
├── video_cache.py       # 源视频缓存：长驻连接池 + m3u8/mp4 下载 + 并发去重 + 分片过滤
├── process_cache.py     # 处理结果缓存：内容寻址 sid + TTL 命中复用（去广告后 HLS）
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

## 源视频缓存与连接池复用

`video_cache.py` 在 ffmpeg 拉流之前先做一次**源视频缓存**，避免同一部影片被多个会话/客户端重复拉取上游：

- **保留 HLS 结构**：m3u8 源缓存播放列表、AES-128 解密 key、fMP4 init 分片与媒体分片到本地，播放列表 URI 重写为本地相对引用；mp4 直链缓存为 `source.mp4`。
- **长驻连接池**：模块级 `httpx.AsyncClient` 懒加载复用，所有会话共享同一 TCP 连接池，减少重复建连。
- **并发去重**：同一 `source_url` 同时仅一个下载任务（per-key 锁 + 双重检查），其他会话 await 后复用结果。
- **TTL 过期**：缓存带 `meta.json`（url / source_type / ts / expires），过期后惰性重新下载。

缓存目录：`{VIDEO_CACHE_ROOT}/{md5(source_url)}/`（`index.m3u8` + `segment_*.ts` + `key_*.key` + `init_*.mp4`，或 `source.mp4` + `meta.json`）。

`factory.create_stream` 会自动先 `ensure_source(...)`；命中缓存后 ffmpeg 改读本地文件（`headers` 清空，本地读无需防盗链）。

**降级场景**（保持原直连行为）：Master playlist 嵌套过深、下载失败、空 URL / 本地路径 —— 均返回原 `url`，ffmpeg 照旧直连拉流。

> AES-128 加密分片不再降级：key / 分片一并缓存、URI 重写为本地引用，ffmpeg 通过 `-allowed_extensions ALL`（见 `pipeline.py`）放开 `.key` 扩展名限制后即可完全离线解密。

## 处理结果缓存（去广告后 HLS 复用）

`process_cache.py` 在**源视频缓存之上**再加一层「处理结果缓存」：当「源视频 url + 帧滤镜（`filters`）+ 流裁剪/空白（`trims`/`blanks`）」都没变时，去广告转流后的 HLS 目录直接复用，跳过 ffmpeg 重编码，减少 CPU 处理压力。

- **内容寻址 sid**：`sid = md5(规范化 StreamRequest)[:16]`，同一「源 + 规则」→ 同一 `sid` → 同一 HLS 目录，天然复用；任一变化 → `sid` 变化 → 重新转流。
- **复用流程**：`factory.create_stream` 先 `ensure_source`（源缓存），再按内容寻址 `sid`，命中处理缓存则返回轻量会话（无 ffmpeg 进程、仅 HLS、无 RTSP）；未命中才起 ffmpeg 转流，转流**正常结束**后写 `meta.json` 登记为缓存。
- **TTL 过期**：`meta.json` 记录 `expires`（默认 7 天，`STREAM_FACTORY_PROCESS_CACHE_TTL` 可配），过期后下次同内容请求重新转流覆盖（惰性清理）。
- **stop 语义**：`stop` 只停止转流进程；**完整缓存保留复用**，仅清理转流中的「半成品」目录。
- **RTSP 说明**：RTSP 是实时推流、无法缓存复用，命中处理缓存时仅提供 HLS（不提供 RTSP）。

处理缓存目录即 HLS 输出目录 `cache/streams/{sid}/`（`HLS_ROOT`），命中时 `/streams/{sid}/index.m3u8` 直接可播。

## URL 处理器与黑名单（OCR 违规词过滤）

`stream_factory` 提供第三类插件 **`UrlHandler`（URL 处理器）**，在**源视频缓存阶段**对每个已下载的 ts 分片做**内容检测**，识别到违规内容（如「澳门新葡京」等赌博广告文字）时把该分片**拉入黑名单、跳过推流**：

- **挂载点**：`video_cache._cache_m3u8_text()` 已逐分片下载 + URI 重写；URL 处理器在此对本地分片做检测，命中则从重写后的 `index.m3u8` 移除该分片行，ffmpeg 读本地 `index.m3u8`（已无该分片）自然跳过推流。整个过程离线完成，**不动 pipeline / session / ffmpeg 命令**。
- **内置实现 `OcrUrlHandler`**：对每个 ts 分片用 ffmpeg 抽中间若干帧为 PNG，再用 `tesseract` 识别文字（`-l chi_sim --psm 6`），命中违规词表任一即拉黑。
- **失败容错**：抽帧 / tesseract 报错、语言包缺失 → 放行（返回 `False`），**宁可漏报不可误杀**，绝不阻断转流。
- **黑名单持久化**（`blacklist.py`）：命中违规的 ts 源 URL 记入 `{BLACKLIST_ROOT}/{md5(url)}.json`（带 TTL，默认 7 天），后续同一 ts **既不下载也不 OCR**、直接跳过。
- **正确性**：URL 处理器会改变输出，故其 `fingerprint()` 纳入 `process_cache.cache_key` 的 `extra` 维度——处理器配置变化 → 指纹变化 → `sid` 变化 → 重新转流，避免错误复用旧 HLS。
- **范围**：仅 m3u8 多分片场景生效（mp4 直链无分片、不适用）；`POST /api/stream`（无 `base_url`）不传 URL 处理器，默认不过滤。

`OcrUrlHandler` 已接入两个站点（cupfox / qqll）的 `STREAM_PIPELINES`。

## 流/帧/URL 插件与站点组合

去广告与内容过滤规则是**系统内部知识**，封装为三类可复用、不绑定站点的插件：

- **`FramePlugin`（帧插件）**：逐帧处理单元，`filters()` 产出 `FilterRule` 列表（如去水印 `drawtext`）。
- **`StreamPlugin`（流插件）**：流级裁剪策略，`trims(source)` 产出 `TrimSegment` 列表，`build_request(source, frame_plugins)` 合成 `StreamRequest`。
- **`UrlHandler`（URL 处理器）**：分片级内容处理器，`handle(segment_url, segment_path)` 对已下载的单个 ts 分片做内容检测（如 OCR 识别违规词），返回 `True` 表示拉黑该分片（从重写的 `index.m3u8` 移除，跳过推流）。

内置插件（`stream_factory/frame_plugins/` 与 `stream_factory/stream_plugins/`，每个具体插件一个文件）：

| 类型 | 插件 | 说明 |
| --- | --- | --- |
| 帧 | `WatermarkFramePlugin` | 去水印/打标（`drawtext`，示例） |
| 帧 | `ShuangmianTextFramePlugin` | **开发案例**：叠加「双面酱」文字水印 |
| 流 | `PassthroughStreamPlugin` | 透传，不裁剪 |
| URL | `OcrUrlHandler` | **开发案例**：抽帧 OCR 识别违规词（如「澳门新葡京」），命中拉黑分片跳过推流 |
| 流 | `CupfoxStreamPlugin` / `QqllStreamPlugin` | 各站点裁剪策略（区间为占位/示例） |
| 流 | `BlankInsertStreamPlugin` | **开发案例**：每隔 N 秒插入 M 秒空白（黑屏+静音+提示文字） |
| 流 | `CompositeStreamPlugin` | 组合流插件：聚合多个流插件的 trims/blanks，供应用层叠加能力 |

插件不携带 `base_url`，站点 → 插件组合关系在**应用层 `main.py`** 的 `STREAM_PIPELINES` 里自由编排：

```python
STREAM_PIPELINES = {
    "https://www.cupfox7.com": (
        CompositeStreamPlugin([CupfoxStreamPlugin(), BlankInsertStreamPlugin()]),
        [WatermarkFramePlugin(text="去广告"), ShuangmianTextFramePlugin()],
        [OcrUrlHandler()],
    ),
    "https://www.qqll.cc": (
        CompositeStreamPlugin([QqllStreamPlugin(), BlankInsertStreamPlugin()]),
        [ShuangmianTextFramePlugin()],
        [OcrUrlHandler()],
    ),
}
```

调用方只需传 `base_url` 与源，经 `POST /api/stream/processed` 触发去广告流；新增/调整站点组合只改 `STREAM_PIPELINES` 一处。

### 自定义插件开发案例

两个开发案例分别位于 `frame_plugins/shuangmian_text.py` 与 `stream_plugins/blank_insert.py`，可直接照抄：

- **`ShuangmianTextFramePlugin`**（帧插件）：实现 `filters()` 返回一条 `drawtext` 规则，即可在画面上叠加「双面酱」文字。
- **`BlankInsertStreamPlugin`**（流插件）：覆盖 `blanks()` 返回 `BlankSegment`，实现「每隔 `interval` 秒插入 `duration` 秒空白（黑屏 + 静音 + 居中提示文字，默认「广告已跳过」）」。

> 以上两个开发案例已接入两个真实站点（cupfox / qqll）的 `STREAM_PIPELINES`：
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
| `STREAM_FACTORY_CACHE_ROOT` | `{项目根}/cache` | 统一缓存根目录（HLS 输出 / 源视频缓存均归其下） |
| `STREAM_FACTORY_HLS_ROOT` | `{项目根}/cache/streams` | HLS 输出根目录（默认位于统一缓存根下） |
| `STREAM_FACTORY_HLS_TIME` | `2` | 分片时长（秒） |
| `STREAM_FACTORY_HLS_LIST_SIZE` | `0` | 播放列表长度（0 = 全部分片，适合点播） |
| `STREAM_FACTORY_RTSP_SERVER` | `rtsp://127.0.0.1:8554` | RTSP 推流目标 |
| `STREAM_FACTORY_RTSP_ENABLED` | `1` | 是否启用 RTSP 双输出 |
| `STREAM_FACTORY_MEDIAMTX_AUTOSTART` | `1` | 服务启动时是否自动拉起 mediamtx（RTSP 启用时生效） |
| `STREAM_FACTORY_MEDIAMTX_BIN` | `/mnt/4t/linux/huanjing/mediamtx/1.8.1/mediamtx` | mediamtx 可执行文件路径 |
| `STREAM_FACTORY_MEDIAMTX_CONFIG` | `{BIN 同级}/mediamtx.yml` | mediamtx 配置文件（自动拉起时显式传入） |
| `STREAM_FACTORY_MEDIAMTX_STARTUP_TIMEOUT` | `10` | 拉起后等待端口就绪超时（秒） |
| `STREAM_FACTORY_READY_TIMEOUT` | `30` | HLS 就绪探测超时（秒） |
| `STREAM_FACTORY_VIDEO_CACHE_ROOT` | `{项目根}/cache/video_cache` | 源视频缓存根目录（按 source_url 哈希建子目录，默认位于统一缓存根下） |
| `STREAM_FACTORY_VIDEO_CACHE_TTL` | `86400` | 源视频缓存过期时间（秒），过期后重新下载 |
| `STREAM_FACTORY_VIDEO_CACHE_CONCURRENCY` | `5` | m3u8 分片并发下载数 |
| `STREAM_FACTORY_BLACKLIST_ROOT` | `{项目根}/cache/blacklist` | 黑名单目录（按 ts 源 URL 哈希建文件） |
| `STREAM_FACTORY_BLACKLIST_TTL` | `604800` | 黑名单有效期（秒），命中违规的 ts 在此期间直接跳过 |
| `STREAM_FACTORY_OCR_TESSERACT_BIN` | `tesseract` | tesseract 可执行文件路径 |
| `STREAM_FACTORY_OCR_LANG` | `chi_sim` | OCR 识别语言（中文简体，需安装对应语言包） |
| `STREAM_FACTORY_OCR_FRAME_COUNT` | `1` | 每个 ts 分片抽帧数（默认抽中间 1 帧） |
| `STREAM_FACTORY_OCR_BLOCKWORDS` | `澳门新葡京,新葡京` | 违规词表（逗号分隔），命中任一即拉黑该 ts |
| `STREAM_FACTORY_OCR_CONCURRENCY` | `1` | OCR 并发数（tesseract 较重，默认低并发） |

## 注意事项

- 会话就绪判定以 **HLS 索引文件出现** 为准；HLS 是主输出，RTSP 是「尽力而为」的独立进程，mediamtx 不可用时只降级为纯 HLS，RTSP 推流失败不影响 HLS 输出。
- RTSP 无损 copy 依赖源为 H.264/AAC；H.265 等编码需走重编码（`filters` 或中间段裁剪会触发）。
- 所有缓存产物统一放 `cache/`（HLS 输出在 `cache/streams/`、源视频缓存在 `cache/video_cache/`），`cache/` 建议加入 `.gitignore`。
- `main.py` 挂载 `api_router` 与 `/streams` 静态目录，应用退出时建议调用 `await stream_factory.shutdown()` 清理子进程。
