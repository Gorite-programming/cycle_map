#!/usr/bin/env python3
"""
Hiroshima ベクタータイル (PMTiles) 生成スクリプト
sandbox/vector_test 内で完結して実行する。
国土地理院の最適化ベクトルタイルから広島エリアを高精度に抽出する。
"""

import os
import subprocess
import sys
import time
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
PIPELINE_DIR = BASE_DIR / "pipeline"
DATA_DIR = BASE_DIR / "data"

OUTPUT_PMTILES = DATA_DIR / "Hiroshima.pmtiles"
PMTILES_CLI = PIPELINE_DIR / "pmtiles"

GSI_PMTILES_URL = "https://cyberjapandata.gsi.go.jp/xyz/optimal_bvmap-v1/optimal_bvmap-v1.pmtiles"
# 広島市〜呉〜宮島 エリア (minLon, minLat, maxLon, maxLat)
BBOX_HIROSHIMA = "132.30,34.25,132.60,34.50"


def main():
    print("=== Hiroshima ベクタータイル (PMTiles) 抽出・生成開始 ===")
    print(f"出力 PMTiles: {OUTPUT_PMTILES}")
    print(f"抽出範囲 BBOX: {BBOX_HIROSHIMA} (広島市・宮島・呉)")

    if not PMTILES_CLI.exists():
        print(f"エラー: pmtiles CLI が見つかりません: {PMTILES_CLI}", file=sys.stderr)
        sys.exit(1)

    cmd = [
        str(PMTILES_CLI),
        "extract",
        GSI_PMTILES_URL,
        str(OUTPUT_PMTILES),
        f"--bbox={BBOX_HIROSHIMA}",
    ]

    print(f"実行コマンド: {' '.join(cmd)}")
    start_time = time.time()

    result = subprocess.run(cmd, cwd=str(PIPELINE_DIR))

    if result.returncode != 0:
        print(f"エラー: pmtiles extract に失敗しました (終了コード {result.returncode})", file=sys.stderr)
        sys.exit(result.returncode)

    elapsed = time.time() - start_time
    if OUTPUT_PMTILES.exists():
        size_mb = OUTPUT_PMTILES.stat().st_size / 1024 / 1024
        print(f"=== 抽出完了! 所要時間: {elapsed:.1f}秒, サイズ: {size_mb:.2f} MB ===")
        print(f"生成先: {OUTPUT_PMTILES}")
    else:
        print("エラー: 出力ファイルが生成されませんでした", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
