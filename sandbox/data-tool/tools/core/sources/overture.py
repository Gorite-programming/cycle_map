"""Overture Maps Foundation (Placesテーマ) → 共通スキーマ。

- AWS S3 (s3://overturemaps-us-west-2/release/latest/theme=places/type=place/*) から
  DuckDB を用いて特定県の bbox / ポリゴン内の POI を直接ストリーミング抽出。
- Overture の taxonomy / basic_category およびブランド・名称ルールに基づき、
  CycleMap (OSM形式) のカテゴリに正規化。
- 住所、名称、座標 (WGS84) を共通スキーマ形式で整形して返す。
"""

import json
import logging
import os
import re
from typing import Any, Dict, List, Optional, Tuple

import duckdb

from .. import config, masterdb

logger = logging.getLogger(__name__)

OVERTURE_S3_PATH = "s3://overturemaps-us-west-2/release/2026-09-23.1/theme=places/type=place/*"

# Overture taxonomy / basic_category -> OSMカテゴリ対応表
OVERTURE_CATEGORY_MAP: Dict[str, str] = {
    # コンビニ
    "convenience_store": "shop:convenience",
    
    # 飲食・カフェ
    "restaurant": "amenity:restaurant",
    "japanese_restaurant": "amenity:restaurant",
    "ramen_restaurant": "amenity:restaurant",
    "soba_restaurant": "amenity:restaurant",
    "udon_restaurant": "amenity:restaurant",
    "sushi_restaurant": "amenity:restaurant",
    "chinese_restaurant": "amenity:restaurant",
    "italian_restaurant": "amenity:restaurant",
    "french_restaurant": "amenity:restaurant",
    "barbecue_restaurant": "amenity:restaurant",
    "burger_restaurant": "amenity:fast_food",
    "fast_food_restaurant": "amenity:fast_food",
    "fast_food": "amenity:fast_food",
    "family_style_restaurant": "amenity:restaurant",
    "diner": "amenity:restaurant",
    "buffet_restaurant": "amenity:restaurant",
    "cafe": "amenity:cafe",
    "coffee_shop": "amenity:cafe",
    "tea_room": "amenity:cafe",
    "bakery": "shop:bakery",
    "ice_cream_shop": "amenity:ice_cream",
    "bar": "amenity:bar",
    "pub": "amenity:pub",
    "izakaya": "amenity:restaurant",

    # スーパー・日用品・自転車
    "supermarket": "shop:supermarket",
    "grocery_store": "shop:supermarket",
    "bicycle_shop": "shop:bicycle",
    "bicycle_repair_station": "amenity:bicycle_repair_station",
    "pharmacy": "amenity:pharmacy",
    "drugstore": "shop:chemist",

    # 医療
    "hospital": "amenity:hospital",
    "general_hospital": "amenity:hospital",
    "clinic": "amenity:clinic",
    "medical_clinic": "amenity:clinic",
    "dental_clinic": "amenity:dentist",
    "dentist": "amenity:dentist",

    # 交通・補給
    "gas_station": "amenity:fuel",
    "ev_charging_station": "amenity:charging_station",
    "train_station": "railway:station",
    "subway_station": "railway:station",
    "commuter_rail_station": "railway:station",
    "bus_station": "amenity:bus_station",
    "bus_stop": "highway:bus_stop",
    "parking": "amenity:parking",
    "parking_lot": "amenity:parking",

    # 観光・レジャー・温泉
    "tourist_attraction": "tourism:attraction",
    "viewpoint": "tourism:viewpoint",
    "museum": "tourism:museum",
    "monument_or_memorial": "tourism:artwork",
    "historical_place": "tourism:attraction",
    "castle": "tourism:attraction",
    "shrine": "tourism:attraction",
    "shinto_shrine": "tourism:attraction",
    "temple_or_buddhist_temple": "tourism:attraction",
    "park": "leisure:park",
    "state_park": "leisure:park",
    "national_park": "leisure:park",
    "campground": "tourism:camp_site",
    "public_bath": "amenity:public_bath",
    "hot_spring": "amenity:public_bath",
    "rest_area": "tourism:attraction",
    "roadside_station": "tourism:attraction",

    # 宿泊
    "hotel": "tourism:hotel",
    "motel": "tourism:hotel",
    "hostel": "tourism:hostel",
    "bed_and_breakfast": "tourism:guest_house",
    "resort": "tourism:resort",

    # 公衆トイレ
    "public_toilet": "amenity:toilets",
    "restroom": "amenity:toilets",
    "toilets": "amenity:toilets",
}

# 名称によるコンビニ等の強制補正パターン
CONVENIENCE_PATTERNS = [
    re.compile(r"セブン[-−ー]?イレブン|7[-−ー]?ELEVEN", re.I),
    re.compile(r"ファミリーマート|FamilyMart", re.I),
    re.compile(r"ローソン|LAWSON", re.I),
    re.compile(r"ミニストップ|MINISTOP", re.I),
    re.compile(r"デイリーヤマザキ|Daily[- ]?Yamazaki", re.I),
    re.compile(r"ポプラ|POPLAR", re.I),
    re.compile(r"セイコーマート|Seicomart", re.I),
]


def resolve_category(name: str, overture_cat: Optional[str], basic_cat: Optional[str]) -> str:
    """Overture のカテゴリと名称から、CycleMap (OSM形式) のカテゴリを決定する。"""
    # 1. 大手コンビニ名称パターンの優先判定
    for pat in CONVENIENCE_PATTERNS:
        if pat.search(name):
            return "shop:convenience"

    # 2. 道の駅
    if "道の駅" in name:
        return "tourism:attraction"

    # 3. 温泉
    if "温泉" in name or "銭湯" in name or "の湯" in name:
        return "amenity:public_bath"

    # 4. Overture taxonomy mapping
    if overture_cat and overture_cat in OVERTURE_CATEGORY_MAP:
        return OVERTURE_CATEGORY_MAP[overture_cat]

    # 5. basic_category mapping
    if basic_cat and basic_cat in OVERTURE_CATEGORY_MAP:
        return OVERTURE_CATEGORY_MAP[basic_cat]

    # 6. 未知・一般施設
    return "named"


def format_address(freeform: Optional[str], locality: Optional[str], region: Optional[str]) -> Optional[str]:
    """構造化住所を1つの読みやすい住所文字列に結合する。"""
    parts = []
    if region and region not in (freeform or ""):
        parts.append(region.strip())
    if locality and locality not in (freeform or ""):
        parts.append(locality.strip())
    if freeform:
        parts.append(freeform.strip())

    res = " ".join(parts).strip()
    return res if res else None


def fetch_overture_places_for_bbox(
    bbox: Tuple[float, float, float, float],
    prefecture_id: str,
    imported_at: str,
    s3_path: str = OVERTURE_S3_PATH,
    min_confidence: float = 0.6,
) -> List[Dict[str, Any]]:
    """DuckDB を用いて S3 から指定 bbox の Overture POI を直接抽出・正規化する。
    
    Args:
        bbox: (min_lon, min_lat, max_lon, max_lat)
        prefecture_id: 'yamaguchi', 'hiroshima' 等
        imported_at: 取り込み日時文字列
        s3_path: Overture Parquet S3 glob パス
        min_confidence: 抽出する最低信頼度 (0.0〜1.0)
    
    Returns:
        master.db (poiテーブル) 投入用の辞書リスト
    """
    min_lon, min_lat, max_lon, max_lat = bbox

    con = duckdb.connect()
    con.execute("INSTALL httpfs; LOAD httpfs;")
    con.execute("SET s3_region = 'us-west-2';")

    query = f"""
    SELECT 
        id AS overture_id,
        names.primary AS name,
        taxonomy.primary AS overture_category,
        basic_category,
        addresses[1].freeform AS address_freeform,
        addresses[1].locality AS locality,
        addresses[1].region AS region,
        addresses[1].postcode AS postcode,
        bbox.xmin AS lon,
        bbox.ymin AS lat,
        confidence
    FROM read_parquet('{s3_path}', filename=true, hive_partitioning=1)
    WHERE bbox.xmin >= {min_lon} AND bbox.xmax <= {max_lon}
      AND bbox.ymin >= {min_lat} AND bbox.ymax <= {max_lat}
      AND names.primary IS NOT NULL
      AND (confidence IS NULL OR confidence >= {min_confidence});
    """

    logger.info("Executing Overture query for %s bbox=%s ...", prefecture_id, bbox)
    rows = con.execute(query).fetchall()
    logger.info("Retrieved %d raw POIs from Overture", len(rows))

    results: List[Dict[str, Any]] = []
    for r in rows:
        (
            overture_id,
            name,
            overture_cat,
            basic_cat,
            freeform,
            locality,
            region,
            postcode,
            lon,
            lat,
            confidence,
        ) = r

        name_str = (name or "").strip()
        if not name_str:
            continue

        cat = resolve_category(name_str, overture_cat, basic_cat)
        full_addr = format_address(freeform, locality, region)

        # 共通スキーマ行を生成
        row = {
            "name": name_str,
            "category": cat,
            "lat": float(lat),
            "lon": float(lon),
            "address": full_addr,
            "prefecture": prefecture_id,
            "source": "overture:places",
            "source_id": overture_id,
            "priority": config.source_priority("overture"),
            "imported_at": imported_at,
            "kana": None,
        }
        results.append(row)

    return results


def import_overture(
    prefecture_id: str,
    db_path: Optional[str] = None,
    min_confidence: float = 0.6,
    s3_path: str = OVERTURE_S3_PATH,
) -> Dict[str, Any]:
    """指定都道府県の Overture POI を取得し、master.db に取り込む。
    
    Returns:
        取り込み結果サマリー辞書
    """
    if prefecture_id not in config.PREFECTURES:
        raise ValueError(f"未知の都道府県ID: {prefecture_id}")

    pref_def = config.PREFECTURES[prefecture_id]
    bbox = pref_def["bbox"]
    imported_at = masterdb.utcnow()

    conn = masterdb.connect(db_path)
    try:
        masterdb.init_db(db_path)

        rows = fetch_overture_places_for_bbox(
            bbox=bbox,
            prefecture_id=prefecture_id,
            imported_at=imported_at,
            s3_path=s3_path,
            min_confidence=min_confidence,
        )

        deleted, inserted = masterdb.replace_source(conn, prefecture_id, "overture:places", rows)

        by_cat: Dict[str, int] = {}
        for r in rows:
            c = r["category"]
            by_cat[c] = by_cat.get(c, 0) + 1

        return {
            "prefecture": prefecture_id,
            "fetched": len(rows),
            "deleted": deleted,
            "inserted": inserted,
            "by_category": by_cat,
        }
    finally:
        conn.close()
