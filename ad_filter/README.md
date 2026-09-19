# ad_filter —— 插件化去广告处理模块

## 用途

输入上游 m3u8 播放地址，**逐 ts 分片抽帧检测广告**，按广告类型分别处理，
最终产出一份**处理后的 m3u8** 返回前端播放：

- **全广告**（整段广告，如片头插播）→ **丢弃**该分片（从 m3u8 移除，播放跳过）；
- **水印广告**（画面上有广告角标/水印）→ **去水印**并把改后的分片**落盘本地**；
- **正常分片** → 不落盘，经**本服务代理**转发上游（带防盗链头）。

本地只保存「修改了画面的分片」（去水印后的），省磁盘；未修改分片由本服务代理，
避免前端直拉源站被防盗链（Referer/Cookie）拦截。

## 与 stream_factory 的关系

本模块**独立于 `stream_factory`**，不 import 它，也不 import `media_source`。
只依赖系统二进制 `ffmpeg` / `ffprobe`（抽帧 / delogo 去水印）、可选的
`tesseract`（OCR 检测；缺失时检测器一律放行，不影响代理透传）与 `httpx`。

## 核心链路

```
POST /api/ad_filter/process { m3u8_url, headers?, base_url? }
  └─ 下载 m3u8（Master 选最高带宽 variant；相对/绝对分片 URI 统一 urljoin 解析）
  └─ 逐分片（并发）：下载到临时目录 → 检测器链 detect()
        ├─ full      → 丢弃（m3u8 不输出该块）
        ├─ watermark → remover.remove() 落盘 {sid}/segment_XXXX.ts，引用 file/…
        └─ none      → 删临时文件，引用 proxy/…（本服务代理）
  └─ 重写 index.m3u8 → 写 meta.json（分片名→上游URL 映射）→ 返回 { sid, playlist_url, stats }
前端：playHls(playlist_url)
```

## 目录结构

```
ad_filter/
├── __init__.py          # 导出 api_router、engine、插件基类与内置插件
├── config.py            # 环境变量配置（前缀 AD_FILTER_*）
├── models.py            # Box / DetectionResult / ProcessRequest / ProcessResult
├── m3u8.py              # m3u8 解析（Master/Media、相对/绝对 URI 统一解析）
├── detector/            # 检测器插件子包
│   ├── base.py          # Detector 抽象基类（detect() + fingerprint()）
│   └── ocr.py           # OcrDetector（抽帧 + tesseract + 分类 + 水印坐标）
├── remover/             # 去水印插件子包
│   ├── base.py          # Remover 抽象基类（remove()）
│   └── delogo.py        # DelogoRemover（ffmpeg delogo 滤镜）
├── proxy.py             # 上游 TS 代理（httpx 流式转发 + 防盗链头）
├── engine.py            # 编排引擎（process 主流程 + sid 幂等复用）
└── api.py               # FastAPI 路由
```

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/ad_filter/process` | 去广告处理，body `{ m3u8_url, headers?, base_url? }` |
| GET | `/ad_filter/{sid}/index.m3u8` | 处理后播放列表 |
| GET | `/ad_filter/{sid}/file/{name}` | 本地去水印后的分片 |
| GET | `/ad_filter/{sid}/proxy/{name}` | 代理上游分片 |

## 插件化

- **`Detector`（检测器）**：`detect(segment_path, segment_url) -> DetectionResult`，
  判定 `none` / `full` / `watermark` 并给出水印区域。内置 `OcrDetector`。
- **`Remover`（去水印器）**：`remove(segment_path, boxes, out_path) -> str`，
  输出处理后的分片。内置 `DelogoRemover`。

不同资源有不同的广告形式与内容，站点 → 插件组合关系在应用层 `main.py` 的
`AD_FILTER_PIPELINES` 里编排（`base_url → (检测器列表, 去水印器)`），并经
`ad_filter.api.set_pipeline_getter(...)` 注入。

### OCR 检测器分类规则（启发式，阈值可配）

- 均匀抽 `AD_FILTER_OCR_FRAME_COUNT` 帧，tesseract TSV 识别文字；
- 命中违规词表（`AD_FILTER_OCR_BLOCKWORDS`）→ 收集命中词的 bbox；
- 命中帧占比 ≥ `AD_FILTER_FULL_FRAME_RATIO` 且命中区域面积占比 ≥
  `AD_FILTER_FULL_AREA_RATIO` → **full**；
- 否则命中 → **watermark**（bbox 合并重叠 + 向外扩展 `AD_FILTER_WATERMARK_MARGIN`）；
- 未命中 / 抽帧或 OCR 失败 → **none**（放行，宁可漏报不可误杀）。

### delogo 去水印说明

`DelogoRemover` 用 ffmpeg `delogo` 滤镜把水印区域用周边像素插值模糊覆盖，
输出 `libx264` 重编码后的 ts。delogo 是通用「去水印」近似（非还原原画），
作为内置实现，可替换为其它去水印算法。

## 配置项（环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AD_FILTER_OUTPUT_ROOT` | `{项目根}/cache/ad_filter` | 处理结果根目录 |
| `AD_FILTER_FFMPEG_BIN` / `AD_FILTER_FFPROBE_BIN` | `ffmpeg` / `ffprobe` | 系统二进制路径 |
| `AD_FILTER_SEGMENT_CONCURRENCY` | `4` | 分片「下载+检测+处理」并发数 |
| `AD_FILTER_OCR_TESSERACT_BIN` | `tesseract` | tesseract 路径 |
| `AD_FILTER_OCR_LANG` | `chi_sim` | OCR 语言（需装语言包） |
| `AD_FILTER_OCR_BLOCKWORDS` | `澳门新葡京,新葡京,博彩` | 违规词表（逗号分隔） |
| `AD_FILTER_OCR_FRAME_COUNT` | `3` | 每个 ts 抽帧数 |
| `AD_FILTER_OCR_CONCURRENCY` | `2` | OCR 并发数 |
| `AD_FILTER_FULL_FRAME_RATIO` | `0.6` | 全广告判定：命中帧占比阈值 |
| `AD_FILTER_FULL_AREA_RATIO` | `0.3` | 全广告判定：命中区域面积占比阈值 |
| `AD_FILTER_WATERMARK_MARGIN` | `10` | 水印区域向外扩展边距（像素） |
| `AD_FILTER_HTTP_TIMEOUT` / `AD_FILTER_HTTP_TRUST_ENV` | `15` / `1` | HTTP 超时 / 是否信任代理 |

## 已知限制

- **加密分片（AES-128）**：检测器抽帧依赖明文分片，加密分片抽帧失败会自动放行
  （走代理透传，不丢内容、不误杀）；暂不做解密后检测。
- **丢弃分片的时间轴**：移除分片会使相邻段之间出现时间跳变，hls.js 通常能容忍。
- **代理带宽**：正常分片经本服务转发，占服务出口带宽（这是省盘的取舍）。
