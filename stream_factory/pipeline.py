"""FFmpeg 命令行构建器

把 ``StreamRequest``（去广告裁剪区间 + 逐帧滤镜）翻译成 ffmpeg 命令行参数。
裁剪分两档，能无损则无损（``-c copy``），否则重编码：

- 无裁剪 / 掐头 / 去尾 → ``-c copy`` 快路径（裁剪精度受关键帧/GOP 限制，去广告够用）；
- 中间段 / 多段裁剪 → ``select``/``aselect`` 滤镜 + ``libx264``/``aac`` 重编码；
- 存在额外滤镜（如水印 ``drawtext``）→ 视频走重编码，音频可保持 copy。

HLS 与 RTSP 各生成一条独立命令，由 ``session`` 用两个独立子进程执行：
HLS 是主输出，RTSP 是「尽力而为」的附加输出，后者失败不影响前者。
"""
import os
from typing import List, Optional, Tuple

from stream_factory import config
from stream_factory.rules import BlankSegment, FilterRule, StreamRequest, TrimSegment


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
    trims: List[TrimSegment], filters: List[FilterRule], blanks: List[BlankSegment]
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

    # 周期性空白段：drawbox 盖黑（视频）+ volume 静音（音频），enable 周期性生效；
    # 配置了 text 时在空白段居中叠加提示文字，避免纯黑屏被误解为故障。
    for b in blanks:
        enable = f"between(mod(t\\,{b.interval:.3f})\\,0\\,{b.duration:.3f})"
        vparts.append(
            f"drawbox=x=0:y=0:w=iw:h=ih:color=black@1:t=fill:enable='{enable}'"
        )
        if b.text:
            vparts.append(
                f"drawtext=text='{b.text}':fontsize=32:fontcolor=white:"
                f"x=(w-text_w)/2:y=(h-text_h)/2:enable='{enable}'"
            )
        aparts.append(f"volume=volume=0:enable='{enable}'")

    vfilter = ",".join(vparts) if vparts else None
    afilter = ",".join(aparts) if aparts else None
    return vfilter, afilter


def _common_prefix(req: StreamRequest) -> List[str]:
    """共享的输入前缀：请求头（防盗链）+ 掐头/去尾裁剪（输入侧 seek）。"""
    prefix: List[str] = []
    if req.headers:
        header_str = "\r\n".join(f"{k}: {v}" for k, v in req.headers.items()) + "\r\n"
        prefix += ["-headers", header_str]
    kind, value = _classify_trims(req.trims)
    if kind == "ss":
        prefix += ["-ss", f"{value:.3f}"]
    elif kind == "to":
        prefix += ["-to", f"{value:.3f}"]
    return prefix


def _codec_args(req: StreamRequest) -> Tuple[str, str, Optional[str], Optional[str]]:
    """返回 ``(vcodec, acodec, vfilter, afilter)``：有视频滤镜才重编码视频，有音频滤镜才重编码音频。"""
    vfilter, afilter = _build_filters(req.trims, req.filters, req.blanks)
    vcodec = "libx264" if vfilter else "copy"
    acodec = "aac" if afilter else "copy"
    return vcodec, acodec, vfilter, afilter


def _map_and_encode(
    cmd: List[str],
    vcodec: str,
    acodec: str,
    vfilter: Optional[str],
    afilter: Optional[str],
) -> None:
    """向 ``cmd`` 追加流映射与编码/滤镜参数（HLS 与 RTSP 输出共享）。"""
    cmd += ["-map", "0:v:0", "-map", "0:a:0?"]
    cmd += ["-c:v", vcodec, "-c:a", acodec]
    if vfilter:
        cmd += ["-vf", vfilter]
    if afilter:
        cmd += ["-af", afilter]


def build_hls_command(req: StreamRequest, hls_dir: str) -> List[str]:
    """构建 HLS 输出命令（主输出，无 ``-re``，尽快转完整点播）。"""
    cmd: List[str] = [config.FFMPEG_BIN, "-y"]
    cmd += _common_prefix(req)
    cmd += ["-i", req.source_url]
    vcodec, acodec, vfilter, afilter = _codec_args(req)
    _map_and_encode(cmd, vcodec, acodec, vfilter, afilter)
    cmd += [
        "-f", "hls",
        "-hls_time", str(config.HLS_TIME),
        "-hls_list_size", str(config.HLS_LIST_SIZE),
        os.path.join(hls_dir, "index.m3u8"),
    ]
    return cmd


def build_rtsp_command(req: StreamRequest, rtsp_url: str) -> List[str]:
    """构建 RTSP 推流命令（独立进程，带 ``-re`` 实时速率；失败不影响 HLS）。"""
    cmd: List[str] = [config.FFMPEG_BIN, "-y", "-re"]
    cmd += _common_prefix(req)
    cmd += ["-i", req.source_url]
    vcodec, acodec, vfilter, afilter = _codec_args(req)
    _map_and_encode(cmd, vcodec, acodec, vfilter, afilter)
    cmd += ["-f", "rtsp", rtsp_url]
    return cmd
