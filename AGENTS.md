# AGENTS.md — CycleMap

完全オフラインの自転車ナビ。3層構成: ルート直下の Python データパイプライン + `application/` Android Gradle プロジェクト + `sandbox/data-tool/` 検索DB生成ツール(Python/Streamlit)。git リポジトリは `cycle_map/` ルート直下に統合済み(GitHub: Gorite-programming/cycle_map, Public)。`application/.git.bak` に旧・独立リポジトリ時代の全履歴を保存(参照用、削除しないこと)。ルートの `build_search_db.py`・`osm-importer`・`japan-latest.osm.pbf` はシンボリックリンク(実体は `application/osm-importer/`・`data/`) — **macOS/Linux専用の機能。Windows環境ではこれらのリンクは機能しない**(下記「クロスプラットフォーム対応」参照)。

## 構成

- `application/` — Gradle プロジェクト(`:app`、`:routing-core`、`:osm-importer`)。`gradlew` はすべて `application/` から実行する。
  - `app/` — Android UI(Compose、osmdroid 6.1.20、play-services-location)。エントリーポイントは `MainActivity.kt`、データ設定は `data/Prefectures.kt`。
  - `routing-core/` — 純粋 JVM ライブラリ:`RoadGraph`、`AStarRouter`、`CyclingCostModel`、mmap リーダー(`LazyMappedRouting.kt`、`MappedRouting.kt`)。**本番ルーティングは A\* のみ。`HsaRouting.kt` / `AltRouting.kt` は実験用** (詳細は `HSA_STAR_EXTERNAL_REVIEW_REQUEST.md`)。
  - `osm-importer/` — JVM CLI(`OsmImporterKt`):OSM `.pbf` → 独自バイナリ `.graph` + サイドカー `.graph.idx`。
- ルートパイプライン:`build_prefectures.py`(中国5県:鳥取・島根・岡山・広島・山口)→県ごとに `<name>.osm.pbf` + `<name>.search.db` + `<name>.graph` / `.idx` → `packages/<name>.zip`。`build_search_db.py` は FTS5 検索 DB ビルダー(OSMタグのみ使用。日本特化POIデータは `sandbox/data-tool/` 参照)。`boundaries/` は行政界ポリゴン(SHA-256 検証付きで自動ダウンロード)。
- `sandbox/data-tool/` — 検索DB生成ツール(Python + Streamlit)。以前は独立Gitだったが現在は `cycle_map` リポジトリに統合済み。OSM PBFを道路基盤とし、POI（コンビニ・飲食店・病院・観光施設等）は Overture Maps Foundation (Placesテーマ、CDLA-Permissive-2.0) および 国土数値情報（医療機関第3.0版・学校第2.0版、PDL1.0）を統合・重複排除（同一敷地・同一住所マージ）して高精度化するパイプライン。表記ゆれ対応（ひらがな・カタカナ・ローマ字）も実装済み。ライセンス表記・出典規定の詳細は `application/DISTRIBUTION_NOTES.md` およびアプリ内「地図情報・ライセンス」ダイアログを参照。**重要**: Android 標準 SQLite は多くの実機で FTS5 非対応(`no such module: fts5`)のため、検索DBは `places.search_text` 列(LIKE検索用、表示名の `name` 列とは別)も保持して互換性を確保している(アプリ側の対応は `Fts5SupportDetector.kt`)。
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

ツールチェイン:AGP 8.10.1、Kotlin 2.0.21、compileSdk/target 35、minSdk 33、Java 11。`local.properties` の `sdk.dir` はマシン固有(`.gitignore`で除外済み、コミットされない) — 新しい環境(Windows含む)では Android Studio の初回起動時に自動生成されるか、手動で `sdk.dir=<自分のAndroid SDKパス>` を記載すること。

## 注意点

- ルーティング用データ(OSM `.graph`)と表示用データ(地理院タイル)は厳密に分離。Google/OSM ラスターデータをグラフに混ぜない。OSM + 地理院の帰属表示(地図隅 + ライセンス画面)はオフラインでも表示を維持する。
- データは APK に同梱しない。アプリはアプリ専用外部ストレージ `getExternalFilesDir(DOCUMENTS)/CycleMap/` を見る:`<name>.graph`、`<name>.graph.idx`、`tiles/cache.db`。県の動的切替は対応済み(`RoutingGraphSelector` / `SearchDbSelector` を `Prefectures.kt` に実装)。起動時は配置済みファイルから名前昇順で最初のものを自動探索し、現在地が確定したら対応する県のグラフ/検索DBに自動切替する。
- `build_prefectures.py` の不変条件:`osmium extract --polygon <boundary> --strategy complete_ways` を使う。importer 呼び出しは `--bbox none`。ZIP 内容は `<name>.search.db` + `<name>.graph` + `<name>.graph.idx` の3点のみ。有効な ZIP があれば `--force` なしではスキップ。失敗時の作業ディレクトリはデバッグ用に残す。
- `CyclingCostModel` は `motorway` を除外。最小倍率 0.90 は A\* ヒューリスティックの下限 — JVM 側と `LazyMappedRoadGraph` で同期を保つ。
- `LazyMappedRouting.readEdge()` は 2 GB 超でオーバーフロー(`offset.toInt()`)。`nearestNodeIndex` は O(n) 線形走査。既知バグ集は `application/bugs.md` — 修正前に必ず確認すること。ただし Critical/High の大半はコミット `3a6faf2` で修正済みのため、着手前に `git -C application log --oneline` と現行コードで再確認する(盲目的な再修正を避ける)。
- 実機手順:Samsung S21 + `adb push`、機内モードで検証。山口 z15–z16 全域タイル(約8.9万枚、1 GB 超)は見送り — 全域取得ではなく経路回廊のみ取得する方針。

## 開発体制・Git運用ポリシー（全エージェント共通）

- **開発体制**:
  - 本プロジェクトは Claude の指揮のもと、複数のAIエージェント（Antigravity、OpenCode、Kiro等）が連携・交代しながら進めています（※Codexはトークン上限のため10/17まで休止中）。
- **クロス環境（Windows / MacBook）と同期**:
  - Windows 環境と MacBook 環境の双方で作業を進めるため、GitHub（`Gorite-programming/cycle_map`）を経由して同期を行っています。
  - 作業着手前や時間が空いた場合は、必ず `git pull` を実行して最新のコード・状況を取り込んでください。
  - 作業完了・コミット後は、別マシンや別エージェントへ速やかに引き継げるよう定期的に `git push` を行います。
- **Git操作は完全承認制**:
  - `git commit` / `git push` を含むGit反映操作は、人間（Gorite）の明示的な承認を得てから人間自身が実行する（または明示的な指示のもとで行う）。
  - Antigravity / OpenCode / Kiro / その他のエージェントは、コード変更後に勝手に `git add`・`git commit`・`git push` を行わない。
  - 各作業の完了時は、変更内容の要約・diff・テスト結果を報告し、コミットやプッシュについての指示を待つ。
  - 「コミットしないでください」という指示が無くても、これがデフォルトの動作とする。