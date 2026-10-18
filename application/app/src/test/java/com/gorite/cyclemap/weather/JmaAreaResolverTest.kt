package com.gorite.cyclemap.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class JmaAreaResolverTest {

    private lateinit var resolver: JmaAreaResolver

    @Before
    fun setUp() {
        val areaFile = File("src/main/assets/jma/area.json")
        val forecastAreaFile = File("src/main/assets/jma/forecast_area.json")
        assertTrue("area.json が存在すること", areaFile.exists())
        assertTrue("forecast_area.json が存在すること", forecastAreaFile.exists())

        resolver = JmaAreaResolver.fromStreams(
            FileInputStream(areaFile),
            FileInputStream(forecastAreaFile)
        )
    }

    @Test
    fun resolveYamaguchiCity_returnsCorrectCodes() {
        val resolved = resolver.resolve("山口県", "山口市")
        assertNotNull(resolved)
        assertEquals("350000", resolved?.officeCode) // 山口県
        assertEquals("350020", resolved?.class10Code) // 中部
        assertEquals("81286", resolved?.amedasCode) // 山口
        assertFalse(resolved!!.isFallback)
    }

    @Test
    fun resolveHiroshimaCity_returnsCorrectCodes() {
        val resolved = resolver.resolve("広島県", "広島市")
        assertNotNull(resolved)
        assertEquals("340000", resolved?.officeCode) // 広島県
        assertEquals("340010", resolved?.class10Code) // 南部
        assertEquals("67437", resolved?.amedasCode) // 広島
        assertFalse(resolved!!.isFallback)
    }

    @Test
    fun resolveOkayamaCity_returnsCorrectCodes() {
        val resolved = resolver.resolve("岡山県", "岡山市")
        assertNotNull(resolved)
        assertEquals("330000", resolved?.officeCode) // 岡山県
        assertEquals("330010", resolved?.class10Code) // 南部
        assertEquals("66408", resolved?.amedasCode) // 岡山
        assertFalse(resolved!!.isFallback)
    }

    @Test
    fun resolveUnknownCity_fallsBackToPrefectureRepresentative() {
        val resolved = resolver.resolve("山口県", "架空の町")
        assertNotNull(resolved)
        assertEquals("350000", resolved?.officeCode)
        assertTrue(resolved!!.isFallback)
        assertTrue(resolved.areaName.contains("山口県"))
    }

    @Test
    fun resolveNullCity_fallsBackToPrefectureRepresentative() {
        val resolved = resolver.resolve("広島県", null)
        assertNotNull(resolved)
        assertEquals("340000", resolved?.officeCode)
        assertTrue(resolved!!.isFallback)
        assertTrue(resolved.areaName.contains("広島県"))
    }

    @Test
    fun resolveInvalidPrefecture_returnsNull() {
        val resolved = resolver.resolve("存在しない県")
        assertNull(resolved)
    }

    @Test
    fun resolveFullReverseGeocodedLabel_returnsCorrectSubArea() {
        val yamaguchiAddress = resolver.resolve("山口県", "山口県 山口市 井口明神")
        assertNotNull(yamaguchiAddress)
        assertEquals("350020", yamaguchiAddress?.class10Code) // 中部
        assertFalse(yamaguchiAddress!!.isFallback)

        val hiroshimaWardAddress = resolver.resolve("広島県", "広島県 広島市中区 基町")
        assertNotNull(hiroshimaWardAddress)
        assertEquals("340010", hiroshimaWardAddress?.class10Code) // 南部
        assertFalse(hiroshimaWardAddress!!.isFallback)
    }
}
