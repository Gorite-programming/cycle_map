"""master.db スキーマ・CRUD (SPEC 5.1)。

master.db は全ソースを共通スキーマで保持する中間DB。
手動編集の overlay テーブルは overlay.py が管理する。
"""

import os
import sqlite3
from datetime import datetime, timezone

from . import config

POI_SCHEMA = """
CREATE TABLE IF NOT EXISTS poi (
    uid          INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL,
    category     TEXT NOT NULL,
    lat          REAL NOT NULL,
    lon          REAL NOT NULL,
    address      TEXT,
    prefecture   TEXT,
    source       TEXT NOT NULL,
    source_id    TEXT,
    priority     INTEGER NOT NULL,
    imported_at  TEXT NOT NULL,
    merged_into  INTEGER,
    kana         TEXT                -- 読み (OSMタグ由来。第2段階で追加)
);
"""

POI_INDEXES = [
    "CREATE INDEX IF NOT EXISTS poi_pref_cat ON poi(prefecture, category);",
    "CREATE INDEX IF NOT EXISTS poi_coords ON poi(lat, lon);",
    "CREATE INDEX IF NOT EXISTS poi_source ON poi(source, source_id);",
]


def utcnow():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def connect(db_path=None):
    db_path = db_path or config.MASTER_DB_PATH
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn


def init_db(db_path=None):
    """poi テーブルとインデックスを作成する。既存データは消さない。"""
    db_path = db_path or config.MASTER_DB_PATH
    conn = connect(db_path)
    try:
        conn.execute(POI_SCHEMA)
        for stmt in POI_INDEXES:
            conn.execute(stmt)
        _migrate(conn)
        conn.commit()
    finally:
        conn.close()
    return db_path


def _migrate(conn):
    """既存DBへの後付け拡張 (第1段階のDBには kana 列が無い)。"""
    cols = {r[1] for r in conn.execute("PRAGMA table_info(poi)")}
    if "kana" not in cols:
        conn.execute("ALTER TABLE poi ADD COLUMN kana TEXT;")


def insert_pois(conn, rows):
    """poi 行 dict の iterable を挿入し、件数を返す。"""
    rows = list(rows)
    conn.executemany(
        """INSERT INTO poi
           (name, category, lat, lon, address, prefecture,
            source, source_id, priority, imported_at, merged_into, kana)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)""",
        [
            (
                r["name"],
                r["category"],
                r["lat"],
                r["lon"],
                r.get("address"),
                r.get("prefecture"),
                r["source"],
                r.get("source_id"),
                r.get("priority", config.source_priority(r["source"])),
                r.get("imported_at", utcnow()),
                r.get("kana"),
            )
            for r in rows
        ],
    )
    conn.commit()
    return len(rows)


def replace_source(conn, prefecture, source, rows):
    """再取り込み: 同一 (prefecture, source) の poi を入れ替え、overlay は残す。

    戻り値は (削除件数, 挿入件数)。
    """
    cur = conn.execute(
        "DELETE FROM poi WHERE prefecture = ? AND source = ?",
        (prefecture, source),
    )
    deleted = cur.rowcount
    inserted = insert_pois(conn, rows)
    return deleted, inserted


def get_poi(conn, uid):
    row = conn.execute("SELECT * FROM poi WHERE uid = ?", (uid,)).fetchone()
    return dict(row) if row else None


def get_by_source_key(conn, source, source_id):
    row = conn.execute(
        "SELECT * FROM poi WHERE source = ? AND source_id = ?"
        " ORDER BY uid LIMIT 1",
        (source, source_id),
    ).fetchone()
    return dict(row) if row else None


def query_pois(conn, prefecture=None, category_like=None, source=None,
               keyword=None, include_merged=False):
    """POI を辞書リストで返す。uid 昇順 (取り込み順)。"""
    conds, params = [], []
    if not include_merged:
        conds.append("merged_into IS NULL")
    if prefecture:
        conds.append("prefecture = ?")
        params.append(prefecture)
    if category_like:
        conds.append("category LIKE ?")
        params.append(category_like)
    if source:
        conds.append("source = ?")
        params.append(source)
    if keyword:
        conds.append("name LIKE ?")
        params.append("%" + keyword + "%")
    where = (" WHERE " + " AND ".join(conds)) if conds else ""
    cur = conn.execute("SELECT * FROM poi" + where + " ORDER BY uid", params)
    return [dict(r) for r in cur.fetchall()]


def count_total(conn, prefecture=None):
    if prefecture:
        row = conn.execute(
            "SELECT COUNT(*) FROM poi WHERE prefecture = ?"
            " AND merged_into IS NULL",
            (prefecture,),
        ).fetchone()
    else:
        row = conn.execute(
            "SELECT COUNT(*) FROM poi WHERE merged_into IS NULL"
        ).fetchone()
    return row[0]


def count_by_category(conn, prefecture=None):
    if prefecture:
        cur = conn.execute(
            "SELECT category, COUNT(*) FROM poi"
            " WHERE prefecture = ? AND merged_into IS NULL"
            " GROUP BY category ORDER BY COUNT(*) DESC",
            (prefecture,),
        )
    else:
        cur = conn.execute(
            "SELECT category, COUNT(*) FROM poi WHERE merged_into IS NULL"
            " GROUP BY category ORDER BY COUNT(*) DESC"
        )
    return [(r[0], r[1]) for r in cur.fetchall()]


def count_by_source(conn, prefecture=None):
    if prefecture:
        cur = conn.execute(
            "SELECT source, COUNT(*) FROM poi"
            " WHERE prefecture = ? AND merged_into IS NULL"
            " GROUP BY source ORDER BY COUNT(*) DESC",
            (prefecture,),
        )
    else:
        cur = conn.execute(
            "SELECT source, COUNT(*) FROM poi WHERE merged_into IS NULL"
            " GROUP BY source ORDER BY COUNT(*) DESC"
        )
    return [(r[0], r[1]) for r in cur.fetchall()]


def last_import_at(conn, prefecture=None):
    if prefecture:
        row = conn.execute(
            "SELECT MAX(imported_at) FROM poi WHERE prefecture = ?",
            (prefecture,),
        ).fetchone()
    else:
        row = conn.execute("SELECT MAX(imported_at) FROM poi").fetchone()
    return row[0]


def clear_prefecture(conn, prefecture):
    """県の poi を全削除する (破壊的操作。overlay は残る)。戻り値は削除件数。"""
    cur = conn.execute("DELETE FROM poi WHERE prefecture = ?", (prefecture,))
    conn.commit()
    return cur.rowcount
