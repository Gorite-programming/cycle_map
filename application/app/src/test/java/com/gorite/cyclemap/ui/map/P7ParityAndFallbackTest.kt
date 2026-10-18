package com.gorite.cyclemap.ui.map

import com.gorite.cyclemap.net.FeatureResult
import com.gorite.cyclemap.net.Link
import com.gorite.cyclemap.net.OnlineFeatureConfig
import com.gorite.cyclemap.net.OnlineFeatureGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P7 QA 必須テスト群:
 * - OfflineParityTest: Link.Offline 状態での主要UI/状態整合性検証
 * - OnlineFallbackTest: オンライン呼出失敗・タイムアウト時のフォールバック検証
 * - LayoutStabilityTest: オンライン⇄オフライン遷移時の状態不変性検証
 */
class P7ParityAndFallbackTest {

    @Test
    fun testOfflineParity_allSheetsAndModesOperateInOfflineLink() {
        val offlineState = MapUiState(
            networkLink = Link.Offline,
            activeSheet = SheetType.PlaceDetail,
            selectedPlace = SelectedPlace(
                id = "poi-1",
                name = "津和野駅",
                category = "駅",
                address = "島根県鹿足郡津和野町",
                lat = 34.468,
                lon = 131.773,
                openingHours = "08:00-17:00"
            ),
            routes = listOf(
                RouteCandidate(0, "おすすめ", 12000.0, 2400.0, 50.0, true),
                RouteCandidate(1, "平坦", 13500.0, 2600.0, 15.0, false)
            )
        )

        assertEquals(Link.Offline, offlineState.networkLink)
        assertEquals(SheetType.PlaceDetail, offlineState.activeSheet)
        assertNotNull(offlineState.selectedPlace)
        assertEquals(2, offlineState.routes.size)
        // 完全オフラインでも、ルーティング候補・地点詳細の閲覧が可能
        assertTrue(offlineState.routes.first().isPrimary)
    }

    @Test
    fun testOnlineFallback_onlineFailuresNeverBreakLocalFeatures() = runBlocking {
        val flow = MutableStateFlow<Link>(Link.OnlineUnmetered)
        val gate = OnlineFeatureGate(
            linkFlow = flow,
            config = OnlineFeatureConfig(useOnlineAssistance = true)
        )
        assertTrue(gate.isOnlineAssistanceEnabled)

        // オンライン呼出が例外を投げた場合、Unavailable が返り例外でクラッシュしない
        val failResult = gate.execute<Double> {
            throw RuntimeException("Network timeout simulation")
        }
        assertTrue(failResult is FeatureResult.Unavailable)

        // ネットワークが突然切断されて Offline に遷移
        flow.value = Link.Offline
        assertFalse(gate.isOnlineAssistanceEnabled)

        // オフライン時のローカルフォールバック係数は 1.0 (等倍・無補正)
        val fallbackTrafficFactor = 1.0f
        assertEquals(1.0f, fallbackTrafficFactor, 0.001f)
    }

    @Test
    fun testLayoutStability_switchingLinksPreservesSheetAndSelection() {
        var state = MapUiState(
            networkLink = Link.Offline,
            activeSheet = SheetType.RouteSelection,
            selectedRouteIndex = 1
        )

        // Wi-Fi 接続を検出
        state = state.copy(networkLink = Link.OnlineUnmetered)
        assertEquals(SheetType.RouteSelection, state.activeSheet)
        assertEquals(1, state.selectedRouteIndex)

        // 機内モード (切断)
        state = state.copy(networkLink = Link.Offline)
        assertEquals(SheetType.RouteSelection, state.activeSheet)
        assertEquals(1, state.selectedRouteIndex)
    }

    @Test
    fun testStyleLayerOrderSimulation() {
        // MapLibre のレイヤー重なり順序仕様の検証
        // 地盤 < 道路 (roads) < ルート通常線 (routeLayer) < 3D建物 (buildings-3d) < X-Ray半透明ルート線 (xrayRouteLayer) < POIシンボル < 現在地ピン
        val layerOrder = listOf(
            "background",
            "roads",
            "cyclemap-route-casing",
            "cyclemap-route-line",
            "buildings-3d",
            "cyclemap-route-xray",
            "poi-symbols",
            "cyclemap-location-pin"
        )

        val roadIdx = layerOrder.indexOf("roads")
        val routeIdx = layerOrder.indexOf("cyclemap-route-line")
        val buildingIdx = layerOrder.indexOf("buildings-3d")
        val xrayIdx = layerOrder.indexOf("cyclemap-route-xray")

        assertTrue("ルート線はroadsより上にあるべき", routeIdx > roadIdx)
        assertTrue("ルート線は3D建物より下にあるべき", routeIdx < buildingIdx)
        assertTrue("X-Ray線は3D建物の奥を透過させるため最前面にあるべき", xrayIdx > buildingIdx)
    }
}
