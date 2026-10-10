# CycleMap α — 完全オフライン自転車ナビゲーションシステム

**CycleMap** は、電波の届かない山間部やロングライドでも完全にスタンドアロンで動作する、**オフライン特化型の Android 自転車専用ナビゲーションシステム** です。

地図タイルの描画、自転車専用コストモデルに基づく経路探索、施設（POI）の高速あいまい検索のすべてを端末内部で完結させ、通信圏外でも信頼できるナビゲーションを提供します。

---

## 🌟 主な機能と特徴

### 1. 🚲 完全オフライン・自転車特化 A* ルーティング
* **自転車専用コストモデル**: 高速道路（`motorway`）の完全除外、舗装路面・道路種別判定、無理のない安全なルートを優先探索。
* **無制限自動リルート**: コースアウト時も 6 秒間隔で自動再探索（電波のない峠道でも安心）。
* **超高速バイナリグラフ**: OSM 道路ネットワークを独自バイナリ（`.graph`）化し、メモリマップド I/O（mmap）によりメモリ制約の厳しい端末でも瞬時にロード。

### 2. ⛰️ 実標高プロファイル & 獲得標高の可視化
* **国土地理院 DEM10B 連携**: 標高タイルキャッシュを解析し、ルート全線のリアルな標高グラフおよび獲得標高（登坂高度）をリアルタイム表示。

### 3. 🔍 高精度オフライン POI（施設）検索
* **複数ソースの統合 & 重複排除**: Overture Maps Foundation、国土交通省（国土数値情報: 病院・学校）、OpenStreetMap を統合。
* **表記ゆれ吸収**: ひらがな・カタカナ・ローマ字（例:「ローソン」「ろーそん」「rooson」）のあいまい検索に対応。
* **現在地連動**: 近い順ソート、主要施設（コンビニ・駅・サイクルショップ・給水スポット・医療）の優先表示。

### 4. 🧭 サイクリスト向け UI & 追従モード
* **5km/h 速度連動ヘディングアップ**: 走行中（時速 5km/h 以上）は地磁気コンパスの揺れを無視し、GPS の進行方向ベクトルに滑らかに追従。
* **フルスクリーン HUD**: 走行中に画面を広く使える大画面ナビゲーション表示。
* **Google Maps 風スポットカード**: アイコンの衝突回避（間引き表示）と、下部シートによるスポット詳細・お気に入り（星マーク）登録。

### 5. 🔋 徹底した省電力設計 & 動的県切替
* **バッテリー最適化**: 待機時・バックグラウンド時の描画ループ停止と GPS サンプリング抑制により長時間ライドに対応。
* **動的エリア切替**: GPS 位置情報から現在県を自動判定し、対応するルーティンググラフと検索 DB をシームレスに自動切替。

---

## 🏗️ システム構成（3層アーキテクチャ）

```text
cycle_map/
├── 1. ルート データパイプライン (Python)
│   ├── build_prefectures.py       # 都道府県別データ一括切り出し・ZIPパッケージ化
│   ├── boundaries/                # 各都道府県の境界ポリゴン (GeoJSON)
│   └── scripts/push_data.sh       # 実機(Android)へのADB一括転送・SHA-256検証
│
├── 2. Android アプリ & コアルーティングエンジン (Gradle: application/)
│   ├── :app                       # Android UI (Jetpack Compose + osmdroid 6.1.20)
│   ├── :routing-core              # 純粋JVM: A* アルゴリズム、自転車専用コストモデル、mmap読み込み
│   └── :osm-importer              # JVM CLI: OSM PBF → 独自バイナリ(.graph, .graph.idx)変換
│
└── 3. 高精度検索DB生成ツール (sandbox/data-tool/)
    └── Python + Streamlit         # Overture + 国交省 + OSM を統合・クラスタリングしたSQLite生成
```

---

## 📱 動作環境

* **対象 OS**: Android 13.0 以上 (minSdk 33, targetSdk 35, compileSdk 35)
* **開発言語 / ツールチェイン**: Kotlin 2.0.21, AGP 8.10.1, OpenJDK 17
* **実機検証環境**: Samsung Galaxy S21 (Android 14 / One UI 6.1)
* **ビルド環境**: macOS / Linux (Khadas Edge 2 ARM64 SBC へのオフロード対応) / Windows

---

## 🚀 クイックスタート

### 1. プロジェクトの取得
```bash
git clone https://github.com/Gorite-programming/cycle_map.git
cd cycle_map
```

### 2. ユニットテストの実行（純粋 JVM）
```bash
cd application
./gradlew :routing-core:test :osm-importer:test
```

### 3. デバッグ APK のビルド
```bash
./gradlew :app:assembleDebug
```
生成物: `application/app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 オフラインデータの配備（実機）

アプリは端末専用外部ストレージ（`getExternalFilesDir(DOCUMENTS)/CycleMap/`）を参照します。

付属の転送スクリプトを用いて、5県（鳥取・島根・岡山・広島・山口）のグラフデータおよび検索 DB を実機へ一括転送し、SHA-256 による整合性検証を行います：

```bash
# 転送計画の確認 (ドライラン)
./scripts/push_data.sh --dry-run

# 実機へ一括転送 & SHA-256 自動検証
./scripts/push_data.sh
```

---

## 📜 ライセンスと出典帰属

本プロジェクトは複数のオープンデータを組み合わせ、利用規約およびライセンスに従って厳格に分離・管理しています。オフライン利用時も含め、アプリ画面内およびライセンスダイアログにて法的帰属表示（アトリビューション）を維持しています。

| データ種別 | 提供元 | ライセンス | 用途 |
| :--- | :--- | :--- | :--- |
| **道路ネットワーク** | OpenStreetMap (Geofabrik) | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/) | ルーティンググラフ（`.graph`）生成 |
| **施設 (POI)** | Overture Maps Foundation | [CDLA-Permissive-2.0](https://cdla.dev/permissive-2-0/) | 施設検索データベース（`.search.db`） |
| **公共施設 (POI)** | 国土交通省（国土数値情報） | [PDL 1.0](https://www.geospatial.jp/content/pdl1-0/)（商用可） | 医療機関・学校データ |
| **背景地図・標高タイル** | 国土地理院（地理院タイル / DEM10B） | [国土地理院利用規約](https://maps.gsi.go.jp/development/ichiran.html) | オフラインタイル表示・実標高プロファイル |
| **行政界ポリゴン** | maderaojen (GitHub) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | 都道府県境界クリッピング |

---

## 📚 関連ドキュメント

* [VERSIONS.md](VERSIONS.md) — リリース履歴および APK ビルド台帳
* [AGENTS.md](AGENTS.md) — AI エージェント開発体制・SBC ビルド環境・Git 運用ポリシー
* [application/README_DATA.md](application/README_DATA.md) — 外部データセットの詳細仕様・ダウンロード手順
