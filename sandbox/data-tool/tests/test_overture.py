"""Overture Maps ソースモジュールの単体テスト。"""

import unittest
from tools.core import config
from tools.core.sources import overture


class TestOvertureSource(unittest.TestCase):
    def test_source_priority(self):
        """Overture の優先度が kokudo (50) より低く osm (10) より高い (30) ことを確認。"""
        self.assertEqual(config.source_priority("overture"), 30)
        self.assertEqual(config.source_priority("overture:places"), 30)
        self.assertTrue(config.source_priority("kokudo") > config.source_priority("overture"))
        self.assertTrue(config.source_priority("overture") > config.source_priority("osm"))

    def test_category_resolution_convenience(self):
        """コンビニ名称パターンによるカテゴリ判定。"""
        self.assertEqual(overture.resolve_category("セブン-イレブン 山口葵店", "shopping", None), "shop:convenience")
        self.assertEqual(overture.resolve_category("ファミリーマート道場門前", None, "store"), "shop:convenience")
        self.assertEqual(overture.resolve_category("LAWSON", None, None), "shop:convenience")
        self.assertEqual(overture.resolve_category("ミニストップ", "restaurant", None), "shop:convenience")

    def test_category_resolution_mapping(self):
        """Overture タクソノミーからの標準マッピング。"""
        self.assertEqual(overture.resolve_category("スターバックス", "coffee_shop", "cafe"), "amenity:cafe")
        self.assertEqual(overture.resolve_category("済生会総合病院", "general_hospital", "hospital"), "amenity:hospital")
        self.assertEqual(overture.resolve_category("あさひ自転車", "bicycle_shop", None), "shop:bicycle")
        self.assertEqual(overture.resolve_category("道の駅 仁保の郷", "tourist_attraction", None), "tourism:attraction")
        self.assertEqual(overture.resolve_category("湯田温泉 温泉宿", "hotel", None), "amenity:public_bath")

    def test_address_formatting(self):
        """住所フォーマット処理。"""
        addr = overture.format_address("葵2丁目6-8", "山口市", "山口県")
        self.assertEqual(addr, "山口県 山口市 葵2丁目6-8")

        # 既にfreeformに県や市が含まれている場合の重複防止
        addr2 = overture.format_address("山口県山口市中央1-1", "山口市", "山口県")
        self.assertEqual(addr2, "山口県山口市中央1-1")


if __name__ == "__main__":
    unittest.main()
