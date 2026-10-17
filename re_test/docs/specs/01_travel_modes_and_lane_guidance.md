# 提案書 01: TravelMode 追加および車線案内 (.graph.idx) 拡張案

## 1. 概要
`plans.md` §6 P1 (移動モード B1, リルート B2, 車線案内 B3) を本番モジュール (`routing-core` および `osm-importer`) に将来統合するための差分提案書です。
現行の夜間実行ルール (§1-4: `application/` は読み取り専用、破壊的変更禁止) に従い、本提案書に実装案を記録します。`re_test` 側ではアダプター層 (`re_test/app/.../routing/TravelModes.kt`, `LaneAndGuidance.kt`, `AdaptiveRerouteManager.kt`) を通じて動作検証を行っています。

## 2. `routing-core` 側の差分案

### 2.1 `Routing.kt` への追加
- `enum class TravelMode { Bicycle, Walk, Car }`
- `WalkCostModel : EdgeCostModel`:
  - `motorway`, `motorway_link`, `trunk` を除外 (`cost() == null`)
  - `footway`, `path`, `pedestrian`, `steps` を許可
  - `minimumCostMultiplier() = 0.90` (A* 下限ヒューリスティック維持)
- `CarCostModel : EdgeCostModel`:
  - `steps`, `footway`, `cycleway`, `pedestrian` を除外
  - `motorway`, `trunk`, `primary` を許可
  - `minimumCostMultiplier() = 0.90`
- `TravelCostModelFactory.forMode(mode: TravelMode): EdgeCostModel`

### 2.2 `Instruction.kt` への追加
```kotlin
data class LaneHint(
    val direction: String,
    val isActiveForRoute: Boolean = false
)

// RouteInstruction へのプロパティ追加 (バイナリ互換のためデフォルト引数付き)
data class RouteInstruction(
    val type: InstructionType,
    val nodeId: Long,
    val routeIndex: Int,
    val distanceFromStartMeters: Double,
    val turnAngleDegrees: Double,
    val junctionBranches: Int,
    val incomingRoadType: String,
    val outgoingRoadType: String,
    val reason: String,
    val destinationSignpost: String? = null,
    val lanes: List<LaneHint>? = null,
    val secondManeuver: InstructionType? = null
)
```

## 3. `osm-importer` 側の差分案
- `OsmImporter.kt`:
  - OSM ウェイのタグから `lanes`, `turn:lanes`, `destination`, `ref` をパース。
  - `.graph.idx` のバイナリフォーマットにヘッダーバージョン (例: バージョン 2) を付与。
  - 旧バージョン (`v1`) の `.graph.idx` を読み込んだ際は、例外でクラッシュさせず「地図データ再生成推奨」の警告ログを出力し、車線案内のみ null として安全に動作させる。

## 4. 検証状況
`re_test/app` 側で単体テスト (`TravelModesTest`, `TravelTimeEstimatorTest`, `AdaptiveRerouteTest`, `LaneAndGuidanceTest`) によりロジックの完全動作を確認済み。
人間の承認後に `application/` へのマージを実施する。
