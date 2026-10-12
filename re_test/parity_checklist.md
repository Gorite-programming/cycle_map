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
| DEF-01 | 通知バナーが QuickSpot チップに被らない | GPS 弱信号を再現し、チップ操作が可能 | ⬜ | |
| DEF-02 | MapLibre 帰属表示が HUD に隠れない | 左下の帰属が常時見える | ⬜ | |
| DEF-03 | 陰影起伏レイヤーがオフラインで空グリッドにならない | 機内モードで切替 | ⬜ | |
| DEF-04 | テーマ設定の表記と実挙動が一致 | 設定とテーマの連動 | ⬜ | |
| DEF-05 | ライセンスに MapLibre の表記がある | ライセンスダイアログ | ⬜ | |
| DEF-06 | edge-to-edge(Scaffold の innerPadding で地図を狭めない) | システムバー下まで地図 | ⬜ | |
| DEF-07 | 3D ベクター地図で POI ピンとカメラ移動が動く | スポット選択 | ⬜ | |
| DEF-08 | 「道の駅」に神社・寺が混ざらない | クイックチップ | ⬜ | |
| DEF-09 | 駅救済クエリがカテゴリタブを無視しない | 「広島駅」+コンビニタブ | ⬜ | |
| DEF-10 | ナビの案内テキストが不自然に折り返さない | ナビ中の表示 | ⬜ | |
| DEF-11 | GPX 記録ボタンのレイアウトが動かない | 記録開始/停止 | ⬜ | |

---

## 2. 機能別パリティ

### 2.1 地図・表示
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 3D ベクター地図(MapLibre、建物 3D) | `VectorMapView.kt` | ⬜ | |
| 2D/3D 切替・ピッチ・回転・コンパス | `VectorMapView.kt` / `ui/cycling/GoogleFloatingControls.kt` | ⬜ | |
| ライト/ダークのスタイル(`protomaps_light/dark.json`) | `assets/styles/` | ⬜ | |
| 日夜自動切替 | `ui/theme/DayNightManager.kt` | ⬜ | |
| 鉄道の階層表示(JR 枕木ハッチ、私鉄分離) | スタイル JSON(コミット `66e06c3`) | ⬜ | |
| 現在地マーカー(方向ビーム付き) | `VectorMapView.kt` / `MapOverlayHelper.kt` | ⬜ | |
| 地理院ラスター(標準/OpenCycleMap/陰影起伏)※osmdroid 依存 | `MapScreen.kt` ほか | ⬜ | |
| 帰属表示(OSM・地理院・MapLibre)の常時表示 | `MapScreen.kt` / ライセンス画面 | ⬜ | |
| POI の動的描画(検索 DB から取得、最大 300 件) | `VectorMapView.kt` | ⬜ | |
| オフライン PMTiles の読込、未導入時の安全中断 | `VectorMapView.kt`(L99〜105, L357〜) | ⬜ | |

### 2.2 UI コンポーネント
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 検索バー + カテゴリチップ(QuickSpot) | `ui/cycling/QuickSpotSheet.kt` / `CyclingPanels.kt` | ⬜ | |
| 右側フローティングボタン群(独立した白丸) | `ui/cycling/GoogleFloatingControls.kt` | ⬜ | |
| ズーム長押し連打ボタン | `GoogleFloatingControls.kt`(`GoogleZoomRepeatButton`) | ⬜ | |
| ナビ開始 FAB | `GoogleFloatingControls.kt`(`NavStartFAB`) | ⬜ | |
| ナビヘッダー(全幅緑) | `ui/cycling/GoogleNavHeader.kt` | ⬜ | |
| ナビフッター(ETA/残距離/機能統合) | `ui/cycling/GoogleNavFooter.kt` | ⬜ | |
| ハンバーガードロワー、設定シート | `MapScreen.kt` / `CyclingPanels.kt` | ⬜ | |
| 開発者オプション画面 | `ui/DeveloperOptionsScreen.kt` | ⬜ | |
| 標高プロファイルチャート | `ui/cycling/ElevationProfileChart.kt` | ⬜ | |
| 走行履歴表示 | `ui/cycling/RideHistoryView.kt` | ⬜ | |
| 速度計 HUD | `MapScreen.kt` | ⬜ | |

### 2.3 検索・POI
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 目的地検索(かな/カナ/ローマ字、LIKE 検索、<120ms) | `SearchHelper.kt` | ⬜ | |
| カテゴリタブのフィルタ | `SearchHelper.kt` | ⬜ | |
| 駅救済クエリ | `SearchHelper.kt` | ⬜ | |
| 近くのスポット(QuickSpot)クエリ | `ui/cycling/QuickSpotSheet.kt` | ⬜ | |
| 逆ジオコーディング(住所表示) | `data/ReverseGeocoder.kt` | ⬜ | |
| 行政界(県境・市区町村) | `data/AdministrativeBoundaries.kt` / `ChugokuBoundaries.kt` | ⬜ | |
| FTS5 非対応端末のフォールバック | `data/Fts5SupportDetector.kt` | ⬜ | |
| お気に入り(保存済み) | `data/FavoritesManager.kt` | ⬜ | |

### 2.4 ルーティング・ナビ
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| オフライン A* 経路探索(97ms 級) | `routing-core`(`application/` を共有) | ⬜ | |
| ターンバイターン案内 HUD | `NavigationHelper.kt` / `GoogleNavHeader.kt` | ⬜ | |
| 逸脱検知とリルート | `routing-core`(`OffRouteDetector`) | ⬜ | |
| 音声案内(TTS、トリガ) | `speech/VoiceGuidance.kt` / `VoicePhraseCatalog.kt` | ⬜ | |
| 経路表示(ルート線・X-Ray ルート線) | `VectorMapView.kt`(L743〜761) | ⬜ | |
| 案内アイコン解決 | `routing/IconResolver.kt` | ⬜ | |
| 推測航法(トンネル等) | `routing-core`(`DeadReckoner`) | ⬜ | |
| 標高データ(DEM) | `data/ElevationRepository.kt` | ⬜ | |
| 県別グラフの自動切替 | `data/Prefectures.kt`(`RoutingGraphSelector`) | ⬜ | |

### 2.5 記録・追跡・電力
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| GPX 記録(前景サービス) | `tracking/LocationTrackingService.kt` / `GpxRecorder.kt` | ⬜ | |
| GPX パース/エクスポート(ISO 8601 ミリ秒対応) | `tracking/GpxParser.kt` | ⬜ | |
| GPS 健全性監視、弱信号バナー | `tracking/GpsHealthMonitor.kt` | ⬜ | |
| 記録ポリシー | `tracking/TrackingPolicy.kt` | ⬜ | |
| バッテリー監視・カメラ更新間引き | `power/BatteryMonitor.kt` / `CameraMotionPolicy.kt` | ⬜ | |
| コンパス(方位センサー、NaN 対策) | `NavigationHelper.kt` | ⬜ | |

### 2.6 データ管理
| 機能 | 元の場所 | 状態 | 証跡 |
|---|---|---|---|
| 県別データの探索・自動切替(検索 DB/グラフ) | `data/Prefectures.kt` | ⬜ | |
| 経路回廊タイル取得(`TileDownloader`) | `data/Prefectures.kt` | ⬜ | |
| 検証用レシーバ | `verify/VerificationReceiver.kt` | ⬜ | |

---

## 3. ユニットテストのパリティ

| 区分 | 件数(@Test) | 方針 | 状態 |
|---|---|---|---|
| `application/routing-core` | 74 | ベースとして共有。全件成功 | ⬜ |
| `test/routing-core` | 69 | `application` にない分のみ追加取り込み | ⬜ |
| `test/osm-importer` | 3 | 共有 | ⬜ |
| `test/app` のユニットテスト(`CameraMotionPolicyTest`、`BatteryPolicyTest`、`DayNightManagerTest`、`GradeCalcTest`、`SearchHelperTest`、`GpxParserTest` ほか) | 要集計 | 移植し、全件成功 | ⬜ |

> 件数は `grep -rE "^\s*@Test"` による実測。**`test/app` の合計は未集計**(最初のタスクで集計して追記すること)。

---

## 4. 移植しないもの(明示)

| 項目 | 理由 |
|---|---|
| `tracking/codex.md`、`tracking/a.txt`(ソースツリー内のメモ) | コードではない。移植対象外 |
| `osmdroid` の `tiles/cache.db` | 破棄・再取得(`overhaul_plan.md` §2-C) |
| 旧パッケージ名の探索パス(`com.gorite.vectortest` ほか) | 廃止し単一探索へ(`plans.md` §5 P3-0) |
| `HsaRouting.kt` / `AltRouting.kt` | 実験用。`application/` の扱いに従う |
