# README_DATA.md — CycleMap 外部データセット仕様・配置ガイド

本ドキュメントは、CycleMap プロジェクトで利用するすべての外部データ（道路ネットワーク、POI検索データベース、地図タイル、行政界ポリゴン）の**取得先リンク、ライセンス、配置場所、および配置手順**をまとめたガイドです。

---

## 1. 外部データセット一覧

| データ種別 | 提供元 | ライセンス | ダウンロード先 / 詳細 URL | リポジトリ・実機内の配置場所 |
| :--- | :--- | :--- | :--- | :--- |
| **日本全域 OSM PBF** | Geofabrik / OSM | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/) | [japan-latest.osm.pbf](https://download.geofabrik.de/asia/japan-latest.osm.pbf) (~2.5GB) | `data/japan-latest.osm.pbf` |
| **中国地方 OSM PBF (軽量版)** | Geofabrik / OSM | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/) | [chugoku-latest.osm.pbf](https://download.geofabrik.de/asia/japan/chugoku-latest.osm.pbf) (~350MB) | `data/chugoku-latest.osm.pbf` (任意) |
| **都道府県境界 GeoJSON** | maderaojen (GitHub) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | [jp-prefectures releases](https://geo.maderaojen.me/datasets/jp-prefectures/releases/v1/2026-08-04.1/data.geojson) | `boundaries/prefectures.geojson`<br>`boundaries/<Prefecture>.geojson`<br>`sandbox/data-tool/data/raw/boundaries/` |
| **国土数値情報 医療機関データ** | 国土交通省 (MLIT) | 公共データ利用規約 [PDL1.0](https://www.geospatial.jp/content/pdl1-0/) (商用利用可) | [第3.0版 (令和2年度/2020)](https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P04-v3_0.html) | `sandbox/data-tool/data/raw/kokudo/medical/P04-20_<コード>_GML/` |
| **国土数値情報 学校データ** | 国土交通省 (MLIT) | 公共データ利用規約 [PDL1.0](https://www.geospatial.jp/content/pdl1-0/) (商用利用可) | [第2.0版 (令和3年度/2021)](https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P29-v2_0.html) | `sandbox/data-tool/data/raw/kokudo/school/P29-21_<コード>_GML/` |
| **地理院タイル (標準地図)** | 国土地理院 | [国土地理院利用規約](https://maps.gsi.go.jp/development/ichiran.html) | `https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png` | 実機: `tiles/cache.db` (アプリが自動キャッシュ) |
| **地理院タイル (陰影起伏図)** | 国土地理院 | [国土地理院利用規約](https://maps.gsi.go.jp/development/ichiran.html) | `https://cyberjapandata.gsi.go.jp/xyz/relief/{z}/{x}/{y}.png` | 実機: `tiles/cache.db` (アプリが自動キャッシュ) |
| **OpenStreetMap 標準タイル** | OpenStreetMap | [ODbL 1.0](https://www.openstreetmap.org/copyright) | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | 実機: `tiles/cache.db` (オプション) |

---

## 2. データの配置場所と役割

### A. パイプライン・検索DBツール側（PC / リポジトリ内）

```text
cycle_map/
├── data/                                 # 【Git管理外】
│   └── japan-latest.osm.pbf              # 日本全域 OSM Raw データ (~2.5GB)
│
├── boundaries/                           # 【Git管理対象】
│   ├── prefectures.geojson               # 全国都道府県ポリゴン (SHA-256 検証付き)
│   └── <Prefecture>.geojson              # 各県切り出しポリゴン (Hiroshima, Yamaguchi 等)
│
└── sandbox/data-tool/data/raw/           # 【Git管理外】
    ├── boundaries/                       # 行政界 GeoJSON (県別判定・クリッピング用)
    │   └── <Prefecture>.geojson
    └── kokudo/
        ├── medical/                      # 国土数値情報 医療機関データ
        │   └── P04-20_<コード>_GML/P04-20_<コード>.geojson
        └── school/                       # 国土数値情報 学校データ
            └── P29-21_<コード>_GML/P29-21_<コード>.geojson
```

※ 国土数値情報の中国5県コード: 鳥取 (`31`), 島根 (`32`), 岡山 (`33`), 広島 (`34`), 山口 (`35`)

---

### B. Android アプリ側（実機内部ストレージ）

アプリは端末専用外部ストレージ `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)/CycleMap/` を参照します。

* **端末内フルパス**:
  `/sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/`

* **必要なファイル群**:
  ```text
  CycleMap/
  ├── <name>.graph           # ルーティング用バイナリグラフ (例: yamaguchi.graph, Hiroshima.graph)
  ├── <name>.graph.idx       # グラフインデックス (例: yamaguchi.graph.idx, Hiroshima.graph.idx)
  ├── <name>.search.db       # POI検索DB (例: yamaguchi.search.db, hiroshima.search.db)
  └── tiles/
      └── cache.db           # osmdroid 地理院タイルキャッシュ (~800MB)
  ```

---

## 3. ダウンロードおよび配置手順

### (1) `japan-latest.osm.pbf` の配置
1. [Geofabrik ダウンロードサイト](https://download.geofabrik.de/asia/japan-latest.osm.pbf) から取得。
2. リポジトリ直下の `data/` フォルダに配置します:
   ```text
   cycle_map/data/japan-latest.osm.pbf
   ```

### (2) それ以外の外部データを一括DL・配置（PowerShell）
リポジトリルートに用意された一括配置スクリプト `download_data.ps1` を実行します:

```powershell
# cycle_map ルートで実行
powershell.exe -ExecutionPolicy Bypass -File .\download_data.ps1
```

このスクリプトにより以下が自動実行されます:
- `boundaries/` 配下の GeoJSON を `sandbox/data-tool/data/raw/boundaries/` にコピー
- 国土交通省から中国5県の医療機関データ（第3.0版 GeoJSON）をダウンロード・展開
- 国土交通省から中国5県の学校データ（第2.0版 GeoJSON）をダウンロード・展開

### (3) Android 実機へのデータ配置コマンド (adb)
生成されたパッケージやバックアップデータを実機に転送する場合:

```powershell
# 転送先ディレクトリの作成
adb shell "mkdir -p /sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/tiles"

# グラフデータ・検索DBの配置
adb push yamaguchi.graph /sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/
adb push yamaguchi.graph.idx /sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/
adb push yamaguchi.search.db /sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/

# タイルキャッシュの配置 (存在する場合)
adb push cache.db /sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/tiles/
```

---

## 4. ライセンス・出典表記ルール

### (1) OpenStreetMap (OSM)
* **ライセンス**: Open Data Commons Open Database License (ODbL) 1.0
* **帰属表記**: `© OpenStreetMap contributors`
* **要件**: アプリ画面隅およびライセンス情報画面にオフライン時でもクレジットを表示すること。

### (2) 国土数値情報（医療機関・学校データ）
* **ライセンス**: 公共データ利用規約 (PDL1.0)（商用利用可能）
* **【重要】版の厳守**:
  * 医療機関: **第3.0版**（令和2年度 / 2020年）を使用すること。（旧版の第2.1版等は非商用限定のため使用禁止）
  * 学校: **第2.0版**（令和3年度 / 2021年）を使用すること。（旧版の第1.1版等は非商用限定のため使用禁止）
* **出典表記（必須）**:
  * `「国土数値情報（医療機関データ）」（国土交通省）（https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P04-v3_0.html）をもとに作成`
  * `「国土数値情報（学校データ）」（国土交通省）（https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P29-v2_0.html）をもとに作成`

### (3) 国土地理院タイル
* **ライセンス**: 国土地理院コンテンツ利用規約
* **帰属表記**: `地理院タイル`（画面隅またはライセンス画面に常時表示）
* **タイル配信仕様**: [地理院タイル一覧・利用規約](https://maps.gsi.go.jp/development/ichiran.html)
