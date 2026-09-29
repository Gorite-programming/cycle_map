"""県別 .search.db 生成 (SPEC 3, 4.4, 13)。

master.db + overlay から再生成する (差分更新はしない)。
出力スキーマの先頭7カラム・FTS構成は SPEC 3 と完全一致させる。
第2段階 (方式A): FTS の name には別表記をスペース区切りで追記する。
places.name (表示用) は元の表記のまま変更しない。

注意: 外部コンテンツFTS (content='places') のため、
INSERT INTO places_fts(places_fts) VALUES('rebuild') を実行すると
places.name から再構築されて別表記が消える。rebuild は使わないこと。
別表記を戻す手順 = 本 export の再実行 (決定的に再生成される)。
"""

import json
import os
import sqlite3
from datetime import datetime, timezone

from . import config, masterdb
from . import overlay as overlay_mod
from .kana import pykakasi_reading
from .normalize import normalize_variants

import json
import os
import sqlite3
from datetime import datetime, timezone

from . import config, masterdb
from . import overlay as overlay_mod

PLACES_SCHEMA = """
CREATE TABLE places (
    id       INTEGER PRIMARY KEY,
    osm_type TEXT    NOT NULL,
    osm_id   INTEGER NOT NULL,
    name     TEXT    NOT NULL,
    category TEXT    NOT NULL,
    lat      REAL    NOT NULL,
    lon      REAL    NOT NULL,
    address  TEXT,
    source   TEXT,
    kana     TEXT,
    -- ↓ 第3段階追記: LIKEフォールバック用 (Android標準SQLiteにFTS5が無い端末向け)。
    -- FTSに投入する文字列と全く同じ内容。表示には使わない
    search_text TEXT
);
"""

PLACES_INDEXES = [
    "CREATE INDEX places_osm_idx ON places(osm_type, osm_id);",
    "CREATE INDEX places_coords_idx ON places(lat, lon);",
]

FTS_SCHEMA = """
CREATE VIRTUAL TABLE places_fts USING fts5(
    name,
    category,
    content='places',
    content_rowid='id',
    tokenize='unicode61'
);
"""

META_SCHEMA = """
CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT
);
"""

META_KEYS = ("schema_version", "created_at", "prefecture", "bbox",
             "source_summary", "row_count", "tool_version")


def _osm_type_id(row, manual_serial):
    """(osm_type, osm_id) を決める。OSM以外は 'ext' + 連番 (SPEC 3.1)。"""
    source, source_id = row.get("source"), row.get("source_id") or ""
    if source == "osm" and "/" in source_id:
        kind, num = source_id.split("/", 1)
        if kind in ("node", "way", "relation") and num.isdigit():
            return kind, int(num)
    return "ext", manual_serial


def export_search_db(db_path=None, prefecture=None, out_path=None,
                     include_named=True, use_pykakasi=False):
    """検索DBを生成する。戻り値は件数サマリ dict。

    use_pykakasi=True のときのみ、タグ由来の読みが無い行に pykakasi
    読みを付与する (誤変換があるため既定OFF。out/kana_sample.md)。
    """
    if prefecture not in config.PREFECTURES:
        raise ValueError("unknown prefecture: %r" % (prefecture,))
    db_path = db_path or config.MASTER_DB_PATH
    if out_path is None:
        out_path = config.search_db_path(prefecture)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)

    rows = overlay_mod.resolve(db_path, prefecture)
    named_excluded = 0
    if not include_named:
        kept = [r for r in rows if r["category"] != "named"]
        named_excluded = len(rows) - len(kept)
        rows = kept
    # 決定的順序: 取り込み順 (uid)。手動追加 (uid None) は末尾。
    rows.sort(key=lambda r: (r["uid"] is None, r["uid"] or 0))

    conn = sqlite3.connect(out_path)
    try:
        conn.execute(PLACES_SCHEMA)
        serial = 0
        records = []
        fts_records = []
        kana_cache = {}
        n_kana_tag = 0
        n_kana_pykakasi = 0
        for i, r in enumerate(rows, start=1):
            if r.get("source") != "osm":
                serial += 1
            osm_type, osm_id = _osm_type_id(r, serial)
            kana = (r.get("kana") or "").strip() or None
            if kana:
                n_kana_tag += 1
            elif use_pykakasi:
                if r["name"] not in kana_cache:
                    reading = pykakasi_reading(r["name"])
                    kana_cache[r["name"]] = (reading["hira"] if reading
                                             else None)
                kana = kana_cache[r["name"]]
                if kana:
                    n_kana_pykakasi += 1
            variants = normalize_variants(r["name"])
            if kana and kana != r["name"]:
                # 読み自体も検索対象にする (normalizeは元文字列を除くため)
                variants.append(kana)
                variants += normalize_variants(kana)
            seen, uniq = set(), []
            for v in variants:
                if v not in seen:
                    seen.add(v)
                    uniq.append(v)
            fts_name = r["name"] + "".join(" " + v for v in uniq)
            records.append((
                i, osm_type, osm_id, r["name"], r["category"],
                r["lat"], r["lon"], r.get("address"),
                r.get("source"), kana, fts_name,
            ))
            fts_records.append((i, fts_name, r["category"]))
        conn.executemany(
            """INSERT INTO places
               (id, osm_type, osm_id, name, category, lat, lon,
                address, source, kana, search_text)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            records,
        )
        for stmt in PLACES_INDEXES:
            conn.execute(stmt)
        conn.execute(FTS_SCHEMA)
        # 方式A: 別表記を含む全文を直接投入する (SELECT再構築はしない)。
        # rebuild/trigger再構築は別表記を消すため使わない (注意書き参照)。
        conn.executemany(
            "INSERT INTO places_fts(rowid, name, category)"
            " VALUES (?, ?, ?);",
            fts_records,
        )
        conn.execute("INSERT INTO places_fts(places_fts) VALUES('optimize');")
        conn.execute(META_SCHEMA)
        by_source = {}
        for r in rows:
            by_source[r.get("source")] = by_source.get(r.get("source"), 0) + 1
        licenses = {}
        for src in by_source:
            if src in config.KOKUDO_SOURCES:
                info = config.KOKUDO_SOURCES[src]
                licenses[src] = {
                    "url": info["url"],
                    "year": info["year"],
                    "version": info["version"],
                    "license": info["license"],
                }
        meta = {
            "schema_version": config.SEARCH_SCHEMA_VERSION,
            "created_at": datetime.now(timezone.utc).isoformat(
                timespec="seconds"),
            "prefecture": prefecture,
            "bbox": json.dumps(config.PREFECTURES[prefecture]["bbox"],
                               ensure_ascii=False),
            "source_summary": json.dumps(
                {"counts": by_source, "licenses": licenses},
                ensure_ascii=False, sort_keys=True),
            "row_count": str(len(records)),
            "tool_version": config.TOOL_VERSION,
        }
        conn.executemany("INSERT INTO meta(key, value) VALUES (?, ?)",
                         list(meta.items()))
        conn.commit()
    finally:
        conn.close()

    by_category = {}
    for r in rows:
        by_category[r["category"]] = by_category.get(r["category"], 0) + 1
    return {
        "out_path": out_path,
        "row_count": len(records),
        "by_category": sorted(by_category.items(),
                              key=lambda kv: kv[1], reverse=True),
        "by_source": by_source,
        "named_excluded": named_excluded,
        "include_named": include_named,
        "use_pykakasi": use_pykakasi,
        "kana_tag_rows": n_kana_tag,
        "kana_pykakasi_rows": n_kana_pykakasi,
    }


def read_meta(search_db_path):
    conn = sqlite3.connect(
        "file:%s?mode=ro" % search_db_path, uri=True)
    try:
        return {k: v for k, v in conn.execute("SELECT key, value FROM meta")}
    finally:
        conn.close()
