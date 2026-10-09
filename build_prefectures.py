#!/usr/bin/env python3
"""Build resumable per-prefecture offline cycling packages."""
from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import logging
import os
import re
import shutil
import shlex
import sqlite3
import subprocess
import sys
import time
import traceback
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

# ------------------------------ CONFIG ---------------------------------
# SOURCE_PBF: ルート直下シンボリックリンク（macOS/Linux）を優先し、
# Windows ではシンボリックリンクが機能しないため data/ 内の実体にフォールバックする。
_root_pbf = Path("japan-latest.osm.pbf")
_data_pbf = Path("data/japan-latest.osm.pbf")
SOURCE_PBF = _root_pbf if _root_pbf.is_file() else _data_pbf

# OSM_IMPORTER: macOS/Linux はシェルスクリプト、Windows は .bat を選択する。
# スクリプト直下の osm-importer/ (シンボリックリンク) と application/ 配下の実体の両方を探索する。
_importer_unix_candidates = [
    Path("osm-importer/build/install/osm-importer/bin/osm-importer"),
    Path("application/osm-importer/build/install/osm-importer/bin/osm-importer"),
]
if os.name == "nt":
    _importer_candidates = [p.with_suffix(".bat") for p in _importer_unix_candidates]
else:
    _importer_candidates = _importer_unix_candidates
OSM_IMPORTER = next((p for p in _importer_candidates if p.exists()), _importer_candidates[0])

# BUILD_SEARCH_DB: ルート直下のシンボリックリンクではなく実体を優先（Windows対応）。
_search_db_candidates = [
    Path("application/osm-importer/build_search_db.py"),
    Path("build_search_db.py"),
]
BUILD_SEARCH_DB = next((p for p in _search_db_candidates if p.is_file() and p.stat().st_size > 0), _search_db_candidates[0])
SEARCH_DB_CALLABLE = "build_search_db:build_search_db"  # module:function

# Heap size passed to the Kotlin osm-importer subprocess (via JAVA_OPTS).
# Increase this if a larger prefecture (e.g. a big one) hits OutOfMemoryError.
IMPORTER_JAVA_OPTS = "-Xmx6g"

BOUNDARY_DIR = Path("boundaries")
BOUNDARY_SOURCE = BOUNDARY_DIR / "prefectures.geojson"
BOUNDARY_URL = (
    "https://geo.maderaojen.me/datasets/"
    "jp-prefectures/releases/v1/2026-08-04.1/data.geojson"
)
BOUNDARY_SHA256 = "c823cde901bb077cd3f861632bbcdd3676283d9e6f30c76b0d0fab330ebf157e"

WORK_DIR = Path("work")
OUTPUT_DIR = Path("packages")
LOG_DIR = Path("logs")
DATA_TOOL_OUT_DIR = Path("sandbox/data-tool/out")

PACKAGE_LICENSE_TEXT = """\
CycleMap Data Package License & Attribution
===========================================

This package contains routing graphs and search database files for offline navigation.
The data is compiled and derived from the following open data sources:

1. OpenStreetMap (OSM)
   - License: Open Database License (ODbL) 1.0
   - Attribution: (c) OpenStreetMap contributors
   - URL: https://www.openstreetmap.org/copyright

2. Overture Maps Foundation (Places Theme)
   - License: Community Data License Agreement - Permissive - Version 2.0 (CDLA-Permissive-2.0)
   - Attribution: (c) Overture Maps Foundation
   - URL: https://overturemaps.org/

3. 国土数値情報 (National Land Numerical Information)
   - 医療機関データ 第3.0版 (2020年)、学校データ 第2.0版 (2021年)
   - 国土交通省 (https://nlftp.mlit.go.jp/ksj/) をもとに加工して作成
   - License: 国土数値情報利用規約 (PDL1.0準拠)
   - URL: https://nlftp.mlit.go.jp/ksj/other/agreement.html
"""

PREFECTURES = [
    {"name": "Tottori", "jp": "鳥取県", "code": "31"},
    {"name": "Shimane", "jp": "島根県", "code": "32"},
    {"name": "Okayama", "jp": "岡山県", "code": "33"},
    {"name": "Hiroshima", "jp": "広島県", "code": "34"},
    {"name": "Yamaguchi", "jp": "山口県", "code": "35"},
]

LOG = logging.getLogger("pref-builder")


def verify_search_db(db_path: Path) -> Dict[str, Any]:
    """Validate schema, indexes, and integrity of search.db before packaging."""
    if not db_path.is_file() or db_path.stat().st_size == 0:
        raise FileNotFoundError(f"Search DB missing or empty: {db_path}")

    con = sqlite3.connect(f"file:{db_path.resolve()}?mode=ro", uri=True)
    try:
        cur = con.cursor()
        # 1. PRAGMA integrity_check
        cur.execute("PRAGMA integrity_check")
        row = cur.fetchone()
        if not row or row[0] != "ok":
            raise ValueError(f"SQLite PRAGMA integrity_check failed for {db_path}: {row}")

        # 2. places table presence
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='places'")
        if not cur.fetchone():
            raise ValueError(f"Table 'places' not found in {db_path}")

        # 3. Required columns
        cur.execute("PRAGMA table_info(places)")
        cols = {r[1] for r in cur.fetchall()}
        required_cols = {"name", "category", "lat", "lon", "search_text"}
        missing_cols = required_cols - cols
        if missing_cols:
            raise ValueError(f"Missing required columns in 'places' table of {db_path}: {missing_cols}")

        # 4. places_coords_idx index presence
        cur.execute("SELECT name FROM sqlite_master WHERE type='index' AND name='places_coords_idx'")
        if not cur.fetchone():
            raise ValueError(f"Index 'places_coords_idx' not found in {db_path}")

        # 5. Row count > 0
        cur.execute("SELECT COUNT(*) FROM places")
        row_count = cur.fetchone()[0]
        if row_count <= 0:
            raise ValueError(f"Search DB has no records in 'places': {db_path}")

        # 6. meta table (optional, extract if present)
        meta: Dict[str, Any] = {}
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='meta'")
        if cur.fetchone():
            cur.execute("SELECT key, value FROM meta")
            meta = dict(cur.fetchall())

        return {
            "path": db_path,
            "row_count": row_count,
            "columns": sorted(cols),
            "meta": meta,
        }
    finally:
        con.close()


def generate_manifest(name: str, db_stats: Dict[str, Any]) -> str:
    """Generate manifest.json content for prefecture package."""
    manifest = {
        "format_version": 1,
        "prefecture": name,
        "generated_at": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "files": {
            "search_db": f"{name}.search.db",
            "graph": f"{name}.graph",
            "graph_idx": f"{name}.graph.idx",
        },
        "search_db": {
            "places_count": db_stats.get("row_count", 0),
            "meta": db_stats.get("meta", {}),
        },
        "sources": [
            {
                "name": "OpenStreetMap",
                "license": "ODbL 1.0",
                "url": "https://www.openstreetmap.org/copyright",
            },
            {
                "name": "Overture Maps Places",
                "license": "CDLA-Permissive-2.0",
                "url": "https://overturemaps.org/",
            },
            {
                "name": "MLIT Kokudo Suuchi Jouhou (Medical/School)",
                "license": "PDL1.0",
                "url": "https://nlftp.mlit.go.jp/ksj/",
            },
        ],
    }
    return json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"



def fmt_size(n: int) -> str:
    x = float(n)
    for u in ("B", "KiB", "MiB", "GiB", "TiB"):
        if x < 1024 or u == "TiB":
            return f"{x:.1f} {u}"
        x /= 1024
    return str(n)


def psize(p: Path) -> str:
    return fmt_size(p.stat().st_size) if p.exists() else "missing"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def run_live(cmd: Sequence[str], log_file: Path, env: Optional[Dict[str, str]] = None) -> int:
    args = [str(x) for x in cmd]
    LOG.info("$ %s", shlex.join(args))
    log_file.parent.mkdir(parents=True, exist_ok=True)
    with log_file.open("a", encoding="utf-8") as log:
        log.write("\n" + "=" * 80 + "\n")
        log.write("$ " + shlex.join(args) + "\n")
        proc = subprocess.Popen(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            env=env,
        )
        assert proc.stdout is not None
        for line in proc.stdout:
            line = line.rstrip("\n")
            log.write(line + "\n")
            log.flush()
            if line:
                LOG.info("| %s", line)
        rc = proc.wait()
        log.write(f"[exit={rc}]\n")
        return rc


def download_boundaries() -> None:
    BOUNDARY_DIR.mkdir(parents=True, exist_ok=True)
    if BOUNDARY_SOURCE.exists():
        return
    LOG.info("Downloading boundary GeoJSON: %s", BOUNDARY_URL)
    tmp = BOUNDARY_SOURCE.with_suffix(".download")
    req = urllib.request.Request(BOUNDARY_URL, headers={"User-Agent": "offline-cycling-map-builder/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r, tmp.open("wb") as out:
            shutil.copyfileobj(r, out, 1024 * 1024)
        os.replace(tmp, BOUNDARY_SOURCE)
    finally:
        tmp.unlink(missing_ok=True)
    actual = sha256(BOUNDARY_SOURCE)
    if actual.lower() != BOUNDARY_SHA256.lower():
        BOUNDARY_SOURCE.unlink(missing_ok=True)
        raise RuntimeError(f"Boundary SHA-256 mismatch: expected {BOUNDARY_SHA256}, got {actual}")


def norm(v: Any) -> str:
    return str(v).strip().replace(" ", "").replace("　", "")


def make_pref_boundaries() -> None:
    with BOUNDARY_SOURCE.open("r", encoding="utf-8") as f:
        fc = json.load(f)
    if fc.get("type") != "FeatureCollection" or not isinstance(fc.get("features"), list):
        raise ValueError("Boundary source is not a GeoJSON FeatureCollection")
    if len(fc["features"]) < 47:
        raise ValueError(f"Expected 47 prefectures, got {len(fc['features'])}")

    for pref in PREFECTURES:
        out = BOUNDARY_DIR / f"{pref['name']}.geojson"
        if out.exists():
            continue
        code = pref["code"].zfill(2)
        matches = []
        for feat in fc["features"]:
            props = feat.get("properties") or {}
            values = {norm(v) for v in props.values() if v is not None}
            jp = norm(pref["jp"])
            jp0 = jp[:-1] if jp[-1:] in "都道府県" else jp
            if jp in values or jp0 in values or norm(pref["name"]) in values:
                matches.append(feat)
                continue
            for k, v in props.items():
                k2 = norm(k).lower()
                if any(x in k2 for x in ("code", "jis", "pref")) and norm(v).zfill(2) == code:
                    matches.append(feat)
                    break
        if len(matches) != 1:
            raise ValueError(f"Could not uniquely find {pref['name']}: {len(matches)} matches")
        geom = matches[0].get("geometry") or {}
        if geom.get("type") not in ("Polygon", "MultiPolygon"):
            raise ValueError(f"Invalid boundary geometry for {pref['name']}: {geom.get('type')!r}")
        with out.open("w", encoding="utf-8") as f:
            json.dump({"type": "FeatureCollection", "features": [matches[0]]}, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")
        LOG.info("Boundary ready: %s (%s)", out, psize(out))


def osm_stats(pbf: Path) -> Dict[str, Any]:
    r = subprocess.run(
        ["osmium", "fileinfo", "-e", "-j", "--no-crc", str(pbf)],
        capture_output=True,
        text=True,
        check=False,
    )
    if r.returncode != 0:
        raise RuntimeError(f"osmium fileinfo failed:\n{r.stdout}\n{r.stderr}")
    return json.loads(r.stdout)


def log_osm_stats(name: str, info: Dict[str, Any]) -> None:
    counts = info.get("data", {}).get("count", {})
    LOG.info(
        "[%s] OSM: nodes=%s ways=%s relations=%s",
        name,
        f"{counts.get('nodes', 0):,}",
        f"{counts.get('ways', 0):,}",
        f"{counts.get('relations', 0):,}",
    )


def build_search_db(input_pbf: Path, output_db: Path) -> None:
    LOG.info("Building search DB: %s", output_db)
    rc = subprocess.run(
        [sys.executable, str(BUILD_SEARCH_DB), "--bbox", "none", str(input_pbf), str(output_db)],
        capture_output=True, text=True,
    )
    if rc.returncode != 0:
        raise RuntimeError(f"{BUILD_SEARCH_DB} failed (exit {rc.returncode}):\n{rc.stdout}\n{rc.stderr}")
    if not output_db.exists():
        raise RuntimeError(f"{BUILD_SEARCH_DB} did not create {output_db}")


def db_count(db: Path, table: str) -> Optional[int]:
    try:
        con = sqlite3.connect(str(db))
        try:
            return int(con.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0])
        finally:
            con.close()
    except sqlite3.Error:
        return None


def log_search_db_stats(db: Path, name: str) -> None:
    p = db_count(db, "places")
    f = db_count(db, "places_fts")
    LOG.info(
        "[%s] search.db: %s, places=%s, places_fts=%s",
        name,
        psize(db),
        f"{p:,}" if p is not None else "n/a",
        f"{f:,}" if f is not None else "n/a",
    )


def parse_importer_counts(text: str) -> Dict[str, int]:
    out: Dict[str, int] = {}
    labels = {
        "nodes": r"nodes?",
        "edges": r"edges?",
        "vertices": r"vertices?",
        "ways": r"ways?",
        "entries": r"entries?",
    }
    for key, label in labels.items():
        patterns = [
            rf"\b{label}\b\s*(?:count)?\s*[:=]\s*([0-9][0-9,]*)",
            rf"\b([0-9][0-9,]*)\s+{label}\b",
        ]
        for pat in patterns:
            m = re.search(pat, text, re.I)
            if m:
                out[key] = int(m.group(1).replace(",", ""))
                break
    return out


def valid_zip(path: Path, name: str) -> bool:
    needed = {f"{name}.search.db", f"{name}.graph", f"{name}.graph.idx", "manifest.json", "LICENSE.txt"}
    if not path.is_file() or path.stat().st_size == 0:
        return False
    try:
        with zipfile.ZipFile(path, "r") as z:
            return z.testzip() is None and needed.issubset(set(z.namelist()))
    except (OSError, zipfile.BadZipFile):
        return False


def make_zip(name: str, db: Path, graph: Path, idx: Path, out: Path, manifest_content: str) -> None:
    for p in (db, graph, idx):
        if not p.is_file() or p.stat().st_size == 0:
            raise FileNotFoundError(f"Missing/empty package input: {p}")
    out.parent.mkdir(parents=True, exist_ok=True)
    partial = out.with_suffix(out.suffix + ".partial")
    partial.unlink(missing_ok=True)
    with zipfile.ZipFile(partial, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        z.write(db, db.name)
        z.write(graph, graph.name)
        z.write(idx, idx.name)
        z.writestr("manifest.json", manifest_content)
        z.writestr("LICENSE.txt", PACKAGE_LICENSE_TEXT)
    os.replace(partial, out)
    if not valid_zip(out, name):
        out.unlink(missing_ok=True)
        raise RuntimeError(f"ZIP validation failed: {out}")


def process(pref: Dict[str, str], force: bool, keep_work: bool, legacy_search_db: bool = False) -> Tuple[bool, float]:
    name = pref["name"]
    start = time.monotonic()
    final_zip = OUTPUT_DIR / f"{name}.zip"
    pref_log = LOG_DIR / f"{name}.log"

    if not force and valid_zip(final_zip, name):
        LOG.info("[%s] SKIP: valid package exists: %s", name, final_zip)
        return True, time.monotonic() - start

    work = WORK_DIR / name
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    boundary = BOUNDARY_DIR / f"{name}.geojson"
    extract = work / f"{name}.osm.pbf"
    db = work / f"{name}.search.db"
    graph = work / f"{name}.graph"
    idx = work / f"{name}.graph.idx"

    try:
        LOG.info("[%s] ===== START =====", name)

        # 1) Exact admin polygon -> OSM extract.
        rc = run_live(
            [
                "osmium", "extract",
                "--polygon", str(boundary),
                "--strategy", "complete_ways",
                str(SOURCE_PBF),
                "--output", str(extract),
            ],
            pref_log,
        )
        if rc != 0:
            raise RuntimeError(f"osmium extract failed (exit {rc})")
        LOG.info("[%s] extract: %s", name, psize(extract))
        log_osm_stats(name, osm_stats(extract))

        # 2) Search DB selection (prefer data-tool/out integrated DB)
        data_tool_db = DATA_TOOL_OUT_DIR / f"{name.lower()}.search.db"
        if data_tool_db.is_file():
            LOG.info("[%s] Using integrated search DB from %s", name, data_tool_db)
            db_stats = verify_search_db(data_tool_db)
            shutil.copy2(data_tool_db, db)
            log_search_db_stats(db, name)
        elif legacy_search_db:
            LOG.warning("[%s] Integrated search DB not found. Falling back to legacy build_search_db.py", name)
            build_search_db(extract, db)
            db_stats = verify_search_db(db)
            log_search_db_stats(db, name)
        else:
            raise FileNotFoundError(
                f"[{name}] Integrated search DB not found at {data_tool_db}. "
                f"Use --legacy-osm-search-db to fallback to basic OSM search DB."
            )

        # 3) Existing Kotlin routing importer.
        #    Pass JAVA_OPTS via env so the installDist wrapper script picks up a
        #    larger heap -- prefectures bigger than Yamaguchi (the original test
        #    case) can exceed the JVM's default heap and OOM otherwise.
        graph.unlink(missing_ok=True)
        idx.unlink(missing_ok=True)
        importer_env = {**os.environ, "JAVA_OPTS": IMPORTER_JAVA_OPTS}
        rc = run_live(
            [str(OSM_IMPORTER), "--bbox", "none", str(extract), str(graph)],
            pref_log,
            env=importer_env,
        )
        if rc != 0:
            raise RuntimeError(f"Kotlin osm-importer failed (exit {rc})")
        if not graph.exists() or not idx.exists():
            raise RuntimeError(f"Importer did not create both {graph} and {idx}")
        text = pref_log.read_text(encoding="utf-8", errors="replace")
        counts = parse_importer_counts(text)
        LOG.info("[%s] graph: %s, idx: %s", name, psize(graph), psize(idx))
        if counts:
            LOG.info("[%s] importer counts: %s", name, ", ".join(f"{k}={v:,}" for k, v in counts.items()))
        else:
            LOG.info("[%s] importer counts: not parseable from stdout/stderr", name)

        # 4) Atomic ZIP package with manifest & license.
        manifest_text = generate_manifest(name, db_stats)
        make_zip(name, db, graph, idx, final_zip, manifest_text)
        LOG.info("[%s] package: %s (%s)", name, final_zip, psize(final_zip))

        if not keep_work:
            shutil.rmtree(work)
        elapsed = time.monotonic() - start
        LOG.info("[%s] ===== DONE %.1f s =====", name, elapsed)
        return True, elapsed

    except Exception as exc:
        LOG.error("[%s] FAILED after %.1f s: %s", name, time.monotonic() - start, exc)
        LOG.error(traceback.format_exc())
        LOG.error("[%s] Work directory kept at: %s", name, work)
        return False, time.monotonic() - start


def configure_logging() -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    LOG.setLevel(logging.INFO)
    LOG.handlers.clear()
    fmt = logging.Formatter("%(asctime)s | %(levelname)-7s | %(message)s", "%Y-%m-%d %H:%M:%S")
    console = logging.StreamHandler(sys.stdout)
    console.setFormatter(fmt)
    LOG.addHandler(console)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    fh = logging.FileHandler(LOG_DIR / f"batch-{stamp}.log", encoding="utf-8")
    fh.setFormatter(fmt)
    LOG.addHandler(fh)


def select_only(arg: Optional[str]) -> List[Dict[str, str]]:
    if not arg:
        return PREFECTURES
    wanted = {x.strip().lower() for x in arg.split(",") if x.strip()}
    selected = [p for p in PREFECTURES if p["name"].lower() in wanted or p["jp"].lower() in wanted]
    known = {p["name"].lower() for p in PREFECTURES} | {p["jp"].lower() for p in PREFECTURES}
    unknown = wanted - known
    if unknown:
        raise ValueError("Unknown prefecture(s): " + ", ".join(sorted(unknown)))
    return selected


def main() -> int:
    configure_logging()
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="Comma-separated names, e.g. Hiroshima,Okayama")
    ap.add_argument("--force", action="store_true", help="Rebuild even if final ZIP already exists")
    ap.add_argument("--keep-work", action="store_true", help="Keep intermediate files after success")
    ap.add_argument("--no-boundary-download", action="store_true", help="Fail instead of downloading missing boundary GeoJSON")
    ap.add_argument("--legacy-osm-search-db", action="store_true", help="Fallback to legacy OSM-only search DB if integrated DB is missing")
    args = ap.parse_args()

    if not SOURCE_PBF.is_file():
        raise FileNotFoundError(f"Source PBF not found: {SOURCE_PBF}")
    if not OSM_IMPORTER.is_file():
        raise FileNotFoundError(f"Kotlin importer not found: {OSM_IMPORTER}")
    if shutil.which("osmium") is None:
        raise FileNotFoundError("osmium not found on PATH")

    if not BOUNDARY_SOURCE.exists():
        if args.no_boundary_download:
            raise FileNotFoundError(f"Missing {BOUNDARY_SOURCE}")
        download_boundaries()
    make_pref_boundaries()

    selected = select_only(args.only)
    LOG.info("Source: %s (%s)", SOURCE_PBF, psize(SOURCE_PBF))
    LOG.info("Selected: %s", ", ".join(p["name"] for p in selected))

    results = []
    for pref in selected:
        results.append((pref["name"],) + process(pref, args.force, args.keep_work, args.legacy_osm_search_db))

    LOG.info("========== SUMMARY ==========")
    failed = []
    for name, ok, sec in results:
        LOG.info("%s | %s | %.1f s", "OK" if ok else "FAIL", name, sec)
        if not ok:
            failed.append(name)
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)