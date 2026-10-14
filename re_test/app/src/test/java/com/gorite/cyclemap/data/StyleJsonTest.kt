package com.gorite.cyclemap.data

import com.gorite.cyclemap.vectorMapAreaFillColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

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
        assertTrue("Must not contain remote resource URLs", !jsonStr.contains("https://") && !jsonStr.contains("http://"))
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
        assertTrue("Must not contain remote resource URLs", !jsonStr.contains("https://") && !jsonStr.contains("http://"))
    }

    @Test
    fun protomapsThemesContainJapaneseLabelsAndOfflineSprites() {
        for (theme in listOf("protomaps_light.json", "protomaps_dark.json")) {
            val styleFile = findAssetStyle(theme)
            val json = styleFile.readText()
            assertTrue("$theme must label roads", json.contains("\"id\": \"roads_labels_minor\""))
            assertTrue("$theme must label places", json.contains("\"id\": \"places_locality\""))
            assertTrue("$theme must include Japanese name selection", json.contains("\"name:ja\""))
            assertTrue("$theme must use the bundled font stack", json.contains("\"Noto Sans Regular\""))
            assertTrue("$theme must not depend on remote assets", !json.contains("https://") && !json.contains("http://"))
        }

        val assetsDir = findAssetStyle("protomaps_light.json").parentFile?.parentFile
            ?: error("Could not resolve main assets directory")
        assertTrue("Latin glyph range must be bundled", File(assetsDir, "fonts/Noto Sans Regular/0-255.pbf").isFile)
        assertTrue("Japanese kana glyph range must be bundled", File(assetsDir, "fonts/Noto Sans Regular/12288-12543.pbf").isFile)
        assertTrue("Japanese full-width glyph range must be bundled", File(assetsDir, "fonts/Noto Sans Regular/65280-65535.pbf").isFile)
        assertTrue("Light sprite sheet must be bundled", File(assetsDir, "sprites/light.json").isFile)
        assertTrue("Dark sprite sheet must be bundled", File(assetsDir, "sprites/dark.json").isFile)
        assertTrue("Font license must be bundled", File(assetsDir, "licenses/protomaps-fonts-OFL.txt").isFile)
        assertTrue("Sprite license must be bundled", File(assetsDir, "licenses/tangrams-icons-MIT.txt").isFile)
    }

    @Test
    fun themeOverridesCoverEveryStaticAreaFillLayer() {
        val lightStyle = findAssetStyle("protomaps_light.json").readText()
        val darkStyle = findAssetStyle("protomaps_dark.json").readText()
        val lightColors = vectorMapAreaFillColors(isNight = false)
        val darkColors = vectorMapAreaFillColors(isNight = true)

        assertEquals("Light and dark overrides must cover the same layers", lightColors.keys, darkColors.keys)
        assertTrue(
            "Theme overrides must include landuse and station building fills",
            lightColors.keys.containsAll(
                listOf(
                    "landuse_hospital",
                    "landuse_industrial",
                    "landuse_school",
                    "landuse_beach",
                    "landuse_zoo",
                    "landuse_aerodrome",
                    "landuse_runway",
                    "landuse_pedestrian",
                    "landuse_pier",
                    "landuse_platform",
                    "buildings_station",
                ),
            ),
        )

        lightColors.forEach { (layerId, color) ->
            assertLayerColor(lightStyle, layerId, color)
        }
        darkColors.forEach { (layerId, color) ->
            assertLayerColor(darkStyle, layerId, color)
        }
    }

    @Test
    fun darkSymbolLabelsHaveReadableContrast() {
        val paints = symbolLabelPaints(findAssetStyle("protomaps_dark.json").readText())
            .filter { it.textColorHexes.isNotEmpty() }
        val labeled = paints.map { it.id }
        assertTrue(
            "Expected Japanese label layers",
            labeled.containsAll(
                listOf(
                    "roads_labels_minor",
                    "roads_labels_major",
                    "places_subplace",
                    "places_locality",
                    "places_region",
                    "water_waterway_label",
                ),
            ),
        )
        paints.forEach { paint ->
            val haloWidth = paint.haloWidth ?: 0.0
            assertTrue("${paint.id} halo width must be at least 1.5, was $haloWidth", haloWidth >= 1.5)
            assertTrue("${paint.id} must define a halo color", paint.haloColorHexes.isNotEmpty())
            paint.haloColorHexes.forEach { halo ->
                assertTrue(
                    "${paint.id} halo $halo must stay darker than the map background",
                    relativeLuminance(halo) <= relativeLuminance("#1F1F1F") + 0.01,
                )
            }
            val colors = if (paint.id == "pois") listOf(paint.textColorHexes.last()) else paint.textColorHexes
            colors.forEach { color ->
                val contrast = contrastRatio(color, "#1F1F1F")
                assertTrue(
                    "${paint.id} text $color contrast $contrast is below 4.5:1 on #1F1F1F",
                    contrast >= 4.5,
                )
            }
        }
    }

    @Test
    fun lightSymbolLabelPaintsStayUnchanged() {
        val paints = symbolLabelPaints(findAssetStyle("protomaps_light.json").readText()).associateBy { it.id }
        assertEquals(listOf("#91888b"), paints.getValue("roads_labels_minor").textColorHexes)
        assertEquals(listOf("#ffffff"), paints.getValue("roads_labels_minor").haloColorHexes)
        assertEquals(1.0, paints.getValue("roads_labels_minor").haloWidth)
        assertEquals(listOf("#938a8d"), paints.getValue("roads_labels_major").textColorHexes)
        assertEquals(listOf("#ffffff"), paints.getValue("roads_labels_major").haloColorHexes)
        assertEquals(listOf("#5c5c5c"), paints.getValue("places_locality").textColorHexes)
        assertEquals(listOf("#e0e0e0"), paints.getValue("places_locality").haloColorHexes)
        assertEquals(listOf("#8f8f8f"), paints.getValue("places_subplace").textColorHexes)
        assertEquals(listOf("#728dd4"), paints.getValue("water_waterway_label").textColorHexes)
        assertEquals(listOf("#80deea"), paints.getValue("water_waterway_label").haloColorHexes)
        assertEquals(listOf("#91888b"), paints.getValue("address_label").textColorHexes)
        assertEquals(listOf("#a3a3a3"), paints.getValue("places_country").textColorHexes)
        assertTrue(
            "Light shields must not gain a dark-theme halo",
            paints.getValue("roads_shields").haloColorHexes.isEmpty(),
        )
        assertNull("Light shields must keep the original missing halo width", paints.getValue("roads_shields").haloWidth)
    }

    private data class SymbolLabelPaint(
        val id: String,
        val textColorHexes: List<String>,
        val haloColorHexes: List<String>,
        val haloWidth: Double?,
    )

    private fun symbolLabelPaints(styleJson: String): List<SymbolLabelPaint> {
        val idRegex = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        val matches = idRegex.findAll(styleJson).toList()
        return matches.mapIndexedNotNull { index, match ->
            val layerJson = styleJson.substring(
                match.range.first,
                matches.getOrNull(index + 1)?.range?.first ?: styleJson.length,
            )
            if (!layerJson.contains("\"type\": \"symbol\"") && !layerJson.contains("\"type\":\"symbol\"")) {
                return@mapIndexedNotNull null
            }
            val paint = objectValue(layerJson, "paint") ?: ""
            SymbolLabelPaint(
                id = match.groupValues[1],
                textColorHexes = hexColorsIn(jsonValue(paint, "text-color")),
                haloColorHexes = hexColorsIn(jsonValue(paint, "text-halo-color")),
                haloWidth = jsonValue(paint, "text-halo-width")?.trim()?.toDoubleOrNull(),
            )
        }
    }

    private fun objectValue(json: String, key: String): String? {
        val marker = Regex("\"$key\"\\s*:\\s*\\{").find(json) ?: return null
        val start = marker.range.last
        var depth = 1
        var i = start + 1
        while (i < json.length && depth > 0) {
            when (json[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return json.substring(start + 1, i - 1)
    }

    private fun jsonValue(json: String, key: String): String? {
        val match = Regex("\"$key\"\\s*:").find(json) ?: return null
        var i = match.range.last + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        return when (json[i]) {
            '"' -> {
                val end = json.indexOf('"', i + 1)
                json.substring(i, end + 1)
            }
            '[' -> {
                var depth = 1
                var j = i + 1
                while (j < json.length && depth > 0) {
                    when (json[j]) {
                        '[' -> depth++
                        ']' -> depth--
                    }
                    j++
                }
                json.substring(i, j)
            }
            else -> {
                val end = json.indexOfFirstFrom(i) { it == ',' || it == '}' || it == '\n' }
                json.substring(i, if (end < 0) json.length else end)
            }
        }
    }

    private fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (i in start until length) {
            if (predicate(this[i])) return i
        }
        return -1
    }

    private fun hexColorsIn(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return Regex("#[0-9A-Fa-f]{3,8}").findAll(value).map { it.value }.toList()
    }

    private fun contrastRatio(first: String, second: String): Double {
        val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
        val darker = minOf(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(hex: String): Double {
        val digits = hex.removePrefix("#")
        val normalized = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
        fun channel(offset: Int): Double {
            val raw = normalized.substring(offset, offset + 2).toInt(16) / 255.0
            return if (raw <= 0.04045) raw / 12.92 else ((raw + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    }

    private fun assertLayerColor(styleJson: String, layerId: String, color: String) {
        val idMarker = "\"id\": \"$layerId\""
        val idStart = styleJson.indexOf(idMarker)
        assertTrue("Style must define layer $layerId", idStart >= 0)

        val nextLayerStart = styleJson.indexOf("\"id\": ", idStart + idMarker.length)
        val layerJson = styleJson.substring(
            idStart,
            if (nextLayerStart < 0) styleJson.length else nextLayerStart,
        )
        assertTrue(
            "Style layer $layerId must use $color",
            layerJson.contains("\"fill-color\": \"$color\""),
        )
    }
}
