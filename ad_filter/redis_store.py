"""Redis 辅助存储：会话心跳与播放位置（可选，未配置/不可用则回退内存）

用途：把「用户是否在场」（心跳）与「播放位置」放到 Redis，
- 服务重启后会话位置不丢（下次播放从上次位置继续预取）；
- 多进程/多实例部署时共享同一份会话状态。

配置：``AD_FILTER_REDIS_URL``（如 ``redis://127.0.0.1:6379/0``），
未配置或连接失败时所有函数返回 ``None``，调用方自动回退内存态。

键位：
    movie:session:{sid}:alive     心跳（TTL 30 秒）
    movie:session:{sid}:position  播放位置（分片索引）
"""
import logging
import os
from typing import Optional

logger = logging.getLogger("ad_filter.redis")

_URL = os.getenv("AD_FILTER_REDIS_URL", "")
_KEY_PREFIX = "movie:session"

_client = None
_failed = False


def get_client():
    """获取 Redis 客户端（懒连接；失败后本次进程内不再重试）。"""
    global _client, _failed
    if not _URL or _failed:
        return None
    if _client is None:
        try:
            import redis

            client = redis.Redis.from_url(
                _URL,
                decode_responses=True,
                socket_timeout=2,
                socket_connect_timeout=2,
                health_check_interval=30,
            )
            client.ping()
            _client = client
            logger.info("Redis 已连接：%s", _URL)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Redis 连接失败，回退内存态：%s", exc)
            _failed = True
            return None
    return _client


def set_alive(sid: str, ttl: int = 30) -> None:
    """刷新会话心跳。"""
    client = get_client()
    if client is None:
        return
    try:
        client.setex(f"{_KEY_PREFIX}:{sid}:alive", ttl, "1")
    except Exception as exc:  # noqa: BLE001
        logger.debug("Redis 写心跳失败：%s", exc)


def is_alive(sid: str) -> Optional[bool]:
    """读取心跳；返回 None 表示 Redis 不可用（调用方用内存判断）。"""
    client = get_client()
    if client is None:
        return None
    try:
        return bool(client.exists(f"{_KEY_PREFIX}:{sid}:alive"))
    except Exception as exc:  # noqa: BLE001
        logger.debug("Redis 读心跳失败：%s", exc)
        return None


def set_position(sid: str, index: int) -> None:
    """保存播放位置（分片索引）。"""
    client = get_client()
    if client is None:
        return
    try:
        client.set(f"{_KEY_PREFIX}:{sid}:position", int(index))
    except Exception as exc:  # noqa: BLE001
        logger.debug("Redis 写位置失败：%s", exc)


def get_position(sid: str) -> Optional[int]:
    """读取播放位置；不可用或不存在返回 None。"""
    client = get_client()
    if client is None:
        return None
    try:
        raw = client.get(f"{_KEY_PREFIX}:{sid}:position")
        return int(raw) if raw is not None else None
    except Exception as exc:  # noqa: BLE001
        logger.debug("Redis 读位置失败：%s", exc)
        return None


def clear(sid: str) -> None:
    """清理会话的全部 Redis 键（用户离开）。"""
    client = get_client()
    if client is None:
        return
    try:
        client.delete(
            f"{_KEY_PREFIX}:{sid}:alive",
            f"{_KEY_PREFIX}:{sid}:position",
        )
    except Exception as exc:  # noqa: BLE001
        logger.debug("Redis 清理失败：%s", exc)
