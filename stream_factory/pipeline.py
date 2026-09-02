"""FFmpeg 命令行构建器

把 ``StreamRequest``（去广告裁剪区间 + 逐帧滤镜）翻译成 ffmpeg 命令行参数。
裁剪分两档，能无损则无损（``-c copy``），否则重编码：

- 无裁剪 / 掐头 / 去尾 → ``-c copy`` 快路径（裁剪精度受关键帧/GOP 限制，去广告够用）；
- 中间段 / 多段裁剪 → ``select``/``aselect`` 滤镜 + ``libx264``/``aac`` 重编码；
- 存在额外滤镜（如水印 ``drawtext``）→ 视频走重编码，音频可保持 copy。

单条命令双输出：HLS（写本地磁盘）+ RTSP（推流到 mediamtx）。
"""
import os
from typing import List, Optional, Tuple

from stream_factory import config
from stream_factory.rules import FilterRule, StreamRequest, TrimSegment


def _classify_trims(trims: List[TrimSegment]) -> Tuple[str, Optional[float]]:
    """判定裁剪方式。

    返回 ``(kind, value)``：
    - ``("none", None)``        无裁剪；
    - ``("ss", end)``           掐头：删 ``[0, end)``，等价 ``-ss end``；
    - ``("to", start)``         去尾：删 ``[start, 结尾)``，等价 ``-to start``；
    - ``("select", None)``      中间段 / 多段，走 ``select`` 滤镜重编码。
    """
    valid = [t for t in trims if t.end is None or t.end > t.start]
    if not valid:
        return ("none", None)
    if len(valid) == 1:
        t = valid[0]
        if t.end is None:
            return ("to", t.start)
        if t.start <= 0:
            return ("ss", t.end)
    return ("select", None)


def _keep_segments(trims: List[TrimSegment]) -> List[Tuple[float, Optional[float]]]:
    """把「删除区间」转为「保留区间」列表 ``[(start, end)]``，``end=None`` 表示到结尾。"""
    ordered = sorted(
        [t for t in trims if t.end is None or t.end > t.start],
        key=lambda t: t.start,
    )
    keeps: List[Tuple[float, Optional[float]]] = []
    cursor: Optional[float] = 0.0
    for t in ordered:
        if t.start > cursor:
            keeps.append((cursor, t.start))
        if t.end is None:
            cursor = None
            break
        cursor = max(cursor, t.end)
    if cursor is not None:
        keeps.append((cursor, None))
    return keeps


def _keep_expr(trims: List[TrimSegment]) -> str:
    """生成 select/aselect 的保留区间表达式（``+`` 连接，逗号用 ``\\,`` 转义）。"""
    parts: List[str] = []
    for start, end in _keep_segments(trims):
        if end is None:
            parts.append(f"gte(t\\,{start:.3f})")
        else:
            parts.append(f"between(t\\,{start:.3f}\\,{end:.3f})")
    return "+".join(parts)


def _build_filter(rule: FilterRule) -> str:
    """把单个逐帧滤镜规则拼成 ``-vf`` 滤镜链片段。"""
    if rule.name == "drawtext":
        # 水印示例；text 含特殊字符需自行转义（冒号/逗号/单引号）
        text = str(rule.params.get("text", "AD"))
        fontsize = rule.params.get("fontsize", 24)
        x = rule.params.get("x", 10)
        y = rule.params.get("y", 10)
        color = rule.params.get("color", "white")
        return f"drawtext=text='{text}':fontsize={fontsize}:x={x}:y={y}:fontcolor={color}"
    # 未知滤镜：透传 name=key1=val1:key2=val2
    opts = ":".join(f"{k}={v}" for k, v in rule.params.items())
    return f"{rule.name}={opts}" if opts else rule.name


def _build_filters(
    trims: List[TrimSegment], filters: List[FilterRule]
) -> Tuple[Optional[str], Optional[str]]:
    """构建视频/音频滤镜链，返回 ``(vfilter, afilter)``（无则 ``None``）。"""
    vparts: List[str] = []
    aparts: List[str] = []

    if _classify_trims(trims)[0] == "select":
        expr = _keep_expr(trims)
        vparts.append(f"select='{expr}'")
        vparts.append("setpts=N/FRAME_RATE/TB")
        aparts.append(f"aselect='{expr}'")
        aparts.append("asetpts=N/SR/TB")

    for f in filters:
        vparts.append(_build_filter(f))

    vfilter = ",".join(vparts) if vparts else None
    afilter = ",".join(aparts) if aparts else None
    return vfilter, afilter


def build_command(req: StreamRequest, sid: str, hls_dir: str) -> List[str]:
    """构建 ffmpeg 命令（``list[str]``，可直接交给 ``create_subprocess_exec``）。

    :param req:     创建流请求（源地址 + 裁剪/滤镜规则）
    :param sid:     会话 id（用于 RTSP 推流路径）
    :param hls_dir: 该会话的 HLS 输出目录
    """
    cmd: List[str] = [config.FFMPEG_BIN, "-y", "-re"]  # -re 实时速率：保证 RTSP 推流持续、HLS 边转边出

    # 透传请求头（Referer 等防盗链），用于 HTTP(S) 输入
    if req.headers:
        header_str = "\r\n".join(f"{k}: {v}" for k, v in req.headers.items()) + "\r\n"
        cmd += ["-headers", header_str]

    # 掐头 / 去尾用输入侧 seek，保证多输出共享同一裁剪后的输入
    kind, value = _classify_trims(req.trims)
    if kind == "ss":
        cmd += ["-ss", f"{value:.3f}"]
    elif kind == "to":
        cmd += ["-to", f"{value:.3f}"]

    cmd += ["-i", req.source_url]

    # 滤镜链与编码策略：有视频滤镜才重编码视频；有音频滤镜才重编码音频
    vfilter, afilter = _build_filters(req.trims, req.filters)
    vcodec = "libx264" if vfilter else "copy"
    acodec = "aac" if afilter else "copy"

    cmd += ["-map", "0:v:0", "-map", "0:a:0?"]

    # 输出 1：HLS 写本地磁盘
    cmd += ["-c:v", vcodec, "-c:a", acodec]
    if vfilter:
        cmd += ["-vf", vfilter]
    if afilter:
        cmd += ["-af", afilter]
    cmd += [
        "-f", "hls",
        "-hls_time", str(config.HLS_TIME),
        "-hls_list_size", str(config.HLS_LIST_SIZE),
        os.path.join(hls_dir, "index.m3u8"),
    ]

    # 输出 2：RTSP 推流到 mediamtx（可关闭）
    if config.RTSP_ENABLED:
        rtsp_url = config.RTSP_SERVER.rstrip("/") + "/" + sid
        cmd += ["-c:v", vcodec, "-c:a", acodec]
        if vfilter:
            cmd += ["-vf", vfilter]
        if afilter:
            cmd += ["-af", afilter]
        cmd += ["-f", "rtsp", rtsp_url]

    return cmd
