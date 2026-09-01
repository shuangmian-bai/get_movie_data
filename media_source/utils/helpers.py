"""通用工具、URL 标准化、数据清洗

所有插件共用的纯函数工具，不包含任何业务逻辑。
"""
import re
from typing import Any, Dict
from urllib.parse import urljoin

_URL_RE = re.compile(r"^https?://", re.IGNORECASE)


def normalize_url(url: str, base_url: str = "") -> str:
    """URL 标准化：拼接相对路径、补齐协议、去除首尾空白。

    - ``//cdn.xx`` -> ``https://cdn.xx``
    - ``/detail/1`` + base_url -> ``{base_url}/detail/1``
    - 已是绝对地址则原样返回
    """
    if not url:
        return ""
    url = str(url).strip()
    if url.startswith("//"):
        return "https:" + url
    if not _URL_RE.match(url) and base_url:
        return urljoin(base_url, url)
    return url


def clean_text(text: Any) -> str:
    """文本清洗：压缩连续空白、去除首尾空白。"""
    if text is None:
        return ""
    return re.sub(r"\s+", " ", str(text)).strip()


def strip_html(text: Any) -> str:
    """去除简单 HTML 标签。"""
    if text is None:
        return ""
    return re.sub(r"<[^>]+>", "", str(text)).strip()


def clean_dict(data: Dict[str, Any]) -> Dict[str, Any]:
    """递归清洗字典：字符串值做 clean_text，嵌套字典 / 列表递归处理。"""
    result: Dict[str, Any] = {}
    for key, value in data.items():
        if isinstance(value, str):
            result[key] = clean_text(value)
        elif isinstance(value, dict):
            result[key] = clean_dict(value)
        elif isinstance(value, list):
            result[key] = [
                clean_dict(item) if isinstance(item, dict) else item for item in value
            ]
        else:
            result[key] = value
    return result
