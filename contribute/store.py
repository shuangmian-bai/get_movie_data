"""投稿存储（JSONL 追加写）

投稿数据属于运行时数据，统一落在项目根 ``cache/contributions/`` 下（gitignore），
按类型分文件追加：``sources.jsonl`` / ``ads.jsonl``。每行一条 JSON，追加写天然
避免并发覆盖；读取时忽略无法解析的行（截断残留容错）。
"""
import json
import os
import threading
import time
import uuid
from pathlib import Path
from typing import Dict, List, Optional

# 项目根目录（contribute 的上一级）
_PROJECT_ROOT = Path(__file__).resolve().parent.parent

# 投稿存储根目录（可用环境变量覆盖）
CONTRIBUTION_DIR: str = os.getenv(
    "CONTRIBUTE_DIR", str(_PROJECT_ROOT / "cache" / "contributions")
)

# 写锁：同一进程内串行追加，避免多线程写乱
_write_lock = threading.Lock()

_FILE_BY_TYPE: Dict[str, str] = {
    "source": "sources.jsonl",
    "ad": "ads.jsonl",
}


def _file_path(kind: str) -> str:
    name = _FILE_BY_TYPE.get(kind)
    if name is None:
        raise ValueError(f"未知投稿类型：{kind}")
    os.makedirs(CONTRIBUTION_DIR, exist_ok=True)
    return os.path.join(CONTRIBUTION_DIR, name)


def _now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S%z")


def append(kind: str, payload: dict, status: str = "pending") -> dict:
    """追加一条投稿记录，返回带 id/status/created_at 的完整记录。"""
    record = {
        "id": uuid.uuid4().hex[:12],
        "type": kind,
        "status": status,
        "created_at": _now_iso(),
        **payload,
    }
    path = _file_path(kind)
    line = json.dumps(record, ensure_ascii=False)
    with _write_lock:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")
            f.flush()
            os.fsync(f.fileno())
    return record


def list_recent(kind: str, limit: int = 50) -> List[dict]:
    """读取最近 limit 条投稿（倒序：新的在前）。坏行跳过。"""
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


def update_status(kind: str, record_id: str, status: str) -> Optional[dict]:
    """按 id 更新状态（重写文件，保留其余记录）。返回更新后的记录，未找到返回 None。"""
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
