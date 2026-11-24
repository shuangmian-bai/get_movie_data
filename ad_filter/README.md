# ad_filter —— 插件化去广告处理模块

## 用途

输入上游 m3u8 播放地址，**逐 ts 分片抽帧检测广告**，按广告类型分别处理，
最终产出一份**处理后的 m3u8** 返回前端播放：

- **全广告**（整段广告，如片头插播）→ **丢弃**该分片（从 m3u8 移除，播放跳过）；
- **水印广告**（画面上有广告角标/水印）→ **去水印**并把改后的分片**落盘本地**；
- **正常分片** → 不落盘，经**本服务代理**转发上游（带防盗链头）。

本地只保存「修改了画面的分片」（去水印后的），省磁盘；未修改分片由本服务代理，
避免前端直拉源站被防盗链（Referer/Cookie）拦截。

## 依赖（纯 Python，pip 安装）

不依赖系统 ffmpeg / tesseract / mediamtx，全部由 Python 库完成：

| 库 | 用途 |
| --- | --- |
| `av`（PyAV） | 读/写 .ts 分片、视频帧解码/编码（自带 ffmpeg 库） |
| `opencv-python-headless` | 抽帧（VideoCapture）、inpaint 去水印 |
| `rapidocr_onnxruntime` | 中文 OCR 文字识别（onnxruntime） |
| `httpx` | m3u8 / 分片下载 + 代理转发 |

## 核心链路（渐进式：秒回 + 分片按需处理）

```
POST /api/ad_filter/process { m3u8_url, headers?, base_url? }
  └─ 下载 m3u8（Master 选最高带宽 variant；相对/绝对分片 URI 统一 urljoin 解析）
  └─ 生成分片清单 → 写 index.m3u8（分片指向 segment/{name}）→ 写 meta.json
  └─ 立即返回 { sid, playlist_url, stats.total }        ← 不处理任何分片，起播不再等整片

GET /ad_filter/{sid}/segment/{name}                     ← 播放器按需拉取
  ├─ 已缓存 + full      → 返回极短空白 ts（无缝跳过）
  ├─ 已缓存 + watermark → 返回缓存中的去水印文件
  ├─ 已缓存 + none      → 代理透传上游
  └─ 未处理 → 下载 → 检测器链 detect() → 处理 → 写分片缓存 → 返回
        ├─ full      → 记录 full（后续返回空白 ts）
        ├─ watermark → remover.remove() 落盘分片缓存
        └─ none      → 记录 none（代理）
  └─ 处理失败 → 降级代理，不阻断播放

分片缓存：{OUTPUT_ROOT}/segments/md5(URL+检测器指纹)/（result.json + 去水印文件）
预取：播放某分片时后台预取其后 PREFETCH_LOOKAHEAD 个（并发 PREFETCH_CONCURRENCY）
```

前端：拿到 `playlist_url` 直接 `playHls`，播放器拉哪个分片就处理哪个。

## 目录结构

```
ad_filter/
├── __init__.py          # 导出 api_router、engine、插件基类与内置插件
├── config.py            # 环境变量配置（前缀 AD_FILTER_*）
├── models.py            # Box / DetectionResult / ProcessRequest / ProcessResult
├── m3u8.py              # m3u8 解析（Master/Media、相对/绝对 URI 统一解析）
├── _compat.py           # Python 3.8 兼容工具（asyncio.to_thread 替代）
├── DESIGN.md            # 按需去广告调度设计（快速起播 / seek 优先 / 分片级复用，待实现）
├── detector/            # 检测器插件子包
│   ├── base.py          # Detector 抽象基类（detect() + fingerprint()）
│   ├── ocr.py           # OcrDetector（opencv 抽帧 + rapidocr + 分类 + 水印坐标）
│   └── top_banner.py    # TopBannerDetector（顶部条带 OCR，弹幕/跑马灯/静态条）
├── remover/             # 去水印插件子包
│   ├── base.py          # Remover 抽象基类（remove()）
│   ├── delogo.py        # DelogoRemover（opencv inpaint + PyAV 写回 .ts）
│   └── top_banner.py    # TopBannerRemover（顶部条带 crop/blur/black）
├── proxy.py             # 上游 TS 代理（httpx 流式转发 + 防盗链头）
├── engine.py            # 编排引擎（process 主流程 + sid 幂等复用）
└── api.py               # FastAPI 路由
```

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/ad_filter/process` | 解析 m3u8 并秒回播放列表地址（分片按需处理），body `{ m3u8_url, headers?, base_url? }` |
| GET | `/ad_filter/{sid}/index.m3u8` | 处理后播放列表（分片指向 `segment/{name}`） |
| GET | `/ad_filter/{sid}/segment/{name}` | 动态分片端点：按需检测/去水印/代理，结果缓存 |
| GET | `/ad_filter/{sid}/file/{name}` | 旧会话的本地去水印分片（兼容保留） |
| GET | `/ad_filter/{sid}/proxy/{name}` | 旧会话的代理分片（兼容保留） |

## 插件化

- **`Detector`（检测器）**：`detect(segment_path, segment_url) -> DetectionResult`，
  判定 `none` / `full` / `watermark` 并给出水印区域。内置 `OcrDetector`（全帧 OCR）
  与 `TopBannerDetector`（顶部条带 OCR，专治弹幕/跑马灯/静态赞助条）。
- **`Remover`（去水印器）**：`remove(segment_path, boxes, out_path) -> str`，
  输出处理后的分片。内置 `DelogoRemover`（inpaint 去水印）与 `TopBannerRemover`
  （顶部条带 crop/blur/black）。

不同资源有不同的广告形式与内容，站点 → 插件组合关系在应用层 `main.py` 的
`AD_FILTER_PIPELINES` 里编排（`base_url → (检测器列表, 去水印器)`），并经
`ad_filter.api.set_pipeline_getter(...)` 注入。

### OCR 检测器分类规则（启发式，阈值可配）

- opencv 均匀抽 `AD_FILTER_OCR_FRAME_COUNT` 帧，rapidocr 识别文字（带 bbox）；
- 命中违规词表（`AD_FILTER_OCR_BLOCKWORDS`）→ 收集命中词的 bbox；
- 命中帧占比 ≥ `AD_FILTER_FULL_FRAME_RATIO` 且命中区域面积占比 ≥
  `AD_FILTER_FULL_AREA_RATIO` → **full**；
- 否则命中 → **watermark**（bbox 合并重叠 + 向外扩展 `AD_FILTER_WATERMARK_MARGIN`）；
- 未命中 / 抽帧或 OCR 失败 → **none**（放行，宁可漏报不可误杀）。

### 去水印说明

`DelogoRemover` 用 opencv `inpaint`（Telea 算法）把水印区域用周边像素插值填补，
再用 PyAV 重编码写回 .ts（视频重编码 + 音频 copy）。inpaint 是通用「去水印」近似
（非还原原画），作为内置实现，可替换为其它去水印算法。

### 顶部条幅广告（弹幕/跑马灯/静态赞助条）

以优质资源库（1080zyk6.com）为例：广告文字固定在画面顶部条带内，有静态居中
（「本节目由 XX 独家赞助播出」）与从右向左滚动两种。组合为
`TopBannerDetector` + `TopBannerRemover`：

1. 检测器只在顶部条带（默认画面高度 12%）内 OCR，命中违规词即判 `watermark`；
2. 条带高度取命中文字最低点 + 边距（跑马灯只改 x 不改 y），返回**整条**作为处理区域；
3. 去除器默认 `inpaint`：用周边内容修复**文字像素**，画面完整、不裁剪不模糊；
   也可配置 `crop`（裁切）、`blur`（模糊）、`black`（纯色填充）。

> 去除器实现约束（实测 av 12.3.0）：同一容器 `decode()` 后再 `demux()` 会在关闭时段错误，
> 因此视频/音频用两个输入容器；先写完视频包再写音频包会让 MPEG-TS 时间戳乱序、
> 产物不可解码，因此音视频包按秒级 dts 交错 mux。

## 配置项（环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AD_FILTER_OUTPUT_ROOT` | `{项目根}/cache/ad_filter` | 处理结果根目录 |
| `AD_FILTER_SEGMENT_CONCURRENCY` | `4` | 分片「下载+检测+处理」并发数 |
| `AD_FILTER_INPAINT_RADIUS` | `3` | opencv inpaint 邻域半径 |
| `AD_FILTER_OUTPUT_CRF` / `AD_FILTER_OUTPUT_PRESET` | `23` / `veryfast` | 去水印输出编码参数 |
| `AD_FILTER_OCR_BLOCKWORDS` | `澳门新葡京,新葡京,博彩` | 违规词表（逗号分隔） |
| `AD_FILTER_OCR_FRAME_COUNT` | `3` | 每个 ts 抽帧数 |
| `AD_FILTER_OCR_SCORE_THRESHOLD` | `0.5` | OCR 置信度阈值 |
| `AD_FILTER_OCR_CONCURRENCY` | `2` | OCR 并发数 |
| `AD_FILTER_FULL_FRAME_RATIO` | `0.6` | 全广告判定：命中帧占比阈值 |
| `AD_FILTER_FULL_AREA_RATIO` | `0.3` | 全广告判定：命中区域面积占比阈值 |
| `AD_FILTER_WATERMARK_MARGIN` | `10` | 水印区域向外扩展边距（像素） |
| `AD_FILTER_TOP_BANNER_RATIO` | `0.12` | 顶部条带高度占画面比例 |
| `AD_FILTER_TOP_BANNER_FRAME_COUNT` | `3` | 顶部条带抽帧数 |
| `AD_FILTER_TOP_BANNER_HIT_RATIO` | `0.5` | 顶部条幅命中帧占比阈值 |
| `AD_FILTER_TOP_BANNER_BLOCKWORDS` | `澳门,威尼斯人,116.am,新葡京,博彩,娱乐城,独家赞助` | 顶部条幅违规词表 |
| `AD_FILTER_TOP_BANNER_REMOVE_MODE` | `inpaint` | 去除模式：inpaint（画面完整）/ crop / blur / black |
| `AD_FILTER_TOP_BANNER_BLUR_STRENGTH` | `0.5` | blur 模式核大小系数（×条带高度） |
| `AD_FILTER_TOP_BANNER_INPAINT_RADIUS` | `5` | inpaint 修复邻域半径（opencv Telea） |
| `AD_FILTER_TOP_BANNER_TEXT_DILATE` | `9` | inpaint 文字掩码膨胀核（覆盖白字黑描边） |
| `AD_FILTER_HTTP_TIMEOUT` / `AD_FILTER_HTTP_TRUST_ENV` | `15` / `1` | HTTP 超时 / 是否自动检测系统代理 |
| `AD_FILTER_HTTP_PROXY` | `""`（空） | 显式代理地址（http/https/socks5），空则自动检测系统代理 |
| `AD_FILTER_PREFETCH_LOOKAHEAD` | `3` | 预取窗口：当前分片之后提前处理 N 个 |
| `AD_FILTER_PREFETCH_CONCURRENCY` | `2` | 预取并发数 |
| `AD_FILTER_ADVANCE_CONCURRENCY` | `3` | 后台推进并行度 |
| `AD_FILTER_REDIS_URL` | `""`（空） | 会话心跳/播放位置的 Redis 地址（空则用内存态） |

## 已知限制

- **加密分片（AES-128）**：opencv/PyAV 无法直接解码加密分片，抽帧失败会自动放行
  （走代理透传，不丢内容、不误杀）；暂不做解密后检测。
- **丢弃分片的时间轴**：移除分片会使相邻段之间出现时间跳变，hls.js 通常能容忍。
- **代理带宽**：正常分片经本服务转发，占服务出口带宽（这是省盘的取舍）。
- **rapidocr 首次加载**：模型初始化较慢（首次请求会稍慢），之后复用；识别失败一律放行。
