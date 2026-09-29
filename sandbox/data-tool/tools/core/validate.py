"""出力 .search.db の検証 (SPEC 10) と現行DB比較レポート (SPEC 9 第1段階)。

validate_search_db: 11項目を自動チェックし、不合格が1つでもあれば
passed=False。結果は GUI・CLI で表示する。
"""

import os
import sqlite3

EXPECTED_HEAD = ["id", "osm_type", "osm_id", "name", "category", "lat", "lon"]
ALLOWED_TAIL = ["address", "source", "kana", "search_text"]
META_KEYS = ("schema_version", "created_at", "prefecture", "bbox",
             "source_summary", "row_count", "tool_version")

APP_QUERIES = {
    "fts": ("SELECT p.name, p.category, p.lat, p.lon"
            " FROM places_fts f JOIN places p ON p.id = f.rowid"
            " WHERE places_fts MATCH ? LIMIT ?;"),
    "like": ("SELECT name, category, lat, lon FROM places"
             " WHERE name LIKE ? LIMIT ?;"),
    "category_near": ("SELECT name, category, lat, lon FROM places"
                      " WHERE category LIKE ? AND lat BETWEEN ? AND ?"
                      " AND lon BETWEEN ? AND ?"
                      " ORDER BY (lat-?)*(lat-?) + (lon-?)*(lon-?) LIMIT ?;"),
    "around": ("SELECT name, category, lat, lon FROM places"
               " WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?"
               " AND category LIKE ? ORDER BY (lat-?)*(lat-?)"
               " + (lon-?)*(lon-?) LIMIT ?;"),
    "revgeo": ("SELECT name, category, lat, lon FROM places"
               " WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?"
               " AND category LIKE 'place:%' LIMIT ?;"),
}


def _connect_ro(path):
    conn = sqlite3.connect("file:%s?mode=ro" % path, uri=True)
    return conn


def validate_search_db(path, keyword=None):
    """11項目を検証し、{"passed": bool, "results": [...]} を返す。"""
    results = []

    def check(cid, name, ok, detail=""):
        results.append({"id": cid, "name": name, "ok": bool(ok),
                        "detail": detail})

    if not os.path.exists(path):
        check(0, "file exists", False, "not found: %s" % path)
        return {"passed": False, "results": results}

    conn = _connect_ro(path)
    try:
        tables = {r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'")}
        check(1, "places / places_fts が存在する",
              {"places", "places_fts"} <= tables,
              "tables=%s" % sorted(tables))

        cols = [r[1] for r in conn.execute("PRAGMA table_info(places)")]
        head_ok = cols[:7] == EXPECTED_HEAD
        tail_ok = all(c in ALLOWED_TAIL for c in cols[7:])
        check(2, "places のカラム名・順序が SPEC 3.1 と一致",
              head_ok and tail_ok, "columns=%s" % cols)

        idx = {r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='index'")}
        check(3, "places_coords_idx と places_osm_idx が存在する",
              {"places_coords_idx", "places_osm_idx"} <= idx,
              "indexes=%s" % sorted(idx))

        n_places = conn.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        n_fts = conn.execute(
            "SELECT COUNT(*) FROM places_fts").fetchone()[0] \
            if "places_fts" in tables else -1
        orphan = conn.execute(
            "SELECT COUNT(*) FROM places_fts f LEFT JOIN places p"
            " ON p.id = f.rowid WHERE p.id IS NULL").fetchone()[0] \
            if "places_fts" in tables else -1
        missing = conn.execute(
            "SELECT COUNT(*) FROM places p LEFT JOIN places_fts f"
            " ON f.rowid = p.id WHERE f.rowid IS NULL").fetchone()[0] \
            if "places_fts" in tables else -1
        check(4, "places_fts の行数 = places の行数・rowid 対応",
              n_places == n_fts and orphan == 0 and missing == 0,
              "places=%d fts=%d orphan=%d missing=%d"
              % (n_places, n_fts, orphan, missing))

        try:
            conn.execute(
                "SELECT name, category, lat, lon FROM places LIMIT 1"
            ).fetchone()
            check(5, "基本 SELECT が成功する", True, "")
        except Exception as e:
            check(5, "基本 SELECT が成功する", False, str(e))

        kw = keyword
        if kw is None:
            try:
                meta = dict(conn.execute("SELECT key, value FROM meta"))
                from . import config as _config
                pref = _config.PREFECTURES.get(meta.get("prefecture", ""),
                                               {})
                kw = pref.get("name_ja", "広島")[:2]
            except Exception:
                kw = "広島"
        try:
            hits = conn.execute(APP_QUERIES["fts"], (kw + "*", 5)).fetchall()
            check(6, "FTS検索 (a) が実行でき既知語でヒットする",
                  len(hits) > 0,
                  "keyword=%s hits=%d" % (kw, len(hits)))
        except Exception as e:
            check(6, "FTS検索 (a) が実行でき既知語でヒットする",
                  False, str(e))

        n_place = conn.execute(
            "SELECT COUNT(*) FROM places WHERE category LIKE 'place:%%'"
        ).fetchone()[0]
        check(7, "category LIKE 'place:%%' の行が存在する (逆ジオコーディング用)",
              n_place > 0, "place:*=%d" % n_place)

        n_bad_geo = conn.execute(
            "SELECT COUNT(*) FROM places"
            " WHERE lat NOT BETWEEN 20 AND 46 OR lon NOT BETWEEN 122 AND 154"
        ).fetchone()[0]
        check(8, "lat 20〜46・lon 122〜154 の範囲外が無い", n_bad_geo == 0,
              "out_of_range=%d" % n_bad_geo)

        n_empty = conn.execute(
            "SELECT COUNT(*) FROM places WHERE name = '' OR name IS NULL"
        ).fetchone()[0]
        check(9, "name が空文字の行が無い", n_empty == 0,
              "empty_name=%d" % n_empty)

        try:
            meta = dict(conn.execute("SELECT key, value FROM meta"))
            missing_keys = [k for k in META_KEYS if k not in meta]
            check(10, "meta テーブルに必須キーが揃っている",
                  not missing_keys, "missing=%s" % missing_keys)
        except Exception as e:
            check(10, "meta テーブルに必須キーが揃っている", False, str(e))

        size = os.path.getsize(path)
        check(11, "ファイルサイズ (参考値)", True,
              "%.2f MB" % (size / 1024 / 1024))

        has_search_text = "search_text" in cols
        n_empty_search = -1
        if has_search_text:
            n_empty_search = conn.execute(
                "SELECT COUNT(*) FROM places"
                " WHERE search_text IS NULL OR search_text = ''"
            ).fetchone()[0]
        check(12, "search_text カラムが存在する (LIKEフォールバック用)",
              has_search_text and n_empty_search == 0,
              "empty=%s" % n_empty_search)
    finally:
        conn.close()

    passed = all(r["ok"] for r in results)
    return {"passed": passed, "results": results}


def compare_with_reference(ref_path, new_path, sample_limit=5):
    """現行DBと新DBの比較データを返す (SPEC 9 第1段階の完了条件用)。"""
    ref = _connect_ro(ref_path)
    new = _connect_ro(new_path)
    try:
        ref_total = ref.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        new_total = new.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        ref_cat = dict(ref.execute(
            "SELECT category, COUNT(*) FROM places GROUP BY category"))
        new_cat = dict(new.execute(
            "SELECT category, COUNT(*) FROM places GROUP BY category"))
        categories = []
        for cat in sorted(set(ref_cat) | set(new_cat),
                          key=lambda c: -(new_cat.get(c, 0)
                                          + ref_cat.get(c, 0))):
            r, n = ref_cat.get(cat, 0), new_cat.get(cat, 0)
            categories.append({"category": cat, "ref": r, "new": n,
                               "diff": n - r})
        samples = []
        for row in new.execute(
                "SELECT id, osm_type, osm_id, name, category, lat, lon"
                " FROM places ORDER BY id LIMIT ?", (sample_limit,)):
            pid, otype, oid, name, cat, lat, lon = row
            hit = ref.execute(
                "SELECT name, category FROM places"
                " WHERE osm_type = ? AND osm_id = ?", (otype, oid)).fetchone()
            samples.append({
                "id": pid, "osm_type": otype, "osm_id": oid,
                "name": name, "category": cat, "lat": lat, "lon": lon,
                "ref_name": hit[0] if hit else None,
                "ref_category": hit[1] if hit else None,
                "match": bool(hit and hit[0] == name and hit[1] == cat),
            })
        # 新DBにあって現行に無いカテゴリ・逆の有無
        only_new = sorted(set(new_cat) - set(ref_cat))
        only_ref = sorted(set(ref_cat) - set(new_cat))
        return {
            "ref_total": ref_total,
            "new_total": new_total,
            "total_diff": new_total - ref_total,
            "categories": categories,
            "only_new": only_new,
            "only_ref": only_ref,
            "samples": samples,
        }
    finally:
        ref.close()
        new.close()


def format_compare_markdown(comp, ref_label, new_label, notes=""):
    lines = [
        "# 現行DB比較レポート",
        "",
        "現行: `%s` / 新: `%s`" % (ref_label, new_label),
        "",
        "## 総件数",
        "",
        "| 現行 | 新 | 差 |",
        "|---:|---:|---:|",
        "| %d | %d | %+d |" % (comp["ref_total"], comp["new_total"],
                               comp["total_diff"]),
        "",
        "## カテゴリ別件数 (現行 / 新 / 差)",
        "",
        "| カテゴリ | 現行 | 新 | 差 |",
        "|---|---:|---:|---:|",
    ]
    for c in comp["categories"]:
        lines.append("| `%s` | %d | %d | %+d |"
                     % (c["category"], c["ref"], c["new"], c["diff"]))
    lines += [
        "",
        "現行のみのカテゴリ: %s" % (comp["only_ref"] or "なし"),
        "",
        "新のみのカテゴリ: %s" % (comp["only_new"] or "なし"),
        "",
        "## サンプル行の比較 (新DBの先頭 %d 件)" % len(comp["samples"]),
        "",
        "| id | osm | name | category | 現行と一致 |",
        "|---:|---|---|---|:---:|",
    ]
    for s in comp["samples"]:
        lines.append("| %d | %s/%d | %s | `%s` | %s |"
                     % (s["id"], s["osm_type"], s["osm_id"], s["name"],
                        s["category"], "一致" if s["match"]
                        else ("不一致(現行:%s/%s)" % (s["ref_name"],
                                                     s["ref_category"])
                              if s["ref_name"] else "現行に無し")))
    if notes:
        lines += ["", "## 差分の理由", "", notes]
    return "\n".join(lines) + "\n"
