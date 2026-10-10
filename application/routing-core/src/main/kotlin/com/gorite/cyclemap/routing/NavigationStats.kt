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
 * @param lastEffectiveSpeedMps 直近の有効実効速度 (停止中のETA急変動防止用)。
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
    lastEffectiveSpeedMps: Double? = null,
): NavigationStats? {
    if (progress == null) return null
    val remaining = (progress.routeDistanceMeters - progress.distanceFromStartMeters).coerceAtLeast(0.0)
    val elapsed = ((nowElapsedRealtimeMs - navStartElapsedRealtimeMs).coerceAtLeast(0L)) / 1_000.0
    val isArrived = remaining <= arrivalRadiusMeters

    val covered = progress.distanceFromStartMeters.coerceAtLeast(0.0)
    val averageSpeed = if (elapsed >= minAverageElapsedSeconds && covered > 0.0) covered / elapsed else Double.NaN

    // 信号待ち・一時停止中のETA発散防止および滑らかな速度ブレンディング
    val isStopped = smoothedSpeedMps < 0.5
    val effective = when {
        // 停止中は直前の実効速度をホールドし、ETAの急上昇（発散）を防ぐ
        isStopped && lastEffectiveSpeedMps != null && lastEffectiveSpeedMps >= minAverageSpeedMps ->
            lastEffectiveSpeedMps.coerceIn(minAverageSpeedMps, maxAverageSpeedMps)
        averageSpeed.isFinite() && averageSpeed >= minAverageSpeedMps -> {
            // 走行中は累積平均(70%)と直近瞬間速度(30%)をブレンドして急変を緩和
            val blended = if (smoothedSpeedMps >= minAverageSpeedMps) {
                averageSpeed * 0.7 + smoothedSpeedMps * 0.3
            } else {
                averageSpeed
            }
            blended.coerceIn(minAverageSpeedMps, maxAverageSpeedMps)
        }
        smoothedSpeedMps > 0.5 -> smoothedSpeedMps.coerceIn(minAverageSpeedMps, maxAverageSpeedMps)
        lastEffectiveSpeedMps != null && lastEffectiveSpeedMps >= minAverageSpeedMps ->
            lastEffectiveSpeedMps.coerceIn(minAverageSpeedMps, maxAverageSpeedMps)
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
