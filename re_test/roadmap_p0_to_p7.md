# CycleMap P0〜P7 ロードマップと受入基準 (`re_test/roadmap_p0_to_p7.md`)

## 1. 全体スケジュールと依存順序
`P0 → P1 → P2 → P3(P3-0含む) → P4 → P5 → P6 → P7`

## 2. 各フェーズの詳細

### P0: 土台整備
- **P0-1**: `MapScreen.kt` (4,133行) の責務分割（移動のみ、挙動不変）。
- **P0-2**: `CyclingPanels.kt` (2,232行) の分割。
- **P0-3**: `net/ConnectivityState.kt` および `net/OnlineFeatureGate.kt` 新設とユニットテスト。
- **P0-4**: テスト基盤 (`ui-test-junit4`、UIテスト設計)。
- **P0-5**: `:routing-core` / `:osm-importer` の `application/` 共有設定 (`includeBuild` / サブプロジェクト参照)。
- **P0-6**: osmdroid 撤去準備（代替条件定義、MBTiles 検討）。
- **P0-7**: MVI 状態分離 (`MapUiState`, `LocationUiState`, `NavProgressState`)。
- **P0-8**: `parity_checklist.md` 整備と追跡開始。

### P1: 経路・案内 (B1, B2, B3, B6)
- **P1-1**: 移動モード (`TravelMode{Bicycle, Walk, Car}`)、各 CostModel、ペナルティ法による複数経路探索 (最大3本)。
- **P1-2**: 所要時間推定器 (`TravelTimeEstimator`)、時間帯別プロファイル (JSON)。
- **P1-3**: 逸脱検知とリルート (`OffRouteDetector`, 30m / 3サンプル判定)。
- **P1-4**: 車線案内 (`lanes`, `turn:lanes`)、案内拡張 (`Instruction`)。

### P2: 検索・POI (B4, B5)
- **P2-1**: `SearchHelper.kt` 分割 (`search/` パッケージ)。
- **P2-2**: 高速オートコンプリート (<120ms)、現在地近傍ブースト。
- **P2-3**: カテゴリ・ジャンル検索の拡充。
- **P2-4**: `opening_hours` パーサ実装と単体テスト 30+ ケース。オフライン営業時間判定。

### P3: 地図スタイル・描画 (F4, F2)
- **P3-0**: PMTiles 県別配布・フォント(glyphs)・アイコン(sprites)オフライン化 (`CycleMap/assets/`)。
- **P3-1**: 道路階層・自転車道ハイライト・面色の最適化。
- **P3-2**: POI アイコンのローカルスプライト連携。
- **P3-3**: z5/z10/z14/z17 ズーム別整合性確認。

### P4: ナビ・UI詳細シート (F5, F6, F7)
- **P4-1**: 3段アンカー場所詳細シート (Peek / Half / Full)。
- **P4-2**: 経路比較選択画面 (所要時間、距離、標高比較)。
- **P4-3**: ナビ画面 (全幅緑ヘッダー、車線案内、ETAフッター、概要ビュー、到着画面)。

### P5: モーション・アクセシビリティ・テーマ (F1, F8, F9)
- **P5-1**: オフライン日夜切替 (`DayNightManager` 天文計算)。
- **P5-2**: 全文字列 `strings.xml` 移行 (`values` / `values-en`)。
- **P5-3**: アクセシビリティ・フォントスケール 1.3x レイアウト耐久性検証。

### P6: データ管理・オンライン補助 (B7, B8)
- **P6-1**: 県別パッケージ管理 UI (容量、SHA-256、アトミック更新)。
- **P6-2**: 任意オンライン通信ガード (3秒タイムアウト、静かフォールバック)。
- **P6-3**: 追跡・GPX エクスポート / インポート・走行履歴。

### P7: 総合QA・自動テスト (B9)
- **P7-1**: `OfflineParityTest`, `OnlineFallbackTest`, `LayoutStabilityTest`.
- **P7-2**: `StyleLayerOrderTest`, `RouteGoldenTest`, `OpeningHoursParserTest`.
- **P7-3**: 性能測定 (経路探索 <300ms, POI <120ms)。
- **P7-4**: 最終自己採点・完了判定。
