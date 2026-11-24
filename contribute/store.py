"""投稿存储：MySQL 主存 + JSONL 降级

投稿属于运行时数据。优先写 MySQL（表 ``contributions``，payload 列存完整 JSON，
便于按类型/状态查询与审核）；MySQL 不可用（未配置或连接失败）时自动降级为
JSONL 追加写（``cache/contributions/*.jsonl``），保证投稿不丢、服务不阻断。

配置（环境变量，未配置即走 JSONL）：
    CONTRIBUTE_DB_HOST     默认 127.0.0.1
    CONTRIBUTE_DB_PORT     默认 3306
    CONTRIBUTE_DB_USER     用户名（未配置则禁用 MySQL）
    CONTRIBUTE_DB_PASSWORD 密码
    CONTRIBUTE_DB_NAME     库名，默认 movie
"""
import json
import logging
import os
import threading
import time
import uuid
from pathlib import Path
from typing import Dict, List, Optional

logger = logging.getLogger("contribute.store")

# 项目根目录（contribute 的上一级）
_PROJECT_ROOT = Path(__file__).resolve().parent.parent

# JSONL 降级目录（可用环境变量覆盖）
CONTRIBUTION_DIR: str = os.getenv(
    "CONTRIBUTE_DIR", str(_PROJECT_ROOT / "cache" / "contributions")
)

# MySQL 配置
_DB_HOST = os.getenv("CONTRIBUTE_DB_HOST", "127.0.0.1")
_DB_PORT = int(os.getenv("CONTRIBUTE_DB_PORT", "3306"))
_DB_USER = os.getenv("CONTRIBUTE_DB_USER", "")
_DB_PASSWORD = os.getenv("CONTRIBUTE_DB_PASSWORD", "")
_DB_NAME = os.getenv("CONTRIBUTE_DB_NAME", "movie")

_write_lock = threading.Lock()

_FILE_BY_TYPE: Dict[str, str] = {
    "source": "sources.jsonl",
    "ad": "ads.jsonl",
}

_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS contributions (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    name VARCHAR(255) DEFAULT NULL,
    base_url VARCHAR(512) DEFAULT NULL,
    payload JSON NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_type_status (type, status),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""


def _db_enabled() -> bool:
    return bool(_DB_USER)


def _connect():
    """建立 MySQL 连接（自动建表）。调用方负责 close。"""
    import pymysql

    conn = pymysql.connect(
        host=_DB_HOST,
        port=_DB_PORT,
        user=_DB_USER,
        password=_DB_PASSWORD,
        database=_DB_NAME,
        charset="utf8mb4",
        autocommit=True,
        connect_timeout=5,
        read_timeout=10,
        write_timeout=10,
    )
    with conn.cursor() as cur:
        cur.execute(_TABLE_SQL)
    return conn


# ---------------------------------------------------------------------------
# JSONL 降级实现
# ---------------------------------------------------------------------------


def _file_path(kind: str) -> str:
    name = _FILE_BY_TYPE.get(kind)
    if name is None:
        raise ValueError(f"未知投稿类型：{kind}")
    os.makedirs(CONTRIBUTION_DIR, exist_ok=True)
    return os.path.join(CONTRIBUTION_DIR, name)


def _jsonl_append(record: dict) -> dict:
    path = _file_path(record["type"])
    line = json.dumps(record, ensure_ascii=False)
    with _write_lock:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")
            f.flush()
            os.fsync(f.fileno())
    return record


def _jsonl_list(kind: str, limit: int) -> List[dict]:
    path = _file_path(kind)
    if not os.path.exists(path):
        return []
    records: List[dict] = []
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return list(reversed(records[-limit:]))


def _jsonl_update(kind: str, record_id: str, status: str) -> Optional[dict]:
    path = _file_path(kind)
    if not os.path.exists(path):
        return None
    target = None
    lines: List[str] = []
    with _write_lock:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                stripped = line.strip()
                if not stripped:
                    continue
                try:
                    record = json.loads(stripped)
                except json.JSONDecodeError:
                    continue
                if record.get("id") == record_id:
                    record["status"] = status
                    target = record
                lines.append(json.dumps(record, ensure_ascii=False))
        if target is not None:
            tmp = path + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                f.write("\n".join(lines) + "\n")
            os.replace(tmp, path)
    return target


# ---------------------------------------------------------------------------
# 对外接口（MySQL 优先，失败降级 JSONL）
# ---------------------------------------------------------------------------


def init_db() -> bool:
    """启动时初始化：MySQL 可用则建表；不可用返回 False（后续走 JSONL）。"""
    if not _db_enabled():
        logger.info("未配置 CONTRIBUTE_DB_USER，投稿使用 JSONL")
        return False
    try:
        conn = _connect()
        conn.close()
        logger.info("MySQL 投稿表已就绪（%s/%s）", _DB_HOST, _DB_NAME)
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("MySQL 初始化失败，投稿走 JSONL：%s", exc)
        return False


def append(kind: str, payload: dict, status: str = "pending") -> dict:
    """追加一条投稿记录，返回带 id/status/created_at 的完整记录。"""
    record = {
        "id": uuid.uuid4().hex[:12],
        "type": kind,
        "status": status,
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        **payload,
    }
    if _db_enabled():
        try:
            conn = _connect()
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "INSERT INTO contributions"
                        " (id, type, status, name, base_url, payload, created_at, updated_at)"
                        " VALUES (%s, %s, %s, %s, %s, %s, NOW(), NOW())",
                        (
                            record["id"],
                            kind,
                            status,
                            str(payload.get("name") or "")[:255] or None,
                            str(payload.get("base_url") or "")[:512] or None,
                            json.dumps(record, ensure_ascii=False),
                        ),
                    )
            finally:
                conn.close()
            return record
        except Exception as exc:  # noqa: BLE001 - MySQL 异常降级 JSONL
            logger.warning("MySQL 写入失败，降级 JSONL：%s", exc)
    return _jsonl_append(record)


def list_recent(kind: str, limit: int = 50) -> List[dict]:
    """读取最近 limit 条投稿（倒序：新的在前）。"""
    if _db_enabled():
        try:
            conn = _connect()
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT payload FROM contributions WHERE type=%s"
                        " ORDER BY created_at DESC LIMIT %s",
                        (kind, int(limit)),
                    )
                    rows = cur.fetchall()
                return [json.loads(row[0]) for row in rows]
            finally:
                conn.close()
        except Exception as exc:  # noqa: BLE001
            logger.warning("MySQL 查询失败，降级 JSONL：%s", exc)
    return _jsonl_list(kind, limit)


def update_status(kind: str, record_id: str, status: str) -> Optional[dict]:
    """按 id 更新状态，返回更新后的记录；未找到返回 None。"""
    if _db_enabled():
        try:
            conn = _connect()
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "UPDATE contributions SET payload=JSON_SET(payload, '$.status', %s),"
                        " status=%s, updated_at=NOW() WHERE id=%s AND type=%s",
                        (status, status, record_id, kind),
                    )
                    cur.execute(
                        "SELECT payload FROM contributions WHERE id=%s AND type=%s",
                        (record_id, kind),
                    )
                    row = cur.fetchone()
                return json.loads(row[0]) if row else None
            finally:
                conn.close()
        except Exception as exc:  # noqa: BLE001
            logger.warning("MySQL 更新失败，降级 JSONL：%s", exc)
    return _jsonl_update(kind, record_id, status)
