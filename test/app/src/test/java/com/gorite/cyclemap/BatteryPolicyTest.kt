package com.gorite.cyclemap

import com.gorite.cyclemap.power.BatteryPolicy
import com.gorite.cyclemap.power.BatteryStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryPolicyTest {

    @Test
    fun batteryStatus_lowPowerThreshold20Percent() {
        // 21% は低残量ではない
        val st21 = BatteryStatus(levelPercent = 21, isCharging = false, isPowerSaveMode = false)
        assertFalse(st21.isLowPower)
        assertFalse(BatteryPolicy.shouldFallbackTo2D(st21))

        // 20% は低残量 (2Dフォールバック対象)
        val st20 = BatteryStatus(levelPercent = 20, isCharging = false, isPowerSaveMode = false)
        assertTrue(st20.isLowPower)
        assertTrue(BatteryPolicy.shouldFallbackTo2D(st20))

        // 10%, 0%
        val st10 = BatteryStatus(levelPercent = 10, isCharging = false, isPowerSaveMode = false)
        assertTrue(st10.isLowPower)
        val st0 = BatteryStatus(levelPercent = 0, isCharging = false, isPowerSaveMode = false)
        assertTrue(st0.isLowPower)
    }

    @Test
    fun batteryStatus_chargingOverridesLowBattery() {
        // 残量15%でも充電中であれば省電力・2Dフォールバックは発動しない
        val charging15 = BatteryStatus(levelPercent = 15, isCharging = true, isPowerSaveMode = false)
        assertFalse(charging15.isLowPower)
        assertFalse(BatteryPolicy.shouldFallbackTo2D(charging15))
    }

    @Test
    fun batteryStatus_powerSaveModeForcesFallbackRegardlessOfLevel() {
        // 残量90%でも OS 省電力モード有効時は 2D フォールバック
        val powerSave90 = BatteryStatus(levelPercent = 90, isCharging = false, isPowerSaveMode = true)
        assertTrue(powerSave90.isLowPower)
        assertTrue(BatteryPolicy.shouldFallbackTo2D(powerSave90))

        // 充電中でも OS 省電力モードがONなら省電力フラグ有効
        val chargingPowerSave = BatteryStatus(levelPercent = 80, isCharging = true, isPowerSaveMode = true)
        assertTrue(chargingPowerSave.isLowPower)
    }

    @Test
    fun batteryPolicy_overloadMethodConsistent() {
        assertTrue(BatteryPolicy.shouldFallbackTo2D(levelPercent = 15, isCharging = false, isPowerSaveMode = false))
        assertFalse(BatteryPolicy.shouldFallbackTo2D(levelPercent = 25, isCharging = false, isPowerSaveMode = false))
        assertFalse(BatteryPolicy.shouldFallbackTo2D(levelPercent = 15, isCharging = true, isPowerSaveMode = false))
        assertTrue(BatteryPolicy.shouldFallbackTo2D(levelPercent = 80, isCharging = false, isPowerSaveMode = true))
    }
}
