# test/now.md — CycleMap /test 現状把握・総合分析レポート

> 作成日時: 2026-10-12  
> 対象ディレクトリ: `cycle_map/test/`  
> 基準Gitコミット: `77466e0` (`tag: v0.8-test` 直後、未コミット変更あり)  
> 検証環境: macOS (M-series) / JDK 21 (`/opt/homebrew/opt/openjdk@21`)  
> 実機検証対象（実績記録）: Samsung Galaxy S21 5G (SC-51B, Android 15 API 35)

---

## 0. エグゼクティブサマリー

`cycle_map/test/` は、プロダクションコード（`application/`）から派生した **次世代UI/UXおよび機能プロトタイプ開発用サンドボックス** です。
完全オフライン動作（自前 A* ルーティング、MapLibre 3D ベクタータイル、SQLite LIKE 検索）というコア強みを維持したまま、**Google Maps と同等のモダンな操作感・視覚体験（目標互換度 ≥90%）** を実現するための集中改修が行われています。

直近のリリースである `v0.8-test`（コミット `77466e0`、2026-10-11）以降、さらに大規模な改善（昼夜連動ダークモード、省電力・発熱抑制ポリシー、Google Maps 3タブ下部ナビ＆地名サマリーシート、独立丸型フローティング操作系、放射状ビーム付きロケーションパック）が実装済みであり、**ユニットテスト全187件合格、APKビルド（`:app:assembleDebug`）100% 成功** の極めて安定した状態にあります。

ただし、作業ツリーには未コミットの変更（9変更ファイル、27未追跡ファイル）が存在するほか、設計原則（巨大ファイルの分割）への抵触、および本体（`application/`）とのテストケース乖離などの課題が確認されています。

---

## 1. 構成とディレクトリ構造の全貌

`test/` ディレクトリ直下には以下の主要リソースが配置されています。

```text
cycle_map/test/
├── app/                        # Android アプリケーションモジュール (com.gorite.cyclemap.test)
│   ├── src/main/assets/styles/ # MapLibre スタイル定義 (protomaps_light.json, protomaps_dark.json 等)
│   ├── src/main/java/          # アプリソースコード (Compose UI, MapLibre, センサー, パワー管理)
│   └── src/test/java/          # 単体テスト (テーマ, 省電力, カメラポリシー, 検索, トラッキング等)
├── routing-core/               # 純粋JVM ルーティングエンジン (A*, CostModel, mmap グラフリーダー)
├── osm-importer/               # JVM CLI OSM PBF → .graph / .graph.idx コンバータ
├── oparations/                 # プロジェクト運用・設計方針ドキュメント (未追跡 2件)
│   ├── plans.md                # Google Maps 互換度 ≥90% 達成指示書 (P0〜P7)
│   └── cautions.md             # アーキテクチャ・ロジック・UI 注意点と不退転の3原則
├── sample_img/                 # Google Maps 公式UI 参考スクリーンショット (未追跡 11枚)
├── screenshots/                # Galaxy S21 実機検証スクリーンショット (86枚)
├── tools/                      # 音声案内生成スクリプト等 (gen_voice.py, voice_phrases.json)
├── vector_test/                # ベクタータイル初期検証用サブプロジェクト
├── *.png                       # 直下に散乱する検証・UIスクリーンショット群 (51枚[実測最大70枚]、約60MB、.gitignore除外対象)
├── DEVICE_TEST_REPORT.md       # 実機検証レポート (Galaxy S21, 1091行, DEF-01〜DEF-11)
├── DISTRIBUTION_NOTES.md       # 配布ライセンス・出典・外部データ（OSM/Overture/国交省）利用規定
├── HSA_STAR_EXTERNAL_REVIEW_REQUEST.md # HSA* 外部レビュー依頼・実験用ルーティング検証文書
├── README_DATA.md              # 外部データセット仕様・配置ガイド (OSM/境界/国土数値情報/タイル)
├── handoff-from-codex.txt      # Codexからの初期引き継ぎメモ (山口県スコープ、完全オフライン方針等)
├── bugs.md                     # 残存・未修正課題レポート (BUG-71〜BUG-85)
├── bugs_archive.md             # 過去の修正完了不具合アーカイブ (全70件)
├── plans.md / plan-re.md       # 過去のタスク指示書 (フェーズ1/2, 動的POI表示)
├── re_bugs.md                  # 第3者UI/UX評価・バグ報告書
└── now.md                      # 本ドキュメント (現状把握台帳)
```

---

## 2. 実装状況と直近の変更点（未コミット作業）

コミット `77466e0`（α0.8-test）からの差分として、**9個の追跡対象変更ファイル（+1,399 / -171 行）** および **27件の未追跡ファイル** が作業ツリーに存在します。

### 2.1 作業ツリー変更・未追跡ファイルの内訳（計36ファイル）

1. **追跡対象変更ファイル (9件)**:
   - `test/app/src/main/java/com/gorite/cyclemap/MainActivity.kt` (+65 / -15)
   - `test/app/src/main/java/com/gorite/cyclemap/MapOverlayHelper.kt` (+19 / -8)
   - `test/app/src/main/java/com/gorite/cyclemap/MapScreen.kt` (+265 / -63)
   - `test/app/src/main/java/com/gorite/cyclemap/VectorMapView.kt` (+263 / -27)
   - `test/app/src/main/java/com/gorite/cyclemap/ui/cycling/CyclingPanels.kt` (+614 / -51)
   - `test/app/src/main/java/com/gorite/cyclemap/ui/cycling/GoogleFloatingControls.kt` (+106 / -0)
   - `test/app/src/main/java/com/gorite/cyclemap/ui/cycling/GoogleNavHeader.kt` (+7 / -3)
   - `test/app/src/main/java/com/gorite/cyclemap/ui/theme/Theme.kt` (+46 / -3)
   - `test/app/src/test/java/com/gorite/cyclemap/ThemeTest.kt` (+14 / -1)
2. **未追跡ファイル (27件)**:
   - **運用・設計方針 (`test/oparations/`, 2件)**:
     - `test/oparations/cautions.md` (アーキテクチャ・ロジック・UI 注意点と不退転の3原則)
     - `test/oparations/plans.md` (Google Maps 互換度 ≥90% 達成指示書 P0〜P7)
   - **参考スクリーンショット (`test/sample_img/`, 11件)**:
     - `01_map_detail_inokuchimyojin.jpg` 〜 `11_map_chiyoda_imperial_palace_overview.jpg` (Google Maps 実機参照画像)
   - **リソース・スタイル (`test/app/src/main/`, 7件)**:
     - `assets/styles/protomaps_dark.json` (MapLibre ナイトモード用ベクタータイルスタイル)
     - `res/drawable/` アイコン 6件 (`ic_google_bookmark.xml`, `ic_google_car.xml`, `ic_google_explore.xml`, `ic_google_mic.xml`, `ic_google_moon.xml`, `ic_google_nav_arrow.xml`)
   - **ソースコード (`test/app/src/main/java/`, 3件)**:
     - `com/gorite/cyclemap/power/BatteryMonitor.kt` (バッテリー監視および `BatteryPolicy` を内部定義)
     - `com/gorite/cyclemap/power/CameraMotionPolicy.kt` (カメラ追従デバウンス制御)
     - `com/gorite/cyclemap/ui/theme/DayNightManager.kt` (NOAA天文計算による日出没判定エンジン)
   - **単体テスト (`test/app/src/test/java/`, 3件)**:
     - `BatteryPolicyTest.kt` (4テスト)
     - `CameraMotionPolicyTest.kt` (6テスト)
     - `DayNightManagerTest.kt` (5テスト)
   - **ドキュメント (1件)**:
     - `test/now.md` (本ドキュメント)

---

### 2.2 主要実装機能の詳細

#### A. 昼夜連動ナイトモード（Day/Night System）
1. **NOAA 天文計算エンジン (`DayNightManager.kt`)**:
   - 外部通信ゼロで、端末時計と GPS 緯度経度から日出・日没時刻（午前0時からの経過分）を正確に計算する `SolarCalculator` を実装。未測位時は広島市（34.3853, 132.4553）をフォールバックとして例外を回避。
   - `DayNightResolver` により「自動（昼夜連動）/ 常にライト / 常にダーク」の3モードを判定。
   - `MainActivity.kt` に 60秒周期の定期タイマー（`timeTick`）を組み込み、停止中や室内でも日没/日の出時刻の到来に合わせて自動切替。
2. **システムバー & UI テーマ連動 (`Theme.kt`, `MainActivity.kt`)**:
   - `GoogleDarkColorScheme`（背景 `#1F1F1F`、サーフェス `#28292A`、案内ヘッダー深緑 `#0D652D`、テキスト `#E8EAED`）を追加。
   - ナビ案内中および夜間は自動的に `SystemBarStyle.dark`（白アイコン）へ遷移し、Edge-to-Edge 表示を最適化。
3. **ベクターマップ動的色替え (`VectorMapView.kt`, `protomaps_dark.json`)**:
   - `applyNightModeColors(style, isNight)` により、MapLibre の背景、陸地、水域（`#17263C`）、建物2D/3D（`#2E3035`）、緑地、道路、ケーシングを夜間配色へ動的切替。
   - POI テキストラベル色も昼夜連動（`MapOverlayHelper.poiTextColor(category, isNight)`）。

#### B. 省電力・発熱抑制ポリシー (`power/`)
1. **バッテリー監視と 2D フォールバック (`BatteryMonitor.kt`)**:
   - ※注意: `BatteryPolicy` は独立したファイルではなく、**`BatteryMonitor.kt` 内の `object BatteryPolicy` として定義**されています。
   - OS の `ACTION_BATTERY_CHANGED` および `ACTION_POWER_SAVE_MODE_CHANGED` を受信し、`StateFlow<BatteryStatus>` で配信。
   - バッテリー残量 20% 以下（かつ非充電中）、または OS 省電力モード有効時に、重い 3D 建物描画を自動抑制して 2D 表示へフォールバック。
2. **カメラモーション・デバウンス制御 (`CameraMotionPolicy.kt`)**:
   - Snapdragon 888 の発熱とバッテリー浪費を防止。
   - 停止時（< 0.5 m/s ≈ 1.8 km/h）: 2.5m 未満の微小な GPS ジッター揺らぎを抑止、更新間隔を最小 2.5 秒に制限。
   - 低速時（1.8〜10.8 km/h）: 1.2m 未満かつ 1 秒未満の小刻みな更新をデバウンス。
   - 巡航時（> 10.8 km/h）: 400ms の高レスポンス追従を維持。

#### C. Google Maps 完全再現 UI/UX
1. **位置サマリーシート (`LocationSummarySheet` in `CyclingPanels.kt`)**:
   - 画面下部に Google Maps 同様の地名（例:「井口明神」）・天候プレースホルダー付きカードを配置。
2. **3タブ下部ナビゲーションバー (`GoogleNavTabBar` in `CyclingPanels.kt`)**:
   - 「スポット（Explore）」「保存済み（Saved）」「記録（Record）」の 3タブ構成。
3. **独立丸型フローティング操作系 (`GoogleFloatingControls.kt`, `MapScreen.kt`)**:
   - **右上 (Top-Right)**: 40dp 白丸レイヤー切替ボタン (`GoogleLayerButton`)。
   - **右中央 (Center-Right カラム)**:
     - **コンパスダイヤル (`CompassDial`)**: カラム最上部に配置。**常時表示**（※Google Maps 本家と異なり、地図が北向きであっても非表示にならず常に表示される仕様）。タップでノースアップ復帰。
     - **3D / 2D トグル**: 48dp 白丸ボタン。
     - **縦並びズーム `+` / `-`**: 400ms 長押し後 180ms 連続リピート機能付き。
     - **エリア選択ボタン**: 地図切り出し範囲指定。
   - **右下 (Bottom-Right)**:
     - 48dp 白丸 GPS 再センタリングボタン (`GoogleGpsButton`)。
     - 56dp 深緑ティール（`#006A60`）のナビ開始 FAB (`NavStartFAB`、白色 ↱ 矢印）。
4. **Google Maps 風ロケーションパック (`VectorMapView.kt`)**:
   - 停止時（方位なし）: 青丸ドット＋白枠。
   - 移動時（方位あり）: 青丸ドット＋前方 60° 放射状グラデーションビーム（地図北追従回転）。
   - ナビ案内中: ナビ専用矢印マーカー。

---

### 2.3 ファイル肥大化と設計ルール違反の現状

`test/oparations/cautions.md` §1.2 および §0 の最優先3原則では、「巨大ファイルへの追記を行わず、機能追加は新規ファイルへ置くこと」が厳格に規定されています。

しかし、直近の改修では既存の巨大ファイルへ直接大規模な実装が行われており、設計ルール違反・技術的負債の増大が確認されます:
- **`MapScreen.kt`**: 4,134行（今回の改修で **+265行 / -63行** 変更）
- **`CyclingPanels.kt`**: 2,233行（今回の改修で **+614行 / -51行** 変更）
- **`VectorMapView.kt`**: 1,001行（今回の改修で **+263行 / -27行** 変更）

これらは次期タスク（P0）において、`ui/map/` 配下（`MapControls.kt`, `MapSheets.kt` 等）への分離・リファクタリングが強く求められます。

---

## 3. ビルド・テスト・実機検証の現況

### 3.1 ユニットテスト結果と `application/` との乖離

#### `test/` 単体テスト実行結果
- **コマンド**: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew test` (または `:routing-core:test :osm-importer:test :app:testDebugUnitTest`)
- **結果**: **187 テスト中 187 件 PASS（失敗 0、エラー 0、スキップ 0）**
  - `:routing-core`: 69 テスト
  - `:osm-importer`: 3 テスト
  - `:app`: 115 テスト (Debug)

#### `application/` とのテスト乖離（Divergence）の分析
`application/` と `test/` を比較検証した結果、両者のテストセットに双方向の乖離が発生しています:

| 項目 | `test/` | `application/` | 乖離の要因・詳細 |
|---|---|---|---|
| **Debug単体テスト総数** | **187 件** | **174 件** | 差分 13 件（187 - 174） |
| **内訳: routing-core** | **69 件** | **74 件** | `application/` 側に 5件先行（未バックポート） |
| **内訳: osm-importer** | **3 件** | **3 件** | 完全一致 |
| **内訳: app (Debug)** | **115 件** | **97 件** | `test/` 側に 18件先行追加 |
| **`test/` にのみ存在するテスト** | **+18 件** | なし | `BatteryPolicyTest` (4件), `CameraMotionPolicyTest` (6件), `DayNightManagerTest` (5件), `SearchHelperTest` (+2件), `ThemeTest` (+1件) が追加済み |
| **`application/` にのみ存在するテスト** | なし | **+5 件** | `application/routing-core` で追加された以下の 5 テストが `test/` に未バックポート: <br>1. `AltRoutingTest.bidirectionalFindsOptimalPathOnAsymmetricGraph`<br>2. `LazyMappedRoutingTest.gradePenalty_asymmetricAndBounded`<br>3. `NavigationStatsTest.holdsEffectiveSpeedWhenStoppedWithLastSpeed`<br>4. `RoutingTest.cyclingCostModel_asymmetricGradePenaltyAndMinimumClamp`<br>5. `RoutingTest.geodesicDistanceMeters_accurateAndHandlesEdgeCases` |

※ 本来あるべき結合テスト総数は **192件**（187 + 5）であり、`application/routing-core` の 5件のテストを `test/` へ取り込む必要があります。

---

### 3.2 アプリケーションビルド結果
- **コマンド**: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug`
- **結果**: **BUILD SUCCESSFUL (所要時間 約 6〜15 秒)**
- **生成アーティファクト**: `test/app/build/outputs/apk/debug/app-debug.apk` (サイズ: 約 82 MB)
- **ビルド警告**:
  - `LocalLifecycleOwner` 非推奨警告 (`MapScreen.kt:370`, `NavigationHelper.kt:254`)
  - `setBuiltInZoomControls` 非推奨警告 (`MapScreen.kt:1701`)
  - `setPadding` 非推奨警告 (`VectorMapView.kt:696`)
  - `LocationTrackingService.kt:220` 条件常時真警告（致命的エラーなし）

---

### 3.3 実機検証ステータス (Samsung Galaxy S21 SC-51B)

- **現在の ADB 接続状態**: **未接続（0台）**  
  `adb devices -l` 実行結果: `List of devices attached`（空行）。現在ホストPCに実機はUSB/Wi-Fi接続されていません。
- **実機検証の実績記録**:  
  過去セッション（`DEVICE_TEST_REPORT.md` および α0.8-test ビルド時）において Galaxy S21 (SC-51B) で機内モード動作確認を実施済み。
  - プロセス: `com.gorite.cyclemap.test` 常駐確認
  - オフライン動作: 広島県データ（187MB グラフ、38MB 検索DB、131MB PMTiles）にて全画面表示・自律ルーティング・LIKE検索・TTS音声案内・3D描画を確認済み。
  - 過去レポート `DEVICE_TEST_REPORT.md` で報告された 11件の不具合（DEF-01〜DEF-11）は `v0.4-test` 以降で全件解消済み。

---

## 4. ドキュメント体系と運用台帳

| ドキュメント | 格納場所 | 内容とステータス |
|---|---|---|
| **plans.md** (最新方針) | `test/oparations/plans.md` | Google Maps 互換度 ≥90% 達成指示書（P0〜P7 フェーズ計画、公式採点基準） |
| **cautions.md** | `test/oparations/cautions.md` | アーキテクチャ上の注意点、二重地図エンジン問題、巨大ファイル対策、不退転の3原則 |
| **sample_img/** | `test/sample_img/` | Google Maps 実機 UI 参考画像群 (11枚。ナビ画面、地名シート、3D建物等の目指すべきUI仕様) |
| **DEVICE_TEST_REPORT.md** | `test/DEVICE_TEST_REPORT.md` | Galaxy S21 実機検証詳細レポート（1,091行、再現手順・全11不具合DEF-01〜11の改修記録） |
| **bugs.md** | `test/bugs.md` | 残存バグ台帳（BUG-71〜BUG-85）。BUG-71（Windows日本語パス）を除き全件修正済み |
| **bugs_archive.md** | `test/bugs_archive.md` | 過去の解決済みバグ台帳（BUG-01〜BUG-70、計70件アーカイブ） |
| **DISTRIBUTION_NOTES.md** | `test/DISTRIBUTION_NOTES.md` | 配布時のライセンス・クレジットメモ（ODbL, 地理院タイル, Overture, 国土数値情報等の利用条件） |
| **HSA_STAR_EXTERNAL_REVIEW_REQUEST.md** | `test/HSA_STAR_EXTERNAL_REVIEW_REQUEST.md` | HSA* 長距離ルーティング改善案の外部レビュー依頼書（設計思想・数値・論点） |
| **README_DATA.md** | `test/README_DATA.md` | 外部データセット仕様・配置ガイド（OSM PBF, 都道府県境界, 国土数値情報, タイル仕様） |
| **handoff-from-codex.txt** | `test/handoff-from-codex.txt` | Codex エージェントからの初期引き継ぎ申し送りメモ（山口県スコープ、完全オフライン方針等） |
| **VERSIONS.md** | ルート直下 `VERSIONS.md` | リリース台帳。最新記録は α0.8-test (`77466e0`)。今回の未コミット差分は α0.9-test 候補 |
| **AGENTS.md** | ルート直下 `AGENTS.md` | プロジェクト全体の開発体制・Git運用ポリシー・ビルドオフロード規定 |

---

## 5. Google Maps 互換度 採点表（現状把握）

> **【重要: 採点表に関する自己評価注記】**  
> 以下の達成度スコアは、`test/oparations/plans.md` §9 の評価軸に基づいて **開発・調査エージェントが直近の実装内容から推定・試算した「主観的自己評価値」** です。  
> `plans.md` §8 で規定されている厳格な QA 検証（`OfflineParityTest`, `SearchLatencyTest` <120ms実測, Paparazzi スクリーンショットテスト等）による公認監査スコアではありません。客観的評価の確定には P7 QA タスクの実施が必要です。

### 5.1 フロントエンド（重み 50%）: 推定スコア 約85.1%
- **F1 (テーマ一貫性, 重み 8)**: **0.95** (昼夜連動 SolarCalculator + GoogleDarkColorScheme + 地図動的色替え完了)
- **F2 (検索バー+チップ, 重み 8)**: **0.90** (上部検索バー、独立スクロールチップ、通知バナー非被り)
- **F3 (右側白丸ボタン群, 重み 6)**: **0.85** (レイヤー40dp、GPS48dp、深緑FAB56dp、独立ズーム実装済。ただしコンパスがノースアップ時も常時表示されており合格基準未達のため減点)
- **F4 (地図スタイル・線路・建物, 重み 14)**: **0.85** (道路拡幅、線路階層化/枕木ハッチング、建物陰影0.72)
- **F5 (場所詳細シート, 重み 8)**: **0.75** (地名サマリーシート追加。営業時間・詳細情報は静的)
- **F6 (経路選択画面, 重み 10)**: **0.80** (プレビューカード、複数ルート比較の基盤あり)
- **F7 (ナビ画面・ヘッダー/フッター, 重み 14)**: **0.95** (全幅深緑ヘッダー、大矢印、残距離、機能統合フッター)
- **F8 (モーション・操作感, 重み 8)**: **0.90** (CameraMotionPolicy、慣性スワイプ、長押しズーム)
- **F9 (アクセシビリティ/多言語, 重み 6)**: **0.60** (ハードコード文字列の `strings.xml` 移行が一部残存)

### 5.2 バックエンド（重み 50%）: 推定スコア 65%
- **B1 (経路探索品質, 重み 14)**: **0.70** (自転車A*は高品質、徒歩・車モード分離は未着手)
- **B2 (リルート/逸脱検知, 重み 8)**: **0.85** (OffRouteDetector、自動リルート機能)
- **B3 (ターンバイターン案内, 重み 10)**: **0.75** (TTS音声案内、距離別トリガー、車線案内データは未統合)
- **B4 (場所検索, 重み 14)**: **0.85** (SQLite LIKE検索、ひらがな/カタカナ/ローマ字対応、<120ms)
- **B5 (POIメタデータ, 重み 10)**: **0.40** (`opening_hours` 構文パーサー未実装)
- **B6 (交通/混雑考慮, 重み 8)**: **0.20** (時間帯別速度プロファイル未着手)
- **B7 (現在地・追跡・記録, 重み 8)**: **0.95** (BatteryPolicy、ForegroundService GPX記録、DeadReckoning)
- **B8 (オフライン切替・データ管理, 重み 8)**: **0.60** (Prefectures動的切替対応、管理専用UIは簡易)
- **B9 (品質保証・テスト, 重み 10)**: **0.75** (187単体テスト、実機検証、GoldenTest一部未整備)

**総合達成度**: フロント (約85.1% × 0.5) + バック (65% × 0.5) = **約 75.05%**  
（α0.7時点の約50%から大幅伸長。目標 90% に向けて B1/B3/B5/P0 の消化が焦点）

---

## 6. 残存課題と次期アクションプラン

### 6.1 直近の残課題（Immediate Next Steps）
1. **未コミット変更の確定とリリース（α0.9-test 候補）**:
   - 作業ツリーにある変更（9ファイル修正 + 27未追跡ファイル）を整理し、人間（Gorite）の承認を得てコミット・APKビルド・`VERSIONS.md` 台帳記録を行う。
2. **テスト乖離の解消（`routing-core` 5テストの取り込み）**:
   - `application/routing-core` で追加された 5件のテストを `test/routing-core` へバックポートし、`test/` 側の単体テスト総数を 192件へ引き上げる。
3. **巨大ファイルの分割（P0 タスク・ルール違反是正）**:
   - `cautions.md` §1.2 に抵触している `MapScreen.kt` (4,134行) および `CyclingPanels.kt` (2,233行) を `ui/map/` 配下（`MapTopBar.kt`, `MapControls.kt`, `MapSheets.kt`, `NavigationScreen.kt`）へ段階的に切り離す。
4. **`application/` への成果還元方針の策定**:
   - `test/` で先行検証された新機能（`VectorMapView`, `GoogleNav*`, `DayNightManager`, `power/*`）をプロダクションへどのように統合・同期するか、マージ戦略を確定する。
5. **ロードマップ機能の実装（P1, P2 タスク）**:
   - 移動モード（徒歩・車）の探索コスト分離、車線案内データのインポーター拡張、営業時間パーサーの実装。
