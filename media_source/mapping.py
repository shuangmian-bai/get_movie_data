"""字段映射引擎、白名单过滤核心逻辑

本模块是数据格式化的唯一入口，核心功能：
1. 解析模板占位符与默认值；
2. 遍历模板白名单字段，过滤原始数据多余字段；
3. 自动填充默认值，保证字段不缺失；
4. 支持字典、字典数组批量格式化。

模板语法
--------
标准输出字段: ``{站点原始字段} | default:默认值``
示例：``name: "{title} | default:'未知影片'"``

取值优先级：原始字段存在则取值，不存在则使用默认值；
若模板未声明默认值且原始字段缺失，则跳过该 key，交由模型默认值兜底。
"""
import ast
import re
from typing import Any, Dict, List, Tuple

# 占位符：{字段名}
_PLACEHOLDER_RE = re.compile(r"\{([^{}]+)\}")
# 默认值： | default:xxx
_DEFAULT_RE = re.compile(r"\|\s*default\s*:\s*(.*)$", re.DOTALL)


def _parse_default(raw: str) -> Any:
    """解析默认值字符串，安全还原为对应 Python 字面量。

    支持引号字符串、数字、空列表/空字典等；解析失败时按普通字符串处理。
    """
    raw = raw.strip()
    try:
        return ast.literal_eval(raw)
    except (ValueError, SyntaxError):
        # 去掉可能残留的引号，作为字符串返回
        return raw.strip("\"'").strip()


def parse_template(template: str) -> Tuple[str, Any]:
    """解析单条映射模板，返回 ``(原始字段名, 默认值)``。

    未声明默认值时默认值返回 ``None``，由调用方决定是否跳过该 key。
    """
    field = ""
    m = _PLACEHOLDER_RE.search(template)
    if m:
        field = m.group(1).strip()

    default = None
    dm = _DEFAULT_RE.search(template)
    if dm:
        default = _parse_default(dm.group(1).strip())
    return field, default


def map_data(raw: Dict[str, Any], mapping: Dict[str, str]) -> Dict[str, Any]:
    """将单个原始字典按模板白名单格式化为标准字典。

    最终输出字段仅为模板定义的 key，原始数据中所有多余字段全部丢弃。
    """
    result: Dict[str, Any] = {}
    for key, template in mapping.items():
        field, default = parse_template(str(template))
        if field and field in raw and raw[field] is not None:
            result[key] = raw[field]
        elif default is not None:
            result[key] = default
        # 二者皆无时跳过，交给 Pydantic 模型默认值兜底
    return result


def map_data_list(raw_list: List[Dict[str, Any]], mapping: Dict[str, str]) -> List[Dict[str, Any]]:
    """批量格式化原始字典列表。"""
    return [map_data(raw, mapping) for raw in raw_list if isinstance(raw, dict)]
