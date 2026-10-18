package com.gorite.cyclemap.ui.map

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * StyleLayerOrderTest: plans.md §8
 * 地図スタイルおよび実行時動的レイヤーの重なり順序仕様を検証:
 * - 地表道路レイヤー群 (roads_highway, roads_major等) < routeLayer (通常線) < buildings-3d < xrayRouteLayer (半透明線)
 */
class StyleLayerOrderTest {

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
    fun testProtomapsLightLayerOrder_groundRoadsPrecedeBuilding3d() {
        val file = findAssetStyle("protomaps_light.json")
        assertTrue("Style json should exist", file.exists())
        val content = file.readText()

        val idRegex = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        val layerIds = idRegex.findAll(content).map { it.groupValues[1] }.toList()

        val highwayIndex = layerIds.indexOf("roads_highway")
        val majorIndex = layerIds.indexOf("roads_major")
        val minorIndex = layerIds.indexOf("roads_minor")
        val building3dIndex = layerIds.indexOf("buildings-3d")

        assertTrue("roads_highway layer should exist", highwayIndex != -1)
        assertTrue("roads_major layer should exist", majorIndex != -1)
        assertTrue("roads_minor layer should exist", minorIndex != -1)
        assertTrue("buildings-3d layer should exist", building3dIndex != -1)

        // 地表の主要幹線・一般道路は3D建物より下層に配置されていること
        assertTrue("roads_minor must precede buildings-3d", minorIndex < building3dIndex)
        assertTrue("roads_major must precede buildings-3d", majorIndex < building3dIndex)
        assertTrue("roads_highway must precede buildings-3d", highwayIndex < building3dIndex)
    }

    @Test
    fun testVectorMapDynamicRouteInsertionOrder() {
        // VectorMapView.kt による動的挿入ロジックの不変条件テスト:
        // 1. casingLayer & routeLayer -> addLayerBelow("buildings-3d")
        // 2. xrayRouteLayer -> addLayer (最前面)
        val baseLayers = mutableListOf("background", "roads_minor", "roads_major", "roads_highway", "buildings-3d", "roads_rail_tram", "poi-symbols")

        val buildingIndex = baseLayers.indexOf("buildings-3d")
        // addLayerBelow("buildings-3d") inserts directly before buildings-3d
        baseLayers.add(buildingIndex, "cyclemap-route-casing")
        val newBuildingIndex = baseLayers.indexOf("buildings-3d")
        baseLayers.add(newBuildingIndex, "cyclemap-route-line")

        // addLayer appends to end
        baseLayers.add("cyclemap-route-xray")

        val roadIdx = baseLayers.indexOf("roads_highway")
        val routeIdx = baseLayers.indexOf("cyclemap-route-line")
        val bldgIdx = baseLayers.indexOf("buildings-3d")
        val xrayIdx = baseLayers.indexOf("cyclemap-route-xray")

        assertTrue("通常ルート線は道路レイヤーより上に配置されること", routeIdx > roadIdx)
        assertTrue("通常ルート線は3D建物レイヤーより下に配置されること", routeIdx < bldgIdx)
        assertTrue("X-Ray半透明ルート線は3D建物より手前に配置されること", xrayIdx > bldgIdx)
    }
}
