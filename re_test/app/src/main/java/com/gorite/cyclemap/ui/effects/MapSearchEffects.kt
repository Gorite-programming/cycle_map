package com.gorite.cyclemap.ui.effects

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import com.gorite.cyclemap.navigation.*
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.search.*
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.QuickSpotType
import com.gorite.cyclemap.ui.cycling.SpotQuickCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
internal fun MapSearchEffects(
    spotCategory: SpotQuickCategory?,
    showSpotPanel: Boolean,
    currentLocation: Location?,
    mapView: MapView?,
    context: Context,
    onSpotLoadingChange: (Boolean) -> Unit,
    onSpotMessageChange: (String?) -> Unit,
    onSpotResultsChange: (List<NearbySpot>) -> Unit,
    activeQuickSpotType: QuickSpotType?,
    showQuickSpotSheet: Boolean,
    onQuickSpotLoadingChange: (Boolean) -> Unit,
    onQuickSpotMessageChange: (String?) -> Unit,
    onQuickSpotResultsChange: (List<NearbySpot>) -> Unit,
    selectedPoiSpot: NearbySpot?,
) {
    // スポット検索 (カテゴリ選択→周辺のオフラインDB検索)
    LaunchedEffect(spotCategory, showSpotPanel) {
        val cat = spotCategory
        if (!showSpotPanel || cat == null) return@LaunchedEffect
        onSpotLoadingChange(true)
        onSpotMessageChange(null)
        val center = currentLocation?.let { it.latitude to it.longitude }
            ?: (mapView?.mapCenter as? GeoPoint)?.let { it.latitude to it.longitude }
        if (center == null) {
            onSpotLoadingChange(false)
            onSpotMessageChange("現在地が取得できません")
            return@LaunchedEffect
        }
        val prefixes = cat.poi.prefixes
        val dbSelection = SearchDbSelector.select(center.first, center.second)
        val dataDir = cycleMapDataDir(context)
        val dbFile = when (dbSelection) {
            is SearchDbSelection.Available -> SearchDbSelector.resolveDbFile(dataDir, dbSelection.fileName)
            is SearchDbSelection.Unavailable -> null
        }
        if (dbSelection is SearchDbSelection.Available) {
            Log.i(
                "CycleMapGeo",
                "POI_DB panel prefecture=${dbSelection.prefectureName} file=${dbSelection.fileName} exists=${dbFile?.isFile}",
            )
        }
        val found = if (dbFile != null && dbFile.isFile) {
            withContext(Dispatchers.IO) {
                searchNearbyPlaces(dbFile, center.first, center.second, 3000.0, limit = 60)
                    .filter { spot -> prefixes.any { spot.category.startsWith(it) } }
                    .take(30)
            }
        } else {
            emptyList()
        }
        onSpotResultsChange(found)
        onSpotLoadingChange(false)
        onSpotMessageChange(
            if (dbFile == null || !dbFile.isFile) {
                "検索DBが見つかりません"
            } else if (found.isEmpty()) {
                "周辺に${cat.label}が見つかりませんでした"
            } else {
                null
            },
        )
    }

    // クイックスポット検索 (QuickSpotType選択→周辺のオフラインDB検索)
    LaunchedEffect(activeQuickSpotType, showQuickSpotSheet) {
        val type = activeQuickSpotType
        if (!showQuickSpotSheet || type == null) return@LaunchedEffect
        onQuickSpotLoadingChange(true)
        onQuickSpotResultsChange(emptyList())
        onQuickSpotMessageChange(null)
        val center = currentLocation?.let { it.latitude to it.longitude }
            ?: (mapView?.mapCenter as? GeoPoint)?.let { it.latitude to it.longitude }
        if (center == null) {
            onQuickSpotLoadingChange(false)
            onQuickSpotMessageChange("現在地が取得できません")
            return@LaunchedEffect
        }
        val dbSelection = SearchDbSelector.select(center.first, center.second)
        val dataDir = cycleMapDataDir(context)
        val dbFile = when (dbSelection) {
            is SearchDbSelection.Available -> SearchDbSelector.resolveDbFile(dataDir, dbSelection.fileName)
            is SearchDbSelection.Unavailable -> null
        }
        if (dbSelection is SearchDbSelection.Available) {
            Log.i(
                "CycleMapGeo",
                "QUICK_SPOT_DB prefecture=${dbSelection.prefectureName} file=${dbSelection.fileName} type=${type.name}",
            )
        }
        val found = if (dbFile != null && dbFile.isFile) {
            withContext(Dispatchers.IO) {
                searchNearbyQuickSpots(
                    dbFile = dbFile,
                    centerLat = center.first,
                    centerLon = center.second,
                    radiusMeters = 8000.0,
                    categoryKeys = type.categoryKeys,
                    nameFilter = type.nameFilter,
                    limit = 30,
                )
            }
        } else {
            emptyList()
        }
        onQuickSpotResultsChange(found)
        onQuickSpotLoadingChange(false)
        onQuickSpotMessageChange(
            if (dbFile == null || !dbFile.isFile) {
                "検索DBが見つかりません"
            } else if (found.isEmpty()) {
                "周辺に${type.label}が見つかりませんでした"
            } else {
                null
            },
        )
    }

    // クイックスポットやPOI選択時のハイライトピン描画
    LaunchedEffect(selectedPoiSpot, mapView) {
        val view = mapView ?: return@LaunchedEffect
        view.overlays.removeAll { it is Marker && it.title == "SELECTED_POI_HIGHLIGHT" }
        val spot = selectedPoiSpot
        if (spot != null) {
            val marker = Marker(view).apply {
                title = "SELECTED_POI_HIGHLIGHT"
                position = GeoPoint(spot.latitude, spot.longitude)
                icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_lucide_map_pin)?.mutate()?.apply {
                    setTint(android.graphics.Color.parseColor("#E91E63"))
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                infoWindow = null
            }
            view.overlays.add(marker)
        }
        view.invalidate()
    }
}
