"""パス・県定義・カテゴリ対応表 (SPEC 2, 4, 7)。

ロジックは持たず定数と軽い県判定ヘルパーのみ。
地理演算は標準ライブラリの自前実装 (shapely 不使用)。
"""

import json
import os

TOOL_VERSION = "0.1.0"
SEARCH_SCHEMA_VERSION = "1"

BASE_DIR = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
)
DATA_DIR = os.path.join(BASE_DIR, "data")
RAW_DIR = os.path.join(DATA_DIR, "raw")
WORK_DIR = os.path.join(DATA_DIR, "work")
OUT_DIR = os.path.join(BASE_DIR, "out")
BOUNDARIES_DIR = os.path.join(RAW_DIR, "boundaries")
REFERENCE_DIR = os.path.join(RAW_DIR, "reference")
MASTER_DB_PATH = os.path.join(WORK_DIR, "master.db")
BACKUP_DIR = os.path.join(WORK_DIR, "backup")
BACKUP_KEEP = 5

# 県定義。bbox は data/raw/boundaries/<Boundary>.geojson から算出
# (lon_min, lat_min, lon_max, lat_max)。検索DB名は <県ID小文字>.search.db に統一。
PREFECTURES = {
    "tottori": {
        "id": "tottori",
        "name_ja": "鳥取",
        "name_en": "Tottori",
        "search_db": "tottori.search.db",
        "boundary": "Tottori.geojson",
        "bbox": (133.13628, 35.05768, 134.51537, 35.61459),
    },
    "shimane": {
        "id": "shimane",
        "name_ja": "島根",
        "name_en": "Shimane",
        "search_db": "shimane.search.db",
        "boundary": "Shimane.geojson",
        "bbox": (131.66797, 34.30244, 133.38599, 37.24426),
    },
    "okayama": {
        "id": "okayama",
        "name_ja": "岡山",
        "name_en": "Okayama",
        "search_db": "okayama.search.db",
        "boundary": "Okayama.geojson",
        "bbox": (133.26679, 34.2986, 134.41316, 35.3529),
    },
    "hiroshima": {
        "id": "hiroshima",
        "name_ja": "広島",
        "name_en": "Hiroshima",
        "search_db": "hiroshima.search.db",
        "boundary": "Hiroshima.geojson",
        "bbox": (132.0366, 34.03423, 133.47064, 35.10508),
    },
    "yamaguchi": {
        "id": "yamaguchi",
        "name_ja": "山口",
        "name_en": "Yamaguchi",
        "search_db": "yamaguchi.search.db",
        "boundary": "Yamaguchi.geojson",
        "bbox": (130.775, 33.71288, 132.49121, 34.79865),
    },
}

# SPEC 4.2: アプリが前方一致 LIKE で判定するカテゴリプレフィックス
APP_CATEGORY_PREFIXES = [
    "shop:convenience",
    "amenity:toilets",
    "railway:station",
    "public_transport",
    "amenity:bus",
    "highway:bus_stop",
    "amenity:restaurant",
    "amenity:cafe",
    "amenity:fast_food",
    "tourism:",
    "amenity:parking",
    "amenity:hospital",
    "amenity:clinic",
    "amenity:doctors",
    "amenity:dentist",  # 第3段階から (国土数値情報の歯科診療所)。アプリ側の対応要否はHANDOFF参照
    "amenity:fuel",
    "leisure:park",
    "amenity:drinking_water",
    "place:",
    "named",
]

# SPEC 5.4: ソース優先度 (大きいほど優先)
SOURCE_PRIORITY = {
    "manual": 100,
    "kokudo": 50,
    "csv": 40,
    "osm": 10,
}


def source_priority(source):
    """'kokudo:medical' のような接尾辞付きソースにも対応して優先度を返す。"""
    if source in SOURCE_PRIORITY:
        return SOURCE_PRIORITY[source]
    head = (source or "").split(":", 1)[0]
    return SOURCE_PRIORITY.get(head, 0)


def search_db_path(pref_id):
    return os.path.join(OUT_DIR, PREFECTURES[pref_id]["search_db"])


# --- 国土数値情報 (第3段階・SPEC追記) ---

# 県コード (ファイル名 P04-20_<code> / P29-21_<code> 用)
KOKUDO_PREF_CODES = {
    "tottori": "31",
    "shimane": "32",
    "okayama": "33",
    "hiroshima": "34",
    "yamaguchi": "35",
}

KOKUDO_DATASETS = {
    "medical": {
        "source": "kokudo:medical",
        "label": "医療機関",
        "dir": os.path.join(RAW_DIR, "kokudo", "medical"),
        "file_template": "P04-20_{code}_GML/P04-20_{code}.geojson",
        "url": ("https://nlftp.mlit.go.jp/ksj/gml/datalist/"
                "KsjTmplt-P04-v3_0.html"),
        "year": "2020",
        "version": "3.0",
        "license": "PDL1.0",
    },
    "school": {
        "source": "kokudo:school",
        "label": "学校",
        "dir": os.path.join(RAW_DIR, "kokudo", "school"),
        "file_template": "P29-21_{code}_GML/P29-21_{code}.geojson",
        "url": ("https://nlftp.mlit.go.jp/ksj/gml/datalist/"
                "KsjTmplt-P29-v2_0.html"),
        "year": "2021",
        "version": "2.0",
        "license": "PDL1.0",
    },
}

KOKUDO_SOURCES = {d["source"]: d for d in KOKUDO_DATASETS.values()}

# 医療機関分類コード (MedClassCd。1=病院, 2=診療所, 3=歯科診療所)
# 根拠: https://nlftp.mlit.go.jp/ksj/gml/codelist/MedClassCd.html
KOKUDO_MEDICAL_CATEGORY = {
    1: "amenity:hospital",
    2: "amenity:clinic",
    3: "amenity:dentist",
}

# 学校分類コード (SchoolClassCd-v2_0)
# 根拠: https://nlftp.mlit.go.jp/ksj/gml/codelist/SchoolClassCd-v2_0.html
KOKUDO_SCHOOL_CATEGORY = {
    16001: "amenity:school",       # 小学校
    16002: "amenity:school",       # 中学校
    16003: "amenity:school",       # 中等教育学校
    16004: "amenity:school",       # 高等学校
    16005: "amenity:college",      # 高等専門学校
    16006: "amenity:college",      # 短期大学
    16007: "amenity:university",   # 大学
    16011: "amenity:kindergarten",  # 幼稚園
    16012: "amenity:school",       # 特別支援学校
    16013: "amenity:kindergarten",  # 幼保連携型認定こども園 (暫定・承認済み)
    16014: "amenity:school",       # 義務教育学校
    16015: "amenity:school",       # 各種学校 (暫定・承認済み)
    16016: "amenity:college",      # 専修学校 (暫定・承認済み)
}

# 休校コード (ClosedSchoolCode)。2=休校中のみ除外。0=調査なし・9=不明は含める
# 根拠: https://nlftp.mlit.go.jp/ksj/gml/codelist/ClosedSchoolCode.html
KOKUDO_CLOSED_CODE = 2

KOKUDO_LICENSE_LINES = [
    "出典：国土交通省国土数値情報ダウンロードサイト",
    "「国土数値情報（医療機関データ）」（国土交通省）"
    "（https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P04-v3_0.html）"
    "をもとに作成",
    "「国土数値情報（学校データ）」（国土交通省）"
    "（https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P29-v2_0.html）"
    "をもとに作成",
]


def kokudo_geojson_path(dataset, pref_id):
    ds = KOKUDO_DATASETS[dataset]
    return os.path.join(
        ds["dir"],
        ds["file_template"].format(code=KOKUDO_PREF_CODES[pref_id]))


def default_pbf_path(pref_id):
    return os.path.join(RAW_DIR, PREFECTURES[pref_id]["name_en"] + ".osm.pbf")


# --- 県判定 (行政界ポリゴン優先、なければ bbox) ---

_boundary_cache = {}


def load_boundary_rings(pref_id):
    """行政界 GeoJSON の外周リング一覧を返す。[(lon, lat), ...] のリストのリスト。"""
    if pref_id in _boundary_cache:
        return _boundary_cache[pref_id]
    path = os.path.join(BOUNDARIES_DIR, PREFECTURES[pref_id]["boundary"])
    rings = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            geo = json.load(f)
        feats = (
            geo["features"]
            if geo.get("type") == "FeatureCollection"
            else [geo]
        )
        for feat in feats:
            geom = feat["geometry"]
            if geom["type"] == "Polygon":
                rings.append([(x, y) for x, y in geom["coordinates"][0]])
            elif geom["type"] == "MultiPolygon":
                for poly in geom["coordinates"]:
                    rings.append([(x, y) for x, y in poly[0]])
    _boundary_cache[pref_id] = rings
    return rings


def point_in_ring(lon, lat, ring):
    """レイキャスティング法による点-in-リング判定。"""
    inside = False
    n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % n]
        if (y1 > lat) != (y2 > lat):
            xinters = (x2 - x1) * (lat - y1) / (y2 - y1) + x1
            if lon < xinters:
                inside = not inside
    return inside


def prefecture_for_point(lat, lon):
    """座標の属する県IDを返す。ポリゴン優先、なければ bbox。該当なしは None。"""
    cands = []
    for pref_id, pref in PREFECTURES.items():
        lon_min, lat_min, lon_max, lat_max = pref["bbox"]
        if lon_min <= lon <= lon_max and lat_min <= lat <= lat_max:
            cands.append(pref_id)
    for pref_id in cands:
        for ring in load_boundary_rings(pref_id):
            if point_in_ring(lon, lat, ring):
                return pref_id
    return cands[0] if cands else None
