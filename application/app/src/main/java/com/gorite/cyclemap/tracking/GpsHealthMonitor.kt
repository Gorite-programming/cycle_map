package com.gorite.cyclemap.tracking

/**
 * GPS測位の健全性状態。
 */
enum class GpsSignalStatus {
    /** 正常: 精度が十分であり、直近5秒以内に測位あり */
    HEALTHY,
    /** 精度低下: 水平精度が100mを超えてドロップされた、または5秒以上測位なし */
    WEAK,
    /** 信号ロスト: 15秒以上測位なし (自車位置停止) */
    LOST,
}

/**
 * GPSの受信状態とタイムアウトを監視するモニター。
 */
class GpsHealthMonitor(
    private val weakTimeoutMs: Long = 5_000L,
    private val lostTimeoutMs: Long = 15_000L,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    var status: GpsSignalStatus = GpsSignalStatus.HEALTHY
        private set

    var lastFixMs: Long = 0L
        private set

    var hasReceivedFirstFix: Boolean = false
        private set

    /** 有効な位置情報（精度100m以下）を受信したときに呼ぶ */
    fun onValidFix(nowMs: Long = clock()) {
        hasReceivedFirstFix = true
        lastFixMs = nowMs
        status = GpsSignalStatus.HEALTHY
    }

    /** 精度100m超などにより破棄されたときに呼ぶ */
    fun onAccuracyDegraded() {
        if (status == GpsSignalStatus.HEALTHY) {
            status = GpsSignalStatus.WEAK
        }
    }

    /** 定期呼び出し（タイマー）で状態を更新 */
    fun tick(nowMs: Long = clock()): GpsSignalStatus {
        if (!hasReceivedFirstFix) return status
        val elapsed = nowMs - lastFixMs
        when {
            elapsed >= lostTimeoutMs -> status = GpsSignalStatus.LOST
            elapsed >= weakTimeoutMs -> {
                if (status == GpsSignalStatus.HEALTHY) {
                    status = GpsSignalStatus.WEAK
                }
            }
        }
        return status
    }

    fun reset() {
        status = GpsSignalStatus.HEALTHY
        lastFixMs = 0L
        hasReceivedFirstFix = false
    }
}
