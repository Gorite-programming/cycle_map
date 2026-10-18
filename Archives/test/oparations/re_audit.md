# CycleMap re_test Phase 0 監査結果（現状監査と基準線）

## 1. 監査対象と実施範囲

本監査は Phase 0 の要求に沿って実施した。対象資料と確認順は以下の通り。

- `AGENTS.md`
- `test/oparations/re_plans.md`
- `re_test/overhaul_plan.md`
- `re_test/plans.md`
- `re_test/cautions.md`
- `re_test/parity_checklist.md`
- `re_test/prompts/integration_fix.md`
- `re_test/FINAL_REPORT.md`
- `re_test/NIGHT_LOG.md`
- `re_test/scripts/check_wiring.sh`

資料の食い違いがある場合は `test/oparations/re_plans.md` を優先し、コードや実機で確認できていないことを「資料上の主張」ではなく「未確認」とした。

## 2. Git 状態と pull 結果

作業開始時に `git status --short --branch` を確認した。

- `## main...origin/main`
- 変更あり: `M re_test/prompts/integration_fix.md`
- 変更あり: `M test/oparations/plans.md`
- 未追跡: `?? test/now.md`
- 未追跡: `?? test/oparations/re_plans.md`
- 未追跡: `?? test/sample_img/`

前述の通り、これら既存の差分は変更せず、そのまま保持した。`git pull --ff-only` を実行した結果:

- 結果: `Already up to date.`
- そのままの作業状態で続行した。

本作業では `git add` / `git commit` / `git push` / `reset` / `stash` を実行していない。

## 3. 主要機能の4段階分類

### 3.1 分類表

| 分類 | 機能/クラス | 根拠 | 状態 |
|---|---|---|---|
| 定義のみ | `AdaptiveRerouteManager`, `TravelTimeEstimator`, `PenaltyMultiRouteFinder`, `OpeningHoursParser`, `SearchRanking`, `GenreDictionary`, `OfflinePackageManager`, `SolarCalculator`, `MapTopBar`, `MapControls`, `ThreeStageBottomSheet`, `RouteSelectionSheet`, `GoogleNavHeaderView`, `GoogleNavFooterView`, `LaneHint` | `re_test/scripts/check_wiring.sh` で `UNWIRED`。定義ファイルは存在するが、他のメインソースから参照されていない。 | 未接続 |
| アプリ本体から呼び出されている | `TravelMode`, `SearchNormalizer`, `OnlineFeatureGate`, `ConnectivityState`, `MapViewModel`, `AccuracyCircleOverlay`, `NetworkStatusIndicator`, `TileSourceSelector` | 同スクリプトで `ok`。少なくとも他のソースから参照されている。 | 接続済みの可能性あり |
| 画面から操作できる | なし | 実機スクリーンショットの確認や、画面の操作系テストの証拠が不足している。 | 未確認 |
| 実機で確認できる証跡がある | なし | 画像ファイル自体はあるが、実際の画面内容の確認証跡がない。スナップショットの再利用・バックアップ重複があるため、実機検証としては不十分。 | 未確認 |

補足:
- `TravelMode` は定義と使用があるが、ルーティングの実際の UI 呼び出し（画面操作での実働）は確認できていない。
- `SearchNormalizer` は `ok` だが、検索画面と連動している証拠は未確認。
- `MapViewModel` は `ok` だが、MVI の操作パスとして画面上で使われているかは未確認。

## 4. `check_wiring.sh` の結果

実行コマンド:

- `cd re_test && ./scripts/check_wiring.sh`

実行結果（要点）:

```text
UNWIRED      AdaptiveRerouteManager  (defined in AdaptiveRerouteManager.kt, no other main-source user)
UNWIRED      TravelTimeEstimator  (defined in TravelTimeEstimator.kt, no other main-source user)
UNWIRED      PenaltyMultiRouteFinder  (defined in TravelModes.kt, no other main-source user)
ok           TravelMode  (1 user file(s))
UNWIRED      OpeningHoursParser  (defined in OpeningHoursParser.kt, no other main-source user)
UNWIRED      SearchRanking  (defined in SearchRanking.kt, no other main-source user)
ok           SearchNormalizer  (2 user file(s))
UNWIRED      GenreDictionary  (defined in GenreDictionary.kt, no other main-source user)
UNWIRED      OfflinePackageManager  (defined in OfflinePackageManager.kt, no other main-source user)
UNWIRED      SolarCalculator  (defined in DayNightManager.kt, no other main-source user)
ok           OnlineFeatureGate  (2 user file(s))
ok           ConnectivityState  (2 user file(s))
ok           MapViewModel  (2 user file(s))
UNWIRED      MapTopBar  (defined in MapTopBar.kt, no other main-source user)
UNWIRED      MapControls  (defined in MapControls.kt, no other main-source user)
ok           AccuracyCircleOverlay  (7 user file(s))
ok           NetworkStatusIndicator  (1 user file(s))
UNWIRED      ThreeStageBottomSheet  (defined in MapSheets.kt, no other main-source user)
UNWIRED      RouteSelectionSheet  (defined in MapSheets.kt, no other main-source user)
UNWIRED      GoogleNavHeaderView  (defined in NavigationScreen.kt, no other main-source user)
UNWIRED      GoogleNavFooterView  (defined in NavigationScreen.kt, no other main-source user)
UNWIRED      LaneHint  (defined in LaneAndGuidance.kt, no other main-source user)
ok           TileSourceSelector  (1 user file(s))
--- big files (limit 500 lines) ---
```

結論:
- 未接続クラスが多数存在する。
- `500行超の Kotlin ファイル` は本スクリプトでは検出されていない（`OVER500` なし）。
- ただし、`UNWIRED` のまま残るクラスが多数あり、Phase 1 以降への依存関係としては大きなブロッカーである。

## 5. 既存のテストファイルと Gradle 結果

### 5.1 既存のテスト・ビルド証跡

以下の既存証跡が確認できた。

- `re_test/app/build/test-results/testDebugUnitTest/`
- `re_test/app/build/reports/tests/testDebugUnitTest/`
- `re_test/app/build/outputs/`

`grep` で確認したテストファイルの例:

- `com.gorite.cyclemap.ui.map.P7ParityAndFallbackTest`
- `com.gorite.cyclemap.routing.TravelModesTest`
- `com.gorite.cyclemap.routing.TravelTimeEstimatorTest`
- `com.gorite.cyclemap.routing.AdaptiveRerouteTest`
- `com.gorite.cyclemap.search.SearchRankingAndLatencyTest`
- `com.gorite.cyclemap.data.StyleJsonTest`
- `com.gorite.cyclemap.ui.theme.AstronomicalSolarTest`
- `com.gorite.cyclemap.ui.map.MapViewModelTest`

`re_test/app/build/test-results/testDebugUnitTest` 配下の XML には、失敗 0 のテストスイートが多数記録されている。

### 5.2 ただし、現在の証跡は「アプリ接続の確認」には使えない

結論として:
- 既存のテスト結果は存在する（未確認ではない）。
- ただしそれは「当該クラスがテストを通る」までを示すだけで、ユーザー操作からの接続や画面表示の確認までは証明していない。
- `re_plans.md` の規約でも、テストクラスや Composable の存在だけでは加点しないと明記されている。

よって、Phase 0 では「テストが存在する」ことは記録するが、実際の採点には使わない。

## 6. スクリーンショットの確認

確認したファイル:

- `re_test/screenshots/P0_device.png`
- `re_test/screenshots/P1_device.png`
- `re_test/screenshots/P2_device.png`
- `re_test/screenshots/P3_device.png`
- `re_test/screenshots/P4_device.png`
- `re_test/screenshots/P5_device.png`
- `re_test/screenshots/P6_device.png`
- `re_test/screenshots/T2_device.png`
- `re_test/screenshots/T2_online.png`
- `re_test/screenshots/before_T1_device.png`
- `re_test/screenshots/after_T1_device.png`
- `re_test/screenshots/*_invalid_bak.png`

### 6.1 画像サイズと重複の確認

`python` + PIL で確認した結果:
- すべて `1080x2400` の画像である。
- 重複ファイルが複数ある。

重複の例:
- `T2_online.png` と `after_T1_device.png` は同一ハッシュ。
- `T2_device_invalid_bak.png`, `T2_online_invalid_bak.png`, `after_T1_device_invalid_bak.png` は同一ハッシュ。

これは「同一画像の再利用」または「backup スクリーンショットの残存」を示し、証跡の信頼性としては不十分である。

### 6.2 実際に何が写っているかの判定

終端上での確認では、画像の内容を可視解析できないため、「どの機能が実際に写っている」と断定できない。したがって、以下のように扱う。

- 「画像ファイルが存在する」ことは確認できた。
- 「それが現行の機能画面の適切な証拠である」ことは未確認。
- 画像名からの推定のみで得点を付けることはしない。

このため、スクリーンショットの大部分は「証拠としては不十分 / 未確認」と扱った。

## 7. Galaxy S21 接続・配置済みデータの確認

### 7.1 端末接続状態

`adb devices -l` の結果:

```text
List of devices attached
R5CR70NDMCM            device usb:17825792X ...
```

接続は確認できた。端末設定変更、アプリ再インストール、アプリデータ消去、データ転送は行わない。

### 7.2 端末上の CycleMap データ存在

確認した CycleMap データのパス:

- `/sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/`

存在している主要ファイル（read-only 確認）:

- `Tottori.search.db`
- `Tottori.graph`
- `Tottori.graph.idx`
- `Shimane.search.db`
- `Shimane.graph`
- `Shimane.graph.idx`
- `Okayama.search.db`
- `Okayama.graph`
- `Okayama.graph.idx`
- `Hiroshima.search.db`
- `Hiroshima.graph`
- `Hiroshima.graph.idx`
- `Yamaguchi.search.db`
- `Yamaguchi.graph`
- `Yamaguchi.graph.idx`

SHA-256 の一例:

- `Tottori.search.db` = `869ffcf10b3e42bacf67d8812bc9e050e4b5718f311b1fc8a9e8371b4b435194`
- `Shimane.search.db` = `8c06000b4339a641890af5b343d8f50950c30792d98ce24da3f25ac8c1866e7e`
- `Hiroshima.graph` = `2ce6177732c68621eb2965b6229805e46bf42f1caa8f5e22897c5b6515434231`
- `Yamaguchi.graph.idx` = `b4b2879adfb0aef2ac168f9e84ca9210e9041d23a7219fcdded60a3478272300`

結論:
- 端末が接続されており、CycleMap の外部データは配置されている。
- ただしこのデータの「アプリの画面とどの程度一致しているか」は未確認。
- 実機確認は「存在確認」に留まり、機能の正常動作確認には使えない。

## 8. 初期スコア再計算（`re_plans.md` 基準）

### 8.1 採点方針

以下は `re_plans.md` の 0 / 0.5 / 1 採点規則に基づく初期スコアである。過去の 90.15% は使用せず、現行コードと接続証跡から再計算した。

### 8.2 フロント項目（重み 82）

| ID | 得点 | 根拠 | テスト名 | 証跡パス | 実機確認 | 未達理由 |
|---|---:|---|---|---|---|---|
| F1 | 0.5 | `ThemeTest` と `DayNightManager.kt` は存在するが、画面からの接続と実機動作は未確認 | `com.gorite.cyclemap.ThemeTest` | `re_test/app/src/test/java/com/gorite/cyclemap/ThemeTest.kt` | 未確認 | UI の実際の切替が画面上で確認できない |
| F2 | 0.5 | `MapTopBar` 自体は未接続だが、現行画面は `MapTopOverlays` 経由で `TopSearchBar` と `CategoryChipRow` を呼び、検索・メニュー等のコールバックを渡している | UIテストなし | `re_test/app/src/main/java/com/gorite/cyclemap/MapTopOverlays.kt`, `MapContentLayout.kt` | 未確認 | 実機操作・表示、チップ項目と通知の重なりが未検証 |
| F3 | 0.5 | `MapControls` 自体は未接続だが、現行画面は `MapControlsOverlay` 経由でレイヤー・ズーム・現在地・コンパス操作を接続している | UIテストなし | `re_test/app/src/main/java/com/gorite/cyclemap/MapControlsOverlay.kt`, `MapContentLayout.kt` | 未確認 | 実機での操作・配置・コンパス挙動が未検証 |
| F4 | 0.5 | `TileSourceSelector` と `StyleJsonTest` は存在し、直近のテスト結果もあるが、画面確認が不足 | `com.gorite.cyclemap.data.StyleJsonTest` | `re_test/app/src/test/java/com/gorite/cyclemap/data/StyleJsonTest.kt` | 未確認 | 実際の地図描画の見た目が確認できない |
| F5 | 0.0 | `ThreeStageBottomSheet` は `UNWIRED`。詳細シートの接続なし | `-` | `re_test/app/src/main/java/com/gorite/cyclemap/ui/map/MapSheets.kt` | 未確認 | 実際の UI と接続がない |
| F6 | 0.0 | `RouteSelectionSheet` は `UNWIRED`。経路比較 UI が画面から呼び出されていない | `-` | `re_test/app/src/main/java/com/gorite/cyclemap/ui/map/MapSheets.kt` | 未確認 | 経路選択のユーザー操作が未確認 |
| F7 | 0.0 | `GoogleNavHeaderView` と `GoogleNavFooterView` は `UNWIRED` | `-` | `re_test/app/src/main/java/com/gorite/cyclemap/ui/cycling/GoogleNavHeader.kt` と `GoogleNavFooter.kt` | 未確認 | ナビ画面の実動作が確認できない |
| F8 | 0.5 | `CameraMotionPolicyTest` と `MotionTest` などは存在し、ロジック自体はあるが、画面の動きの証跡が不足 | `com.gorite.cyclemap.CameraMotionPolicyTest` | `re_test/app/src/test/java/com/gorite/cyclemap/CameraMotionPolicyTest.kt` | 未確認 | アプリ画面での操作感は確認不能 |
| F9 | 0.5 | 文字列のリソースや `strings.xml` が存在するが、実際の UI での表示・A11y を検証できていない | `com.gorite.cyclemap.ThemeTest` / 一部 UI テスト | `re_test/app/src/main/res/values/strings.xml` | 未確認 | 日本語UI とアクセシビリティの画面確認がない |

Front 合計: 25 / 82 = 30.49%

### 8.3 バック項目（重み 90）

| ID | 得点 | 根拠 | テスト名 | 証跡パス | 実機確認 | 未達理由 |
|---|---:|---|---|---|---|---|
| B1 | 0.0 | `AdaptiveRerouteManager`, `TravelTimeEstimator`, `PenaltyMultiRouteFinder` は `UNWIRED` | `com.gorite.cyclemap.routing.TravelTimeEstimatorTest` 等 | `re_test/app/src/test/java/com/gorite/cyclemap/routing/TravelTimeEstimatorTest.kt` | 未確認 | ルーティング結果が画面または実際のアプリ遷移から走らない |
| B2 | 0.0 | `AdaptiveRerouteManager` が `UNWIRED`; リルートが本体から呼ばれていない | `com.gorite.cyclemap.routing.AdaptiveRerouteTest` | `re_test/app/src/test/java/com/gorite/cyclemap/routing/AdaptiveRerouteTest.kt` | 未確認 | 実際に画面上で再計算が走る証跡がない |
| B3 | 0.0 | `LaneHint` は `UNWIRED`; ナビ案内の接続なし | `com.gorite.cyclemap.routing.LaneAndGuidanceTest` | `re_test/app/src/test/java/com/gorite/cyclemap/routing/LaneAndGuidanceTest.kt` | 未確認 | UI への接続がなく、車線案内の実運用確認なし |
| B4 | 0.5 | `SearchNormalizer` は `ok` でロジック接続が見えるが、画面検索の実動作・操作確認は不足 | `com.gorite.cyclemap.search.SearchRankingAndLatencyTest` | `re_test/app/src/test/java/com/gorite/cyclemap/search/SearchRankingAndLatencyTest.kt` | 未確認 | 検索バーからの実際の入力〜結果反映が確認できない |
| B5 | 0.0 | `OpeningHoursParser` は `UNWIRED` | `com.gorite.cyclemap.search.OpeningHoursParserTest` | `re_test/app/src/test/java/com/gorite/cyclemap/search/OpeningHoursParserTest.kt` | 未確認 | POI 詳細の UI 連携がない |
| B6 | 0.0 | 時間帯別推定のロジックはファイルレベルでは存在するが、アプリ本体からの呼び出し証拠がない | `TravelTimeEstimatorTest` | `re_test/app/src/main/assets/speed_profiles.json` | 未確認 | 実際の経路選択への反映が確認できない |
| B7 | 0.5 | `GpsHealthMonitorTest` と `GpxParserTest` は存在する; 追跡と記録は個別ロジックとしては存在するが、顕著な端末検証がない | `com.gorite.cyclemap.tracking.GpxParserTest` | `re_test/app/src/test/java/com/gorite/cyclemap/tracking/GpxParserTest.kt` | 未確認 | 実機での GPS/トラッキング画面連動が未確認 |
| B8 | 0.5 | `OfflinePackageManager` は定義ありだが `UNWIRED`。データ管理 UI への接続なし | `com.gorite.cyclemap.data.OfflinePackageManagerTest` | `re_test/app/src/test/java/com/gorite/cyclemap/data/OfflinePackageManagerTest.kt` | 未確認 | 端末上のデータ管理画面と連携していない |
| B9 | 0.5 | テスト結果、レポート記録、スクリンショットは存在するが、テストが UI と現行画面に対して十分検証していない | `com.gorite.cyclemap.ui.map.P7ParityAndFallbackTest` | `re_test/app/build/reports/tests/testDebugUnitTest/index.html` | 一部未確認 | 実機での画面検証と接続確認が未完了 |

Back 合計: 20 / 90 = 22.22%

### 8.4 総合計算

- Front = 25 / 82 = 30.49%
- Back = 20 / 90 = 22.22%
- Overall = (30.49 + 22.22) / 2 = 26.36%

Phase 1 のソース配線確認後、F2/F3 を接続済み・実機未確認として各 0.5 に補正した現行スコアは 26.36%。過去の 90.15% は「資料上の主張」であり、現行コードと接続証跡に対しては破棄した。

## 9. 確認した事実と資料上の主張の区別

### 9.1 確認できた事実
- `git pull --ff-only` は `Already up to date.`
- `re_test` 配下に多数の Kotlin クラスとテストが存在する。
- `check_wiring.sh` は `UNWIRED` を複数検出している。
- 重大な `UNWIRED` クラスが多数存在し、上位の UI/経路/ナビへ接続されていない。
- 端末 `R5CR70NDMCM` は接続されている。
- `com.gorite.cyclemap/files/Documents/CycleMap/` には検索DB と graph が存在する。
- 既存の `re_test/app/build/test-results` と `build/reports` は存在する。

### 9.2 資料だけに記載されている主張
- `re_test/FINAL_REPORT.md` と `re_test/NIGHT_LOG.md` の「P0〜P7 完了」「実機確認済み」「90.15%達成」
- これらの主張は、現行コードと `check_wiring.sh` の実測と照合すると、接続性・画面確認・証跡の妥当性が不足している。
- そのため、作業中の監査では採用しない。

### 9.3 未確認事項
- 画面に何が実際に写っているか（視覚的な検証結果）
- `P1_device.png` 〜 `P6_device.png` が実際の機能と一致しているか
- `MapTopBar`, `MapControls`, `ThreeStageBottomSheet`, `NavigationScreen` のユーザー操作が実際に成功しているか
- 端末上のアプリ状態と実機の挙動の一致

### 9.4 次フェーズの依存関係とブロッカー

Phase 0 時点では Phase 1 に進まず監査を完了した。Phase 1 の開始条件は `re_plans.md` の対象・受入条件であり、無関係な全 `UNWIRED` クラスを 0 にすることではない。Phase 1 に必要な地図機能の現況と残課題は、以下の追記を優先する。

## 10. Phase 1 地図機能の再確認・作業記録 (2026-10-13)

### 10.1 地図機能の配線を対象限定で再確認

| 対象 | コード上で確認した経路 | 判定 | 実機証跡 |
|---|---|---|---|
| 検索バー・カテゴリチップ | `MapContentLayout` → `MapTopOverlays` → `TopSearchBar` / `CategoryChipRow`。検索・メニュー・カテゴリ選択コールバックを渡す | アプリ画面ツリーに接続。旧 `MapTopBar` クラスが未接続という `check_wiring.sh` の結果だけでは、代替の現行UIまで未接続とはいえない | 未確認 |
| 地図操作 | `MapContentLayout` → `MapControlsOverlay`。レイヤー切替、ズーム、現在地追従、コンパス、範囲選択がコールバック経由で接続 | アプリ画面ツリーに接続。旧 `MapControls` クラスは未接続だが、現行操作Overlayは別実装 | 未確認 |
| ベクター地図 | `MapViewsContainer` が `VECTOR_LIBERTY` 選択時に `VectorMapView` を表示し、現在地・テーマ・ルート・POI等を渡す | 呼出し経路あり。描画成功まではコードだけでは確認できない | 未確認 |
| 県別PMTiles解決 | 以前は `VectorMapView` から座標を渡さず、最初に見つかった任意県のファイルへフォールバックしていた。また初期化後の県境移動でMapLibreが再選択されなかった | Phase 1 修正済み。現在地の県選択が変わると `VectorMapView` を再生成し、その県のファイルだけを解決する。未対応・未導入ならその県名を表示して中断する | 未確認 |

### 10.2 PMTiles と生成材料の確認

- リポジトリ内に生成済みファイルを確認した。
  - `sandbox/vector_test/data/Hiroshima_osm.pmtiles`: 23,732,025 bytes、SHA-256 `8b911d6ec67bc8f6b1b450a0fdc4738e4473b60aa87b1a64ce992e09fa3e6d85`。`pmtiles show` は PMTiles v3 / MVT / zoom 0–15 / 広島範囲を示し、`pmtiles verify` 成功。
  - `sandbox/vector_test/data/Hiroshima.pmtiles`: 46,942,442 bytes、SHA-256 `6aa342c46500ddcbd63e27e86adbf11d91de4161ef4d30ed846c762d3b26af1e`。PMTiles v3 / MVT / zoom 4–16、`pmtiles verify` 成功。これはGSI最適化ベクトルタイルの別データで、アプリの県別selectorが広島用に選ぶファイルではない。
- 実機 `R5CR70NDMCM` のテストアプリ用 `Documents/CycleMap/Hiroshima_osm.pmtiles` を読み取り専用で確認。サイズとSHA-256が上記のリポジトリ内ファイルに一致した。したがってPhase 1 の広島PMTiles配置のためにPhase 6の生成・配置を先行させる必要はない。
- 生成手段・入力: `sandbox/vector_test/pipeline/build_tiles.py` と同ディレクトリの `pmtiles` CLI は存在するが、このスクリプトはGSIのリモートPMTilesから `Hiroshima.pmtiles` を抽出するもの。アプリが選択する `Hiroshima_osm.pmtiles` を同スクリプトで再生成する手順は確認できなかった。入力候補 `sandbox/vector_test/data/Hiroshima.osm.pbf` (75,214,736 bytes) はOSM PBFと確認でき、`planetiler.jar` も存在する。Planetilerのヘルプ出力からOSM入力パスとMBTiles出力のオプションは確認したが、対象アーカイブを同じ内容に再生成する完全なコマンド／設定はリポジトリ上で確認できていない。ヘルプ確認のみで生成コマンドは実行していない。生成物が存在するため、この作業では再生成しなかった。
- アプリデータ内の `assets/fonts/` と `assets/sprites/` は存在しなかった。APKには `sprites/ofm` のJSON/PNGはあるが、glyph PBFは見つからない。外部フォント取得を必要とせず日本語ラベルが出ることは未確認。

### 10.3 Phase 1 の変更・検証

- `TileSourceSelector.resolveTileForLocation` を追加し、未導入県や対象外地域で別県のPMTilesを返さないようにした。
- `VectorMapView` は現在地を県判定・初期カメラ位置に使い、地図データがなければ対象県名と導入先を案内する。`MapViewsContainer` は県選択が変わったときだけベクター地図を再生成して再選択する。旧 `MapControls` / `MapTopBar` 全体を無理に接続する変更はしていない。
- `VectorMapStyles` のglyph/sprite URIフォールバックをローカル `asset://` に変更し、アクティブなProtomapsスタイルから暗黙のHTTPS取得を行わない形にした。ローカルglyphが不足しているため、これだけでラベル受入条件を満たすとは判定しない。
- テスト: `:app:testDebugUnitTest --tests 'com.gorite.cyclemap.data.TileSourceSelectorTest' --tests 'com.gorite.cyclemap.data.StyleJsonTest'` 成功。計6テスト、失敗0。`:app:assembleDebug` も成功。APKは作成したが、端末へはインストールしていない。Javaは Android Studio 同梱JDKを明示して実行した。
- PMTilesの `show` と `verify` は両アーカイブで成功。端末上のファイル確認は読み取り専用。
- Git操作、端末設定変更、アプリインストール、端末データ消去・転送は行っていない。

### 10.4 Phase 1 受入条件と残ブロッカー

| 受入条件 (`re_plans.md`) | 現在の状態 | 残作業／ブロッカー |
|---|---|---|
| オフラインでPMTiles・地名ラベル・アイコン・現在地・POI・ルート線・帰属表示 | 未確認 | PMTilesファイルの存在・構造だけ確認。画面描画は未検証。glyph PBFがなく、日本語ラベルの表示も未確認 |
| 未導入県で状態を明示し、別県へフォールバックしない | コード修正と単体テスト済み | 県名表示の実機確認は未実施。県別PMTilesは広島以外の配置を確認していない |
| 4ズーム × ライト/ダーク × オフラインの画像と確認ログ | 未達 | 実機スクリーンショット8枚と内容確認ログが未作成 |
| 外部通信が発生しないこと | 未確認 | アクティブスタイルのリソースfallbackはローカル化したが、実機の通信ログ／ネットワーク制御による確認は未実施。別ソース・他機能を含めた通信有無も未証明 |
| 現在地優先、POIタップ・カメラ移動、ルート線、帰属表示 | コード上に経路あり、実機未確認 | 端末上の操作・表示結果が必要 |

端末の `airplane_mode_on` は読み取り結果 `0`（機内モード無効）。端末設定を変更しない指示に従って切り替えず、オフライン実機検証・スクリーンショット取得は行っていない。現段階では Phase 1 受入未完了であり、コード確認やユニットテストを実機合格の代替とはしない。次の実機作業は、人間が機内モードを有効にした状態で上記8画面・通信・地図操作を確認すること。glyph PBFのライセンス確認・配置と、`Hiroshima_osm.pmtiles` の再生成手順の確立も残課題。

## 11. Phase 1 機内モード実機確認（2026-10-13）

本節は §10 の実機未確認記録を更新する。§10.3 の「端末へはインストールしていない」、§10.4 の `airplane_mode_on=0` および未撮影という記録は、この確認より前の時点の履歴であり、以下の実測で置き換える。

### 11.1 実機条件・データ

- 対象: 接続中の Galaxy S21 (`R5CR70NDMCM`, `SC-51B`)。確認時刻: 2026-10-13 20:55–20:59 JST。
- 機内モード: `settings get global airplane_mode_on` は `1`。`dumpsys connectivity` は `Active default network: none`。端末設定は変更していない。
- アプリ: `com.gorite.cyclemap.test`。既存アプリデータを消去せず `adb install -r` でテストAPKを更新した状態で検証。インストールAPKのSHA-256は `cdb637234e5f6b3a7d3b3c9cb2a593762d50f9f51baaf8c7bdbbf6c660a1ee60`。設定画面は `α0.2-test` / `5ec2532-dirty` と表示する。
- 配置済みPMTilesのSHA-256: `Documents/CycleMap/Hiroshima_osm.pmtiles` = `8b911d6ec67bc8f6b1b450a0fdc4738e4473b60aa87b1a64ce992e09fa3e6d85`。§10.2 で確認したリポジトリ内広島PMTilesと一致。
- アプリ設定のテーマをライト／ダークで確認後、元の「自動」に戻した。`P1_offline_theme_settings_restored.png` で自動が選択状態であることを目視確認した。端末のOS設定は変更していない。
- MapLibre の glyph/sprite リモート要求に関するログは、更新APKでの実機確認時間帯（20:50–20:59）の `logcat` 抽出では検出されなかった。一方、アクティブなスタイルにはラベル/アイコンレイヤーがないため、glyph要求自体が発生しない状態と区別できない。通信全体を監視する計測は行っていないため、「外部通信なし」の受入証明とはしない。APK更新前の旧実行ログに残っていた失敗要求は、今回の更新後ログとは分けて扱う。

### 11.2 画面証跡と視覚確認

最終ファイル名を画面内容と照合した。z14の最初の候補名にライト/ダークの取り違えがあったため、ライトz14を再撮影し、ダークz14も取り直した。下表のSHA-256は最終ファイルの値。

| 証跡 | 内容・確認事項 | SHA-256 |
|---|---|---|
| `re_test/screenshots/P1_offline_z14_light.png` | ライト配色のベクター地図、道路網・河川・鉄道・アプリPOIアイコンを表示。初期カメラ付近（コード初期値14.5、実機画面にはズーム値表示なし）。不規則な黒い領域が見え、タイル/スタイル描画品質は要調査。 | `ac8f23b54dd017543a66c46755e337c701919887667f896a04d3cbd12eabb91e` |
| `re_test/screenshots/P1_offline_z17_light.png` | ライト配色の高倍率表示。ズームボタン操作後の画面。正確なカメラズーム値を画面から読めないため、z17は概算ラベル。 | `3be33e23c3b117dbdd40d22f290babb3cd42f676e507feb8f2d2406a1ae10838` |
| `re_test/screenshots/P1_offline_z14_dark.png` | ダーク配色の地図、道路網・河川・鉄道・現在地マーカー・アプリPOIアイコン。AUTOがシステムのダーク配色になった状態で撮影。初期カメラ付近、正確な値は未計測。 | `3f50e33e46f67111acd6daa1678b719d826d7e38cb361ee1fc337e61469264d0` |
| `re_test/screenshots/P1_offline_z17_dark.png` | ダーク配色の高倍率表示。ズームボタン操作後の画面。正確なカメラズーム値は未計測。 | `a0a7c81cd61df480dc84544c9e4571668d75b4782331bafca6cd16ab5996d418` |
| `re_test/screenshots/P1_offline_poi_tap.png` | POIアイコンをタップした後の地図。操作時にはマーカー強調とカメラ移動を確認したが、この画像だけでは選択状態を明瞭に判定できず、POI詳細カード/情報の表示は確認できない。 | `bfafebd769c71c0d452d881c5d2f9485d4722ff2c961335891b99ea1d0414086` |
| `re_test/screenshots/P1_offline_attribution.png` | オフライン時に地図上から開いたライセンス/出典ダイアログ。OSM、地理院、Overture、国土数値情報等の記載を確認。地図キャンバス上の常時帰属マークが見える証拠ではない。 | `8cb9a399fda8461775cc4ff582255ce5d6fe35762bd10dac2ff29161bfa898c1` |
| `re_test/screenshots/P1_offline_theme_settings_restored.png` | テーマ設定で「自動」が選択されていることを確認。 | `1bb4de696b8b3bf2fbf8e7292bb1894432c3051633177b7e4b4183e0062c812e` |

地図表示として、ベクター道路・鉄道・水域・緑地/建物形状、現在地マーカー、およびCycleMap側のPOIマーカーは視認できた。画面には「GPS信号を受信できません（GPSロスト）」が出ていたため、青い位置マーカーは確認できても、ライブ測位・追従の正常性は確認できない。POIマーカーをタップした後の詳細情報表示も未確認。ルート線は今回の画面に表示されていない。

### 11.3 ラベル・glyph・アイコンの原因確認

- 現行の `protomaps_light.json` と `protomaps_dark.json` はどちらも `glyphs` / `sprite` のプレースホルダーを持つが、スタイル本体に `type: symbol`、`text-field`、`icon-image` のレイヤー定義がない。したがって道路名・地名ラベルやベースマップのPOIアイコンを描画する定義が存在しないことが、画面にラベルが出ない直接原因。
- `re_test/app/src/main/assets/` とビルド済みAPKを調べた範囲では glyph/font PBF を確認できなかった。APKには `assets/sprites/ofm.json/png` と `@2x` の画像が含まれるが、参照するスタイルアイコンレイヤーがない。アプリの独自POIマーカー表示とは別物である。
- コード上の `asset://fonts/{fontstack}/{range}.pbf` 参照先にも実ファイルは見つからない。将来 symbol/text レイヤーを追加するには、ライセンスを確認したglyphデータの生成/取得・ローカル配置・APK/配布パッケージへの同梱が必要。現状のglyph/Sprite URIをローカル化したコード変更だけではラベル表示を満たさない。

### 11.4 Phase 1 受入判定の更新

| `re_plans.md` の受入条件 | 実測結果 | 判定 |
|---|---|---|
| 機内モードでタイル、地名ラベル、アイコン、現在地、POI、ルート線、帰属表示 | ベクター形状・現在地マーカー・アプリPOIアイコンは表示。日本語ラベル/ベースマップアイコンなし。GPSロスト表示あり。ルート線なし。ライセンスダイアログの出典は表示したが地図上の帰属は確認できず。ライトz14には黒い描画領域もある。 | 未達 |
| 未導入県の状態表示と他県への誤フォールバック防止 | §10.3 のコード修正と単体テストの記録はある。未導入県を使った実機操作は今回行っていない。 | 実機未確認 |
| z5/z10/z14/z17 × ライト/ダーク × オフラインの8画像と確認ログ | ライト/ダークそれぞれで初期付近と高倍率の計4画像を確認。z5/z10画像はなし。z14/z17は実機表示値を記録できず、ファイル名のz17は概算。 | 未達 |
| 外部通信が発生しないこと | 機内モード有効、デフォルトネットワークなし。更新APKの確認時間帯にMapLibreのリモートglyph/sprite要求ログは見つからなかったが、全通信を観測する手段ではなく、スタイルからglyph要求が発生しない可能性もある。 | 未証明 |

### 11.5 スコア・停止条件

§8 の初期スコア（Front 25/82、Back 20/90、Overall 26.36%）は変更しない。F1/F3/F4 は実機でテーマ切替・地図・ズーム操作の一部が確認できたものの、それぞれの全受入条件や該当テストを満たしていないため、既存の0.5を1へ引き上げない。他の未確認項目も据え置く。

**Phase 1 は未合格。Phase 2へ進まない。** 次に解消すべきブロッカー:

1. ライセンス適合するローカルglyph PBFの生成/取得・配置手順を確立し、symbol/text/iconレイヤーを追加した後、日本語ラベルとベースマップアイコンを実機で再確認する。
2. ライトz14に見える黒い領域の原因を調査し、背景色のタイル欠損と異常な黒塗りを区別して解消する。
3. ライブ位置情報の取得状態を確認し、z5/z10/z14/z17それぞれでライト/ダーク計8枚を、実ズーム値が再現可能なログと一緒に取得する。
4. 地図上の帰属マーク、POI詳細表示、オフラインルート線、未導入県での案内/誤フォールバックなしを実機確認する。
5. 機内モードで全通信の発生有無をログまたはネットワーク制御で検証する。今回のMapLibreログ抽出だけを通信なしの証拠にしない。

この実機確認で新たなユニットテストやビルドは実行していない。コード/テストの直近の結果は §10.3 に記録した Phase 1 の6ユニットテスト成功と `:app:assembleDebug` 成功であり、今回更新した監査記録と画面検証はそれらの代替ではない。Gitのadd/commit/push、端末設定変更、アプリデータ消去は行っていない。

## 12. 日本語ラベル・ローカルsprite対応 (2026-10-13)

### 12.1 原因の解消方針

- PMTilesの `show --metadata` で `roads` と `places` のレイヤー、および `name:ja` 属性を確認した。ラベル用データが欠けているのではなく、スタイルにsymbolレイヤーがなかったことが表示されない原因。
- `@protomaps/basemaps` 5.7.2 の `layers(..., { lang: "ja", labelsOnly: true })` から生成した14レイヤーを、ライト/ダーク双方へ追加した。道路・水路・海岸/湖沼・場所名・POI/主要施設等を含む。再生成は `node sandbox/vector_test/pipeline/generate_re_test_label_layers.js`。
- `text-font` は `Noto Sans Regular` に統一。MapLibre Native AndroidのCJK Unified Ideographsは端末内生成が既定で有効であり、`VectorMapView.kt` でも `localIdeographFontFamilyEnabled(true)` と `sans-serif` を明示している。APKにはASCII、全角かな等の必要範囲のPBFだけを同梱し、全漢字コードポイント分の巨大なPBFを追加しない構成。
- Protomaps Basemaps Assets v4のライト/ダークspriteを同梱し、ローカルデータ側にspriteがない場合もAPK内 `asset://sprites/light` / `asset://sprites/dark` を参照するよう修正した。全スタイルリソースはローカル参照で、HTTPS fallbackはない。

### 12.2 出所・ライセンス

- Glyph PBF と OFLテキスト: `https://github.com/protomaps/basemaps-assets` の `fonts/Noto Sans Regular/`。同リポジトリREADMEはfontsをSIL Open Font License 1.1と記載。MapLibre font-makerの公式説明はCJK漢字をローカルideographとして生成する挙動を説明している。
- Sprite JSON/PNG: `https://protomaps.github.io/basemaps-assets/sprites/v4/{light,dark}.{json,png}`。同リポジトリREADMEはspritesをMITライセンスのTangram icons由来と明記。
- 日本語ラベルレイヤー: `@protomaps/basemaps` 5.7.2、BSD 3-Clause。Protomapsの公式ライセンスは視覚デザインをCC0、含まれるTangram icon派生物をMITとしている。
- 従来から同梱されている `ofm*` ファイル4点は `https://tiles.openfreemap.org/sprites/ofm_f384/ofm{,@2x}.{json,png}` の配布内容とそれぞれSHA-256一致。OpenFreeMap StylesのLICENSE.mdはMaki POI iconをCC0、同リポジトリをMITと記載。従来ファイルは残置し、新たに表示するspriteはProtomaps v4へ切り替えた。
- ライセンス文書を `re_test/app/src/main/assets/licenses/` に同梱した。`NOTICE.md` に取得元と適用範囲を記載。

### 12.3 変更・検証

- スタイル生成: `sandbox/vector_test/pipeline/generate_re_test_label_layers.js`。生成した両スタイルに各14個のsymbolレイヤーがあり、`name:ja` を参照し、font stackは `Noto Sans Regular` に統一されていることを確認。
- アプリ: `re_test/app/src/main/java/com/gorite/cyclemap/VectorMapStyles.kt` のsprite fallbackをテーマ別ローカルspriteへ変更。
- `MapDialogs.kt` の「地図情報・ライセンス」にProtomaps basemap、Tangram icons、Noto Sansのライセンス名と出典リンクを追加し、本文はAPK内の `assets/licenses/` に同梱した。
- テスト: `:app:testDebugUnitTest --tests com.gorite.cyclemap.data.StyleJsonTest --tests com.gorite.cyclemap.ui.map.StyleLayerOrderTest` 成功。合計6件、失敗0、エラー0。StyleJsonTestは日本語symbolレイヤー、ローカル参照、glyph/sprite/license assetの存在を確認。
- ビルド: `:app:assembleDebug` 成功。テスト・ビルドはいずれもAndroid Studio同梱JDKで実行。
- 同梱glyph範囲のSHA-256: `0-255.pbf` = `62c6d49b15fa836eb6aa45e259c7ca6762f44b011b09e47776efbe4a6db1b397`; `12288-12543.pbf` = `4d776ca89b51ab5ec679eda50ced7bbb12ccdf7b861d12daec7cfbe3f50445e8`; `65280-65535.pbf` = `953b22dad634ed0eb0a4c80aeb8ee9f88a7f10d80a75fdc6e9265403f34d52a3`。

### 12.4 判定と残作業

- 実装上のsymbol/text/icon定義、ローカルフォント/sprite資産、出所・ライセンスの記録は揃った。これは実機で日本語ラベル/アイコンが描画された証明ではない。
- Galaxy S21で機内モードのライト/ダーク双方について、道路名・地名・POI/ベースマップアイコンが表示されることを再確認し、スクリーンショットとログを追記する必要がある。今回は端末インストール・設定変更・撮影をしていない。
- 8ズーム/テーマ証跡、黒い面の修正後の再確認、GPS回復、地図上帰属、POI詳細、ルート線、未導入県、全通信監視は未確認または未達のまま。Phase 1は未合格、Phase 2へ進まない。
- Git add/commit/pushは行っていない。

## 13. Galaxy S21 オフライン描画確認（2026-10-13）

- 対象端末はGalaxy S21 (`R5CR70NDMCM`, `SC-51B`)、アプリは `com.gorite.cyclemap.test` / `α0.2-test`。§12でビルドしたdebug APKを `adb install -r` で更新済み。アプリデータの消去はしていない。
- `settings get global airplane_mode_on` は確認前後とも `1`、`dumpsys connectivity` は `Active default network: none`。機内モード設定を変更せずに確認した。
- ライト/ダーク双方で、PMTiles地図、道路・水域・鉄道等の地物、日本語の道路名・地名、およびCycleMapのPOIアイコンが描画された。ライト表示は目視上明瞭。ダーク表示もラベルは出るが、複数の地名・道路名が暗い文字色で背景とのコントラストが弱く、視認性に課題がある。実ズーム値は読み取っておらず、4ズーム条件の達成証拠ではない。
- 画像: `re_test/screenshots/P1_offline_symbols_light.png` SHA-256 `09a4f089ddad0a50a38bc9863ec38fa8c62f2f2d759402c9564883b3b045c93a`; `re_test/screenshots/P1_offline_symbols_dark.png` SHA-256 `abb8d91d03d9ca56876ca76622135fecc884b997b95000375c20f4ece94e49ad`。
- アプリ内テーマを確認のためライトへ切替後、「自動」へ戻した。戻した後にシステムのダーク外観へ地図が戻ることを画面で確認。Androidの機内モードや他の端末設定は変更していない。
- GPSロスト警告が表示されておりライブ測位・追従は確認していない。ルート線、POI詳細カード、地図上の帰属、未導入県動作、正確なズーム別表示、全通信監視も今回の描画確認範囲外。外部接続なしの根拠は機内モードとdefault networkなしであり、通信パケット全体を捕捉した証明ではない。
- Phase 1は依然未合格。ダークテーマの文字コントラスト改善、および§11.4 / §11.5の残受入項目の確認が必要。Git add/commit/pushは行っていない。

## 12. Phase 1 地図表示の追加調査・修正（2026-10-13）

### 12.1 変更開始前の作業状態

作業開始時 `git status --short --branch` は `## main...origin/main`。`git pull --ff-only` は `Already up to date.`。既存差分・未追跡ファイルは以下で、本タスク開始前から存在していた。これらを初期状態として保持し、無関係なファイルは編集しない。

- 変更済み: `re_test/app/src/main/java/com/gorite/cyclemap/MapViewsContainer.kt`, `VectorMapFiles.kt`, `VectorMapStyles.kt`, `VectorMapView.kt`, `data/PrefectureSelectors.kt`, `app/src/test/java/com/gorite/cyclemap/data/StyleJsonTest.kt`, `TileSourceSelectorTest.kt`, `re_test/prompts/integration_fix.md`, `test/oparations/plans.md`。
- 未追跡: 直前の実機調査スクリーンショット7枚、`test/now.md`, `test/oparations/re_audit.md`, `test/oparations/re_plans.md`, `test/sample_img/`。
- この時点では当該差分に対する変更・リセット・退避は行っていない。レビューしたベースライン差分では、`VectorMapStyles.kt` の外部glyph/sprite URLをローカルURIへ置き換えた変更、`StyleJsonTest.kt` のリモートURL禁止アサーション等が既存作業として確認できた。

### 12.2 表示されないラベルとアイコンの原因

- `protomaps_light.json` と `protomaps_dark.json` は各66レイヤーで、`symbol` 型、`text-field`、`icon-image` を使うレイヤーは0件。したがって、glyphやsprite URLの正しさ以前に、ベースマップの文字・アイコン描画を要求するstyle layerがなかった。
- アプリ用 `Hiroshima_osm.pmtiles` (23,732,025 bytes、SHA-256 `8b911d6ec67bc8f6b1b450a0fdc4738e4473b60aa87b1a64ce992e09fa3e6d85`) はPMTiles v3 / MVT / z0–15、領域 132.25–132.65E, 34.20–34.55N。アーカイブ検証は成功。PMTiles metadataと z14 のローカルタイルを読み取り、`roads`, `places`, `pois` レイヤーが含まれること、対象タイルに道路 `name` / `name:ja` (例「鯉城通り」「国道2号」)、場所名 `name:ja` (例「広島市」「南区」)とPOI featureがあることを確認した。タイルデータに対象文字列・対象レイヤーがないことが原因ではない。
- APKには `assets/sprites/ofm{,@2x}.json/png` が含まれ、sprite JSONに264種の画像名があるが、使用するsymbol layerがない。またアプリ内・APK内にglyph PBFは存在しない。公開されているProtomaps `basemaps-assets` README/OFLは確認できたが、同リポジトリの提供フォントは Noto Sans Regular/Medium/Italic と Devanagari Regularに限定され、日本語glyphセットではない。現行 `ofm` spriteファイルの正確な取得元・当該ファイルへのライセンス添付はリポジトリ内資料から確認できなかった。出所と配布条件を推測せず、今回フォント/spriteデータは追加しない。
- よって日本語ラベルの直接原因はstyle内symbol/text layer不在。修正には、name/name:jaを利用する地物別symbol layerに加え、ライセンス確認済みの日本語対応フォントから再現可能な glyph PBF を生成し、そのライセンス・帰属情報を配布物に含める必要がある。現在の調査ではフォント生成コマンド/ツールと採用する日本語glyphデータの取得元・範囲を確定できず、この点は未修正ブロッカーとして残す。sprite由来確認後、アイコンレイヤーも別途追加が必要。

### 12.3 ライトz14の黒い領域の原因と修正

- `P1_offline_z14_light.png` の地図領域には RGB `(37,38,40)` (`#252628`) の画素が多数ある。これはライト版styleにはなく、ダーク版styleの `landuse_hospital`, `landuse_industrial`, `landuse_school` 等に使われる塗り色と一致する。スクリーンショットでは画面の他部分はライト配色なので、これはライト版の意図的な背景色ではない。
- `VectorMapView` はテーマ切替時にstyle全体を再読込せず `applyNightModeColors` で既存レイヤーを更新する。既存関数は背景・water・park・road・buildings等だけを変更し、`landuse_hospital`, `landuse_industrial`, `landuse_school`, `landuse_beach`, `landuse_zoo`, `landuse_aerodrome`, `landuse_runway`, `landuse_pedestrian`, `landuse_pier`, `landuse_platform`, `buildings_station` は変更していなかった。このためダークstyleからライトへ切替後にもダーク色が残る経路がある。単なるPMTiles欠損なら背景色が見えるはずであり、観測色がダークstyleの塗り色と一致すること、style切替実装の塗色更新漏れから、黒領域はタイル欠損ではなく昼夜切替時の残存塗色と特定した。
- 今回は上記の静的塗色レイヤーすべてをライト/ダーク両方で更新するよう限定修正する。低・高ズームの描画および実機再現確認は本依頼の制約で実施しないため、画面上の黒領域が解消したかは実機未確認とする。styleの `landcover` にあるカテゴリ式塗色のテーマ切替完全性も残るため、追加検証を要する。

### 12.4 本作業の変更・テスト・停止条件

以下に今回の変更ファイル、対象テスト/ビルド結果、未解決事項を追記する。検索・経路・ナビ、`application/`、端末設定/データには変更を加えず、機内モード実機検証も行わない。Phase 1 は全受入条件未達のため未合格のまま保持し、Phase 2へ進まない。

### 12.5 変更内容と検証結果

- `re_test/app/src/main/java/com/gorite/cyclemap/VectorMapStyles.kt`: `vectorMapAreaFillColors` を追加し、テーマ切替のたびに全15個の静的な水域・土地利用・建物面レイヤー（water、土地利用カテゴリ、buildings-2d/station）の塗色を、それぞれ対応するライト/ダークスタイルの値へ更新する。既存関数が更新していた一部レイヤーも同じ一覧に集約し、スタイル切替後にダーク色が残る穴を塞いだ。背景・earth・線レイヤー等の既存処理は維持。
- `re_test/app/src/test/java/com/gorite/cyclemap/data/StyleJsonTest.kt`: 色上書きのライト/ダーク両マップが同じ静的面レイヤーを網羅し、各色が同名レイヤーのJSON定義と一致することを検証するテストを追加。
- `test/oparations/re_audit.md`: 本節に、開始前の差分、PMTiles/style/APK調査、修正、検証、ブロッカーを記録。
- 変更した資産データはない。日本語glyphとベースマップアイコンは実装・描画確認とも未完了。Protomaps公式 `basemaps-assets` は [README](https://github.com/protomaps/basemaps-assets/blob/main/README.md) で glyph が font-maker 生成物と説明し、[fonts.json](https://github.com/protomaps/basemaps-assets/blob/main/fonts.json) は Noto Sans Regular/Medium/Italic のみを列挙する。添付の [OFL.txt](https://github.com/protomaps/basemaps-assets/blob/main/fonts/OFL.txt) はNoto Project Authors著作権のSIL OFL 1.1だが、日本語フォントglyphを提供していない。font-maker実行ファイルはPATH/リポジトリに確認できず、別途採用するNoto Sans CJK JPの正確な配布元・ライセンスファイル・必要glyph range・生成コマンドを確定していない。現行の `ofm` sprite群は出所/ライセンスの記録が見つからないため採用済みのまま保持し、新規spriteや利用レイヤーは追加しなかった。
- テスト: `cd re_test && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ./gradlew :app:testDebugUnitTest --tests 'com.gorite.cyclemap.data.StyleJsonTest' --tests 'com.gorite.cyclemap.ui.map.StyleLayerOrderTest'` — 成功、5 tests / 0 failures / 0 errors / 0 skipped。
- ビルド: `cd re_test && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ./gradlew :app:assembleDebug` — `BUILD SUCCESSFUL`。APK SHA-256: `6fbf16d2b02cb063899f3c32bcc2a16c4bf737167c8cfe58c230475975f66031`。
- `git diff --check` は成功。最終Git状態では変更済み既存ファイル/未追跡ファイルは開始時のものを維持し、今回変更した3パス以外を編集していない。`git pull --ff-only` のみ実行し、add/commit/pushは未実施。

### 12.6 未解決事項・端末での再確認

- 日本語の道路名・地名: PMTilesにname/name:jaと `roads` / `places` featureがあることは確認したが、両アクティブstyleにsymbol/text layerがなく、対応する日本語glyph PBFもない。ライセンス確認済み日本語フォント・再現可能な glyph PBF 生成手順・そのLICENSE同梱を整えてから、道路名と地名の symbol layer を追加する必要がある。
- ベースマップアイコン: styleにicon-image layerがなく、既存 `ofm` spriteの出所/ライセンスも確認できていない。sprite由来と利用条件を確定後に、PMTilesの `pois` / `places` fields と sprite keys の対応を設計し、style layer追加が必要。独自アプリPOIマーカーはこの欠落を補うベースマップアイコンではない。
- 黒領域: 観測した `#252628` とダークstyleの塗色、テーマ切替時の未更新レイヤーが一致したため、塗色更新漏れを修正した。ただし修正後の画面・複数タイルでの見た目は実機未確認。`landcover` はカテゴリ式fill色を持つ別レイヤーであり、今回の静的色上書き対象外。修正後APKで暗い地物色が残らないかも確認する。
- Galaxy S21で次回、オフライン設定を人間が維持したまま、修正版APKを適切な承認のもとで試験し、ライト→ダーク→ライト切替のz14画面で各土地利用カテゴリ/建物に旧テーマ色が残らないことと、タイル欠損でないことを確認する。併せてライセンス適合したglyph/sprite導入後に日本語道路名・地名・ベースマップアイコンを確認する。今回APKのインストール、端末設定変更、データ消去、実機検証はしていない。
- Phase 1 の合否は未達のまま。日本語ラベル/ベースアイコンの表示、タイルテーマ修正後の実機描画、z5/z10を含む8画像、POI詳細、ルート線、地図上帰属、未導入県実機、外部通信なしのネットワーク検証が残っている。Phase 2へ進まない。
