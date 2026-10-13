package com.gorite.cyclemap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TileSourceSelectorTest {

    @Test
    fun selectsYamaguchiAndHiroshimaPmtiles() {
        // Yamaguchi City ~ 34.185, 131.471
        val yama = TileSourceSelector.select(34.185, 131.471)
        assertTrue(yama is TileSourceSelection.Available)
        assertEquals("yamaguchi.pmtiles", (yama as TileSourceSelection.Available).fileName)

        // Hiroshima City ~ 34.385, 132.455
        val hiro = TileSourceSelector.select(34.385, 132.455)
        assertTrue(hiro is TileSourceSelection.Available)
        assertEquals("Hiroshima_osm.pmtiles", (hiro as TileSourceSelection.Available).fileName)
    }

    @Test
    fun unmappedCoordinatesReturnUnavailable() {
        // Tokyo coordinates (not in Chugoku region)
        val tokyo = TileSourceSelector.select(35.68, 139.76)
        assertTrue(tokyo is TileSourceSelection.Unavailable)
    }

    @Test
    fun fallbackResolvesCaseVariants() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cyclemap_tile_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val tileFile = File(tempDir, "yamaguchi.pmtiles")
            tileFile.writeText("fake pmtiles content")

            val resolved = TileSourceSelector.resolveTileFile(tempDir, "Yamaguchi.pmtiles")
            assertTrue(resolved != null && resolved.exists())
            assertEquals(tileFile.canonicalPath.lowercase(), resolved?.canonicalPath?.lowercase())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
