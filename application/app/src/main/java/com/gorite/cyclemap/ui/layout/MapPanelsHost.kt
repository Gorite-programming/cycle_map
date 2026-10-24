package com.gorite.cyclemap.ui.layout

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import com.gorite.cyclemap.navigation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.gorite.cyclemap.data.FavoriteSpot
import com.gorite.cyclemap.data.FavoritesManager
import com.gorite.cyclemap.ui.mapview.resolveSearchDbFile
import com.gorite.cyclemap.routing.NavigationStats
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.ui.cycling.CyclingTab
import com.gorite.cyclemap.ui.cycling.ElevationSample
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.QuickSpotBottomSheet
import com.gorite.cyclemap.ui.cycling.QuickSpotType
import com.gorite.cyclemap.ui.cycling.RoutePanelSheet
import com.gorite.cyclemap.ui.cycling.SpotPanelSheet
import com.gorite.cyclemap.ui.cycling.SpotQuickCategory
import com.gorite.cyclemap.ui.cycling.buildRouteElevationProfile
import com.gorite.cyclemap.ui.cycling.elevationGain
import com.gorite.cyclemap.ui.cycling.elevationSourceLabel
import com.gorite.cyclemap.ui.cycling.gradeSummary
import com.gorite.cyclemap.ui.cycling.mockElevationProfile
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
internal fun MapPanelsHost(
    showRoutePanel: Boolean,
    onCloseRoutePanel: () -> Unit,
    realElevProfile: List<ElevationSample>,
    routeSummary: RouteSummary?,
    navigationRoute: List<RoutePoint>,
    navStats: NavigationStats?,
    onOpenSearchFromRoutePanel: () -> Unit,
    showSpotPanel: Boolean,
    onCloseSpotPanel: () -> Unit,
    spotCategory: SpotQuickCategory?,
    onSpotCategoryChange: (SpotQuickCategory) -> Unit,
    spotLoading: Boolean,
    spotMessage: String?,
    spotResults: List<NearbySpot>,
    onQuickSpotTypeSelectFromSpotPanel: (QuickSpotType) -> Unit,
    onRouteToSpot: (GeoPoint) -> Unit,
    showFavoritesOnlyInSheet: Boolean,
    showQuickSpotSheet: Boolean,
    onCloseQuickSpotSheet: () -> Unit,
    activeQuickSpotType: QuickSpotType?,
    onActiveQuickSpotTypeChange: (QuickSpotType) -> Unit,
    quickSpotLoading: Boolean,
    quickSpotMessage: String?,
    quickSpotResults: List<NearbySpot>,
    onSelectPoiSpot: (NearbySpot?) -> Unit,
    mapView: MapView?,
    vectorMap: org.maplibre.android.maps.MapLibreMap? = null,
    calculatedRoutes: List<com.gorite.cyclemap.contract.RouteOption> = emptyList(),
    selectedPoiSpot: NearbySpot? = null,
    selectedRouteIndex: Int = 0,
    onSelectRouteIndex: (Int) -> Unit = {},
    selectedTravelMode: com.gorite.cyclemap.contract.TravelModeType = com.gorite.cyclemap.contract.TravelModeType.Bicycle,
    onSelectTravelMode: (com.gorite.cyclemap.contract.TravelModeType) -> Unit = {},
    onStartNavigation: () -> Unit = {},
) {
    if (showRoutePanel && calculatedRoutes.isEmpty() && routeSummary == null) {
        val routeProfile = if (realElevProfile.isNotEmpty()) realElevProfile else remember(routeSummary, navigationRoute) {
            routeSummary?.let {
                if (navigationRoute.isNotEmpty()) buildRouteElevationProfile(navigationRoute.map { pt -> pt.latitude to pt.longitude }, it.distanceMeters)
                else mockElevationProfile(it.distanceMeters)
            } ?: emptyList()
        }
        val routeGradeSummary = remember(routeProfile) {
            routeProfile.takeIf { it.size >= 2 }?.let { gradeSummary(it) }
        }
        RoutePanelSheet(
            hasRoute = routeSummary != null,
            totalDistanceM = routeSummary?.distanceMeters,
            remainingText = navStats?.let { formatRemaining(it.remainingMeters) }
                ?: routeSummary?.let { formatRemaining(it.distanceMeters) } ?: "--",
            durationText = navStats?.let { formatDuration(it.durationRemainingSeconds) } ?: "--",
            gainM = routeProfile.takeIf { it.size >= 2 }?.let {
                elevationGain(it, routeSummary?.distanceMeters ?: 0.0)?.toInt()
            },
            elevationSource = elevationSourceLabel(routeProfile),
            maxGradePct = routeGradeSummary?.maxUphillPct,
            maxDownhillPct = routeGradeSummary?.maxDownhillPct,
            onSearchClick = onOpenSearchFromRoutePanel,
            onDismiss = onCloseRoutePanel,
        )
    }

    // 契約 PlaceDetail 3段階アンカーボトムシート (Peek120 / Half320 / Full560)
    var sheetAnchor by androidx.compose.runtime.remember(selectedPoiSpot) {
        androidx.compose.runtime.mutableStateOf(com.gorite.cyclemap.ui.map.BottomSheetAnchor.Half)
    }

    var placeDetail by androidx.compose.runtime.remember(selectedPoiSpot) {
        val initial = selectedPoiSpot?.let { spot ->
            val catLabel = com.gorite.cyclemap.ui.cycling.PoiCategory.forCategory(spot.category)?.label ?: spot.category
            com.gorite.cyclemap.contract.PlaceDetail(
                id = "${spot.name}_${spot.latitude}_${spot.longitude}",
                name = spot.name,
                category = catLabel,
                address = "",
                distanceMeters = spot.distanceM,
                lat = spot.latitude,
                lon = spot.longitude,
                businessStatus = com.gorite.cyclemap.contract.BusinessStatus.Unknown
            )
        }
        androidx.compose.runtime.mutableStateOf(initial)
    }

    val localContext = androidx.compose.ui.platform.LocalContext.current
    val favoritesManager = remember {
        FavoritesManager.getInstance(localContext)
    }
    val favoritesList by favoritesManager.favorites.collectAsState()

    androidx.compose.runtime.LaunchedEffect(selectedPoiSpot) {
        val spot = selectedPoiSpot ?: return@LaunchedEffect
        val provider = com.gorite.cyclemap.real.RealPlaceDetailProvider(
            searchDbFileProvider = {
                resolveSearchDbFile(localContext, spot.latitude, spot.longitude)
            }
        )
        val loaded = provider.getPlaceDetail(spot.name)
        val catLabel = com.gorite.cyclemap.ui.cycling.PoiCategory.forCategory(spot.category)?.label ?: spot.category
        val loadedDist = loaded?.distanceMeters
        if (loaded != null) {
            placeDetail = loaded.copy(
                category = com.gorite.cyclemap.ui.cycling.PoiCategory.forCategory(loaded.category)?.label ?: catLabel,
                distanceMeters = if (loadedDist != null && loadedDist > 0.0) loadedDist else spot.distanceM,
                lat = spot.latitude,
                lon = spot.longitude,
            )
        }
    }

    if (showSpotPanel && selectedPoiSpot == null && placeDetail == null) {
        SpotPanelSheet(
            selectedCategory = spotCategory,
            onCategorySelect = onSpotCategoryChange,
            isLoading = spotLoading,
            spots = spotResults,
            message = spotMessage,
            onSpotClick = { spot ->
                onCloseSpotPanel()
                onRouteToSpot(GeoPoint(spot.latitude, spot.longitude))
            },
            onQuickSpotTypeSelect = onQuickSpotTypeSelectFromSpotPanel,
            onRouteToSpot = { spot ->
                onCloseSpotPanel()
                onRouteToSpot(GeoPoint(spot.latitude, spot.longitude))
            },
            onDismiss = onCloseSpotPanel,
            initialShowFavoritesOnly = showFavoritesOnlyInSheet,
        )
    }

    if (showQuickSpotSheet && activeQuickSpotType != null) {
        QuickSpotBottomSheet(
            selectedType = activeQuickSpotType,
            onSelectType = onActiveQuickSpotTypeChange,
            spots = quickSpotResults,
            isLoading = quickSpotLoading,
            errorMessage = quickSpotMessage,
            onSpotClick = { spot ->
                onCloseQuickSpotSheet()
                mapView?.controller?.animateTo(GeoPoint(spot.latitude, spot.longitude))
                vectorMap?.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLng(
                        org.maplibre.android.geometry.LatLng(spot.latitude, spot.longitude)
                    )
                )
                onSelectPoiSpot(spot)
            },
            onRouteClick = { spot ->
                onCloseQuickSpotSheet()
                onRouteToSpot(GeoPoint(spot.latitude, spot.longitude))
            },
            onDismiss = onCloseQuickSpotSheet,
        )
    }

    if (showSpotPanel && placeDetail != null) {
        val currentDetail = placeDetail!!
        val isFav = favoritesList.any {
            kotlin.math.abs(it.latitude - currentDetail.lat) < 0.0001 &&
                kotlin.math.abs(it.longitude - currentDetail.lon) < 0.0001
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(12f),
            contentAlignment = Alignment.BottomCenter
        ) {
            com.gorite.cyclemap.ui.map.ThreeStageBottomSheet(
                anchor = sheetAnchor,
                onAnchorChange = { sheetAnchor = it }
            ) {
                com.gorite.cyclemap.ui.map.PlaceDetailContent(
                    detail = currentDetail,
                    isFavorite = isFav,
                    onToggleFavorite = {
                        val existing = favoritesList.firstOrNull {
                            kotlin.math.abs(it.latitude - currentDetail.lat) < 0.0001 &&
                                kotlin.math.abs(it.longitude - currentDetail.lon) < 0.0001
                        }
                        if (existing != null) {
                            favoritesManager.removeFavorite(existing.id)
                        } else {
                            favoritesManager.addFavorite(
                                FavoriteSpot(
                                    id = currentDetail.id,
                                    name = currentDetail.name,
                                    category = selectedPoiSpot?.category ?: currentDetail.category,
                                    latitude = currentDetail.lat,
                                    longitude = currentDetail.lon,
                                    address = currentDetail.address.takeIf { it.isNotBlank() },
                                )
                            )
                        }
                    },
                    onStartRoute = {
                        placeDetail = null
                        onSelectPoiSpot(null)
                        onCloseSpotPanel()
                        onRouteToSpot(GeoPoint(currentDetail.lat, currentDetail.lon))
                    },
                    onClose = {
                        placeDetail = null
                        onSelectPoiSpot(null)
                        onCloseSpotPanel()
                    }
                )
            }
        }
    }

    // 複数経路選択シート (RouteSelectionSheetV2)
    val routes = if (calculatedRoutes.isNotEmpty()) {
        calculatedRoutes
    } else {
        listOfNotNull(
            routeSummary?.let {
                com.gorite.cyclemap.contract.RouteOption(
                    id = "route_primary",
                    title = "主要サイクリングルート (推奨)",
                    travelMode = com.gorite.cyclemap.contract.TravelModeType.Bicycle,
                    distanceMeters = it.distanceMeters,
                    durationSeconds = it.distanceMeters / 4.2,
                    elevationGainMeters = 0.0,
                    isPrimary = true
                )
            }
        )
    }

    if (showRoutePanel && routes.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(12f),
            contentAlignment = Alignment.BottomCenter
        ) {
            com.gorite.cyclemap.ui.map.RouteSelectionSheet(
                routes = routes,
                selectedRouteIndex = selectedRouteIndex.coerceIn(0, routes.lastIndex),
                selectedMode = selectedTravelMode,
                onSelectMode = onSelectTravelMode,
                onSelectRoute = onSelectRouteIndex,
                onStartNavigation = {
                    onCloseRoutePanel()
                    onStartNavigation()
                },
                onClose = onCloseRoutePanel
            )
        }
    }
}
