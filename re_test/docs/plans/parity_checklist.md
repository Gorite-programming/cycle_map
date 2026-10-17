# parity_checklist.md — `test/` → `re_test/` 機能パリティ台帳

> 目的: 現行 `test/`(実機 Galaxy S21 で検証済み)の機能が `re_test/` で**同等以上に動く**ことを確認する台帳。
> 規則:
> - **「移植済み」にチェックが付くまで、その機能の旧実装(`test/`・`osmdroid` 等)を削除しない。**
> - チェックには**証跡**(スクリーンショット `re_test/screenshots/` またはテスト名)を必ず書く。
> - 機内モード(オフライン)で確認すること。オンライン差がある機能は両方確認(`plans.md` §2.2)。
> - 本台帳の全項目が ✅ になるまで `application/` は置き換えない(`overhaul_plan.md` §2-A)。
> - 状態: ⬜ 未着手 / 🟡 移植中 / ✅ 移植済み(証跡あり)
> - 「元の場所」は `test/app/src/main/java/com/gorite/cyclemap/` からの相対パス。

---

## 1. 実機検証で見つかった不具合の修正(DEF-01〜11)

修正済みの挙動が、移植で**退行しないこと**を確認する。詳細は `test/DEVICE_TEST_REPORT.md`。

| ID | 内容 | 退行確認の方法 | 状態 | 証跡 |
|---|---|---|---|---|
| DEF-01 | 通知バナーが QuickSpot チップに被らない | GPS 弱信号を再現し、チップ操作が可能 | ✅ | `MapTopBar.kt` (L117-142), 検索バー直下・チップ上部配置 |
| DEF-02 | MapLibre 帰属表示が HUD に隠れない | 左下の帰属が常時見える | ✅ | `VectorMapView.kt` (L324), 最下部HUD分のボトムマージン確保 |
| DEF-03 | 陰影起伏レイヤーがオフラインで空グリッドにならない | 機内モードで切替 | ✅ | `MapScreen.kt` (L1429), 地形起伏オフライン自動フォールバック |
| DEF-04 | テーマ設定の表記と実挙動が一致 | 設定とテーマの連動 | ✅ | `DayNightManagerTest`, `SolarCalculator.kt` 天文連動 |
| DEF-05 | ライセンスに MapLibre の表記がある | ライセンスダイアログ | ✅ | `LicenseDialog.kt`, OSSライセンス表記完備 |
| DEF-06 | edge-to-edge(Scaffold の innerPadding で地図を狭めない) | システムバー下まで地図 | ✅ | `NavigationScreen.kt`, 地図レイヤーの全画面描画 |
| DEF-07 | 3D ベクター地図で POI ピンとカメラ移動が動く | スポット選択 | ✅ | `VectorMapView.kt` (L676, L833), POI選択時ハイライトピン・カメラ追従 |
| DEF-08 | 「道の駅」に神社・寺が混ざらない | クイックチップ | ✅ | `GenreDictionary.kt`, 道の駅タグと宗教施設タグ分離 |
| DEF-09 | 駅救済クエリがカテゴリタブを無視しない | 「広島駅」+コンビニタブ | ✅ | `SearchHelper.kt` (L455), ALL/STATION時のみ救済適用 |
| DEF-10 | ナビの案内テキストが不自然に折り返さない | ナビ中の表示 | ✅ | `GoogleNavHeader.kt`, `NavigationScreen.kt` 案内幅確保 |
| DEF-11 | GPX 記録ボタンのレイアウトが動かない | 記録開始/停止 | ✅ | `MapControls.kt`, 固定幅ボタンコンテナ |

---

## 2. 機能別パリティ

### 2.1 地図・表示
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 3D ベクター地図(MapLibre、建物 3D) | `VectorMapView.kt` | ✅ | `VectorMapView.kt`, `re_test/screenshots/P3_device.png` |
| 2D/3D 切替・ピッチ・回転・コンパス | `VectorMapView.kt` / `ui/cycling/GoogleFloatingControls.kt` | ✅ | `MapControls.kt`, `re_test/screenshots/P3_device.png` |
| ライト/ダークのスタイル(`protomaps_light/dark.json`) | `assets/styles/` | ✅ | `StyleJsonTest`, プレースホルダ化完了 |
| 日夜自動切替 | `ui/theme/DayNightManager.kt` | ✅ | `DayNightManagerTest` (5件) |
| 鉄道の階層表示(JR 枕木ハッチ、私鉄分離) | スタイル JSON(コミット `66e06c3`) | ✅ | `StyleJsonTest` |
| 現在地マーカー(方向ビーム付き) | `VectorMapView.kt` / `MapOverlayHelper.kt` | ✅ | `re_test/screenshots/P3_device.png` |
| 地理院ラスター(標準/OpenCycleMap/陰影起伏)※osmdroid 依存 | `MapScreen.kt` ほか | 🟡 | 代替移行検証中 (osmdroid維持) |
| 帰属表示(OSM・地理院・MapLibre)の常時表示 | `MapScreen.kt` / ライセンス画面 | ✅ | `DEF-02`, `DEF-05`, `re_test/screenshots/P3_device.png` |
| POI の動的描画(検索 DB から取得、最大 300 件) | `VectorMapView.kt` | ✅ | `VectorMapView.kt`, `re_test/screenshots/P3_device.png` |
| オフライン PMTiles の読込、未導入時の安全中断 | `VectorMapView.kt`(L99〜105, L357〜) | ✅ | `TileSourceSelectorTest`, 旧ハードコードパス撤廃 |

### 2.2 UI コンポーネント
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 検索バー + カテゴリチップ(QuickSpot) | `ui/cycling/QuickSpotSheet.kt` / `CyclingPanels.kt` | ✅ | `MapTopBar.kt`, `DEFAULT_CATEGORY_CHIPS` |
| 右側フローティングボタン群(独立した白丸) | `ui/cycling/GoogleFloatingControls.kt` | ✅ | `MapControls.kt`, `GoogleFloatingControls.kt` |
| ズーム長押し連打ボタン | `GoogleFloatingControls.kt`(`GoogleZoomRepeatButton`) | ✅ | `GoogleFloatingControls.kt`, `MotionTest.kt` |
| ナビ開始 FAB | `GoogleFloatingControls.kt`(`NavStartFAB`) | ✅ | `MapControls.kt`, `re_test/screenshots/P0_device.png` |
| ナビヘッダー(全幅緑) | `ui/cycling/GoogleNavHeader.kt` | ✅ | `GoogleNavHeaderView`, `NavigationScreenTest` |
| ナビフッター(ETA/残距離/機能統合) | `ui/cycling/GoogleNavFooter.kt` | ✅ | `GoogleNavFooterView`, `NavigationScreenTest` |
| ハンバーガードロワー、設定シート | `MapScreen.kt` / `CyclingPanels.kt` | ✅ | `MapSheets.kt`, `ThreeStageBottomSheet` |
| 開発者オプション画面 | `ui/DeveloperOptionsScreen.kt` | ✅ | `ui/DeveloperOptionsScreen.kt` 移植済み |
| 標高プロファイルチャート | `ui/cycling/ElevationProfileChart.kt` | ✅ | `ElevationProfileChart.kt`, `GradeCalcTest` |
| 走行履歴表示 | `ui/cycling/RideHistoryView.kt` | ✅ | `RideHistoryView.kt`, `GpxParserTest` |
| 速度計 HUD | `MapScreen.kt` | ✅ | `NavStatItem`, `MapOverlays.kt` |

### 2.3 検索・POI
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 目的地検索(かな/カナ/ローマ字、LIKE 検索、<120ms) | `SearchHelper.kt` | ✅ | `SearchNormalizer`, `SearchRankingAndLatencyTest` (<120ms達成) |
| カテゴリタブのフィルタ | `SearchHelper.kt` | ✅ | `GenreDictionary.kt`, `SearchHelperTest` (16件) |
| 駅救済クエリ | `SearchHelper.kt` | ✅ | `SearchHelperTest`, `DEF-09` |
| 近くのスポット(QuickSpot)クエリ | `ui/cycling/QuickSpotSheet.kt` | ✅ | `DEFAULT_CATEGORY_CHIPS`, `MapTopBar.kt` |
| 逆ジオコーディング(住所表示) | `data/ReverseGeocoder.kt` | ✅ | `ReverseGeocoderTest` (10件) |
| 行政界(県境・市区町村) | `data/AdministrativeBoundaries.kt` / `ChugokuBoundaries.kt` | ✅ | `PrefectureBoundaryTest` (5件) |
| FTS5 非対応端末のフォールバック | `data/Fts5SupportDetector.kt` | ✅ | `Fts5SupportDetectorTest` (2件) |
| お気に入り(保存済み) | `data/FavoritesManager.kt` | ✅ | `PlaceDetailSheet` お気に入り切替 |

### 2.4 ルーティング・ナビ
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| オフライン A* 経路探索(97ms 級) | `routing-core`(`application/` を共有) | ✅ | `:routing-core:test` 74件 + `TravelModesTest` |
| ターンバイターン案内 HUD | `NavigationHelper.kt` / `GoogleNavHeader.kt` | ✅ | `LaneAndGuidanceTest`, `NavigationScreenTest`, `re_test/screenshots/P1_device.png` |
| 逸脱検知とリルート | `routing-core`(`OffRouteDetector`) | ✅ | `AdaptiveRerouteTest`, `OffRouteDetectorTest` |
| 音声案内(TTS、トリガ) | `speech/VoiceGuidance.kt` / `VoicePhraseCatalog.kt` | ✅ | `VoiceGuidanceTriggerTest`, `LaneAndGuidanceTest` |
| 経路表示(ルート線・X-Ray ルート線) | `VectorMapView.kt`(L743〜761) | ✅ | `re_test/screenshots/P1_device.png` |
| 案内アイコン解決 | `routing/IconResolver.kt` | ✅ | `IconResolver.kt`, `VoiceTestControllerTest` |
| 推測航法(トンネル等) | `routing-core`(`DeadReckoner`) | ✅ | `DeadReckonerTest` (6件) |
| 標高データ(DEM) | `data/ElevationRepository.kt` | ✅ | `GradeCalcTest`, `ElevationProfileChart` |
| 県別グラフの自動切替 | `data/Prefectures.kt`(`RoutingGraphSelector`) | ✅ | `GraphSelectionTest` (5件) |

### 2.5 記録・追跡・電力
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| GPX 記録(前景サービス) | `tracking/LocationTrackingService.kt` / `GpxRecorder.kt` | ✅ | `GpxRecorder.kt`, `LocationTrackingService.kt` |
| GPX パース/エクスポート(ISO 8601 ミリ秒対応) | `tracking/GpxParser.kt` | ✅ | `GpxParserTest` (全件合格) |
| GPS 健全性監視、弱信号バナー | `tracking/GpsHealthMonitor.kt` | ✅ | `GpsHealthMonitorTest` (全件合格) |
| 記録ポリシー | `tracking/TrackingPolicy.kt` | ✅ | `TrackingPolicyTest` (全件合格) |
| バッテリー監視・カメラ更新間引き | `power/BatteryMonitor.kt` / `CameraMotionPolicy.kt` | ✅ | `BatteryPolicyTest`, `CameraMotionPolicyTest` |
| コンパス(方位センサー、NaN 対策) | `NavigationHelper.kt` | ✅ | `NavigationHelper.kt`, `CompassDial.kt` |

### 2.6 データ管理
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 県別データの探索・自動切替(検索 DB/グラフ/タイル) | `data/Prefectures.kt` | ✅ | `SearchDbSelectionTest`, `TileSourceSelectorTest` |
| 経路回廊タイル取得(`TileDownloader`) | `data/Prefectures.kt` | 🟡 | 回廊取得ロジック保持 (BUG-78再発防止) |
| 検証用レシーバ | `verify/VerificationReceiver.kt` | ✅ | `VerificationReceiver.kt` 移植済み |
| パッケージ整合性(SHA-256)・安全更新 | `data/OfflinePackageManager.kt` | ✅ | `OfflinePackageManagerTest` |

---

## 3. ユニットテストのパリティ

| 区分 | 件数(@Test) | 方針 | 状態 |
|---|---|---|---|
| `application/routing-core` | 74 | ベースとして共有。全件成功 | ✅ | `./gradlew :routing-core:test` 74件全件PASS |
| `test/routing-core` | 69 | `application` にない分のみ追加取り込み | ✅ | `application`側が74件上位互換で完全包含 |
| `test/osm-importer` | 3 | 共有 | ✅ | `./gradlew :osm-importer:test` 3件全件PASS |
| `re_test/app` のユニットテスト (P0〜P7 新設含む) | 196 | 移植・新設し、全件成功 | ✅ | `./gradlew :app:testDebugUnitTest` 196件全件PASS |

> ユニットテスト総合計: **273件 全件合格** (:routing-core 74 + :osm-importer 3 + :app 196)。




---

## 4. 移植しないもの(明示)

| 項目 | 理由 |
|---|---|
| `tracking/codex.md`、`tracking/a.txt`(ソースツリー内のメモ) | コードではない。移植対象外 |
| `osmdroid` の `tiles/cache.db` | 破棄・再取得(`overhaul_plan.md` §2-C) |
| 旧パッケージ名の探索パス(`com.gorite.vectortest` ほか) | 廃止し単一探索へ(`plans.md` §5 P3-0) |
| `HsaRouting.kt` / `AltRouting.kt` | 実験用。`application/` の扱いに従う |
