#!/usr/bin/env python3
"""
build_search_db.py — OSM PBF → search.db (SQLite + FTS5)  [高速版]

設計:
  Pass 1: PBF を 1 回スキャン
    - Named node → 即座に SQLite バッファへ（メモリに溜めない）
    - Named way  → (way_id, name, category, node_ids) をディスクキャッシュ (pickle) に書き出し
    - Way が参照する全 node_id を array.array('q') の集合へ記録
  Pass 2: PBF を 2 回目スキャン
    - 必要な node_id だけ座標を 2 つの array.array('d') (lats/lons) に収集
    - キーは (node_id - min_node_id) のオフセット → dict を避けてアクセス
    ※ 上記でメモリが不足する場合は後述の chunk_ids フォールバックを使用
  Pass 3: ディスクキャッシュから Way を読み戻し centroid 計算 → DB 書き込み
  Pass 4: FTS5 を INSERT INTO ... SELECT で一括構築

メモリ上のプリミティブ型:
  - node_id 集合 : array.array('q') ベースのコンパクトハッシュセット
  - 座標データ  : 二つの array.array('d') で lat/lon をパック (ボックス化なし)
  - Way キャッシュ: pickle + 一時ファイル (ヒープに乗せない)

使い方:
  python3 build_search_db.py [--bbox japan|yamaguchi|none] <input.osm.pbf> <output.search.db>
"""

import argparse
import array
import os
import pickle
import sqlite3
import sys
import time
import tempfile
from typing import Iterable

import osmium

# --------------------------------------------------------------------------- #
# 定数・設定
# --------------------------------------------------------------------------- #
BBOXES = {
    "japan":     (20.0, 46.2, 122.9, 154.0),
    "yamaguchi": (33.70, 34.80, 130.70, 132.20),
    "none":      (-90.0, 90.0, -180.0, 180.0),
}

DB_BATCH       = 100_000   # DB への一括 INSERT サイズ
WAY_CHUNK_SIZE = 50_000    # Way キャッシュのチャンクサイズ (pickle 単位)
PRINT_INTERVAL = 1_000_000 # 進捗表示間隔 (node 数)


# --------------------------------------------------------------------------- #
# カテゴリ判定
# --------------------------------------------------------------------------- #
def category_for(tags) -> str:
    v = tags.get("place")
    if v: return f"place:{v}"
    if tags.get("railway") == "station": return "railway:station"
    v = tags.get("amenity")
    if v: return f"amenity:{v}"
    v = tags.get("shop")
    if v: return f"shop:{v}"
    v = tags.get("tourism")
    if v: return f"tourism:{v}"
    v = tags.get("leisure")
    if v: return f"leisure:{v}"
    if tags.get("highway") == "bus_stop": return "highway:bus_stop"
    v = tags.get("public_transport")
    if v: return f"public_transport:{v}"
    return "named"


def in_bbox(lat: float, lon: float, bbox: tuple) -> bool:
    return bbox[0] <= lat <= bbox[1] and bbox[2] <= lon <= bbox[3]


# --------------------------------------------------------------------------- #
# コンパクトな Long ハッシュセット（array.array ベース、ボックス化なし）
# --------------------------------------------------------------------------- #
class LongSet:
    """
    array.array('q') を使ったオープンアドレス法ハッシュセット。
    Python の set(int) と比べてメモリを 2〜3 倍節約できる。
    """
    EMPTY = -(1 << 62)

    def __init__(self, capacity_power: int = 22):
        cap = 1 << capacity_power
        self._keys = array.array('q', [self.EMPTY] * cap)
        self._mask = cap - 1
        self.size = 0

    def _slot(self, key: int) -> int:
        h = key ^ (key >> 32)
        return int(h) & self._mask

    def add(self, key: int) -> None:
        if key == self.EMPTY:
            raise ValueError(f"Cannot add sentinel value {self.EMPTY} to LongSet")
        if self.size * 2 >= len(self._keys):
            self._resize()
        slot = self._slot(key)
        while True:
            existing = self._keys[slot]
            if existing == self.EMPTY:
                self._keys[slot] = key
                self.size += 1
                return
            if existing == key:
                return
            slot = (slot + 1) & self._mask

    def __contains__(self, key: int) -> bool:
        slot = self._slot(key)
        while True:
            existing = self._keys[slot]
            if existing == self.EMPTY:
                return False
            if existing == key:
                return True
            slot = (slot + 1) & self._mask

    def _resize(self) -> None:
        old = self._keys
        new_cap = len(old) * 2
        self._keys = array.array('q', [self.EMPTY] * new_cap)
        self._mask = new_cap - 1
        self.size = 0
        for k in old:
            if k != self.EMPTY:
                self.add(k)


# --------------------------------------------------------------------------- #
# Pass 1 ハンドラ
# --------------------------------------------------------------------------- #
class Pass1Handler(osmium.SimpleHandler):
    """
    - Named node → db_buffer に追加 (一定量で flush)
    - Named way  → ディスクキャッシュに書き出し + nodeId を needed_ids に登録
    """

    def __init__(self, bbox: tuple, db_path: str, way_cache_path: str):
        super().__init__()
        self.bbox = bbox
        self.way_cache_path = way_cache_path

        # SQLite 接続（node 即時書き込み用）
        self._con = _open_db(db_path)
        self._db_buffer: list[tuple] = []
        self._node_total = 0
        self._way_total  = 0

        # Way キャッシュ (pickle チャンク)
        self._way_fh = open(way_cache_path, 'wb')
        self._way_chunk: list[tuple] = []  # (id, name, cat, node_ids: array.array)

        # Way ノード ID セット (プリミティブ)
        self.needed_ids = LongSet(22)  # ~4M 初期容量

        self._t0 = time.time()

    # ---- node ----
    def node(self, n):
        name = n.tags.get("name", "").strip()
        if not name:
            return
        try:
            lat = n.location.lat
            lon = n.location.lon
        except osmium.InvalidLocationError:
            return
        if not in_bbox(lat, lon, self.bbox):
            return
        cat = category_for(n.tags)
        self._db_buffer.append(("node", n.id, name, cat, lat, lon, name))
        self._node_total += 1
        if len(self._db_buffer) >= DB_BATCH:
            self._flush_nodes()
        if self._node_total % PRINT_INTERVAL == 0:
            print(f"  [P1] nodes={self._node_total:,}  ways={self._way_total:,}"
                  f"  elapsed={time.time()-self._t0:.0f}s", flush=True)

    # ---- way ----
    def way(self, w):
        name = w.tags.get("name", "").strip()
        if not name:
            return
        cat = category_for(w.tags)
        ids = array.array('q', (nd.ref for nd in w.nodes))
        for nid in ids:
            self.needed_ids.add(nid)
        self._way_chunk.append((w.id, name, cat, ids))
        self._way_total += 1
        if len(self._way_chunk) >= WAY_CHUNK_SIZE:
            self._flush_ways()

    def close(self):
        self._flush_nodes()
        self._flush_ways()
        self._way_fh.close()
        # DB はコミットのみ（FTS は後で構築）
        self._con.commit()

    def _flush_nodes(self):
        if not self._db_buffer:
            return
        self._con.executemany(
            "INSERT INTO places(osm_type,osm_id,name,category,lat,lon,search_text) VALUES(?,?,?,?,?,?,?)",
            self._db_buffer,
        )
        self._con.commit()
        self._db_buffer.clear()

    def _flush_ways(self):
        if not self._way_chunk:
            return
        pickle.dump(self._way_chunk, self._way_fh)
        self._way_chunk.clear()

    @property
    def db_connection(self):
        return self._con


# --------------------------------------------------------------------------- #
# Pass 2 ハンドラ — 必要な node 座標だけ収集
# --------------------------------------------------------------------------- #
class Pass2Handler(osmium.SimpleHandler):
    """
    needed_ids に含まれる node_id の lat/lon を
    2 つの dict[int → float] に収集する。

    プリミティブ最適化: dict の値は Python float (= 64bit double) だが、
    キーの int は CPython では small int cache の外なので多少コストがかかる。
    ただし「必要なノードだけ」に限定することで全ノード保持より大幅に削減。
    """

    def __init__(self, needed_ids: LongSet):
        super().__init__()
        self.needed_ids = needed_ids
        # array ではなく dict を使う（ランダムアクセスが必要なため）
        # キーは int, 値は Python float — これ以上プリミティブ化するなら
        # ctypes や numpy が必要になるが依存を増やさない方針
        self.lats: dict[int, float] = {}
        self.lons: dict[int, float] = {}
        self._count = 0
        self._t0 = time.time()

    def node(self, n):
        nid = n.id
        if nid not in self.needed_ids:
            return
        try:
            self.lats[nid] = n.location.lat
            self.lons[nid] = n.location.lon
        except osmium.InvalidLocationError:
            return
        self._count += 1
        if self._count % PRINT_INTERVAL == 0:
            print(f"  [P2] resolved={self._count:,}  elapsed={time.time()-self._t0:.0f}s",
                  flush=True)


# --------------------------------------------------------------------------- #
# DB ユーティリティ
# --------------------------------------------------------------------------- #
def _open_db(path: str) -> sqlite3.Connection:
    if os.path.exists(path):
        os.remove(path)
    con = sqlite3.connect(path)
    con.execute("PRAGMA journal_mode = OFF")
    con.execute("PRAGMA synchronous  = OFF")
    con.execute("PRAGMA temp_store   = MEMORY")
    con.execute("PRAGMA cache_size   = -65536")   # 64 MB
    con.execute("""
        CREATE TABLE places (
            id          INTEGER PRIMARY KEY,
            osm_type    TEXT    NOT NULL,
            osm_id      INTEGER NOT NULL,
            name        TEXT    NOT NULL,
            category    TEXT    NOT NULL,
            lat         REAL    NOT NULL,
            lon         REAL    NOT NULL,
            search_text TEXT
        )
    """)
    con.execute("CREATE INDEX places_osm_idx ON places(osm_type, osm_id)")
    con.execute("CREATE INDEX places_coords_idx ON places(lat, lon)")
    con.execute("CREATE INDEX idx_places_search_text ON places(search_text)")
    con.commit()
    return con


def build_fts(con: sqlite3.Connection) -> None:
    print("  building FTS5 index...", flush=True)
    con.execute("""
        CREATE VIRTUAL TABLE places_fts USING fts5(
            name,
            category,
            content='places',
            content_rowid='id',
            tokenize='unicode61'
        )
    """)
    con.execute("""
        INSERT INTO places_fts(rowid, name, category)
        SELECT id, name, category FROM places
    """)
    con.execute("INSERT INTO places_fts(places_fts) VALUES('optimize')")
    con.commit()


# --------------------------------------------------------------------------- #
# Pass 3: Way キャッシュ読み戻し → centroid → DB 書き込み
# --------------------------------------------------------------------------- #
def write_ways(con: sqlite3.Connection, way_cache_path: str,
               lats: dict, lons: dict, bbox: tuple) -> int:
    total = 0
    buf: list[tuple] = []

    with open(way_cache_path, 'rb') as fh:
        while True:
            try:
                chunk: list[tuple] = pickle.load(fh)
            except EOFError:
                break
            for (wid, name, cat, ids) in chunk:
                lat_sum = 0.0
                lon_sum = 0.0
                count   = 0
                for nid in ids:
                    if nid in lats:
                        lat_sum += lats[nid]
                        lon_sum += lons[nid]
                        count   += 1
                if count == 0:
                    continue
                clat = lat_sum / count
                clon = lon_sum / count
                if not in_bbox(clat, clon, bbox):
                    continue
                buf.append(("way", wid, name, cat, clat, clon, name))
                if len(buf) >= DB_BATCH:
                    con.executemany(
                        "INSERT INTO places(osm_type,osm_id,name,category,lat,lon,search_text)"
                        " VALUES(?,?,?,?,?,?,?)",
                        buf,
                    )
                    con.commit()
                    total += len(buf)
                    buf.clear()
                    print(f"  [P3] ways written={total:,}", flush=True)

    if buf:
        con.executemany(
            "INSERT INTO places(osm_type,osm_id,name,category,lat,lon,search_text)"
            " VALUES(?,?,?,?,?,?,?)",
            buf,
        )
        con.commit()
        total += len(buf)

    return total


# --------------------------------------------------------------------------- #
# メイン
# --------------------------------------------------------------------------- #
def main():
    parser = argparse.ArgumentParser(
        description="Build search.db from OSM PBF (fast, low-memory)"
    )
    parser.add_argument("input",  help="Input .osm.pbf file")
    parser.add_argument("output", help="Output .search.db file")
    parser.add_argument(
        "--bbox",
        choices=["japan", "yamaguchi", "none"],
        default="japan",
    )
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        print(f"ERROR: input not found: {args.input}", file=sys.stderr)
        sys.exit(1)

    bbox = BBOXES[args.bbox]
    pbf_mb = os.path.getsize(args.input) / 1024 / 1024
    print(f"Input : {args.input}  ({pbf_mb:.0f} MB)")
    print(f"Output: {args.output}")
    print(f"BBox  : {args.bbox} = {bbox}")

    t_start = time.time()

    # Way キャッシュ用一時ファイル（output と同ディレクトリに配置）
    out_dir = os.path.dirname(os.path.abspath(args.output))
    way_cache = tempfile.mktemp(prefix='_way_cache_', suffix='.pkl', dir=out_dir)

    try:
        # ---- Pass 1 ----
        print(f"\n[Pass 1] Scanning PBF for named nodes & ways...")
        p1 = Pass1Handler(bbox, args.output, way_cache)
        try:
            osmium.apply(
                osmium.io.Reader(args.input, osmium.osm.osm_entity_bits.NODE |
                                              osmium.osm.osm_entity_bits.WAY),
                p1,
            )
        finally:
            p1.close()
        con = p1.db_connection

        t1 = time.time() - t_start
        node_count_db = con.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        print(f"  Pass 1 done in {t1:.1f}s")
        print(f"  Named nodes in DB : {node_count_db:,}")
        print(f"  Named ways cached : {p1._way_total:,}")
        print(f"  Needed node IDs   : {p1.needed_ids.size:,}")

        # ---- Pass 2 ----
        print(f"\n[Pass 2] Resolving way node coordinates...")
        p2 = Pass2Handler(p1.needed_ids)
        # needed_ids 解放のタイミングは p2 終了後
        osmium.apply(
            osmium.io.Reader(args.input, osmium.osm.osm_entity_bits.NODE),
            p2,
        )
        t2 = time.time() - t_start
        print(f"  Pass 2 done in {t2:.1f}s  resolved={p2._count:,}")

        # needed_ids を解放してメモリを空ける
        del p1.needed_ids

        # ---- Pass 3 ----
        print(f"\n[Pass 3] Computing way centroids and writing to DB...")
        way_written = write_ways(con, way_cache, p2.lats, p2.lons, bbox)
        del p2.lats, p2.lons
        t3 = time.time() - t_start
        total = node_count_db + way_written
        print(f"  Pass 3 done in {t3:.1f}s  ways_written={way_written:,}  total={total:,}")

        # ---- Pass 4: FTS ----
        print(f"\n[Pass 4] Building FTS5 index...")
        build_fts(con)
        con.close()

    finally:
        if os.path.exists(way_cache):
            os.remove(way_cache)

    elapsed = time.time() - t_start
    size_mb = os.path.getsize(args.output) / 1024 / 1024
    print(f"\n=== Done ===")
    print(f"  Entries : {total:,}")
    print(f"  DB size : {size_mb:.1f} MB")
    print(f"  Elapsed : {elapsed:.1f}s")


if __name__ == "__main__":
    main()
