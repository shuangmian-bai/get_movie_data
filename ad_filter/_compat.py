"""兼容工具

提供低版本 Python（3.8）缺失的 ``asyncio.to_thread`` 等价实现：用
``loop.run_in_executor`` 在线程池中运行同步阻塞函数（如 opencv 抽帧 / inpaint、
onnxruntime 推理），避免阻塞事件循环。
"""
import asyncio
from functools import partial
from typing import Any, Callable


async def to_thread(func: Callable[..., Any], *args: Any, **kwargs: Any) -> Any:
    """在线程池中运行同步函数并等待结果（兼容 Python 3.8，等价 ``asyncio.to_thread``）。"""
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(None, partial(func, *args, **kwargs))
