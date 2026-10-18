package com.gorite.cyclemap.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
) {
    /**
     * バッテリー残量 20% 以下（かつ未充電）、または OS 省電力モード有効時に 3D 描画を抑制
     */
    val isLowPower: Boolean
        get() = isPowerSaveMode || (!isCharging && levelPercent in 0..LOW_POWER_THRESHOLD_PERCENT)

    companion object {
        const val LOW_POWER_THRESHOLD_PERCENT = 20

        val DEFAULT = BatteryStatus(
            levelPercent = 100,
            isCharging = false,
            isPowerSaveMode = false,
        )
    }
}

object BatteryPolicy {
    fun shouldFallbackTo2D(status: BatteryStatus): Boolean = status.isLowPower

    fun shouldFallbackTo2D(levelPercent: Int, isCharging: Boolean, isPowerSaveMode: Boolean): Boolean {
        return isPowerSaveMode || (!isCharging && levelPercent in 0..BatteryStatus.LOW_POWER_THRESHOLD_PERCENT)
    }
}

class BatteryMonitor(private val context: Context) {
    private val _status = MutableStateFlow(queryCurrentStatus(context))
    val status: StateFlow<BatteryStatus> = _status.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                _status.value = queryCurrentStatus(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        receiver = r
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(r, filter)
            }
        } catch (_: Exception) {}
        _status.value = queryCurrentStatus(context)
    }

    fun stop() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            receiver = null
        }
    }

    companion object {
        fun queryCurrentStatus(context: Context): BatteryStatus {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSave = powerManager?.isPowerSaveMode ?: false

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            var level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            var isCharging = false

            try {
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent != null) {
                    val rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (rawLevel >= 0 && scale > 0) {
                        level = (rawLevel * 100) / scale
                    }
                    val rawStatus = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    isCharging = rawStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                            rawStatus == BatteryManager.BATTERY_STATUS_FULL ||
                            plugged != 0
                }
            } catch (_: Exception) {}

            return BatteryStatus(
                levelPercent = if (level in 0..100) level else 100,
                isCharging = isCharging,
                isPowerSaveMode = isPowerSave,
            )
        }
    }
}
