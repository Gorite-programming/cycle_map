"""重複統合 (SPEC 5.3 + 追記§7 + Overture統合対応)。

同一施設が複数ソースに存在する場合の統合。
- 優先順位: manual(100) > kokudo(50) > csv(40) > osm(30) > overture(20)
- 座標精度: 人手で正確な位置がマッピングされている OSM (または kokudo) の座標を100%維持。
- 住所補完: 勝者側の住所が NULL の場合、敗者側 (Overture等) の詳細住所を補完。
- 名称補完: 勝者側の名称が汎用名 (例: 「ローソン」) で敗者側に詳細店舗名 (例: 「ローソン 広島井口五丁目店」) がある場合、詳細名称へアップデート。
- 同一住所ゴースト排除: Overture 内部で同一チェーン・同一住所なのに座標が異なるゴーストを統合・除外。
"""

import math
import re
from typing import Any, Dict, List, Optional, Tuple

from . import config, masterdb
from .normalize import compact

# 互換カテゴリ群 (同一群内または同一カテゴリで統合可)
COMPATIBLE_GROUPS = [
    frozenset(("amenity:hospital", "amenity:clinic", "amenity:doctors")),
    frozenset(("amenity:restaurant", "amenity:fast_food", "amenity:cafe", "amenity:bar", "amenity:pub")),
    frozenset(("shop:supermarket", "shop:grocery", "shop:convenience")),
    frozenset(("shop:chemist", "amenity:pharmacy")),
    frozenset(("highway:bus_stop", "amenity:bus_station")),
    frozenset(("tourism:attraction", "tourism:artwork", "tourism:museum")),
]

# 主要チェーン名・ブランドリスト (事前にcompact化)
RAW_MAJOR_CHAINS = [
    "セブンイレブン", "セブン-イレブン", "ローソン", "ファミリーマート", "デイリーヤマザキ",
    "ミニストップ", "ポプラ", "セイコーマート",
    "すき家", "吉野家", "松屋", "なか卯", "かつや", "ほっともっと", "本家かまどや",
    "ガスト", "サイゼリヤ", "ジョイフル", "ココス", "ロイヤルホスト", "びっくりドンキー",
    "マクドナルド", "モスバーガー", "ケンタッキー", "ミスタードーナツ", "サブウェイ",
    "スターバックス", "ドトール", "タリーズ", "コメダ珈琲", "サンマルクカフェ",
    "もち吉", "ちから", "むさし", "餃子の王将", "大阪王将", "丸亀製麺", "はなまるうどん",
    "スシロー", "くら寿司", "はま寿司", "かっぱ寿司",
    "ウォンツ", "コスモス", "クスリのアオキ", "マツモトキヨシ", "ウエルシア", "レデイ薬局", "くすりのレデイ", "ダイレックス",
    "ゆめタウン", "ゆめマート", "フジ", "イオン", "マックスバリュ", "業務スーパー", "ハローズ", "アルゾ",
    "エディオン", "ヤマダデンキ", "ケーズデンキ", "ニトリ", "ナフコ", "コメリ", "ジュンテンドー",
    "ENEOS", "出光", "apollostation", "コスモ石油", "キグナス", "SOLATO",
]
COMPACT_CHAINS = [compact(c) for c in RAW_MAJOR_CHAINS if compact(c)]

# 交通・地名など誤統合してはならないカテゴリ
STRICT_EXACT_ONLY_CATEGORIES = {
    "highway:bus_stop", "railway:station",
    "place:neighbourhood", "place:quarter", "place:city", "place:suburb", "place:town", "place:village",
}


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = (math.sin(dp / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(a))


def categories_compatible(a: str, b: str) -> bool:
    if a == b:
        return True
    if a in STRICT_EXACT_ONLY_CATEGORIES or b in STRICT_EXACT_ONLY_CATEGORIES:
        return False
    if a == "named" or b == "named":
        return True
    return any({a, b} <= g for g in COMPATIBLE_GROUPS)


def names_match_fast(ca: str, cb: str, cat_a: str = "", cat_b: str = "") -> bool:
    """事前に compact 化された文字列で同一施設か高速判定する。"""
    if not ca or not cb:
        return False
    if ca == cb:
        return True

    if cat_a in STRICT_EXACT_ONLY_CATEGORIES or cat_b in STRICT_EXACT_ONLY_CATEGORIES:
        return False

    if ca.startswith(cb) or cb.startswith(ca):
        return True

    for c_brand in COMPACT_CHAINS:
        if c_brand in ca and c_brand in cb:
            return True

    if (ca in cb or cb in ca) and min(len(ca), len(cb)) >= 4:
        ratio = min(len(ca), len(cb)) / max(len(ca), len(cb))
        if ratio >= 0.5:
            return True

    return False


def names_match(a: str, b: str, cat_a: str = "", cat_b: str = "") -> bool:
    return names_match_fast(compact(a or ""), compact(b or ""), cat_a, cat_b)


def find_candidates(conn, prefecture: str, distance_m: float = 100.0) -> List[Tuple[Dict[str, Any], Dict[str, Any], float]]:
    """統合候補 [(勝者行, 敗者行, 距離m)] を返す。決定的順序。
    
    空間グリッドハッシュ + 事前compact計算により高速に動作する。
    1. kokudo vs osm / overture (医療・学校等、50m以内)
    2. osm vs overture (全カテゴリ、100m以内)
    3. overture vs overture (同一住所・同一チェーンのゴースト重複)
    """
    all_pois = masterdb.query_pois(conn, prefecture=prefecture, include_merged=False)
    for p in all_pois:
        p["_cname"] = compact(p["name"])
    
    kokudo_rows = [r for r in all_pois if (r["source"] or "").startswith("kokudo:")]
    osm_rows = [r for r in all_pois if r["source"] == "osm"]
    overture_rows = [r for r in all_pois if (r["source"] or "").startswith("overture")]

    cands = []
    used_losers = set()

    grid_size = 0.005  # 約500m

    def build_grid(rows):
        grid: Dict[Tuple[int, int], List[Dict[str, Any]]] = {}
        for r in rows:
            gx = int(r["lat"] / grid_size)
            gy = int(r["lon"] / grid_size)
            grid.setdefault((gx, gy), []).append(r)
        return grid

    def get_neighbors(grid, lat, lon):
        gx = int(lat / grid_size)
        gy = int(lon / grid_size)
        res = []
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                bucket = grid.get((gx + dx, gy + dy))
                if bucket:
                    res.extend(bucket)
        return res

    # パス1: kokudo vs (osm + overture) (医療・学校等、50m以内)
    target_grid = build_grid(osm_rows + overture_rows)
    for k in kokudo_rows:
        window_lat = 50.0 / 111320.0
        window_lon = 50.0 / (111320.0 * max(0.5, math.cos(math.radians(k["lat"]))))
        
        best, best_d = None, None
        for o in get_neighbors(target_grid, k["lat"], k["lon"]):
            if o["uid"] in used_losers:
                continue
            if not categories_compatible(k["category"], o["category"]):
                continue
            if abs(o["lat"] - k["lat"]) > window_lat or abs(o["lon"] - k["lon"]) > window_lon:
                continue
            if not names_match_fast(k["_cname"], o["_cname"], k["category"], o["category"]):
                continue
            d = haversine_m(k["lat"], k["lon"], o["lat"], o["lon"])
            if d <= 50.0 and (best_d is None or d < best_d):
                best, best_d = o, d
        if best is not None:
            cands.append((k, best, best_d))
            used_losers.add(best["uid"])

    # パス2: osm vs overture (全カテゴリ、100m以内) - OSMの正確な座標が勝者
    overture_grid = build_grid([v for v in overture_rows if v["uid"] not in used_losers])
    window_lat = distance_m / 111320.0

    for o in osm_rows:
        if o["uid"] in used_losers:
            continue
        window_lon = distance_m / (111320.0 * max(0.5, math.cos(math.radians(o["lat"]))))
        
        best, best_d = None, None
        for v in get_neighbors(overture_grid, o["lat"], o["lon"]):
            if v["uid"] in used_losers:
                continue
            if not categories_compatible(o["category"], v["category"]):
                continue
            if abs(v["lat"] - o["lat"]) > window_lat or abs(v["lon"] - o["lon"]) > window_lon:
                continue
            if not names_match_fast(o["_cname"], v["_cname"], o["category"], v["category"]):
                continue
            d = haversine_m(o["lat"], o["lon"], v["lat"], v["lon"])
            if d <= distance_m and (best_d is None or d < best_d):
                best, best_d = v, d
        if best is not None:
            cands.append((o, best, best_d))
            used_losers.add(best["uid"])
            if not o.get("address") and best.get("address"):
                o["address"] = best["address"]

    # 住所正規化ヘルパー (全角数字・ハイフン・漢数字丁目・市区町村プレフィックスの完全正規化)
    def normalize_addr_key(raw_addr: Optional[str]) -> str:
        if not raw_addr:
            return ""
        s = raw_addr.strip().lower()
        tr = str.maketrans("０１２３４５６７８９一二三四五六七八九十", "01234567891234567891")
        s = s.translate(tr)
        s = re.sub(r"[−ー‐―–〜~ｰ/／,，.．\s]+", "-", s)
        s = re.sub(r"(丁目|番地|番|号)", "-", s)
        for _ in range(3):
            s = re.sub(r"^.*?(?:県|府|都|道|市|郡|区)-*", "", s)
        s = re.sub(r"-+", "-", s).strip("-")
        return compact(s)

    # パス3: 同一住所＋同一チェーン・施設 (測地系ズレ・誤配置ゴーストの完全排除)
    all_active = [p for p in all_pois if p["uid"] not in used_losers]
    addr_map: Dict[Tuple[str, str], List[Dict[str, Any]]] = {}
    for p in all_active:
        n_addr = normalize_addr_key(p.get("address"))
        if not n_addr or len(n_addr) < 4:
            continue
        c_name = p["_cname"]
        c_brand = c_name.replace("銀行", "").replace("atm", "")
        # 代表チェーン名があればブランドに正規化
        for brand in COMPACT_CHAINS:
            if brand in c_brand:
                c_brand = brand
                break
        key = (c_brand, n_addr)
        addr_map.setdefault(key, []).append(p)

    for key, group in addr_map.items():
        if len(group) > 1:
            group.sort(key=lambda x: (config.source_priority(x["source"]), -x["uid"]), reverse=True)
            winner = group[0]
            for loser in group[1:]:
                if loser["uid"] in used_losers:
                    continue
                d = haversine_m(winner["lat"], winner["lon"], loser["lat"], loser["lon"])
                cands.append((winner, loser, d))
                used_losers.add(loser["uid"])

    cands.sort(key=lambda t: t[0]["uid"])
    return cands


def apply_merge(conn, winner_uid: int, loser_uid: int, auto_commit: bool = True) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    """統合を適用する。勝者側の住所・詳細名・具体的カテゴリを補完し、敗者側を merged_into に設定。"""
    winner = masterdb.get_poi(conn, winner_uid)
    loser = masterdb.get_poi(conn, loser_uid)
    if winner is None or loser is None:
        raise ValueError("poi not found")
    if winner["merged_into"] is not None or loser["merged_into"] is not None:
        raise ValueError("already merged")
    
    w_pri = config.source_priority(winner["source"])
    l_pri = config.source_priority(loser["source"])
    if w_pri < l_pri:
        raise ValueError("winner must have higher or equal priority")

    updates = []
    params = []

    # 1. 住所補完: 勝者側が NULL で敗者側にあればコピー
    if not winner["address"] and loser["address"]:
        updates.append("address = ?")
        params.append(loser["address"])

    # 2. 名称補完: 勝者側が汎用名で、敗者側に具体的な店舗名がある場合は詳細名を適用
    w_name = winner["name"]
    l_name = loser["name"]
    if len(w_name) < len(l_name) and l_name.startswith(w_name) and ("店" in l_name or "支店" in l_name):
        updates.append("name = ?")
        params.append(l_name)

    # 3. カテゴリ補完: 勝者側が named (汎用) で敗者側が具体的カテゴリの場合、具体的カテゴリに更新
    if winner["category"] == "named" and loser["category"] != "named":
        updates.append("category = ?")
        params.append(loser["category"])

    if updates:
        params.append(winner_uid)
        conn.execute(f"UPDATE poi SET {', '.join(updates)} WHERE uid = ?", params)

    # 敗者側をマージ済みに設定
    conn.execute("UPDATE poi SET merged_into = ? WHERE uid = ?", (winner_uid, loser_uid))
    if auto_commit:
        conn.commit()
    return masterdb.get_poi(conn, winner_uid), masterdb.get_poi(conn, loser_uid)


def merge_all(conn, prefecture: str, distance_m: float = 100.0) -> int:
    """候補を全件自動適用する。トランザクションで一括コミット。戻り値は適用件数。"""
    n = 0
    cands = find_candidates(conn, prefecture, distance_m)
    for w, l, _ in cands:
        try:
            apply_merge(conn, w["uid"], l["uid"], auto_commit=False)
            n += 1
        except ValueError:
            continue
    conn.commit()
    return n


def undo_merge(conn, uid: int) -> int:
    """merged_into を NULL に戻す。戻り値は更新件数。"""
    cur = conn.execute("UPDATE poi SET merged_into = NULL WHERE uid = ?", (uid,))
    conn.commit()
    return cur.rowcount


def undo_all(conn, prefecture: str) -> int:
    """県の統合を全て取り消す。戻り値は更新件数。"""
    cur = conn.execute(
        "UPDATE poi SET merged_into = NULL WHERE prefecture = ? AND merged_into IS NOT NULL",
        (prefecture,),
    )
    conn.commit()
    return cur.rowcount


def merge_stats(conn, prefecture: str) -> Dict[str, Any]:
    """(統合済み件数, ソース別内訳)。"""
    cur = conn.execute(
        "SELECT source, COUNT(*) FROM poi WHERE prefecture = ? AND merged_into IS NOT NULL GROUP BY source",
        (prefecture,),
    )
    by_source = [(r[0], r[1]) for r in cur.fetchall()]
    total = sum(n for _, n in by_source)
    return {"merged": total, "by_source": by_source}
