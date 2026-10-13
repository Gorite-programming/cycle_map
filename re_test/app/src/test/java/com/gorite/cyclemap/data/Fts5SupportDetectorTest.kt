package com.gorite.cyclemap.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Fts5SupportDetectorTest {

    @After
    fun tearDown() {
        Fts5SupportDetector.setOverrideForTesting(null)
    }

    @Test
    fun overrideForTesting_controlsIsSupported() {
        Fts5SupportDetector.setOverrideForTesting(true)
        assertTrue(Fts5SupportDetector.isSupported())

        Fts5SupportDetector.setOverrideForTesting(false)
        assertFalse(Fts5SupportDetector.isSupported())

        Fts5SupportDetector.setOverrideForTesting(null)
    }

    @Test
    fun cachedSupport_persistsAcrossCalls() {
        Fts5SupportDetector.setOverrideForTesting(true)
        assertEquals(true, Fts5SupportDetector.isSupported())
        // override を変更せずに再度呼んでもキャッシュが返る
        assertEquals(true, Fts5SupportDetector.isSupported())
    }
}
