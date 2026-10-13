package com.gorite.cyclemap.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewModelTest {

    @Test
    fun initialStatesAreCorrect() {
        val vm = MapViewModel()
        val ui = vm.uiState.value
        assertEquals(SheetType.None, ui.activeSheet)
        assertEquals(MapLayerMode.Vector3D, ui.activeLayer)
        assertFalse(ui.isNavigating)

        val loc = vm.locationState.value
        assertFalse(loc.hasValidPosition)
    }

    @Test
    fun intentsUpdateUiStateProperly() {
        val vm = MapViewModel()

        vm.onIntent(MapUiIntent.SelectCategory("convenience"))
        assertEquals("convenience", vm.uiState.value.selectedCategory)

        vm.onIntent(MapUiIntent.OpenSheet(SheetType.Search))
        assertEquals(SheetType.Search, vm.uiState.value.activeSheet)

        vm.onIntent(MapUiIntent.CloseSheet)
        assertEquals(SheetType.None, vm.uiState.value.activeSheet)

        vm.onIntent(MapUiIntent.StartNavigation)
        assertTrue(vm.uiState.value.isNavigating)
        assertEquals(SheetType.Navigation, vm.uiState.value.activeSheet)

        vm.onIntent(MapUiIntent.StopNavigation)
        assertFalse(vm.uiState.value.isNavigating)
        assertEquals(SheetType.None, vm.uiState.value.activeSheet)
    }

    @Test
    fun locationUpdateGuardsAgainstNaNBearing() {
        val vm = MapViewModel()
        vm.updateLocation(
            lat = 34.3853,
            lon = 132.4553,
            alt = 15.0,
            speed = 4.2f,
            bearing = Float.NaN, // NaN check (BUG-79)
            accuracy = 3.0f
        )

        val loc = vm.locationState.value
        assertTrue(loc.hasValidPosition)
        assertEquals(0.0f, loc.bearingDeg, 1e-4f)
        assertEquals(34.3853, loc.latitude, 1e-6)
    }

    @Test
    fun navProgressUpdatesCorrectly() {
        val vm = MapViewModel()
        vm.updateNavProgress(
            distanceToTurn = 150.0,
            remainingDistance = 1200.0,
            remainingSec = 300.0,
            currentInst = null,
            nextInst = null,
            lanes = listOf("left", "straight")
        )

        val nav = vm.navProgressState.value
        assertEquals(150.0, nav.distanceToTurnMeters, 1e-6)
        assertEquals(1200.0, nav.remainingDistanceMeters, 1e-6)
        assertEquals(2, nav.currentLanes.size)
    }

    @Test
    fun switchLayerUpdatesMapLayerMode() {
        val vm = MapViewModel()
        assertEquals(MapLayerMode.Vector3D, vm.uiState.value.activeLayer)

        vm.onIntent(MapUiIntent.SwitchLayer(MapLayerMode.GsiStandard))
        assertEquals(MapLayerMode.GsiStandard, vm.uiState.value.activeLayer)

        vm.onIntent(MapUiIntent.SwitchLayer(MapLayerMode.OpenCycleMap))
        assertEquals(MapLayerMode.OpenCycleMap, vm.uiState.value.activeLayer)
    }

    @Test
    fun networkLinkUpdatesReflectInUiState() {
        val vm = MapViewModel()
        assertEquals(com.gorite.cyclemap.net.Link.Offline, vm.uiState.value.networkLink)

        vm.onIntent(MapUiIntent.UpdateNetworkLink(com.gorite.cyclemap.net.Link.OnlineUnmetered))
        assertEquals(com.gorite.cyclemap.net.Link.OnlineUnmetered, vm.uiState.value.networkLink)

        vm.onIntent(MapUiIntent.UpdateNetworkLink(com.gorite.cyclemap.net.Link.OnlineMetered))
        assertEquals(com.gorite.cyclemap.net.Link.OnlineMetered, vm.uiState.value.networkLink)
    }

    @Test
    fun highFrequencyLocationDoesNotPolluteLowFrequencyUiState() {
        val vm = MapViewModel()
        val initialUi = vm.uiState.value

        // 高頻度で位置情報のみが更新される
        vm.updateLocation(34.39, 132.46, 10.0, 5.0f, 180f, 2.0f)
        vm.updateLocation(34.40, 132.47, 11.0, 5.2f, 182f, 2.0f)

        // UIState は同一インスタンスのまま影響を受けない
        assertEquals(initialUi, vm.uiState.value)
        assertEquals(34.40, vm.locationState.value.latitude, 1e-6)
    }
}

