"""OSM PBF → 共通スキーマ (SPEC 6.1)。

- category は現行と同じ規則 (place > railway=station > amenity > shop >
  tourism > leisure > highway=bus_stop > public_transport > named)。
- name が空の要素は取り込まない。
- Way の座標はノード座標の平均 (現行同様。改善しない)。
- addr:* タグがあれば address を組み立てる (任意)。
"""

import osmium

from .. import config, masterdb
from .. import overlay as overlay_mod
from ..kana import tag_reading


def _as_dict(tags):
    if isinstance(tags, dict):
        return tags
    return {t.k: t.v for t in tags}


def category_for(tags):
    """現行 category_for と同じ規則でカテゴリを決める。"""
    d = _as_dict(tags)
    v = d.get("place")
    if v:
        return "place:%s" % v
    if d.get("railway") == "station":
        return "railway:station"
    v = d.get("amenity")
    if v:
        return "amenity:%s" % v
    v = d.get("shop")
    if v:
        return "shop:%s" % v
    v = d.get("tourism")
    if v:
        return "tourism:%s" % v
    v = d.get("leisure")
    if v:
        return "leisure:%s" % v
    if d.get("highway") == "bus_stop":
        return "highway:bus_stop"
    v = d.get("public_transport")
    if v:
        return "public_transport:%s" % v
    return "named"


def address_from_tags(tags):
    """addr:* から住所文字列を組み立てる。取れなければ None。"""
    d = _as_dict(tags)
    full = (d.get("addr:full") or "").strip()
    if full:
        return full
    body = "".join([
        d.get("addr:prefecture") or "",
        d.get("addr:city") or "",
        d.get("addr:ward") or "",
        d.get("addr:suburb") or "",
        d.get("addr:quarter") or "",
        d.get("addr:neighbourhood") or "",
        d.get("addr:street") or "",
        d.get("addr:block_number") or "",
        d.get("addr:housenumber") or "",
    ]).strip()
    postcode = (d.get("addr:postcode") or "").strip()
    if postcode and body:
        return postcode + " " + body
    return body or postcode or None


class _PoiHandler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.rows = []

    def _push(self, osm_type, osm_id, tags, lat, lon):
        d = _as_dict(tags)
        name = (d.get("name") or "").strip()
        if not name:
            return
        self.rows.append({
            "name": name,
            "category": category_for(d),
            "lat": lat,
            "lon": lon,
            "address": address_from_tags(d),
            "prefecture": None,  # 取り込み時に県判定する
            "source": "osm",
            "source_id": "%s/%s" % (osm_type, osm_id),
            "priority": config.source_priority("osm"),
            # 読みタグ (name:ja-Hira 優先、なければ name:ja_rm)。無ければ None
            "kana": tag_reading(d),
        })

    def node(self, n):
        if not n.visible:
            return
        try:
            lat, lon = n.location.lat, n.location.lon
        except Exception:
            return
        self._push("node", n.id, n.tags, lat, lon)

    def way(self, w):
        if not w.visible:
            return
        lats, lons = [], []
        for nd in w.nodes:
            try:
                loc = nd.location
                lats.append(loc.lat)
                lons.append(loc.lon)
            except Exception:
                continue
        if not lats:
            return
        self._push("way", w.id, w.tags,
                   sum(lats) / len(lats), sum(lons) / len(lons))


def parse_pbf(pbf_path):
    """PBF をパースして共通スキーマ行リストを返す (DBに触らない)。"""
    handler = _PoiHandler()
    handler.apply_file(pbf_path, locations=True)
    return handler.rows


def import_osm(pbf_path, prefecture, db_path=None):
    """PBF を取り込み、(prefecture, 'osm') の poi を入れ替える。

    県判定は座標→行政界ポリゴン (なければ bbox) で行う (SPEC 7)。
    どの県にも属さない点のみ、引数の prefecture に寄せる (PBFは県切り出し済み想定)。
    overlay は残る。戻り値は件数サマリ。
    """
    db_path = db_path or config.MASTER_DB_PATH
    masterdb.init_db(db_path)
    overlay_mod.ensure_schema(db_path)

    rows = parse_pbf(pbf_path)
    n_named = sum(1 for r in rows if r["category"] == "named")
    assigned, fallback = [], 0
    for r in rows:
        hit = config.prefecture_for_point(r["lat"], r["lon"])
        if hit is None:
            hit = prefecture
            fallback += 1
        r["prefecture"] = hit
        assigned.append(r)

    from datetime import datetime, timezone
    stamp = datetime.now(timezone.utc).isoformat(timespec="seconds")
    target_rows = [r for r in assigned if r["prefecture"] == prefecture]
    for r in target_rows:
        r["imported_at"] = stamp

    conn = masterdb.connect(db_path)
    try:
        deleted, inserted = masterdb.replace_source(
            conn, prefecture, "osm", target_rows)
    finally:
        conn.close()
    return {
        "parsed": len(rows),
        "named": n_named,
        "assigned_to_target": len(target_rows),
        "assigned_elsewhere": len(assigned) - len(target_rows),
        "fallback_to_target": fallback,
        "deleted": deleted,
        "inserted": inserted,
    }
