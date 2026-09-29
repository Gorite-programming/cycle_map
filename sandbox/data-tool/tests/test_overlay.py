import os

from conftest import sample_rows
from tools.core import masterdb
from tools.core import overlay as overlay_mod


def test_hide_excludes_from_resolve(tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
    finally:
        conn.close()
    overlay_mod.hide_place(tmp_db, target_source="osm",
                           target_source_id="node/2")
    rows = overlay_mod.resolve(tmp_db, "hiroshima")
    assert {r["name"] for r in rows} == {
        "広島駅", "平和記念公園", "旧山陽道", "広島市"}


def test_edit_overrides_fields(tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
        uid = masterdb.get_by_source_key(conn, "osm", "node/1")["uid"]
    finally:
        conn.close()
    overlay_mod.edit_place(tmp_db, target_uid=uid, name="広島駅(新幹線口)")
    rows = {r["source_id"]: r for r in
            overlay_mod.resolve(tmp_db, "hiroshima")}
    assert rows["node/1"]["name"] == "広島駅(新幹線口)"
    assert rows["node/1"]["category"] == "railway:station"


def test_add_manual_place(tmp_db):
    overlay_mod.add_place(tmp_db, name="手入力スポット",
                          category="tourism:viewpoint",
                          lat=34.3, lon=132.4, prefecture="hiroshima")
    rows = overlay_mod.resolve(tmp_db, "hiroshima")
    assert len(rows) == 1
    assert rows[0]["source"] == "manual"
    assert rows[0]["source_id"].startswith("manual/")


def test_edit_survives_reimport_via_source_key(tmp_db):
    """再取り込みで uid が変わっても (source, source_id) で再適用される。"""
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
    finally:
        conn.close()
    # uid ではなく source キーで編集を記録
    overlay_mod.edit_place(tmp_db, target_source="osm",
                           target_source_id="node/2",
                           name="セブンイレブン広島店(改称)")
    # 再取り込み (uid が変わる)
    conn = masterdb.connect(tmp_db)
    try:
        new_rows = [dict(r, name=r["name"] + "'") for r in sample_rows()]
        masterdb.replace_source(conn, "hiroshima", "osm", new_rows)
    finally:
        conn.close()
    rows = {r["source_id"]: r for r in
            overlay_mod.resolve(tmp_db, "hiroshima")}
    assert rows["node/2"]["name"] == "セブンイレブン広島店(改称)"


def test_backup_created_on_mutation(tmp_db, tmp_path, monkeypatch):
    from tools.core import config
    monkeypatch.setattr(config, "BACKUP_DIR", str(tmp_path / "backup"))
    overlay_mod.add_place(tmp_db, name="x", category="named",
                          lat=34.3, lon=132.4, prefecture="hiroshima")
    assert os.path.isdir(config.BACKUP_DIR)
    assert len(os.listdir(config.BACKUP_DIR)) >= 1
