# AGENTS.md — CycleMap

完全オフラインの自転車ナビ。2層構成: ルート直下の Python データパイプライン + `application/` Android Gradle プロジェクト。git リポジトリはルート直下ではなく `application/` のみ(`application/.git` あり、ルートにはなし)。ルートの `build_search_db.py`・`osm-importer`・`japan-latest.osm.pbf` はシンボリックリンク(実体は `application/osm-importer/`・`data/`)。

## 構成

- `application/` — Gradle プロジェクト(`:app`、`:routing-core`、`:osm-importer`)。`gradlew` はすべて `application/` から実行する。
  - `app/` — Android UI(Compose、osmdroid 6.1.20、play-services-location)。エントリーポイントは `MainActivity.kt`、データ設定は `data/Prefectures.kt`。
  - `routing-core/` — 純粋 JVM ライブラリ:`RoadGraph`、`AStarRouter`、`CyclingCostModel`、mmap リーダー(`LazyMappedRouting.kt`、`MappedRouting.kt`)。**本番ルーティングは A\* のみ。`HsaRouting.kt` / `AltRouting.kt` は実験用** (詳細は `HSA_STAR_EXTERNAL_REVIEW_REQUEST.md`)。
  - `osm-importer/` — JVM CLI(`OsmImporterKt`):OSM `.pbf` → 独自バイナリ `.graph` + サイドカー `.graph.idx`。
- ルートパイプライン:`build_prefectures.py`(中国5県:鳥取・島根・岡山・広島・山口)→県ごとに `<name>.osm.pbf` + `<name>.search.db` + `<name>.graph` / `.idx` → `packages/<name>.zip`。`build_search_db.py` は FTS5 検索 DB ビルダー。`boundaries/` は行政界ポリゴン(SHA-256 検証付きで自動ダウンロード)。
- 大容量データ(コミット・移動禁止):`data/japan-latest.osm.pbf`(約2.5 GB。ルート直下の `japan-latest.osm.pbf` はシンボリックリンク)、`work/` 中間生成物、`venv/`。

## コマンド

```bash
cd application
./gradlew :routing-core:test :osm-importer:test   # JVM ユニットテスト
./gradlew :app:assembleDebug                       # APK
./gradlew :osm-importer:installDist                # osm-importer CLI をビルド
JAVA_OPTS="-Xmx6g" osm-importer/build/install/osm-importer/bin/osm-importer --bbox none <in.osm.pbf> <out.graph>  # .graph + .graph.idx を生成
```

```bash
# ルートパイプライン — venv の python を必ず使うこと(system python には osmium がない):
venv/bin/python build_search_db.py --bbox none <in.osm.pbf> <out.search.db>   # --bbox: japan|yamaguchi|none
venv/bin/python build_prefectures.py --only Yamaguchi --keep-work             # 再ビルドは --force を追加。PATH に `osmium` CLI が必要
```

ツールチェイン:AGP 8.10.1、Kotlin 2.0.21、compileSdk/target 35、minSdk 33、Java 11。`local.properties` の `sdk.dir` はこの Mac 専用 — 他マシンでは更新すること。

## 注意点

- ルーティング用データ(OSM `.graph`)と表示用データ(地理院タイル)は厳密に分離。Google/OSM ラスターデータをグラフに混ぜない。OSM + 地理院の帰属表示(地図隅 + ライセンス画面)はオフラインでも表示を維持する。
- データは APK に同梱しない。アプリはアプリ専用外部ストレージ `getExternalFilesDir(DOCUMENTS)/CycleMap/` を見る:`<name>.graph`、`<name>.graph.idx`、`tiles/cache.db`。グラフ名は現状 `MainActivity.kt` のグラフ読み込み部に `yamaguchi.graph` でハードコード(約626行目付近、`Prefectures.kt` の県別基盤はあるが未接続) — 県追加時は汎化が必要。
- `build_prefectures.py` の不変条件:`osmium extract --polygon <boundary> --strategy complete_ways` を使う。importer 呼び出しは `--bbox none`。ZIP 内容は `<name>.search.db` + `<name>.graph` + `<name>.graph.idx` の3点のみ。有効な ZIP があれば `--force` なしではスキップ。失敗時の作業ディレクトリはデバッグ用に残す。
- `CyclingCostModel` は `motorway` を除外。最小倍率 0.90 は A\* ヒューリスティックの下限 — JVM 側と `LazyMappedRoadGraph` で同期を保つ。
- `LazyMappedRouting.readEdge()` は 2 GB 超でオーバーフロー(`offset.toInt()`)。`nearestNodeIndex` は O(n) 線形走査。既知バグ集は `application/bugs.md` — 修正前に必ず確認すること。ただし Critical/High の大半はコミット `3a6faf2` で修正済みのため、着手前に `git -C application log --oneline` と現行コードで再確認する(盲目的な再修正を避ける)。
- 実機手順:Samsung S21 + `adb push`、機内モードで検証。山口 z15–z16 全域タイル(約8.9万枚、1 GB 超)は見送り — 全域取得ではなく経路回廊のみ取得する方針。
