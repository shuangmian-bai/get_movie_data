"""m3u8 播放列表解析与 URI 重写

- 自动识别 Master / Media 播放列表；
- Master 选带宽最高的 variant；
- Media 拆出全局头 + 有序分片段（标签块 + URI）；
- 分片 URI 可能是相对路径（``seg.ts`` / ``../seg.ts``）、绝对路径
  （``https://cdn/seg.ts``）或协议相对（``//cdn/seg.ts``），统一用
  ``urljoin`` 解析为绝对 URL；
- ``#EXT-X-KEY`` / ``#EXT-X-MAP`` 行内的 URI 同样重写为绝对 URL，
  保证重写后的播放列表自包含（前端无需相对索引文件再次解析）。
"""
import re
from typing import List, Tuple
from urllib.parse import urljoin

# #EXT-X-STREAM-INF 的 BANDWIDTH 属性
_BANDWIDTH_RE = re.compile(r"BANDWIDTH=(\d+)")

# URI 属性（#EXT-X-KEY / #EXT-X-MAP 行内 URI="..."）
_URI_ATTR_RE = re.compile(r'URI\s*=\s*"([^"]*)"')


def resolve_uri(base_url: str, uri: str) -> str:
    """把分片 / 子列表 / key / init 的 URI 解析为绝对 URL。

    相对路径（``seg.ts``、``../seg.ts``）、绝对路径（``https://…``）、
    协议相对（``//…``）统一由 ``urljoin`` 处理。
    """
    return urljoin(base_url, uri.strip())


def extract_variants(lines: List[str]) -> List[str]:
    """从 Master 播放列表提取各 variant 的 URI，按带宽降序返回（最高带宽优先）。"""
    variants = []
    for i, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF"):
            m = _BANDWIDTH_RE.search(line)
            bandwidth = int(m.group(1)) if m else 0
            if i + 1 < len(lines):
                uri = lines[i + 1].strip()
                if uri and not uri.startswith("#"):
                    variants.append((bandwidth, uri))
    variants.sort(key=lambda x: x[0], reverse=True)
    return [uri for _, uri in variants]


def is_master(text: str) -> bool:
    """是否为 Master 播放列表（含多码率 variant）。"""
    return "#EXT-X-STREAM-INF" in text


def _rewrite_uri_attr(line: str, base_url: str) -> str:
    """把 KEY / MAP 行内的 URI 重写为绝对 URL（相对/绝对/协议相对统一处理）。"""
    m = _URI_ATTR_RE.search(line)
    if not m or not m.group(1):
        return line
    raw = m.group(1)
    return line.replace(raw, resolve_uri(base_url, raw))


def parse_media(
    text: str, base_url: str
) -> Tuple[List[str], List[Tuple[List[str], str]]]:
    """解析 Media 播放列表。

    返回 ``(header_lines, segments)``：
    - ``header_lines``：全局头（首个 ``#EXTINF`` 之前的行，含 ``#EXTM3U``、
      ``#EXT-X-TARGETDURATION``、默认 KEY / MAP 等，URI 已重写为绝对 URL）；
    - ``segments``：有序分片段 ``[(标签行列表, 绝对URL)]``，标签行含 ``#EXTINF``
      及可能的 ``#EXT-X-DISCONTINUITY`` / KEY / MAP（URI 已重写为绝对 URL）。
    """
    lines = text.splitlines()

    first_inf = -1
    for i, line in enumerate(lines):
        if line.strip().startswith("#EXTINF"):
            first_inf = i
            break

    header_lines: List[str] = []
    head = lines if first_inf == -1 else lines[:first_inf]
    for line in head:
        stripped = line.strip()
        if not stripped or stripped == "#EXT-X-ENDLIST":
            continue
        header_lines.append(_rewrite_uri_attr(line, base_url))

    segments: List[Tuple[List[str], str]] = []
    tags: List[str] = []
    body = lines[first_inf:] if first_inf != -1 else []
    for line in body:
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("#"):
            tags.append(_rewrite_uri_attr(line, base_url))
        else:
            segments.append((tags, resolve_uri(base_url, stripped)))
            tags = []
    return header_lines, segments
