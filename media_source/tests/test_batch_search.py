"""批量并发搜索单元测试"""
import unittest

from media_source.base import MediaSourcePlugin
from media_source.plugin_manager import PluginManager


class TestBatchSearch(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.manager = PluginManager()
        self.manager.scan_plugins()

    async def test_full_scan(self):
        results = await self.manager.batch_search("流浪", [])
        names = {r.name for r in results}
        self.assertIn("流浪地球", names)
        self.assertIn("流浪地球2", names)

    async def test_specific_sources(self):
        results = await self.manager.batch_search(
            "流浪", ["https://www.site-a.example.com"]
        )
        self.assertTrue(results)
        self.assertTrue(
            all(r.base_url == "https://www.site-a.example.com" for r in results)
        )

    async def test_invalid_url_filtered(self):
        # 无效 URL 被跳过，返回空列表且不抛异常
        results = await self.manager.batch_search(
            "流浪", ["https://invalid.example.com"]
        )
        self.assertEqual(results, [])

    async def test_single_failure_not_break_batch(self):
        class FailingPlugin(MediaSourcePlugin):
            base_url = "https://fail.example.com"
            source_name = "失败站点"
            search_mapping = {"name": "{title} | default:''"}

            async def _raw_search(self, key):
                raise RuntimeError("boom")

            async def _raw_get_info(self, search_item):
                return {}

            async def _raw_get_play_url(self, media_info, episode_index):
                return {}

        # 注入一个必定失败的插件
        self.manager._plugins["https://fail.example.com"] = FailingPlugin()
        results = await self.manager.batch_search("流浪", [])
        # 失败站点被隔离，其他站点结果正常返回
        self.assertIn("流浪地球", {r.name for r in results})
        self.assertTrue(
            all(r.base_url != "https://fail.example.com" for r in results)
        )


if __name__ == "__main__":
    unittest.main()
