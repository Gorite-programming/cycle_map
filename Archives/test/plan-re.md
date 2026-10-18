# 案件: 検索DBを活用した3DベクターマップへのGoogleマップ風POI動的表示の実装

現在、`test/` ディレクトリ配下にある Androidアプリ (`com.gorite.cyclemap.test`) において、3Dベクターマップ上にGoogleマップのような「カメラを倒しても立ち上がるPOIアイコンとテキスト」を動的に描画する機能を追加してください。

## 1. 背景と目的
現在の `protomaps_light.json` には完全オフライン化の影響で `symbol` レイヤーが存在しません。ベクトルタイル内のPOIに依存するのではなく、既存の `Hiroshima.search.db` (FTS5非対応のフォールバック動作環境) を活用し、現在画面に映っている範囲（Bounding Box）のPOIを高速に取得して MapLibre の `GeoJsonSource` と `SymbolLayer` を用いて動的描画します。

## 2. 要件 (Requirements)

### R1. SearchHelper.kt の拡張 (BBox検索クエリの追加)
- `SearchHelper` クラスに、緯度経度の境界（minLat, minLon, maxLat, maxLon）を受け取り、その範囲内にあるPOIリストを返す関数 `searchPoisInBounds(...)` を追加してください。
- ズームアウト時に大量のデータが返るのを防ぐため、適切な `LIMIT`（例: 50〜100件）を設けるか、ズームレベルに応じて検索をスキップするガードを入れてください。

### R2. VectorMapView.kt への動的POIレイヤー追加
- MapLibreの `Style` 初期化時に、動的POI用の `GeoJsonSource`（例: `cyclemap-dynamic-pois`）と `SymbolLayer` を追加してください。
- Googleマップ風の「立ち上がり表示」を実現するため、以下のプロパティを必ず設定してください。
  - `PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT)`
  - `PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT)`
- `text-field` プロパティを利用して、スポットの名前（`name`）をラベルとして表示し、システムフォントが利用されるように適切に設定してください（文字の縁取り `text-halo-color` や `text-halo-width` も追加して視認性を高めること）。

### R3. カメラ連動による動的フェッチと更新
- MapLibre のカメラ移動完了イベント（例: `addOnCameraIdleListener`）を捕捉し、現在の表示領域（`map.projection.visibleRegion.latLngBounds`）を取得してください。
- バックグラウンドスレッド（Coroutinesの `Dispatchers.IO` 等）で `R1` の関数を呼び出し、取得したPOIを `FeatureCollection` に変換してメインスレッドで `GeoJsonSource` に流し込んでください。

### R4. カテゴリ別アイコンの動的生成
- `VectorMapView.kt` または専用のヘルパー関数にて、Androidのベクタードローアブル（コンビニ、飲食店、病院など、数種類の主要カテゴリ）を Bitmap に変換し、`style.addImage(...)` を用いてMapLibreに登録してください。
- `SymbolLayer` の `icon-image` プロパティで、Featureのプロパティ（カテゴリ等）に応じたアイコン画像が適切に選択されるようにしてください（`Expression.match` 等を活用）。

## 3. 受入基準 (Acceptance Criteria)
- [ ] アプリを実行し、地図をスクロール（パン）した際、画面内のPOIが動的に出現すること。
- [ ] 地図を3D（60度）に傾けたり、コンパスを回転させても、POIのアイコンと文字が画面正面を向いて「立ち上がった状態」を維持すること。
- [ ] 文字に白い縁取り（Halo）があり、道路や建物の背景と重なっても視認性が確保されていること。
- [ ] メインスレッド（UI）をブロックせず、スクロール中も地図の描画やアプリの動作がカクつかない（ANRが発生しない）こと。
- [ ] 変更はすべて `test/` 以下のファイルに適用され、ビルドエラーが発生しないこと。
