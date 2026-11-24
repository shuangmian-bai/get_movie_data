# contribute —— 用户投稿模块

## 用途

收集用户投稿的两类信息，供维护者扩展数据源与去广告插件：

1. **数据源投稿**：新影视站点的接入线索（站点名、根地址、说明、样本链接）；
2. **广告信息投稿**：已接入站点上广告的类型、位置、样本时间点与广告文字，
   便于扩展检测器词表、条带区域与去除策略。

投稿只落盘为 `pending`，**不直接生效**——是否接入由人工审核后转成插件配置。

## 对外接口

| 接口 | 类型 | 说明 |
|---|---|---|
| `POST /api/contribute/source` | REST | 投稿数据源，body 见 `SourceContribution` |
| `POST /api/contribute/ad` | REST | 投稿广告信息，body 见 `AdContribution` |
| `GET /api/contribute/list?type=source\|ad&limit=N` | REST | 查看最近投稿（倒序） |

## 依赖

- 无外部依赖；仅 Pydantic（项目已有）与标准库。
- 不 import `media_source` / `ad_filter`，投稿与业务模块解耦，由应用层挂载。

## 存储

- 目录：`{项目根}/cache/contributions/`（可用 `CONTRIBUTE_DIR` 覆盖）；
- 文件：`sources.jsonl` / `ads.jsonl`，每行一条 JSON，追加写；
- 字段：`id`（12 位）、`type`、`status`（pending/approved/rejected）、`created_at`、
  业务字段（见 `models.py`）。

## 副作用

- 写文件（追加 + fsync）；读取时忽略损坏行（截断残留容错）。

## 限制

- 单条投稿不校验站点是否真实可访问，只做格式校验（URL 前缀、长度、枚举值）；
- 投稿频率无服务端限流（部署在反代层或后续加），垃圾投稿由人工审核过滤。

## 改动校验点

- 改字段 → 同步 `models.py`、前端 `features/contribute/types.ts` 与本文件；
- 改接口路径 → 同步 `main.py` 挂载、前端 `api/contribute.ts` 与本文件；
- 接入审核流程时，`store.update_status` 是现成入口。
