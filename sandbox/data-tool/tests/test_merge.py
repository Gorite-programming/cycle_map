import pytest

from tools.core import masterdb
from tools.core import merge as mg


def _row(name, category, lat, lon, source, source_id, priority,
         address=None):
    return {"name": name, "category": category, "lat": lat, "lon": lon,
            "address": address, "prefecture": "hiroshima", "source": source,
            "source_id": source_id, "priority": priority}


def _seed(conn):
    masterdb.insert_pois(conn, [
        _row("広島市立広島市民病院", "amenity:hospital", 34.3900, 132.4600,
             "kokudo:medical", "medical:A@B", 50),
        _row("広島市民病院", "amenity:hospital", 34.3901, 132.4601,
             "osm", "node/1", 10, address="広島市中区"),
        _row("遠い病院", "amenity:hospital", 34.4000, 132.4700,
             "osm", "node/2", 10),
        _row("広島歯科", "amenity:dentist", 34.3900, 132.4600,
             "osm", "node/3", 10),
    ])


def test_haversine():
    assert mg.haversine_m(34.0, 132.0, 34.0, 132.0) == 0
    assert 100 < mg.haversine_m(34.0, 132.0, 34.001, 132.0) < 125


def test_names_match():
    assert mg.names_match("広島市立広島市民病院", "広島市民病院")
    assert mg.names_match("セブン-イレブン", "セブンイレブン")
    assert not mg.names_match("広島市民病院", "県立広島病院")
    assert not mg.names_match("", "x")


def test_compatible():
    assert mg.categories_compatible("amenity:hospital", "amenity:clinic")
    assert mg.categories_compatible("amenity:clinic", "amenity:doctors")
    assert not mg.categories_compatible("amenity:hospital",
                                        "amenity:dentist")
    assert not mg.categories_compatible("amenity:school",
                                        "amenity:kindergarten")


def test_find_apply_undo(tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        _seed(conn)
        cands = mg.find_candidates(conn, "hiroshima")
        assert len(cands) == 1
        k, o, d = cands[0]
        assert k["name"] == "広島市立広島市民病院"
        assert o["name"] == "広島市民病院"
        assert d < 50.0
        mg.apply_merge(conn, k["uid"], o["uid"])
        assert masterdb.get_poi(conn, o["uid"])["merged_into"] == k["uid"]
        # 住所フォールバック (kokudo側NULL→OSM採用)
        assert masterdb.get_poi(conn, k["uid"])["address"] == "広島市中区"
        assert mg.merge_stats(conn, "hiroshima")["merged"] == 1
        assert mg.undo_merge(conn, o["uid"]) == 1
        assert masterdb.get_poi(conn, o["uid"])["merged_into"] is None
    finally:
        conn.close()


def test_apply_enforces_priority(tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        _seed(conn)
        rows = {r["source_id"]: r for r in
                masterdb.query_pois(conn, include_merged=True)}
        with pytest.raises(ValueError):
            mg.apply_merge(conn, rows["node/1"]["uid"],
                           rows["medical:A@B"]["uid"])
    finally:
        conn.close()


def test_merge_all_and_export_excludes(tmp_db, tmp_path):
    from tools.core import export as export_mod
    conn = masterdb.connect(tmp_db)
    try:
        _seed(conn)
        assert mg.merge_all(conn, "hiroshima") == 1
    finally:
        conn.close()
    out = str(tmp_path / "m.search.db")
    summary = export_mod.export_search_db(tmp_db, "hiroshima", out)
    assert summary["row_count"] == 3  # 4件 - 統合1件
