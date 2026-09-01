"""插件开发模板 —— 新增站点直接复制本文件夹

复制后修改：
1. 本文件导出插件主类；
2. ``constants.py`` 定义站点接口、请求头、固定参数；
3. ``parser.py`` 编写站点专属解析逻辑；
4. ``main.py`` 定义站点元信息 + 三套映射模板，实现三个 ``_raw_*`` 方法。
"""
from media_source.plugins.template.main import TemplatePlugin

__all__ = ["TemplatePlugin"]
