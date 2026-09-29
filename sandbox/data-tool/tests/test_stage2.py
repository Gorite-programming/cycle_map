"""第2段階: FTS別表記 (方式A) と kana 格納のテスト。"""
import sqlite3

import pytest

from conftest import sample_rows
from tools.core import export as export_mod
from tools.core import masterdb
from tools.core import validate as validate_mod
from tools.core.kana import pykakasi_available


def _export(tmp_db, tmp_path, name="t.search.db", **kwargs):
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
    finally:
        conn.close()
    out = str(tmp_path / name)
    return out, export_mod.export_search_db(tmp_db, "hiroshima", out,
                                            **kwargs)


def _fts_match(out, query):
    """外部コンテンツFTSの索引内容は MATCH で検証する
    (列読み出しは content表 places の値を返すため不可)。"""
    conn = sqlite3.connect("file:%s?mode=ro" % out, uri=True)
    try:
        return conn.execute(
            "SELECT p.name FROM places_fts f JOIN places p"
            " ON p.id = f.rowid WHERE places_fts MATCH ?",
            (query,)).fetchall()
    finally:
        conn.close()


def test_fts_contains_variants_places_unchanged(tmp_path, tmp_db):
    out, summary = _export(tmp_db, tmp_path)
    assert summary["row_count"] == 5
    # 別表記で検索できる (ひらがな・ローマ字)
    assert any("セブンイレブン広島店" in h[0]
               for h in _fts_match(out, "せぶん*"))
    assert any("セブンイレブン広島店" in h[0]
               for h in _fts_match(out, "sebun*"))
    conn = sqlite3.connect("file:%s?mode=ro" % out, uri=True)
    try:
        name = conn.execute(
            "SELECT name FROM places WHERE id = 2").fetchone()[0]
        assert name == "セブンイレブン広島店"  # 表示名は不変
        assert conn.execute(
            "SELECT kana FROM places WHERE id = 2").fetchone()[0] is None
    finally:
        conn.close()
    # 別表記で検索できる
    assert len(_fts_match(out, "せぶん*")) >= 1


def test_tag_kana_used_in_fts(tmp_path, tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        rows = sample_rows()
        rows[0]["kana"] = "ひろしまえき"  # タグ由来相当
        masterdb.insert_pois(conn, rows)
    finally:
        conn.close()
    out = str(tmp_path / "k.search.db")
    summary = export_mod.export_search_db(tmp_db, "hiroshima", out)
    assert summary["kana_tag_rows"] == 1
    assert any("広島駅" in h[0] for h in _fts_match(out, "ひろしまえき*"))
    conn = sqlite3.connect("file:%s?mode=ro" % out, uri=True)
    try:
        assert conn.execute(
            "SELECT kana FROM places WHERE id = 1").fetchone()[0] == \
            "ひろしまえき"
    finally:
        conn.close()


def test_pykakasi_opt_in(tmp_path, tmp_db):
    if not pykakasi_available():
        pytest.skip("pykakasi not installed")
    out, summary = _export(tmp_db, tmp_path, name="p.search.db",
                           use_pykakasi=True)
    assert summary["kana_pykakasi_rows"] >= 1
    conn = sqlite3.connect("file:%s?mode=ro" % out, uri=True)
    try:
        kana = conn.execute(
            "SELECT kana FROM places WHERE name = '広島駅'").fetchone()[0]
        assert kana == "ひろしまえき"
    finally:
        conn.close()
    hits = _fts_match(out, "ひろしま*")
    assert any("広島駅" in h[0] for h in hits)
    result = validate_mod.validate_search_db(out)
    assert result["passed"]


def test_search_text_matches_fts_content(tmp_path, tmp_db):
    """search_text はFTS投入文字列と同一・別表記を含む・末尾カラム。"""
    out, _ = _export(tmp_db, tmp_path, name="st.search.db")
    conn = sqlite3.connect("file:%s?mode=ro" % out, uri=True)
    try:
        cols = [r[1] for r in conn.execute("PRAGMA table_info(places)")]
        assert cols[-1] == "search_text"
        name, search_text = conn.execute(
            "SELECT name, search_text FROM places WHERE id = 2").fetchone()
        assert name == "セブンイレブン広島店"  # 表示名は不変
        assert search_text.startswith(name + " ")
        assert "せぶん" in search_text and "sebun" in search_text
        # search_text内の別表記トークンがFTSで引ける (=投入内容と同一)
        # (' を含むトークンはMATCH構文上使えないため除外)
        toks = [t for t in search_text.split()[1:4] if "'" not in t]
        assert toks, search_text
        for tok in toks:
            hits = _fts_match(out, tok + "*")
            assert any("セブンイレブン広島店" in h[0] for h in hits), tok
        n_empty = conn.execute(
            "SELECT COUNT(*) FROM places"
            " WHERE search_text IS NULL OR search_text = ''").fetchone()[0]
        assert n_empty == 0
    finally:
        conn.close()


def test_katakana_query_matches_hiragana_name(tmp_path, tmp_db):
    """逆方向: ひらがな名の行にカタカナ入力でMATCHする。"""
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, [{
            "name": "せぶんいれぶん", "category": "shop:convenience",
            "lat": 34.39, "lon": 132.46, "address": None,
            "prefecture": "hiroshima", "source": "osm",
            "source_id": "node/99", "priority": 10}])
    finally:
        conn.close()
    out = str(tmp_path / "rev.search.db")
    export_mod.export_search_db(tmp_db, "hiroshima", out)
    assert any("せぶんいれぶん" in h[0]
               for h in _fts_match(out, "セブン*"))


def test_osm_tag_pickup():
    from types import SimpleNamespace
    from tools.core.sources.osm import _PoiHandler
    h = _PoiHandler()
    h.node(SimpleNamespace(
        visible=True, id=1,
        tags={"name": "広島駅", "name:ja-Hira": "ひろしまえき"},
        location=SimpleNamespace(lat=34.397, lon=132.475)))
    h.node(SimpleNamespace(
        visible=True, id=2,
        tags={"name": "X", "name:ja_rm": "X-eki"},
        location=SimpleNamespace(lat=34.3, lon=132.4)))
    h.node(SimpleNamespace(
        visible=True, id=3, tags={"name": "Y"},
        location=SimpleNamespace(lat=34.3, lon=132.4)))
    by_id = {r["source_id"]: r for r in h.rows}
    assert by_id["node/1"]["kana"] == "ひろしまえき"
    assert by_id["node/2"]["kana"] == "X-eki"
    assert by_id["node/3"]["kana"] is None
