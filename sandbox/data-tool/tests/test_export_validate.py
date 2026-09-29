import sqlite3

from conftest import sample_rows
from tools.core import export as export_mod
from tools.core import masterdb
from tools.core import validate as validate_mod


def test_export_validate_app_queries(tmp_path, tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
    finally:
        conn.close()
    out = str(tmp_path / "hiroshima.search.db")
    summary = export_mod.export_search_db(tmp_db, "hiroshima", out)
    assert summary["row_count"] == 5

    result = validate_mod.validate_search_db(out)
    for r in result["results"]:
        assert r["ok"], (r["id"], r["name"], r["detail"])
    assert result["passed"]

    # アプリと同じクエリ (SPEC 3.3 の a〜e) が実行できること
    conn = sqlite3.connect("file:%s?mode=ro" % out, uri=True)
    try:
        rows = conn.execute(
            validate_mod.APP_QUERIES["fts"], ("広島*", 10)).fetchall()
        assert len(rows) >= 1
        assert len(rows[0]) == 4  # name, category, lat, lon の順
        conn.execute(validate_mod.APP_QUERIES["like"],
                     ("%広島%", 10)).fetchall()
        conn.execute(validate_mod.APP_QUERIES["category_near"],
                     ("shop:%", 34.0, 35.0, 132.0, 133.0,
                      34.39, 34.39, 132.46, 132.46, 10)).fetchall()
        conn.execute(validate_mod.APP_QUERIES["around"],
                     (34.0, 35.0, 132.0, 133.0, "shop:convenience%",
                      34.39, 34.39, 132.46, 132.46, 10)).fetchall()
        rev = conn.execute(validate_mod.APP_QUERIES["revgeo"],
                           (34.0, 35.0, 132.0, 133.0, 10)).fetchall()
        assert len(rev) == 1 and rev[0][1] == "place:city"
    finally:
        conn.close()


def test_export_exclude_named(tmp_path, tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
    finally:
        conn.close()
    out = str(tmp_path / "h.search.db")
    summary = export_mod.export_search_db(tmp_db, "hiroshima", out,
                                          include_named=False)
    assert summary["row_count"] == 4
    assert summary["named_excluded"] == 1
    result = validate_mod.validate_search_db(out)
    assert result["passed"]
