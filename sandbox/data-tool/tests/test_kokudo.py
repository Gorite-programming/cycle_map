import json

import pytest

from tools.core import masterdb
from tools.core.sources import kokudo as kk


def _med(code, name="中央病院", addr="広島市中区1-1"):
    return {"P04_001": code, "P04_002": name, "P04_003": addr,
            "P04_004": "内科", "P04_005": None, "P04_006": None,
            "P04_007": 9, "P04_008": 100, "P04_009": 9, "P04_010": 9}


def _sch(code, closed=1, name="中央小学校", scode="A0001"):
    return {"P29_001": "34101", "P29_002": scode, "P29_003": code,
            "P29_004": name, "P29_005": "中区1-1",
            "P29_006": 2, "P29_007": closed}


def test_medical_mapping():
    assert kk.adapt_medical(_med(1), 132.4, 34.3, "hiroshima",
                            "t")["category"] == "amenity:hospital"
    assert kk.adapt_medical(_med(2), 132.4, 34.3, "hiroshima",
                            "t")["category"] == "amenity:clinic"
    assert kk.adapt_medical(_med(3), 132.4, 34.3, "hiroshima",
                            "t")["category"] == "amenity:dentist"


def test_medical_unknown_code_raises():
    with pytest.raises(kk.KokudoError):
        kk.adapt_medical(_med(9), 132.4, 34.3, "hiroshima", "t")


def test_medical_source_id_composite():
    r = kk.adapt_medical(_med(1), 132.4, 34.3, "hiroshima", "t")
    assert r["source"] == "kokudo:medical"
    assert r["source_id"] == "medical:中央病院@広島市中区1-1"
    assert r["priority"] == 50


def test_school_mapping_provisional():
    assert kk.adapt_school(_sch(16013), 132.4, 34.3, "hiroshima",
                           "t")["category"] == "amenity:kindergarten"
    assert kk.adapt_school(_sch(16016), 132.4, 34.3, "hiroshima",
                           "t")["category"] == "amenity:college"
    assert kk.adapt_school(_sch(16015), 132.4, 34.3, "hiroshima",
                           "t")["category"] == "amenity:school"
    assert kk.adapt_school(_sch(16007), 132.4, 34.3, "hiroshima",
                           "t")["category"] == "amenity:university"


def test_school_closed_skipped_unknown_kept():
    assert kk.adapt_school(_sch(16001, closed=2), 132.4, 34.3,
                           "hiroshima", "t") is None
    assert kk.adapt_school(_sch(16001, closed=0), 132.4, 34.3,
                           "hiroshima", "t") is not None
    assert kk.adapt_school(_sch(16001, closed=9), 132.4, 34.3,
                           "hiroshima", "t") is not None


def test_school_unknown_code_raises():
    with pytest.raises(kk.KokudoError):
        kk.adapt_school(_sch(99999), 132.4, 34.3, "hiroshima", "t")


def test_school_id_branch_numbering_deterministic():
    rows = [kk.adapt_school(_sch(16001, name=n, scode="X"), 132.4, 34.3,
                            "hiroshima", "t") for n in ["B校", "A校"]]
    kk._assign_school_ids(rows)
    ids = sorted(r["source_id"] for r in rows)
    assert ids == ["school:X", "school:X#2"]
    by_name = {r["name"]: r["source_id"] for r in rows}
    assert by_name["A校"] == "school:X"  # 名称昇順で1件目が素コード


def test_check_schema_raises_on_old():
    with pytest.raises(kk.KokudoError):
        kk.check_schema([{"P04_001": 1}], kk.MEDICAL_ATTRS, "old.geojson")


def _write_geojson(path, props_list):
    feats = [{"type": "Feature",
              "properties": p,
              "geometry": {"type": "Point",
                           "coordinates": [132.4, 34.3]}}
             for p in props_list]
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"type": "FeatureCollection", "features": feats}, f,
                  ensure_ascii=False)


def test_import_kokudo_mini(tmp_path, tmp_db):
    path = str(tmp_path / "med.geojson")
    _write_geojson(path, [_med(1), _med(2), _med(3)])
    s = kk.import_kokudo("medical", "hiroshima", tmp_db, path=path)
    assert s["inserted"] == 3
    assert sorted(s["by_category"]) == sorted(
        [("amenity:clinic", 1), ("amenity:hospital", 1),
         ("amenity:dentist", 1)])


def test_export_meta_licenses(tmp_path, tmp_db):
    from tools.core import export as export_mod
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, [{
            "name": "中央病院", "category": "amenity:hospital",
            "lat": 34.3, "lon": 132.4, "address": "広島市",
            "prefecture": "hiroshima", "source": "kokudo:medical",
            "source_id": "medical:中央病院@広島市", "priority": 50}])
    finally:
        conn.close()
    out = str(tmp_path / "lic.search.db")
    export_mod.export_search_db(tmp_db, "hiroshima", out)
    ss = json.loads(export_mod.read_meta(out)["source_summary"])
    assert ss["counts"] == {"kokudo:medical": 1}
    lic = ss["licenses"]["kokudo:medical"]
    assert lic["url"].endswith("KsjTmplt-P04-v3_0.html")
    assert lic["year"] == "2020" and lic["license"] == "PDL1.0"


def test_import_kokudo_missing_file(tmp_db):
    with pytest.raises(kk.KokudoError):
        kk.import_kokudo("medical", "hiroshima", tmp_db,
                         path="/nonexistent.geojson")
