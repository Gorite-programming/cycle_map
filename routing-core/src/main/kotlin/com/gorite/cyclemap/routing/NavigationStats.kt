package com.gorite.cyclemap.routing

/**
 * ナビ中にUIへ表示する集計値。純粋KotlinでAndroid非依存。
 */
data class NavigationStats(
    /** 目的地までの残距離 (m) */
    val remainingMeters: Double,
    /** 案内開始からの経過時間 (s) */
    val elapsedSeconds: Double,
    /** ETA算出に使った実効速度 (m/s) */
    val effectiveSpeedMps: Double,
    /** 残り所要時間の推定 (s)。速度が得られない場合は +INF */
    val durationRemainingSeconds: Double,
    /** 到着予想時刻 (epoch millis)。算出不能時は null */
    val etaEpochMillis: Long?,
    /** 到着済みか */
    val isArrived: Boolean,
)

/**
 * [RouteProgress] から [NavigationStats] を算出する。ETAは平均速度ベース。
 *
 * 瞬間速度はGPSのばらつきでETAが跳ねるため、実効速度は「案内開始からの
 * 走行距離 ÷ 経過時間」の平均速度を優先する。平均がまだ得られない序盤
 * (経過が短い・走行距離が短い・平均が極端に低い) のみ、瞬間速度→
 * デフォルト巡航速度の順でフォールバックする。
 *
 * @param smoothedSpeedMps アプリ側で平滑化した現在速度。平均が未確定の序盤のみ使用。
 * @param navStartElapsedRealtimeMs 案内開始時刻 ([android.os.SystemClock.elapsedRealtime] 基準)。
 *   リルート成功時は新ルート起点でリセットすること (距離が0に戻るため)。
 * @param nowElapsedRealtimeMs 現在時刻 (同基準)。
 * @param nowEpochMillis 現在時刻 (epoch millis、ETA表示用)。
 * @param arrivalRadiusMeters この残距離以下で到着とみなす。
 * @param defaultSpeedMps 速度が得られない場合のフォールバック (自転車の巡航速度)。
 * @param minAverageElapsedSeconds この経過秒数未満では平均を使わない。
 * @param minAverageSpeedMps 平均がこの速度未満の場合は停止・渋滞とみなしフォールバックする。
 * @param maxAverageSpeedMps 平均の上限 (GPSジャンプ由来の異常値を抑える)。
 */
fun computeNavigationStats(
    progress: RouteProgress?,
    smoothedSpeedMps: Double,
    navStartElapsedRealtimeMs: Long,
    nowElapsedRealtimeMs: Long,
    nowEpochMillis: Long,
    arrivalRadiusMeters: Double = 30.0,
    defaultSpeedMps: Double = 15_000.0 / 3_600.0,
    minAverageElapsedSeconds: Double = 10.0,
    minAverageSpeedMps: Double = 1.0,
    maxAverageSpeedMps: Double = 60_000.0 / 3_600.0,
): NavigationStats? {
    if (progress == null) return null
    val remaining = (progress.routeDistanceMeters - progress.distanceFromStartMeters).coerceAtLeast(0.0)
    val elapsed = ((nowElapsedRealtimeMs - navStartElapsedRealtimeMs).coerceAtLeast(0L)) / 1_000.0
    val isArrived = remaining <= arrivalRadiusMeters

    val covered = progress.distanceFromStartMeters.coerceAtLeast(0.0)
    val averageSpeed = if (elapsed >= minAverageElapsedSeconds && covered > 0.0) covered / elapsed else Double.NaN
    // 平均速度を優先し、未確定・異常値のときのみ瞬間速度→デフォルトへ倒す。
    val effective = when {
        averageSpeed.isFinite() && averageSpeed >= minAverageSpeedMps ->
            averageSpeed.coerceAtMost(maxAverageSpeedMps)
        smoothedSpeedMps > 0.5 -> smoothedSpeedMps.coerceAtMost(maxAverageSpeedMps)
        else -> defaultSpeedMps
    }
    val durationRemaining = if (isArrived || remaining <= 0.0) {
        0.0
    } else if (effective > 0.0) {
        remaining / effective
    } else {
        Double.POSITIVE_INFINITY
    }
    val eta = if (durationRemaining.isFinite()) {
        nowEpochMillis + (durationRemaining * 1_000.0).toLong()
    } else {
        null
    }
    return NavigationStats(
        remainingMeters = remaining,
        elapsedSeconds = elapsed,
        effectiveSpeedMps = effective,
        durationRemainingSeconds = durationRemaining,
        etaEpochMillis = eta,
        isArrived = isArrived,
    )
}

/**
 * ルート逸脱検出器 (有状態)。GPSの瞬間的なジャンプで誤発火しないよう、
 * 逸脱距離しきい値 × 連続回数で確定し、復帰にはヒステリシスを持たせる。
 */
class OffRouteDetector(
    /** この距離(m)を超えたら逸脱カウント */
    val offRouteDistanceMeters: Double = 50.0,
    /** この回数連続で超えたらオフロード確定 */
    val requiredConsecutive: Int = 3,
    /** 確定後にこの距離(m)以内へ戻れば復帰 */
    val recoverDistanceMeters: Double = 20.0,
) {
    private var consecutiveCount: Int = 0

    var isOffRoute: Boolean = false
        private set

    /** 現在のルートからの距離を与えて状態を更新し、確定状態を返す。 */
    fun update(distanceToRouteMeters: Double): Boolean {
        if (isOffRoute) {
            if (distanceToRouteMeters <= recoverDistanceMeters) {
                isOffRoute = false
                consecutiveCount = 0
            }
            return isOffRoute
        }
        if (distanceToRouteMeters > offRouteDistanceMeters) {
            consecutiveCount++
            if (consecutiveCount >= requiredConsecutive) {
                isOffRoute = true
            }
        } else {
            consecutiveCount = 0
        }
        return isOffRoute
    }

    fun reset() {
        consecutiveCount = 0
        isOffRoute = false
    }
}
