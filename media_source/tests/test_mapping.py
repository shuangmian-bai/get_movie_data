"""映射引擎单元测试"""
import unittest

from media_source.mapping import map_data, map_data_list, parse_template


class TestParseTemplate(unittest.TestCase):
    def test_basic_placeholder_and_default(self):
        field, default = parse_template("{title} | default:'未知影片'")
        self.assertEqual(field, "title")
        self.assertEqual(default, "未知影片")

    def test_empty_string_default(self):
        field, default = parse_template("{year} | default:''")
        self.assertEqual(field, "year")
        self.assertEqual(default, "")

    def test_int_default(self):
        field, default = parse_template("{index} | default:0")
        self.assertEqual(default, 0)

    def test_dict_default(self):
        field, default = parse_template("{headers} | default:{}")
        self.assertEqual(default, {})

    def test_no_default_is_none(self):
        field, default = parse_template("{name}")
        self.assertEqual(field, "name")
        self.assertIsNone(default)


class TestMapData(unittest.TestCase):
    def test_whitelist_filters_extra_fields(self):
        raw = {"title": "三体", "href": "/x", "junk": "drop", "score": "8.7"}
        mapping = {"name": "{title} | default:''", "link": "{href} | default:''"}
        result = map_data(raw, mapping)
        self.assertEqual(set(result.keys()), {"name", "link"})
        self.assertEqual(result["name"], "三体")
        self.assertNotIn("junk", result)
        self.assertNotIn("score", result)

    def test_default_fallback_when_missing(self):
        raw = {"title": "三体"}
        mapping = {"name": "{title} | default:''", "link": "{href} | default:'/none'"}
        result = map_data(raw, mapping)
        self.assertEqual(result["link"], "/none")

    def test_none_treated_as_missing(self):
        raw = {"title": None}
        mapping = {"name": "{title} | default:'未知'"}
        result = map_data(raw, mapping)
        self.assertEqual(result["name"], "未知")

    def test_map_data_list(self):
        raw_list = [{"title": "A"}, {"title": "B"}]
        mapping = {"name": "{title} | default:''"}
        result = map_data_list(raw_list, mapping)
        self.assertEqual([r["name"] for r in result], ["A", "B"])


if __name__ == "__main__":
    unittest.main()
