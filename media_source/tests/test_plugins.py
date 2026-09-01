"""站点插件单元测试"""
import unittest

from media_source.models import MediaInfo, PlaySource, SearchItem
from media_source.plugin_manager import PluginManager


class TestPlugins(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.manager = PluginManager()
        self.manager.scan_plugins()
        self.site_a = self.manager.get_plugin_instance("https://www.site-a.example.com")
        self.site_b = self.manager.get_plugin_instance("https://www.site-b.example.com")

    async def test_site_a_search(self):
        items = await self.site_a.search("三体")
        self.assertTrue(items)
        self.assertIsInstance(items[0], SearchItem)
        self.assertEqual(items[0].base_url, "https://www.site-a.example.com")
        self.assertEqual(items[0].name, "三体")
        self.assertEqual(items[0].type, "剧集")

    async def test_site_a_info(self):
        items = await self.site_a.search("三体")
        info = await self.site_a.get_info(items[0])
        self.assertIsInstance(info, MediaInfo)
        self.assertEqual(info.name, "三体")
        self.assertEqual(len(info.episodes), 3)

    async def test_site_a_play(self):
        items = await self.site_a.search("三体")
        info = await self.site_a.get_info(items[0])
        play = await self.site_a.get_play_url(info, 1)
        self.assertIsInstance(play, PlaySource)
        self.assertEqual(play.type, "m3u8")
        self.assertTrue(play.url.endswith(".m3u8"))

    async def test_site_b_unified_output(self):
        """异构字段名映射后输出结构一致。"""
        items = await self.site_b.search("狂飙")
        self.assertEqual(items[0].name, "狂飙")
        self.assertEqual(items[0].type, "tv")
        self.assertEqual(items[0].year, "2023")
        info = await self.site_b.get_info(items[0])
        self.assertEqual(len(info.episodes), 2)
        play = await self.site_b.get_play_url(info, 1)
        self.assertEqual(play.type, "m3u8")


if __name__ == "__main__":
    unittest.main()
