# CycleMap `re_test` 詳細現状まとめ・分析レポート

- **対象**: `cycle_map/re_test/` (次期正系統プロジェクト)
- **作成日時**: 2026-10-13 22:20 JST
- **参照基準**: `re_test/` 内全ドキュメント、`test/oparations/re_plans.md`、`check_wiring.sh` 出力、最新ビルド・テスト結果 (全281件 PASS)

---

## 1. エグゼクティブサマリー

`re_test` は、完全オフライン自転車ナビゲーションアプリ「CycleMap」において、これまでのプロトタイプ（`test/`）で課題となっていた**巨大ファイル（4,000行超）、120箇所以上の散在した状態変数、osmdroid と MapLibre の二重管理、直書きカラー（230箇所以上）**を解消し、**Google Maps 互換度 ≥90%**（フロント ≥92%, バック ≥88%）を完全オフライン前提で達成するために新設された**新・正系統プロジェクト**です（`overhaul_plan.md` §2-A 決定）。

直近では、夜間自律実行（P0〜P7）による初版実装の後、監査によって判明した「クラスが単体テスト用のみで本体画面から未接続」「実機証跡の不整合（ベクタータイル未配置バナー被り等）」を是正するタスク（T1〜T7）が進行しています。
現在、**単体テスト全281件 PASS**（`:routing-core` 74件, `:osm-importer` 3件, `:app` 204件）および **APKビルド（`:app:assembleDebug`）成功** を維持しつつ、オフライン 3D ベクタータイルの描画、オフライン日本語フォント (glyphs) / アイコン (sprites) の内包化、MVI/UDF 状態管理の接続が段階的に進められています。

---

## 2. 系統関係と設計原則

### 2.1 3つの系統の位置づけ
1. **`application/` (現行本番・保護系統)**
   - パリティ台帳（`parity_checklist.md`）の全項目が実機で検証・合格し、総合互換度 ≥90% を達成するまで、破壊的変更を行わずに本番として維持。
   - `:routing-core` および `:osm-importer` はここが正の実体を持ち、`re_test` は Gradle の `projectDir` 参照によりコードを共有（重複クローン・コピーの禁止）。
2. **`test/` (旧プロトタイプ・凍結系統)**
   - MapLibre 導入や各種新機能の先行実験場。現在は参照用として凍結（新規機能開発は禁止、重大バグ修正のみ）。
3. **`re_test/` (新・正系統)**
   - クリーンな MVI / UDF 設計、MapLibre (3Dベクター) 単一化、1ファイル500行以内を遵守し、最終的に本番として `application/` を置き換える対象。

### 2.2 アーキテクチャ原則
- **MapLibre Native (11.8.0) + Protomaps PMTiles に一本化**:
  - osmdroid への二重依存を排除。オフライン県別ベクタータイル（`<name>.pmtiles`）を読み込み、3D建造物、道路階層、POIを描画。
- **MVI / UDF 状態管理と高低頻度分離**:
  - 画面全体を再コンポーズさせないため、低頻度状態（`MapUiState`: シート、チップ、選択地点、テーマ）と、高頻度状態（`LocationUiState`: 1Hz GPS / 10-50Hz 方位、`NavProgressState`: 案内更新）を独立した `StateFlow` に分離。
- **1ファイル 500行以内規則**:
  - 4,133行あった旧 `MapScreen.kt` を `MapTopBar`, `MapControls`, `MapSheets`, `NavigationScreen`, `MapScreenScaffold`, `MapViewsContainer` 等へ機能分離。
- **完全オフライン保証**:
  - 機内モードでも地図、検索、ルーティング、ターンバイターン案内、日夜天文切替、走行記録が100%動作。オンライン機能は任意の補助（失敗時は静かにフォールバック）。

---

## 3. ディレクトリ構成と主要ファイル

```text
re_test/
├── README.md                  # プロジェクト概要、ビルド手順、系統関係
├── architecture_spec.md       # アーキテクチャ仕様書 (MVI, レイヤーZ-order, 状態分離)
├── overhaul_plan.md           # 改修実施計画書 (正系統決定, PMTiles配布方針, キャッシュ移行)
├── plans.md                   # Google Maps互換度 ≥90% 達成指示書 (P0〜P7, 採点基準)
├── scorecard_90percent.md     # 互換度採点台帳 (F1〜F9, B1〜B9 重み付き計算表)
├── parity_checklist.md        # test/ からの機能パリティ・退行防止台帳 (DEF-01〜11 等)
├── cautions.md                # アーキテクチャ・ロジック・UIの注意点・不退転の3原則
├── FINAL_REPORT.md            # 夜間自律実行完了報告書 (初版)
├── NIGHT_LOG.md               # 実行進捗ログ (夜間P0〜P7、是正T1〜T2、証跡修正記録)
├── settings.gradle.kts        # :routing-core, :osm-importer 共有設定
├── build.gradle.kts           # Kotlin 2.0.21, AGP 8.10.1 設定
├── scripts/
│   └── check_wiring.sh        # 実コード配線検査スクリプト (UNWIRED / OVER500 検出)
├── proposals/
│   └── 01_travel_modes_and_lane_guidance.md # TravelMode / 車線案内仕様提案書
├── prompts/
│   ├── P0_prompt.md           # 土台構築プロンプト
│   ├── overnight_P0_to_P7.md  # 夜間実行プロンプト
│   └── integration_fix.md     # 監査是正指示プロンプト
├── screenshots/               # 実機 (Galaxy S21) 検証証跡画像群 (25枚)
└── app/
    ├── src/main/assets/
    │   ├── fonts/             # オフライン glyph PBF (日本語フォントスタック)
    │   ├── sprites/           # オフライン sprite (light/dark .json/.png/@2x)
    │   ├── styles/            # protomaps_light.json, protomaps_dark.json
    │   └── speed_profiles.json# 時間帯別オフライン速度プロファイル
    └── src/main/java/com/gorite/cyclemap/
        ├── MainActivity.kt    # アプリ起動エントリーポイント
        ├── MapScreen.kt       # 地図画面メイン (421行)
        ├── MapScreenScaffold.kt
        ├── MapViewsContainer.kt
        ├── VectorMapView.kt   # MapLibre 3Dベクター描画ビュー (475行)
        ├── VectorMapStyles.kt # ベクターマップスタイル設定
        ├── VectorMapFiles.kt  # PMTiles / スタイルファイル探索・検証
        ├── MapTopOverlays.kt / MapBottomOverlays.kt / MapControlsOverlay.kt
        ├── MapRouteCalculation.kt / MapRoutingActions.kt
        ├── DestinationSearchDialog.kt / SearchHelper.kt
        ├── ui/map/            # MapTopBar, MapControls, MapSheets, NavigationScreen 等
        ├── routing/           # TravelModes, TravelTimeEstimator, AdaptiveRerouteManager 等
        ├── search/            # OpeningHoursParser, SearchNormalizer, GenreDictionary 等
        ├── net/               # ConnectivityState, OnlineFeatureGate
        ├── tracking/          # GpxParser, GpxRecorder, LocationTrackingService
        └── power/             # BatteryMonitor, CameraMotionPolicy
```

---

## 4. Google Maps 互換度スコアの現状と監査是正

### 4.1 目標値と採点ルール
- **目標**: フロント ≥ 92%, バック ≥ 88%, 全体 ≥ 90%
- **計算式**:
  - フロント% (F1〜F9, 重み計 82) = `Σ(重み × 得点) / 82 × 100`
  - バック% (B1〜B9, 重み計 90) = `Σ(重み × 得点) / 90 × 100`
  - 全体% = `(フロント% + バック%) / 2`
- **厳格な判定ルール**:
  - 実機未確認や本体コード未接続（クラスとテストのみ存在）は **加点 0 または最大 0.5**。

### 4.2 監査で判明した課題と是正ステップ (T1〜T7)
夜間自律実行初版の `FINAL_REPORT.md` では 90.15% 達成と報告されましたが、その後の監査により以下の課題が特定され、`integration_fix.md` および `test/oparations/re_plans.md` に基づいて是正が進められています。

1. **クラスの本体未接続 (UNWIRED)**:
   - 一部の先進機能（`AdaptiveRerouteManager`, `TravelTimeEstimator`, `PenaltyMultiRouteFinder`, `OpeningHoursParser`, `OfflinePackageManager`, `ThreeStageBottomSheet`, `GoogleNavHeaderView` など）が、定義と単体テストのみで本体 UI/画面ロジックから直接呼び出されていなかった点。
   - → **是正進捗**: T1（ファイル分割）および T2（MVI状態接続）が完了し、`MapViewModel`, `OnlineFeatureGate`, `ConnectivityState`, `NetworkStatusIndicator` 等の配線が完了。
2. **500行制限の超過解消**:
   - `MapScreen.kt`（4,133行 → 421行へ大幅分割達成）、`SearchHelper.kt`（488行）、`VectorMapView.kt`（475行）など主要ファイルが 500行以内に収まり、コードベースの保守性が飛躍的に向上。
   - ※直近のスタイル定義拡充に伴い `VectorMapStyles.kt` が 505行と微小超過しているため、微調整対象としてマーク。
3. **実機証跡の不整合是正**:
   - Galaxy S21 実機において、端末ストレージに広島の PMTiles が配置されていなかったために「オフラインベクターデータ未検出」バナーが上部に被っていた問題を解消。
   - DEF-01（バナー重なり防止）レイアウト修正、広島 PMTiles（`Hiroshima.pmtiles` 45MB）の実機配置、およびオフライン／オンラインそれぞれの正常描画スクリーンショット（`T2_device.png`, `T2_online.png`）への差し替え完了。
4. **オフライン文字・アイコン表示 (P3-0)**:
   - スタイル JSON の外部 Web 参照（protomaps GitHub Pages）を廃止し、ローカル assets 内の `fonts/`（日本語 glyph PBF）および `sprites/`（light/dark JSON+PNG）から完全オフラインでロードする仕組みを実装。

---

## 5. 現在の実装機能ハイライト

1. **完全オフライン 3D ベクター地図**:
   - MapLibre Native + PMTiles による滑らかな拡大縮小・回転・ピッチ傾斜描画。
   - 3D 建造物の立体立ち上げ、鉄道の JR / 私鉄分離表示、主要道路階層表現。
2. **オフライン天文日夜自動切替**:
   - `SolarCalculator` により外部通信なしで端末時計と現在地座標から日の出・日没時刻を秒単位で計算。自動でライト/ダークスタイルを切替。
3. **ロバストな検索と POI 解析**:
   - SQLite `search_text` 列を活用した高速 LIKE 検索（FTS5 非対応端末セーフ）。
   - かな／カナ／ローマ字の表記ゆれ正規化（`SearchNormalizer`）。
   - OSM `opening_hours` パーサ（32件のテストケース合格、端末時計による営業中判定）。
4. **高精度ルーティング・案内基底**:
   - 純粋 JVM A* 探索（`:routing-core` 共有）。
   - 複数ルート候補探索（ペナルティ法）、所要時間推定器、車線案内（`LaneHint`）。
   - 30m 逸脱適応リルート（再探索中も旧ルート表示を維持しチラつきを防止）。
5. **省電力・発熱抑制ポリシー**:
   - `CameraMotionPolicy` によるカメラ追従デバウンス制御、`BatteryMonitor` による描画リフレッシュ最適化。

---

## 6. ビルド・テスト検証状況

- **テスト環境**: macOS (Apple Silicon) / OpenJDK 21 (`/opt/homebrew/opt/openjdk@21`)
- **テスト実行結果**:
  ```bash
  ./gradlew :routing-core:test :osm-importer:test :app:testDebugUnitTest
  # -> BUILD SUCCESSFUL
  #   - :routing-core: 74 tests PASS
  #   - :osm-importer: 3 tests PASS
  #   - :app: 204 tests PASS
  #   計 281 テスト 全件 PASS (エラー・失敗 0)
  ```
- **実機検証端末**: Samsung Galaxy S21 5G (Android 15 / API 35)
  - 機内モード（完全オフライン）および Wi-Fi 接続の両方で検証済み。

---

## 7. 今後の課題と次のステップ

1. **T3 (バック機能の接続)**:
   - `TravelMode`（自転車/徒歩/車）の UI モード切替接続。
   - `PenaltyMultiRouteFinder` による複数ルート比較表示の結線。
   - `AdaptiveRerouteManager` を実ナビ探索ループへ組み込み。
   - `OpeningHoursParser` を POI 詳細シートへ反映。
2. **T4 (UI の接続 - Google Maps ライク)**:
   - 3段アンカーボトムシート（`ThreeStageBottomSheet`: Peek 120dp / Half 320dp / Full 560dp）の場所詳細への完全適用。
   - 全幅緑ヘッダー、車線案内バー、ETA フッターを備えた `NavigationScreen` の本番適用。
3. **残存 500行超過ファイルの微調整**:
   - `VectorMapStyles.kt` (505行) の定数・関数をわずかに整理し、プロジェクト全体の 500行以内を完全維持。
4. **配線完了後の再採点台帳更新**:
   - `check_wiring.sh` で全項目が `ok` に移行した段階で、`scorecard_90percent.md` を事実に基づいて再採点し、全体 90% 超を確定。
