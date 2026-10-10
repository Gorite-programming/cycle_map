package com.gorite.cyclemap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsHealthMonitorTest {

    @Test
    fun initialState_isHealthyAndNoFirstFix() {
        val monitor = GpsHealthMonitor()
        assertEquals(GpsSignalStatus.HEALTHY, monitor.status)
        assertFalse(monitor.hasReceivedFirstFix)
    }

    @Test
    fun onValidFix_marksHealthyAndFirstFix() {
        var currentTime = 1000L
        val monitor = GpsHealthMonitor(clock = { currentTime })
        monitor.onValidFix()

        assertTrue(monitor.hasReceivedFirstFix)
        assertEquals(1000L, monitor.lastFixMs)
        assertEquals(GpsSignalStatus.HEALTHY, monitor.status)
    }

    @Test
    fun onAccuracyDegraded_transitionsToWeak() {
        var currentTime = 1000L
        val monitor = GpsHealthMonitor(clock = { currentTime })
        monitor.onValidFix()

        monitor.onAccuracyDegraded()
        assertEquals(GpsSignalStatus.WEAK, monitor.status)

        // Valid fix recovers to HEALTHY
        currentTime = 2000L
        monitor.onValidFix()
        assertEquals(GpsSignalStatus.HEALTHY, monitor.status)
    }

    @Test
    fun tick_transitionsToWeakAfter5Seconds() {
        var currentTime = 1000L
        val monitor = GpsHealthMonitor(
            weakTimeoutMs = 5000L,
            lostTimeoutMs = 15000L,
            clock = { currentTime },
        )
        monitor.onValidFix()

        // 4.9s later -> still HEALTHY
        currentTime = 5900L
        assertEquals(GpsSignalStatus.HEALTHY, monitor.tick())

        // 5.0s later -> WEAK
        currentTime = 6000L
        assertEquals(GpsSignalStatus.WEAK, monitor.tick())
    }

    @Test
    fun tick_transitionsToLostAfter15Seconds() {
        var currentTime = 1000L
        val monitor = GpsHealthMonitor(
            weakTimeoutMs = 5000L,
            lostTimeoutMs = 15000L,
            clock = { currentTime },
        )
        monitor.onValidFix()

        // 14.9s later -> WEAK
        currentTime = 15900L
        assertEquals(GpsSignalStatus.WEAK, monitor.tick())

        // 16.0s later -> LOST
        currentTime = 17000L
        assertEquals(GpsSignalStatus.LOST, monitor.tick())

        // Valid fix recovers from LOST to HEALTHY
        currentTime = 18000L
        monitor.onValidFix()
        assertEquals(GpsSignalStatus.HEALTHY, monitor.status)
    }

    @Test
    fun tick_beforeFirstFix_doesNotTimeout() {
        var currentTime = 1000L
        val monitor = GpsHealthMonitor(clock = { currentTime })

        currentTime = 20000L
        assertEquals(GpsSignalStatus.HEALTHY, monitor.tick())
        assertFalse(monitor.hasReceivedFirstFix)
    }
}
