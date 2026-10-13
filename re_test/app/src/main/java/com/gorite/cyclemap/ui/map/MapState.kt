package com.gorite.cyclemap.ui.map

import androidx.lifecycle.ViewModel
import com.gorite.cyclemap.net.Link
import com.gorite.cyclemap.routing.RouteInstruction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SheetType {
    None,
    Search,
    PlaceDetail,
    RouteSelection,
    Navigation,
    Settings,
    RideHistory
}

enum class MapLayerMode {
    Vector3D,
    Vector2D,
    GsiStandard,
    OpenCycleMap,
    Hillshade
}

data class SelectedPlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val openingHours: String? = null,
    val phone: String? = null,
    val website: String? = null
)

data class RouteCandidate(
    val index: Int,
    val title: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val elevationGainMeters: Double,
    val isPrimary: Boolean = false
)

/**
 * 低頻度UI状態: 画面全体のレイアウトやシートの開閉、選択対象など
 */
data class MapUiState(
    val activeSheet: SheetType = SheetType.None,
    val activeLayer: MapLayerMode = MapLayerMode.Vector3D,
    val selectedCategory: String? = null,
    val selectedPlace: SelectedPlace? = null,
    val selectedRouteIndex: Int = 0,
    val routes: List<RouteCandidate> = emptyList(),
    val isNightMode: Boolean = false,
    val isGpsWeak: Boolean = false,
    val networkLink: Link = Link.Offline,
    val isMuted: Boolean = false,
    val isNavigating: Boolean = false
)

/**
 * 高頻度位置情報状態: GPS 1Hz / コンパス 10-50Hz
 * MapScreen 全体を再コンポーズさせず、現在地ピンや方位針でのみ購読する
 */
data class LocationUiState(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f,
    val bearingDeg: Float = 0f,
    val accuracyMeters: Float = 0f,
    val hasValidPosition: Boolean = false
)

/**
 * ナビゲーション進捗状態: 走行中の案内更新
 */
data class NavProgressState(
    val distanceToTurnMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingSeconds: Double = 0.0,
    val currentInstruction: RouteInstruction? = null,
    val nextInstruction: RouteInstruction? = null,
    val currentLanes: List<String> = emptyList(),
    val isRerouting: Boolean = false
)

sealed interface MapUiIntent {
    data class SelectCategory(val category: String?) : MapUiIntent
    data class SelectPlace(val place: SelectedPlace?) : MapUiIntent
    data class OpenSheet(val sheet: SheetType) : MapUiIntent
    data object CloseSheet : MapUiIntent
    data class SwitchLayer(val mode: MapLayerMode) : MapUiIntent
    data class SelectRoute(val index: Int) : MapUiIntent
    data object ToggleMute : MapUiIntent
    data class SetNightMode(val isNight: Boolean) : MapUiIntent
    data class UpdateNetworkLink(val link: Link) : MapUiIntent
    data object StartNavigation : MapUiIntent
    data object StopNavigation : MapUiIntent
}

class MapViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _locationState = MutableStateFlow(LocationUiState())
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()

    private val _navProgressState = MutableStateFlow(NavProgressState())
    val navProgressState: StateFlow<NavProgressState> = _navProgressState.asStateFlow()

    fun onIntent(intent: MapUiIntent) {
        when (intent) {
            is MapUiIntent.SelectCategory -> _uiState.update { it.copy(selectedCategory = intent.category) }
            is MapUiIntent.SelectPlace -> _uiState.update {
                it.copy(
                    selectedPlace = intent.place,
                    activeSheet = if (intent.place != null) SheetType.PlaceDetail else it.activeSheet
                )
            }
            is MapUiIntent.OpenSheet -> _uiState.update { it.copy(activeSheet = intent.sheet) }
            is MapUiIntent.CloseSheet -> _uiState.update { it.copy(activeSheet = SheetType.None) }
            is MapUiIntent.SwitchLayer -> _uiState.update { it.copy(activeLayer = intent.mode) }
            is MapUiIntent.SelectRoute -> _uiState.update { it.copy(selectedRouteIndex = intent.index) }
            is MapUiIntent.ToggleMute -> _uiState.update { it.copy(isMuted = !it.isMuted) }
            is MapUiIntent.SetNightMode -> _uiState.update { it.copy(isNightMode = intent.isNight) }
            is MapUiIntent.UpdateNetworkLink -> _uiState.update { it.copy(networkLink = intent.link) }
            is MapUiIntent.StartNavigation -> _uiState.update {
                it.copy(isNavigating = true, activeSheet = SheetType.Navigation)
            }
            is MapUiIntent.StopNavigation -> _uiState.update {
                it.copy(isNavigating = false, activeSheet = SheetType.None)
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double, alt: Double, speed: Float, bearing: Float, accuracy: Float) {
        // BUG-79: 方位 NaN ガード
        val safeBearing = if (bearing.isNaN()) 0f else bearing
        _locationState.update {
            it.copy(
                latitude = lat,
                longitude = lon,
                altitudeMeters = alt,
                speedMps = speed,
                bearingDeg = safeBearing,
                accuracyMeters = accuracy,
                hasValidPosition = true
            )
        }
    }

    fun updateNavProgress(
        distanceToTurn: Double,
        remainingDistance: Double,
        remainingSec: Double,
        currentInst: RouteInstruction?,
        nextInst: RouteInstruction?,
        lanes: List<String> = emptyList(),
        isRerouting: Boolean = false
    ) {
        _navProgressState.update {
            it.copy(
                distanceToTurnMeters = distanceToTurn,
                remainingDistanceMeters = remainingDistance,
                remainingSeconds = remainingSec,
                currentInstruction = currentInst,
                nextInstruction = nextInst,
                currentLanes = lanes,
                isRerouting = isRerouting
            )
        }
    }
}
