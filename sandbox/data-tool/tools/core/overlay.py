"""手動編集 (オーバーレイ) の管理 (SPEC 5.2)。

- 手動編集は常に最優先。再取り込み (poi 入れ替え) しても消えない。
- edit/hide の対象は (source, source_id) でも特定できる。再取り込みで
  uid が変わっても同じ (source, source_id) に編集が再適用される。
- hide は生成時除外のみで、データ自体は消さない (復元可能)。
- 書き込み操作の前に master.db を data/work/backup/ に自動バックアップ
  (直近 BACKUP_KEEP 世代を保持)。
"""

import glob
import os
import shutil
import sqlite3
from datetime import datetime, timezone

from . import config, masterdb

OVERLAY_SCHEMA = """
CREATE TABLE IF NOT EXISTS overlay (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    action            TEXT NOT NULL,
    target_uid        INTEGER,
    target_source     TEXT,
    target_source_id  TEXT,
    name              TEXT,
    category          TEXT,
    lat               REAL,
    lon               REAL,
    address           TEXT,
    prefecture        TEXT,
    kana              TEXT,
    note              TEXT,
    created_at        TEXT NOT NULL
);
"""

ACTIONS = ("add", "edit", "hide")


def utcnow():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def ensure_schema(db_path=None):
    db_path = db_path or config.MASTER_DB_PATH
    conn = masterdb.connect(db_path)
    try:
        conn.execute(OVERLAY_SCHEMA)
        cols = {r[1] for r in conn.execute("PRAGMA table_info(overlay)")}
        if "kana" not in cols:
            conn.execute("ALTER TABLE overlay ADD COLUMN kana TEXT;")
        conn.commit()
    finally:
        conn.close()
    return db_path


def backup_db(db_path=None, keep=None):
    """master.db を backup/ にコピーし、パスを返す。DB未作成時は何もしない。"""
    db_path = db_path or config.MASTER_DB_PATH
    keep = config.BACKUP_KEEP if keep is None else keep
    if not os.path.exists(db_path):
        return None
    os.makedirs(config.BACKUP_DIR, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    dest = os.path.join(config.BACKUP_DIR, "master-%s.db" % stamp)
    shutil.copy2(db_path, dest)
    olds = sorted(glob.glob(os.path.join(config.BACKUP_DIR, "master-*.db")))
    for old in olds[:-keep]:
        os.remove(old)
    return dest


def _connect(db_path):
    ensure_schema(db_path)
    return masterdb.connect(db_path)


def add_place(db_path, name, category, lat, lon, address=None,
              prefecture=None, kana=None, note=None):
    """手動追加。戻り値は overlay id。"""
    db_path = db_path or config.MASTER_DB_PATH
    backup_db(db_path)
    conn = _connect(db_path)
    try:
        cur = conn.execute(
            """INSERT INTO overlay
               (action, target_uid, target_source, target_source_id,
                name, category, lat, lon, address, prefecture, kana,
                note, created_at)
               VALUES ('add', NULL, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (name, category, lat, lon, address, prefecture, kana, note,
             utcnow()),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def edit_place(db_path, target_uid=None, target_source=None,
               target_source_id=None, note=None, **fields):
    """手動修正。fields は name/category/lat/lon/address/prefecture の
    変更したい項目のみ。戻り値は overlay id。"""
    allowed = ("name", "category", "lat", "lon", "address", "prefecture",
               "kana")
    values = {k: v for k, v in fields.items() if k in allowed}
    if not values:
        raise ValueError("no editable fields given")
    if target_uid is None and target_source_id is None:
        raise ValueError("target_uid or (target_source, target_source_id) required")
    db_path = db_path or config.MASTER_DB_PATH
    backup_db(db_path)
    conn = _connect(db_path)
    try:
        cur = conn.execute(
            """INSERT INTO overlay
               (action, target_uid, target_source, target_source_id,
                name, category, lat, lon, address, prefecture, kana,
                note, created_at)
               VALUES ('edit', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                target_uid, target_source, target_source_id,
                values.get("name"), values.get("category"),
                values.get("lat"), values.get("lon"),
                values.get("address"), values.get("prefecture"),
                values.get("kana"), note, utcnow(),
            ),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def hide_place(db_path, target_uid=None, target_source=None,
               target_source_id=None, note=None):
    """手動非表示 (生成時に除外)。戻り値は overlay id。"""
    if target_uid is None and target_source_id is None:
        raise ValueError("target_uid or (target_source, target_source_id) required")
    db_path = db_path or config.MASTER_DB_PATH
    backup_db(db_path)
    conn = _connect(db_path)
    try:
        cur = conn.execute(
            """INSERT INTO overlay
               (action, target_uid, target_source, target_source_id,
                name, category, lat, lon, address, prefecture, kana,
                note, created_at)
               VALUES ('hide', ?, ?, ?, NULL, NULL, NULL, NULL,
                       NULL, NULL, NULL, ?, ?)""",
            (target_uid, target_source, target_source_id, note, utcnow()),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def delete_overlay(db_path, overlay_id):
    """オーバーレイ行を削除する (編集の取り消し・非表示の復元)。"""
    db_path = db_path or config.MASTER_DB_PATH
    backup_db(db_path)
    conn = _connect(db_path)
    try:
        cur = conn.execute("DELETE FROM overlay WHERE id = ?", (overlay_id,))
        conn.commit()
        return cur.rowcount
    finally:
        conn.close()


def list_overlays(db_path, action=None):
    db_path = db_path or config.MASTER_DB_PATH
    conn = _connect(db_path)
    try:
        if action:
            cur = conn.execute(
                "SELECT * FROM overlay WHERE action = ? ORDER BY id", (action,)
            )
        else:
            cur = conn.execute("SELECT * FROM overlay ORDER BY id")
        return [dict(r) for r in cur.fetchall()]
    finally:
        conn.close()


def resolve(db_path=None, prefecture=None):
    """生成用に poi + overlay を合成した行リストを返す。

    hide 対象は除外、edit は非NULL項目を上書き、add は source='manual' の
    新規行として追加する。各行は uid/name/category/lat/lon/address/
    prefecture/source/source_id を持つ。
    """
    db_path = db_path or config.MASTER_DB_PATH
    conn = masterdb.connect(db_path)
    try:
        base = masterdb.query_pois(conn, prefecture=prefecture)
        overlays = [
            dict(r)
            for r in conn.execute(
                "SELECT * FROM overlay ORDER BY id"
            ).fetchall()
        ]
    finally:
        conn.close()

    by_uid = {p["uid"]: dict(p) for p in base}
    by_key = {(p["source"], p["source_id"]): p["uid"] for p in base}

    def _target_uid(ov):
        if ov["target_uid"] in by_uid:
            return ov["target_uid"]
        return by_key.get((ov["target_source"], ov["target_source_id"]))

    hidden = set()
    edits = {}
    adds = []
    for ov in overlays:
        if ov["action"] == "hide":
            uid = _target_uid(ov)
            if uid is not None:
                hidden.add(uid)
        elif ov["action"] == "edit":
            uid = _target_uid(ov)
            if uid is not None:
                edits.setdefault(uid, []).append(ov)
        elif ov["action"] == "add":
            if prefecture is None or ov["prefecture"] in (None, prefecture):
                adds.append(ov)

    resolved = []
    for uid, poi in by_uid.items():
        if uid in hidden:
            continue
        for ov in edits.get(uid, []):
            for key in ("name", "category", "lat", "lon",
                        "address", "prefecture", "kana"):
                if ov[key] is not None:
                    poi[key] = ov[key]
        if prefecture is not None and poi["prefecture"] != prefecture:
            continue
        resolved.append(poi)
    for ov in adds:
        resolved.append({
            "uid": None,
            "name": ov["name"],
            "category": ov["category"],
            "lat": ov["lat"],
            "lon": ov["lon"],
            "address": ov["address"],
            "prefecture": ov["prefecture"],
            "kana": ov["kana"],
            "source": "manual",
            "source_id": "manual/%d" % ov["id"],
        })
    return resolved


def export_rows(db_path=None, prefecture=None):
    """export.py 用: sqlite3.Row ではなく素の dict を返す resolve の別名。"""
    return resolve(db_path, prefecture)


def _raw_execute(db_path, sql, params=()):
    conn = sqlite3.connect(db_path)
    try:
        cur = conn.execute(sql, params)
        conn.commit()
        return cur
    finally:
        conn.close()
