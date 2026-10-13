# CycleMap アーキテクチャ仕様書 (`re_test/architecture_spec.md`)

## 1. 基本方針
- **MapLibre 単一エンジン化**: osmdroid への依存を整理し、Protomaps PMTiles + MapLibre Native に一本化（osmdroid 代替検証完了後に完全撤廃）。
- **MVI / UDF (Unidirectional Data Flow)**: 単一方向のデータフローを徹底。
- **1ファイル 500行以内規則**: 巨大ファイルを分割し、責任を明確化。
- **完全オフライン保証**: すべての基本機能（地図表示、検索、ルーティング、案内、履歴、テーマ切替）は機内モードで動作。オンライン機能は任意の付加価値として静かにフォールバック。

## 2. 状態管理の分離 (高頻度 vs 低頻度)
GPS やコンパス方位の更新で画面全体が再コンポーズされるのを防ぐため、状態を明確に分離する。

```kotlin
// 低頻度UI状態 (画面遷移、シート展開、チップ選択、ルート選択、テーマなど)
data class MapUiState(
    val activeSheet: ActiveSheet = ActiveSheet.None,
    val selectedCategory: String? = null,
    val selectedPlace: PlaceItem? = null,
    val selectedRouteIndex: Int = 0,
    val availableRoutes: List<RouteSummary> = emptyList(),
    val isNightMode: Boolean = false,
    val networkLink: Link = Link.Offline
)

// 高頻度位置状態 (1Hz GPS / 10-50Hz 方位)
data class LocationUiState(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedMps: Float = 0f,
    val bearingDeg: Float = 0f,
    val accuracyMeters: Float = 0f
)

// ナビゲーション進捗状態 (案内更新時)
data class NavProgressState(
    val distanceToTurnMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingSeconds: Double = 0.0,
    val currentInstruction: Instruction? = null,
    val nextInstruction: Instruction? = null
)
```

高頻度状態は末端の専用 Composable (`CompassNeedle`, `NavProgressBar` 等) でのみ `collectAsState()` し、`derivedStateOf` と `key` を活用する。

## 3. レイヤー構成 (Z-Order)
地図描画におけるレイヤー順序:
1. 地図背景 / 水域 / 土地被覆
2. 道路網 (`roads`)
3. ルートライン (`route-layer` / `route-casing`)
4. 3D建物 (`buildings-3d`) ※地面ドレープまたは建物透過
5. ラベル層 (道路名、地名)
6. POIシンボル / アイコン
7. 現在地ピン / 方位コーン

レイヤーIDは定数として管理し、`StyleLayerOrderTest` で順序崩れを防止する。

## 4. UIコンポーネント分割設計
- `ui/map/MapTopBar.kt`: 検索バー、カテゴリ横スクロールチップ、ネットワーク状態インジケータ
- `ui/map/MapControls.kt`: 右側フローティングボタン群 (コンパス、レイヤー切替、現在地、ズームボタン)
- `ui/map/MapOverlays.kt`: ルート線、現在地マーカー、POIピンの MapLibre オーバーレイ
- `ui/map/MapSheets.kt`: 3段アンカー場所詳細シート、検索結果シート
- `ui/map/NavigationScreen.kt`: 全幅緑ヘッダー、車線案内バー、ETAフッター
- `ui/map/MapState.kt`: `MapUiState`, `MapUiIntent`, `MapViewModel`

## 5. スレッド規律とリソース管理
- SQLite / ファイル / DEM / グラフ探索処理は必ず `Dispatchers.IO` で実行。
- DB カーソルは `use { }` で確実にクローズ（リーク防止）。
- `OnlineFeatureGate` による通信はタイムアウト3秒、失敗時は例外を投げず `FeatureResult.Unavailable` でオフライン表示を維持。
