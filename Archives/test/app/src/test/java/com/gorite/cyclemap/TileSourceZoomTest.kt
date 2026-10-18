package com.gorite.cyclemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.MapTileIndex

class TileSourceZoomTest {

    @Test
    fun testGsiTileSourceAllowsDeepZoomUpTo21() {
        val gsi = gsiTileSource()
        // 最大ズームレベルが21まで拡大されていること
        assertEquals(21, gsi.maximumZoomLevel)
        assertEquals(2, gsi.minimumZoomLevel)

        // オンライン対応ズーム (z <= 18) では正規URLが生成されること
        val z18Tile = MapTileIndex.getTileIndex(18, 230000, 100000)
        val urlZ18 = gsi.getTileURLString(z18Tile)
        assertTrue("z18 should have valid URL", urlZ18.startsWith("https://cyberjapandata.gsi.go.jp/xyz/std/18/"))

        // オンライン非対応の超拡大ズーム (z > 18) では空文字を返し無駄なHTTP通信を遮断すること
        val z19Tile = MapTileIndex.getTileIndex(19, 460000, 200000)
        val urlZ19 = gsi.getTileURLString(z19Tile)
        assertEquals("", urlZ19)

        val z21Tile = MapTileIndex.getTileIndex(21, 1840000, 800000)
        val urlZ21 = gsi.getTileURLString(z21Tile)
        assertEquals("", urlZ21)
    }

    @Test
    fun testOsmTileSourceAllowsDeepZoomUpTo21() {
        val osm = osmTileSource()
        assertEquals(21, osm.maximumZoomLevel)
        assertEquals(2, osm.minimumZoomLevel)

        // OSMは z19 までオンライン配信がある
        val z19Tile = MapTileIndex.getTileIndex(19, 460000, 200000)
        val urlZ19 = osm.getTileURLString(z19Tile)
        assertTrue("z19 should have valid OSM URL", urlZ19.contains("/19/"))

        // z20以上は空文字で無駄通信防止
        val z20Tile = MapTileIndex.getTileIndex(20, 920000, 400000)
        assertEquals("", osm.getTileURLString(z20Tile))
    }
}
