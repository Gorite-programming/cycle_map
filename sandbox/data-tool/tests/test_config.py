from tools.core import config


def test_five_prefectures_defined():
    assert set(config.PREFECTURES) == {
        "tottori", "shimane", "okayama", "hiroshima", "yamaguchi"}


def test_search_db_names_lowercase_id():
    for pref_id, pref in config.PREFECTURES.items():
        assert pref["search_db"] == "%s.search.db" % pref_id


def test_bbox_order():
    for pref in config.PREFECTURES.values():
        lon_min, lat_min, lon_max, lat_max = pref["bbox"]
        assert lon_min < lon_max and lat_min < lat_max


def test_source_priority_kokudo_suffix():
    assert config.source_priority("kokudo:medical") == 50
    assert config.source_priority("manual") == 100
    assert config.source_priority("osm") == 30
    assert config.source_priority("overture") == 20
    assert config.source_priority("csv") == 40


def test_prefecture_for_point_hiroshima_city():
    # 広島市中心部は広島と判定されること
    assert config.prefecture_for_point(34.385, 132.455) == "hiroshima"


def test_prefecture_for_point_outside():
    assert config.prefecture_for_point(35.68, 139.69) is None
