package com.gorite.cyclemap.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StyleJsonTest {

    private fun findAssetStyle(name: String): File {
        val candidates = listOf(
            File("src/main/assets/styles/$name"),
            File("app/src/main/assets/styles/$name"),
            File("re_test/app/src/main/assets/styles/$name"),
            File("/Users/gorite/Desktop/cycle_map/re_test/app/src/main/assets/styles/$name")
        )
        return candidates.firstOrNull { it.exists() } ?: File("src/main/assets/styles/$name")
    }

    @Test
    fun protomapsLightContainsPlaceholdersAndLayers() {
        val file = findAssetStyle("protomaps_light.json")
        assertTrue("Style file should exist", file.exists())
        val jsonStr = file.readText()

        assertTrue("Should contain __LOCAL_GLYPHS__", jsonStr.contains("\"glyphs\": \"__LOCAL_GLYPHS__\""))
        assertTrue("Should contain __LOCAL_SPRITES__", jsonStr.contains("\"sprite\": \"__LOCAL_SPRITES__\""))
        assertTrue("Should contain __LOCAL_PMTILES__", jsonStr.contains("\"url\": \"__LOCAL_PMTILES__\""))
        assertTrue("Should contain background layer", jsonStr.contains("\"id\": \"background\""))
        assertTrue("Should contain building layer", jsonStr.contains("building"))
        assertTrue("Should contain highway or road layer", jsonStr.contains("highway") || jsonStr.contains("road"))
    }

    @Test
    fun protomapsDarkContainsPlaceholders() {
        val file = findAssetStyle("protomaps_dark.json")
        assertTrue("Dark style file should exist", file.exists())
        val jsonStr = file.readText()

        assertTrue("Should contain __LOCAL_GLYPHS__", jsonStr.contains("\"glyphs\": \"__LOCAL_GLYPHS__\""))
        assertTrue("Should contain __LOCAL_SPRITES__", jsonStr.contains("\"sprite\": \"__LOCAL_SPRITES__\""))
        assertTrue("Should contain __LOCAL_PMTILES__", jsonStr.contains("\"url\": \"__LOCAL_PMTILES__\""))
    }
}
