# sandbox/vector_test — オフラインベクター地図 & 3Dレンダリング検証

完全オフラインで動作するベクタータイル（PMTiles）と MapLibre Native Android を用い、本家 Liberty スタイル配色および 3D 立体押し出し（`fill-extrusion`）を CycleMap に統合するための検証環境・パイプラインです。

---

## 📌 概要と目的

- **背景**: 従来の CycleMap は osmdroid + ラスタータイル（地理院・OSM）を基盤としており、拡大時の解像度低下やデータ容量の肥大化が課題でした。
- **目的**: 
  1. ベクタータイル形式（PMTiles）による超軽量・高精細なオフライン地図描画の実現。
  2. 自転車ナビゲーションに適した本家 Liberty 配色（道路ケーシング、水域 `#9ebdff`、緑地 `#d8e8c8` 等）の再現。
  3. 2D 平面図形と 3D 立体建物押し出し（鳥瞰パース）のシームレスな切替。
  4. A* ルーティング結果（シアンブルールート線）および音声案内の完全連動。

---

## 🚀 これまでの実装進捗（段階化）

### Phase 1: タイル抽出・オフラインパイプライン構築
- **ツール整備**: `pmtiles` CLI（v3）および `planetiler.jar` をパイプラインに導入。
- **データ抽出**: 国土地理院 最適化ベクトルタイル（`optimal_bvmap-v1`）および OSM / Protomaps から広島エリア（広島市・宮島・呉）の PMTiles（`Hiroshima.pmtiles`, `Hiroshima_osm.pmtiles`）を抽出・生成。
- **スクリプト**: `pipeline/build_tiles.py` により自動抽出フローを確立。

### Phase 2: オフライン Liberty スタイル設計
- **スタイルジェネレーター**: `pipeline/generate_liberty_offline_style.js` を作成（`@protomaps/basemaps` ベース）。
- **配色チューニング**:
  - 背景・大地: `#f8f4f0`
  - 水域: `#9ebdff`（Liberty 準拠）
  - 緑地・森林: `#d8e8c8`, `#a0d9a0`
  - 幹線道路: 中塗り `#ffeaa0` ＋ ケーシング `#e9ac77`
  - 一般道: 中塗り純白 `#ffffff` ＋ ケーシング `#cfcdca`
  - 建物: `#dedad5`
- **オフライン化**: タイル URL を `pmtiles://file://...` に書き換えるオフラインスタイル `protomaps_light.json` を生成。

### Phase 3: テスト環境（`cycle_map/test/`）への移植と「CycleMap-Test」構築
- **完全隔離環境**: 本番コード（`application/`）を汚さないよう `test/` ディレクトリを作成して元アプリと `vector_test` を移植。
- **共存対応**: 
  - `applicationId = "com.gorite.cyclemap.test"`
  - `app_name = "CycleMap-Test"`
  - カスタムパーミッション分離により、実機（Galaxy S21）上の本番アプリ（`CycleMap α`）と完全共存・並行起動を可能に。
- **ライブラリ追加**: MapLibre Native Android SDK（`org.maplibre.gl:android-sdk:11.8.0`）を導入。

### Phase 4: `VectorMapView`（Compose ラッパー）の実装 & ナビゲーション結合
- **2D/3D シームレス切替**:
  - チルト角 15° を境界として、`buildings-2d`（平面 fill）と `buildings-3d`（立体 fill-extrusion）をカメラ角度に応じて自動切替。
  - 「3D 鳥瞰 (60°) / 2D 平面」トグルボタンを設置。
- **ルート・案内連動**:
  - A* ルーターの探索結果（`navigationRoute`）を GeoJSON `LineLayer`（シアンブルー `#00B0FF` ＋濃紺ケーシング `#0D47A1`）でベクター地図上にリアルタイム描画。
  - 現在地マーカー（青丸＋白枠）の描画。
  - コンパス（北上リセット）、現在地ボタン、ズーム（＋ / −）操作の MapLibre 連動。
  - 右側レイヤー切替で「3Dベクター」「標準」「自転車」「地形」の瞬時トグル切替。

### Phase 5: 3D建物の半透明化（視認性向上）
- **課題**: 3D建物（`fill-extrusion`）がほぼ不透明（opacity: 0.85）だったため、斜め鳥瞰時に奥の道路や案内ルート線が隠れて境界把握が困難だった。
- **対応**: `fill-extrusion-opacity` を **`0.5`（半透明）** に調整。
- **効果**: 立体感を保ちつつ、建物の向こう側にある道路・ルート・敷地境界が鮮明に透けて見えるようになり、走行時の視認性が飛躍的に向上。

---

## 📋 今後の改善ロードマップ（段階化）

### Stage 1: 操作性改善（ジェスチャー連携）
- [ ] **2本指上下スワイプによる無段階チルト操作**:
  - `UiSettings.isTiltGesturesEnabled` の明示的初期化および Jetpack Compose のタッチインターセプト競合の解消。
  - 2本指上下移動でカメラのチルト角（0°〜60°）を滑らかに操作可能にし、視点の高さを直感的に変更できるようにする。

### Stage 2: 鉄道（線路）の視認性改善
- [ ] **JR線の白黒ゼブラ表示（OSM標準スタイル）**:
  - ベース黒線（幅 3.5dp）＋白色破線（幅 2.2dp、`line-dasharray: [1.2, 1.2]`）の 2 重 line レイヤー構造を実装。
- [ ] **私鉄・路面電車（広島電鉄等）・地下鉄の地理院記号ハッチング**:
  - 単線・複線・路面電車に対応した専用ラインスタイル（実線＋垂直ハッチング表現）を構築し、JR幹線と明確に区別。

### Stage 3: POI・道路記号の追加
- [ ] **幹線道路の国道・県道シールドマーク**:
  - ベクタータイルの `shield_text` / `ref` 属性を読み込み、青地おにぎり（国道）および六角形（県道）の番号付きシールドを道路上に配置（`symbol-placement: line`）。
- [ ] **主要駅・ランドマークのシンボル表示**:
  - `pois` レイヤーの `kind: station` などの主要施設をアイコン＋テキストで表示。

### Stage 4: 本番アプリ（`application/`）へのフィードバック
- [ ] テスト環境（`test/`）での検証結果を踏まえ、安定したベクター地図機能を本体の `application/` へ正式マージ・統合。

---

## 🛠️ 主要ファイル構成

```
sandbox/vector_test/
├── README.md                              # 本ドキュメント
├── data/
│   ├── Hiroshima.pmtiles                 # 国土地理院 最適化ベクトルタイル (BBOX)
│   └── Hiroshima_osm.pmtiles             # OSM / Protomaps ベクタータイル (BBOX)
└── pipeline/
    ├── build_tiles.py                     # PMTiles 抽出スクリプト
    ├── generate_liberty_offline_style.js  # Liberty オフラインスタイル生成
    ├── pmtiles                            # PMTiles CLI (v3)
    └── planetiler.jar                     # OSM → PMTiles 生成エンジン
```
