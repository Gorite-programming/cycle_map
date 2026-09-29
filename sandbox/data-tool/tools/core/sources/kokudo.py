"""国土数値情報 → 共通スキーマ (SPEC 6.2 + SPEC追記)。

- 医療 (P04 第3.0版) →学校 (P29 第2.0版) の順に取り込む。
- 読むのは GeoJSON のみ (両データセットに配布あり)。GML/SHAPEは読まない。
  SHAPEしか無いファイルが出たら止める (呼び出し側で判定)。
- 座標は JGD2011 ≒ WGS84 として変換なし (追記§6)。
- 版の検証: ファイル名 (P04-20_*/P29-21_*) と属性スキーマで照合する。
  旧版の疑いがあれば例外にして止める。
- 休校中 (P29_007==2) は取り込まない。調査なし(0)・不明(9)は含める。
- 未知のコード値は例外にして止める (決め打ちしない)。
"""

import json
import os

from .. import config, masterdb
from .. import overlay as overlay_mod

MEDICAL_ATTRS = ("P04_001", "P04_002", "P04_003", "P04_004", "P04_005",
                 "P04_006", "P04_007", "P04_008", "P04_009", "P04_010")
SCHOOL_ATTRS = ("P29_001", "P29_002", "P29_003", "P29_004", "P29_005",
                "P29_006", "P29_007")


class KokudoError(Exception):
    pass


def read_geojson_features(path):
    """GeoJSON の (properties, lon, lat) リストを返す。"""
    with open(path, encoding="utf-8") as f:
        geo = json.load(f)
    feats = (geo["features"] if geo.get("type") == "FeatureCollection"
             else [geo])
    out, bad_geo = [], 0
    for feat in feats:
        geom = (feat.get("geometry") or {})
        coords = geom.get("coordinates")
        if geom.get("type") != "Point" or not coords or len(coords) < 2:
            bad_geo += 1
            continue
        out.append((feat.get("properties") or {},
                    float(coords[0]), float(coords[1])))
    return out, bad_geo


def check_schema(props_list, required, path):
    """旧版混入の検出: 必須属性が無ければ例外にして止める。"""
    keys = set()
    for props in props_list:
        keys.update(props.keys())
    missing = [k for k in required if k not in keys]
    if missing:
        raise KokudoError(
            "旧版の疑い: %s に必須属性 %s が無い" % (path, missing))


def adapt_medical(props, lon, lat, prefecture, imported_at):
    """医療1件 → 共通スキーマ行。未知コードは例外。"""
    code = props.get("P04_001")
    if code not in config.KOKUDO_MEDICAL_CATEGORY:
        raise KokudoError("未知の医療機関分類コード: %r" % (code,))
    name = (props.get("P04_002") or "").strip()
    if not name:
        return None
    address = (props.get("P04_003") or "").strip() or None
    return {
        "name": name,
        "category": config.KOKUDO_MEDICAL_CATEGORY[code],
        "lat": lat,
        "lon": lon,
        "address": address,
        "prefecture": prefecture,
        "source": "kokudo:medical",
        # P04に固有IDは無い。名称+住所の複合を安定IDにする
        # (広島実績: 重複0件。HANDOFF参照)
        "source_id": "medical:%s@%s" % (name, address or ""),
        "priority": config.source_priority("kokudo:medical"),
        "imported_at": imported_at,
        "kana": None,  # 国土数値情報に読みは無い
    }


def adapt_school(props, lon, lat, prefecture, imported_at):
    """学校1件 → 共通スキーマ行。休校中は None (取り込まない)。"""
    code = props.get("P29_003")
    if code not in config.KOKUDO_SCHOOL_CATEGORY:
        raise KokudoError("未知の学校分類コード: %r" % (code,))
    if props.get("P29_007") == config.KOKUDO_CLOSED_CODE:
        return None  # 休校中
    name = (props.get("P29_004") or "").strip()
    if not name:
        return None
    # P29_005は市区町村名を除いた所在地。そのまま格納する
    address = (props.get("P29_005") or "").strip() or None
    return {
        "name": name,
        "category": config.KOKUDO_SCHOOL_CATEGORY[code],
        "lat": lat,
        "lon": lon,
        "address": address,
        "prefecture": prefecture,
        "source": "kokudo:school",
        # P29_002は固有の学校コード。分校等で重複する場合は後で枝番を振る
        "source_id": "school:%s" % (props.get("P29_002") or ""),
        "priority": config.source_priority("kokudo:school"),
        "imported_at": imported_at,
        "kana": None,
    }


def _assign_school_ids(rows):
    """学校コード重複 (分校・キャンパス) に決定論的な枝番を振る。

    (名称, 住所, lat, lon) の昇順で1件目は素のコード、2件目以降は #2, #3…。
    同一ファイルの再取り込みでは同じ結果になる。
    """
    from collections import defaultdict
    groups = defaultdict(list)
    for r in rows:
        groups[r["source_id"]].append(r)
    for code in sorted(groups):
        members = sorted(groups[code],
                         key=lambda r: (r["name"], r["address"] or "",
                                        r["lat"], r["lon"]))
        for i, r in enumerate(members[1:], start=2):
            r["source_id"] = "%s#%d" % (code, i)
    return rows


def import_kokudo(dataset, prefecture, db_path=None, path=None):
    """国土数値情報を取り込み、(prefecture, source) の poi を入れ替える。

    戻り値は件数サマリ。未知コード・旧版疑い・ファイル無しは例外。
    """
    if dataset not in config.KOKUDO_DATASETS:
        raise KokudoError("unknown dataset: %r" % (dataset,))
    if prefecture not in config.PREFECTURES:
        raise KokudoError("unknown prefecture: %r" % (prefecture,))
    db_path = db_path or config.MASTER_DB_PATH
    path = path or config.kokudo_geojson_path(dataset, prefecture)
    if not os.path.exists(path):
        raise KokudoError("ファイルが無い: %s" % path)

    masterdb.init_db(db_path)
    overlay_mod.ensure_schema(db_path)
    feats, bad_geo = read_geojson_features(path)
    required = (MEDICAL_ATTRS if dataset == "medical" else SCHOOL_ATTRS)
    check_schema([p for p, _, _ in feats], required, path)

    from datetime import datetime, timezone
    stamp = datetime.now(timezone.utc).isoformat(timespec="seconds")
    adapt = adapt_medical if dataset == "medical" else adapt_school
    source = config.KOKUDO_DATASETS[dataset]["source"]
    rows, skipped_closed, skipped_noname, elsewhere = [], 0, 0, 0
    for props, lon, lat in feats:
        # ファイルの県に帰属させる (承認済み方針)。判定はログのみ
        if config.prefecture_for_point(lat, lon) != prefecture:
            elsewhere += 1
        row = adapt(props, lon, lat, prefecture, stamp)
        if row is None:
            if (dataset == "school"
                    and props.get("P29_007") == config.KOKUDO_CLOSED_CODE):
                skipped_closed += 1
            else:
                skipped_noname += 1
            continue
        rows.append(row)
    if dataset == "school":
        rows = _assign_school_ids(rows)

    conn = masterdb.connect(db_path)
    try:
        deleted, inserted = masterdb.replace_source(
            conn, prefecture, source, rows)
    finally:
        conn.close()
    by_cat = {}
    for r in rows:
        by_cat[r["category"]] = by_cat.get(r["category"], 0) + 1
    return {
        "dataset": dataset,
        "path": path,
        "features": len(feats),
        "bad_geo": bad_geo,
        "inserted": inserted,
        "deleted": deleted,
        "skipped_closed": skipped_closed,
        "skipped_noname": skipped_noname,
        "assigned_elsewhere": elsewhere,
        "by_category": sorted(by_cat.items(), key=lambda kv: kv[1],
                              reverse=True),
    }
