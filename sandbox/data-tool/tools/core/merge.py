"""重複統合 (SPEC 5.3 + 追記§7)。

同一施設が複数ソースに存在する場合の統合。承認済み方針:
- 同一条件: 互換カテゴリ内・距離50m以内 (設定可)・名称が類似
  (compact正規化後の完全一致または包含)
- 生存はkokudo側uid。吸収されたOSM側は merged_into に統合先を記録
- 住所はkokudo優先、NULL時はOSMにフォールバック
- 互換カテゴリ: hospital⇔clinic⇔doctors のみ相互可。
  dentist・学校系は同一カテゴリのみ
"""

import math

from . import masterdb
from .normalize import compact

# 互換カテゴリ群 (同一群内のみ統合可)。群外は同一カテゴリのみ
COMPATIBLE_GROUPS = [
    frozenset(("amenity:hospital", "amenity:clinic", "amenity:doctors")),
]

# kokudo が生成しうるカテゴリ (OSM側の探索範囲を絞る用)
KOKUDO_CATEGORIES = (
    "amenity:hospital", "amenity:clinic", "amenity:dentist",
    "amenity:school", "amenity:kindergarten", "amenity:university",
    "amenity:college",
)


def haversine_m(lat1, lon1, lat2, lon2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = (math.sin(dp / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(a))


def categories_compatible(a, b):
    if a == b:
        return True
    return any({a, b} <= g for g in COMPATIBLE_GROUPS)


def names_match(a, b):
    ca, cb = compact(a or ""), compact(b or "")
    if not ca or not cb:
        return False
    return ca == cb or ca in cb or cb in ca


def find_candidates(conn, prefecture, distance_m=50.0):
    """統合候補 [(kokudo行, osm行, 距離m)] を返す。決定的順序。

    kokudo行ごとに最も近いOSM行を1件だけ候補にする。
    """
    kokudo_rows = [r for r in masterdb.query_pois(conn, prefecture=prefecture)
                   if r["source"] in ("kokudo:medical", "kokudo:school")]
    osm_rows = [r for r in masterdb.query_pois(conn, prefecture=prefecture,
                                               source="osm")
                if r["category"] in KOKUDO_CATEGORIES]
    cands = []
    for k in kokudo_rows:
        window_lat = distance_m / 111320.0
        window_lon = distance_m / (111320.0
                                   * max(0.5, math.cos(math.radians(k["lat"]))))
        best, best_d = None, None
        for o in osm_rows:
            if not categories_compatible(k["category"], o["category"]):
                continue
            if abs(o["lat"] - k["lat"]) > window_lat:
                continue
            if abs(o["lon"] - k["lon"]) > window_lon:
                continue
            if not names_match(k["name"], o["name"]):
                continue
            d = haversine_m(k["lat"], k["lon"], o["lat"], o["lon"])
            if d <= distance_m and (best_d is None or d < best_d):
                best, best_d = o, d
        if best is not None:
            cands.append((k, best, best_d))
    cands.sort(key=lambda t: t[0]["uid"])
    return cands


def apply_merge(conn, kokudo_uid, osm_uid):
    """統合を適用する。戻り値は (kokudo行, osm行)。"""
    kokudo = masterdb.get_poi(conn, kokudo_uid)
    osm = masterdb.get_poi(conn, osm_uid)
    if kokudo is None or osm is None:
        raise ValueError("poi not found")
    if kokudo["merged_into"] is not None or osm["merged_into"] is not None:
        raise ValueError("already merged")
    if kokudo["priority"] <= osm["priority"]:
        raise ValueError("winner must have higher priority")
    # 住所はkokudo優先、NULL時はOSMにフォールバック
    if not kokudo["address"] and osm["address"]:
        conn.execute("UPDATE poi SET address = ? WHERE uid = ?",
                     (osm["address"], kokudo_uid))
    conn.execute("UPDATE poi SET merged_into = ? WHERE uid = ?",
                 (kokudo_uid, osm_uid))
    conn.commit()
    return masterdb.get_poi(conn, kokudo_uid), masterdb.get_poi(conn, osm_uid)


def merge_all(conn, prefecture, distance_m=50.0):
    """候補を全件自動適用する。戻り値は適用件数。"""
    n = 0
    for k, o, _ in find_candidates(conn, prefecture, distance_m):
        try:
            apply_merge(conn, k["uid"], o["uid"])
            n += 1
        except ValueError:
            continue
    return n


def undo_merge(conn, uid):
    """merged_into を NULL に戻す。戻り値は更新件数。"""
    cur = conn.execute("UPDATE poi SET merged_into = NULL WHERE uid = ?",
                       (uid,))
    conn.commit()
    return cur.rowcount


def undo_all(conn, prefecture):
    """県の統合を全て取り消す。戻り値は更新件数。"""
    cur = conn.execute(
        "UPDATE poi SET merged_into = NULL"
        " WHERE prefecture = ? AND merged_into IS NOT NULL",
        (prefecture,))
    conn.commit()
    return cur.rowcount


def merge_stats(conn, prefecture):
    """(統合済み件数, ソース別内訳)。"""
    cur = conn.execute(
        "SELECT source, COUNT(*) FROM poi"
        " WHERE prefecture = ? AND merged_into IS NOT NULL"
        " GROUP BY source", (prefecture,))
    by_source = [(r[0], r[1]) for r in cur.fetchall()]
    total = sum(n for _, n in by_source)
    return {"merged": total, "by_source": by_source}
