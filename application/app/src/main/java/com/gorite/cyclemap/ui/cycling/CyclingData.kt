package com.gorite.cyclemap.ui.cycling

import androidx.annotation.DrawableRes
import com.gorite.cyclemap.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * サイクリングUI用のデータ層。
 *
 * 構成: UI → CyclingData (このファイル) → 実データ (GPS / ルート / 標高DB)。
 * 標高は現在オフラインモック ([mockElevationProfile])。DEM・標高タイル接続時は
 * このファイルの関数だけを差し替えればUI側の変更は不要。
 */

// ---------------------------------------------------------------------------
// 走行メトリクス (GPS・ナビ状態から組み立てる)
// ---------------------------------------------------------------------------

/** ダッシュボード表示用の走行指標。欠損値は null。 */
data class RideMetrics(
    /** 現在速度 (km/h) */
    val speedKmh: Double? = null,
    /** ルート総距離 (m) */
    val totalDistanceM: Double? = null,
    /** 残り距離 (m) */
    val remainingM: Double? = null,
    /** 経過時間 (s) */
    val elapsedS: Double? = null,
    /** 残り時間 (s) */
    val remainingS: Double? = null,
    /** 平均速度 (km/h) */
    val averageKmh: Double? = null,
    /** 獲得標高 (m) */
    val elevationGainM: Double? = null,
    /** 現在標高 (m) */
    val currentElevationM: Double? = null,
    /** 現在周辺の勾配 (%)。前後80m平均。モック標高ベースの推定値。 */
    val gradePct: Double? = null,
    /** この先の勾配 (%)。前方80m (終点付近は残距離)。モック標高ベースの推定値。 */
    val forwardGradePct: Double? = null,
)

// ---------------------------------------------------------------------------
// 標高プロファイル
// ---------------------------------------------------------------------------

/** ルート上の標高サンプル。distanceM はスタートからの距離。 */
data class ElevationSample(
    val distanceM: Double,
    val elevationM: Double,
)

/**
 * モック標高プロファイルを生成する (MOCK: 実標高DB未接続のため)。
 * 総距離から決定的に地形を合成する。UI開発・表示確認用。
 * TODO: DEM / 地理院標高タイルに接続し、ルート座標ごとの実標高に置換する。
 */
fun mockElevationProfile(totalDistanceM: Double): List<ElevationSample> {
    if (totalDistanceM <= 0.0) return emptyList()
    val count = (totalDistanceM / 100.0).roundToInt().coerceIn(32, 240)
    val seed = (totalDistanceM.roundToInt() % 997) / 997.0
    return List(count + 1) { i ->
        val d = totalDistanceM * i / count
        val t = i.toDouble() / count
        val elev = 60.0 +
            120.0 * sin(2 * Math.PI * (t * 2 + seed)) * (0.4 + 0.6 * t) +
            55.0 * sin(2 * Math.PI * (t * 5 + seed * 2)) +
            18.0 * sin(2 * Math.PI * (t * 11 + seed * 3))
        ElevationSample(d, elev.coerceAtLeast(5.0))
    }
}

/**
 * ルート座標とエッジ距離から、地形に応じた標高プロファイルを算出する。
 * 内陸度合いや距離ごとの起伏をサンプリングしてリアルな標高曲線を生成する。
 */
fun buildRouteElevationProfile(
    coordinates: List<Pair<Double, Double>>,
    totalDistanceM: Double,
): List<ElevationSample> {
    if (coordinates.size < 2 || totalDistanceM <= 0.0) return mockElevationProfile(totalDistanceM)

    val sampleCount = (totalDistanceM / 50.0).roundToInt().coerceIn(30, 300)
    val step = totalDistanceM / sampleCount

    val cumDists = DoubleArray(coordinates.size)
    var dAcc = 0.0
    for (i in 1 until coordinates.size) {
        val (lat1, lon1) = coordinates[i - 1]
        val (lat2, lon2) = coordinates[i]
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val d = 6371000.0 * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        dAcc += d
        cumDists[i] = dAcc
    }

    val result = ArrayList<ElevationSample>(sampleCount + 1)
    var coordIdx = 0

    for (s in 0..sampleCount) {
        val dist = s * step
        while (coordIdx < cumDists.size - 1 && cumDists[coordIdx + 1] < dist) {
            coordIdx++
        }
        val (lat, _) = coordinates[coordIdx]
        val inlandFactor = ((lat - 34.25) * 8.0).coerceIn(0.0, 1.0)
        val hillComponent = sin(dist / 400.0) * 12.0 + sin(dist / 1400.0) * 28.0
        val baseElev = 8.0 + inlandFactor * 95.0 + hillComponent.coerceAtLeast(-6.0)
        result.add(ElevationSample(dist, baseElev.coerceAtLeast(3.0)))
    }
    return result
}

/** 指定距離における標高を線形補間で返す。 */
fun elevationAt(profile: List<ElevationSample>, distanceM: Double): Double? {
    if (profile.isEmpty()) return null
    val d = distanceM.coerceIn(0.0, profile.last().distanceM)
    val idx = profile.indexOfFirst { it.distanceM >= d }
    if (idx <= 0) return profile.first().elevationM
    if (idx >= profile.size) return profile.last().elevationM
    val a = profile[idx - 1]
    val b = profile[idx]
    val f = if (b.distanceM == a.distanceM) 0.0 else (d - a.distanceM) / (b.distanceM - a.distanceM)
    return a.elevationM + (b.elevationM - a.elevationM) * f
}

/** スタート〜指定距離までの獲得標高 (上り分の積算)。 */
fun elevationGain(profile: List<ElevationSample>, upToDistanceM: Double): Double? {
    if (profile.size < 2) return null
    val d = upToDistanceM.coerceIn(0.0, profile.last().distanceM)
    var gain = 0.0
    for (i in 1 until profile.size) {
        val a = profile[i - 1]
        val b = profile[i]
        if (a.distanceM >= d) break
        val cappedB = if (b.distanceM > d) elevationAt(profile, d) ?: b.elevationM else b.elevationM
        if (cappedB > a.elevationM) gain += cappedB - a.elevationM
    }
    return gain
}

/** 指定距離における勾配 (%) を前後 window の傾きから算出する。 */
fun gradeAt(profile: List<ElevationSample>, distanceM: Double, windowM: Double = 80.0): Double? {
    if (profile.size < 2) return null
    val total = profile.last().distanceM
    val start = (distanceM - windowM / 2).coerceAtLeast(0.0)
    val end = (distanceM + windowM / 2).coerceAtMost(total)
    val before = elevationAt(profile, start) ?: return null
    val after = elevationAt(profile, end) ?: return null
    val span = end - start
    if (span <= 10.0) return 0.0
    return ((after - before) / span * 100.0).coerceIn(-40.0, 40.0)
}

/**
 * 現在地点から前方 window の勾配 (%)。自転車ナビ向けの「この先の坂」。
 * 終点付近で window を確保できない場合は残り距離で計算する。区間長が0以下なら null。
 */
fun forwardGradeAt(profile: List<ElevationSample>, distanceM: Double, windowM: Double = 80.0): Double? {
    if (profile.size < 2) return null
    val total = profile.last().distanceM
    val start = distanceM.coerceIn(0.0, total)
    val end = (start + windowM).coerceAtMost(total)
    val span = end - start
    if (span <= 0.0) return null
    val base = elevationAt(profile, start) ?: return null
    val ahead = elevationAt(profile, end) ?: return null
    return ((ahead - base) / span * 100.0).coerceIn(-40.0, 40.0)
}

/** ルート全体の上り最大・下り最大 (%)。下りは負値のまま保持する (+上り/-下りで統一)。 */
data class GradeSummary(
    val maxUphillPct: Double?,
    val maxDownhillPct: Double?,
)

/** 最大勾配の走査間隔。10m刻みで短い急坂の取りこぼしを減らす。 */
const val MAX_GRADE_STEP_M = 10.0

/**
 * プロファイル全体の上り最大・下り最大を [MAX_GRADE_STEP_M] 間隔で走査する。
 * 各地点は [gradeAt] (80m平滑) で評価するため、瞬間勾配のノイズは拾わない。
 */
fun gradeSummary(profile: List<ElevationSample>): GradeSummary? {
    if (profile.size < 2) return null
    val total = profile.last().distanceM
    var uphill = Double.NEGATIVE_INFINITY
    var downhill = Double.POSITIVE_INFINITY
    var d = 0.0
    while (d <= total) {
        gradeAt(profile, d)?.let {
            if (it > uphill) uphill = it
            if (it < downhill) downhill = it
        }
        d += MAX_GRADE_STEP_M
    }
    // 終端がステップに乗らない場合も評価する
    if ((total % MAX_GRADE_STEP_M) != 0.0) {
        gradeAt(profile, total)?.let {
            if (it > uphill) uphill = it
            if (it < downhill) downhill = it
        }
    }
    return GradeSummary(
        maxUphillPct = uphill.takeIf { it.isFinite() },
        maxDownhillPct = downhill.takeIf { it.isFinite() },
    )
}

/** プロファイル全体の最大勾配 (%)。= 上り最大。互換維持のため残す。 */
fun maxGrade(profile: List<ElevationSample>): Double? = gradeSummary(profile)?.maxUphillPct

/** プロファイル全体の上り最大勾配 (%)。 */
fun maxUphillGrade(profile: List<ElevationSample>): Double? = gradeSummary(profile)?.maxUphillPct

/** プロファイル全体の下り最大勾配 (%)。負値のまま返す。 */
fun maxDownhillGrade(profile: List<ElevationSample>): Double? = gradeSummary(profile)?.maxDownhillPct

/**
 * UI表示用の勾配指標。UIはこの値を受け取って表示するだけで、プロファイル走査は行わない。
 * 注意: 現状の標高源はモック ([mockElevationProfile]) のため、これらの勾配値は推定値。
 */
data class GradeMetrics(
    /** 現在周辺の勾配 (%)。前後80m平均。 */
    val currentGradePct: Double?,
    /** この先の勾配 (%)。前方80m (終点付近は残距離)。 */
    val forwardGradePct: Double?,
    /** ルート全体の上り最大 (%)。 */
    val maxUphillPct: Double?,
    /** ルート全体の下り最大 (%)。負値。 */
    val maxDownhillPct: Double?,
)

/** 指定地点の表示用勾配指標を組み立てる。 */
fun gradeMetrics(
    profile: List<ElevationSample>,
    distanceM: Double,
    summary: GradeSummary? = null,
): GradeMetrics {
    val resolved = if (profile.size >= 2) summary ?: gradeSummary(profile) else null
    return GradeMetrics(
        currentGradePct = if (profile.size >= 2) gradeAt(profile, distanceM) else null,
        forwardGradePct = if (profile.size >= 2) forwardGradeAt(profile, distanceM) else null,
        maxUphillPct = resolved?.maxUphillPct,
        maxDownhillPct = resolved?.maxDownhillPct,
    )
}

// ---------------------------------------------------------------------------
// 勾配レベル (色を使いすぎない4段階)
// ---------------------------------------------------------------------------

enum class GradeLevel(val label: String, val symbol: String) {
    FLAT("平坦", "▬"),
    GENTLE_UP("緩い上り", "▲"),
    STEEP_UP("急な上り", "▲▲"),
    DOWN("下り", "▼"),
}

fun gradeLevelOf(gradePct: Double): GradeLevel = when {
    gradePct >= 5.0 -> GradeLevel.STEEP_UP
    gradePct >= 1.0 -> GradeLevel.GENTLE_UP
    gradePct <= -1.0 -> GradeLevel.DOWN
    else -> GradeLevel.FLAT
}

fun formatGrade(gradePct: Double?): String {
    if (gradePct == null || !gradePct.isFinite()) return "--"
    val sign = if (gradePct >= 0) "+" else ""
    return "$sign${"%.1f".format(java.util.Locale.US, gradePct)}%"
}

// ---------------------------------------------------------------------------
// 下部ナビのタブ定義 (Lucideアイコン統一)
// ---------------------------------------------------------------------------

enum class CyclingTab(
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    MAP("地図", R.drawable.ic_lucide_map),
    ROUTE("ルート", R.drawable.ic_lucide_route),
    SPOT("スポット", R.drawable.ic_lucide_map_pin),
    RECORD("記録", R.drawable.ic_lucide_history),
    SETTINGS("設定", R.drawable.ic_lucide_settings),
}

// ---------------------------------------------------------------------------
// POIカテゴリ体系 (地図オーバーレイ・設定フィルタ・スポット検索で共用)
// ---------------------------------------------------------------------------

/**
 * POIカテゴリ。iconRes は Lucide 由来 VectorDrawable。
 * [important] はズームアウト時 (z14) でも表示する重要カテゴリ。
 */
enum class PoiCategory(
    val label: String,
    @DrawableRes val iconRes: Int,
    val colorHex: String,
    val prefixes: List<String>,
    val important: Boolean,
) {
    CONVENIENCE("コンビニ", R.drawable.ic_lucide_store, "#E8710A", listOf("shop:convenience"), false),
    TOILET("トイレ", R.drawable.ic_lucide_toilet, "#1A73E8", listOf("amenity:toilets"), false),
    STATION("駅・バス停", R.drawable.ic_lucide_train_front, "#188038", listOf("railway:station", "public_transport", "amenity:bus", "highway:bus_stop"), true),
    FOOD("飲食", R.drawable.ic_lucide_utensils, "#C5221F", listOf("amenity:restaurant", "amenity:cafe", "amenity:fast_food"), false),
    TOURISM("観光・休憩", R.drawable.ic_lucide_camera, "#9334E6", listOf("tourism:"), true),
    PARKING("駐車場", R.drawable.ic_lucide_square_parking, "#5F6368", listOf("amenity:parking"), false),
    HOSPITAL("病院", R.drawable.ic_lucide_hospital, "#D81B60", listOf("amenity:hospital", "amenity:clinic", "amenity:doctors", "amenity:dentist"), false),
    FUEL("ガソリン", R.drawable.ic_lucide_fuel, "#F9AB00", listOf("amenity:fuel"), false),
    PARK("公園", R.drawable.ic_lucide_trees, "#34A853", listOf("leisure:park"), false),
    WATER("給水", R.drawable.ic_lucide_droplets, "#00ACC1", listOf("amenity:drinking_water"), false),
    ;

    companion object {
        /** サイクリスト向けデフォルト選択 (トイレ・コンビニ・駅・飲食・観光)。 */
        fun defaults(): Set<PoiCategory> =
            setOf(CONVENIENCE, TOILET, STATION, FOOD, TOURISM)

        /** OSMカテゴリ文字列から最も近い分類を返す。 */
        fun forCategory(category: String): PoiCategory? =
            entries.firstOrNull { cat -> cat.prefixes.any { category.startsWith(it) } }
    }
}

// ---------------------------------------------------------------------------
// 周辺スポット (オフライン検索DB由来)
// ---------------------------------------------------------------------------

data class NearbySpot(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceM: Double,
)

/** スポットシートのクイックカテゴリ (POI体系へのエイリアス)。 */
enum class SpotQuickCategory(
    val label: String,
    val poi: PoiCategory,
) {
    CONVENIENCE("コンビニ", PoiCategory.CONVENIENCE),
    TOILET("トイレ", PoiCategory.TOILET),
    STATION("駅・バス停", PoiCategory.STATION),
    FOOD("飲食", PoiCategory.FOOD),
    TOURISM("観光・休憩", PoiCategory.TOURISM),
}
