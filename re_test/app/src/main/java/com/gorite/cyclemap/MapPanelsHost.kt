package com.gorite.cyclemap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    onSelectPoiSpot: (NearbySpot) -> Unit,
    mapView: MapView?,
) {
    if (showRoutePanel) {
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
            maxGradePct = routeGradeSummary?.maxUphillPct,
            maxDownhillPct = routeGradeSummary?.maxDownhillPct,
            onSearchClick = onOpenSearchFromRoutePanel,
            onDismiss = onCloseRoutePanel,
        )
    }

    if (showSpotPanel) {
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
                onSelectPoiSpot(spot)
            },
            onRouteClick = { spot ->
                onCloseQuickSpotSheet()
                onRouteToSpot(GeoPoint(spot.latitude, spot.longitude))
            },
            onDismiss = onCloseQuickSpotSheet,
        )
    }
}
