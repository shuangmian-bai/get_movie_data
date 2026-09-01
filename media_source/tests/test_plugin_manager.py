"""插件管理器单元测试"""
import unittest

from media_source.exceptions import PluginNotFoundError
from media_source.plugin_manager import PluginManager


class TestPluginManager(unittest.TestCase):
    def setUp(self):
        self.manager = PluginManager()
        self.manager.scan_plugins()

    def test_scan_plugins_loads_sites_and_skips_template(self):
        urls = {s.base_url for s in self.manager.get_supported_sources()}
        self.assertIn("https://www.site-a.example.com", urls)
        self.assertIn("https://www.site-b.example.com", urls)
        # template 目录应被跳过
        self.assertNotIn("https://www.example.com", urls)

    def test_get_supported_sources(self):
        sources = self.manager.get_supported_sources()
        self.assertEqual(len(sources), 2)
        for s in sources:
            self.assertTrue(s.base_url)
            self.assertTrue(s.source_name)

    def test_get_plugin_instance(self):
        plugin = self.manager.get_plugin_instance("https://www.site-a.example.com")
        self.assertEqual(plugin.source_name, "示例站点A")

    def test_get_plugin_instance_not_found(self):
        with self.assertRaises(PluginNotFoundError):
            self.manager.get_plugin_instance("https://unknown.example.com")


if __name__ == "__main__":
    unittest.main()
