package com.gorite.cyclemap

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.gorite.cyclemap.data.AddressDisplayController
import com.gorite.cyclemap.data.SearchDbReverseGeocoder
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.tracking.AppLifecycleState
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale

@Composable
internal fun MapLifecycleEffects(
    mapView: MapView?,
    vectorMap: MapLibreMap?,
    lifecycleOwner: LifecycleOwner,
    context: Context,
    currentLocation: Location?,
    followLocation: Boolean,
    locationMarker: Marker?,
    accuracyCircle: AccuracyCircleOverlay?,
    appLifecycleState: AppLifecycleState,
    effectiveAnimationEnabled: Boolean,
    navArrowCache: NavArrowCache,
    onUpdateNavArrow: () -> Unit,
    poiVisible: Boolean,
    poiSelected: Set<PoiCategory>,
    poiRefreshTick: Int,
    isNavigationActive: Boolean,
    onSelectPoiSpot: (NearbySpot) -> Unit,
    addressController: AddressDisplayController,
    onLocationLabelChanged: (String) -> Unit,
    isHeadingUp: Boolean,
    headingDegrees: Float,
    orientationAnimator: MapOrientationAnimator,
    selectedLayer: MapLayer,
) {
    // Location Animation & Map Centering
    LaunchedEffect(currentLocation, mapView, followLocation, locationMarker, accuracyCircle, appLifecycleState, effectiveAnimationEnabled) {
        val targetLocation = currentLocation ?: return@LaunchedEffect
        val marker = locationMarker ?: return@LaunchedEffect
        val view = mapView ?: return@LaunchedEffect
        val target = GeoPoint(targetLocation.latitude, targetLocation.longitude)
        // タップで座標・精度を確認できる (通常時はドットのみで情報を詰め込まない)
        marker.snippet = "%.5f, %.5f".format(Locale.US, targetLocation.latitude, targetLocation.longitude) +
            (if (targetLocation.hasAccuracy()) " (±${targetLocation.accuracy.toInt()}m)" else "")
        if (targetLocation.hasAccuracy()) {
            accuracyCircle?.update(target, targetLocation.accuracy)
        } else {
            accuracyCircle?.isEnabled = false
        }
        // 矢印の向き・表示切替は位置アニメーション(900msループ)と分離して1回だけ更新する。
        navArrowCache.onFix(
            targetLocation.latitude,
            targetLocation.longitude,
            if (targetLocation.hasAccuracy()) targetLocation.accuracy else Float.NaN,
            SystemClock.elapsedRealtime(),
        )
        onUpdateNavArrow()

        // アプリが前面 (RESUMED) でないとき、およびアニメーション無効時は即時スナップ (GPU/CPU浪費防止)
        val shouldAnimate = appLifecycleState == AppLifecycleState.RESUMED && effectiveAnimationEnabled
        if (!shouldAnimate) {
            marker.position = target
            if (followLocation) {
                view.controller.setCenter(target)
            }
            view.invalidate()
            return@LaunchedEffect
        }

        val start = marker.position
        val durationNanos = 900_000_000L
        val minFrameIntervalNanos = 33_333_333L // 最大30fpsに間引き、120Hz/60Hzの毎フレーム全画面再描画を抑止
        val startTime = withFrameNanos { it }
        var lastRenderNanos = startTime
        while (true) {
            val frameTime = withFrameNanos { it }
            val elapsed = frameTime - startTime
            val value = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
            val isFinal = value >= 1.0

            if (isFinal || (frameTime - lastRenderNanos) >= minFrameIntervalNanos) {
                val point = GeoPoint(
                    start.latitude + (target.latitude - start.latitude) * value,
                    start.longitude + (target.longitude - start.longitude) * value,
                )
                marker.position = point
                if (followLocation) {
                    view.controller.setCenter(point)
                }
                view.invalidate()
                lastRenderNanos = frameTime
            }
            if (isFinal) break
        }
    }

    // POIオーバーレイ (オフラインDB・選択カテゴリ・ズーム別ルール・件数上限)。
    LaunchedEffect(poiVisible, poiSelected, poiRefreshTick, currentLocation, mapView, isNavigationActive) {
        val view = mapView ?: return@LaunchedEffect
        if (!poiVisible || poiSelected.isEmpty() || isNavigationActive) {
            view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
            view.invalidate()
            return@LaunchedEffect
        }
        delay(500L)
        val zoom = view.zoomLevelDouble
        // z14未満はゴチャつき防止で非表示
        if (zoom < 14.0) {
            view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
            view.invalidate()
            return@LaunchedEffect
        }
        val center = view.mapCenter as? GeoPoint ?: return@LaunchedEffect
        // ズームアウト時は重要POI (駅・観光) のみ、件数も絞る
        val zoomedOut = zoom < 15.0
        val activeCategories = if (zoomedOut) poiSelected.filter { it.important }.toSet() else poiSelected
        if (activeCategories.isEmpty()) {
            view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
            view.invalidate()
            return@LaunchedEffect
        }
        val radius = if (zoom >= 16.0) 800.0 else if (zoom >= 15.0) 1500.0 else 3000.0
        val limit = if (zoomedOut) 15 else 40
        val dbSelection = SearchDbSelector.select(center.latitude, center.longitude)
        val dataDir = cycleMapDataDir(context)
        val dbFile = when (dbSelection) {
            is SearchDbSelection.Available -> SearchDbSelector.resolveDbFile(dataDir, dbSelection.fileName)
            is SearchDbSelection.Unavailable -> null
        }
        val pois = if (dbFile != null && dbFile.isFile) {
            withContext(Dispatchers.IO) {
                // バス停 (highway:bus_stop) の密集で画面が圧迫されるのを防ぎ、駅 (railway:station) を優先
                val rawList = searchNearbyPlaces(dbFile, center.latitude, center.longitude, radius, limit = limit * 4)
                    .mapNotNull { spot ->
                        if (spot.category.startsWith("highway:bus_stop") && zoom < 16.5) return@mapNotNull null
                        val cat = PoiCategory.forCategory(spot.category) ?: return@mapNotNull null
                        if (cat !in activeCategories) null else spot to cat
                    }
                    .sortedWith(
                        // 駅・重要スポットを最優先し、同一重要度内では距離順
                        compareByDescending<Pair<NearbySpot, PoiCategory>> { (_, cat) -> cat.important }
                            .thenBy { (spot, _) -> spot.distanceM },
                    )
                rawList
            }
        } else {
            emptyList()
        }
        view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
        val showLabel = zoom >= 15.0
        val density = context.resources.displayMetrics.density
        val projection = view.projection ?: return@LaunchedEffect
        val screenBounds = Rect(0, 0, view.width.coerceAtLeast(1080), view.height.coerceAtLeast(1920))
        val placedRects = ArrayList<Rect>()
        val point = Point()

        for ((poi, cat) in pois) {
            if (placedRects.size >= limit) break
            val geo = GeoPoint(poi.latitude, poi.longitude)
            projection.toPixels(geo, point)

            // 画面外はスキップ
            if (!screenBounds.contains(point.x, point.y)) continue

            val visual = googlePoiMarker(context, cat, poi.name, showLabel = showLabel)
            val w = visual.drawable.intrinsicWidth
            val h = visual.drawable.intrinsicHeight
            val left = (point.x - w * visual.anchorU).toInt()
            val top = (point.y - h * visual.anchorV).toInt()
            val right = left + w
            val bottom = top + h
            // Googleマップ風の適度な余白 (マージン) を持った衝突判定矩形
            val margin = (5f * density).toInt()
            val poiRect = Rect(left - margin, top - margin, right + margin, bottom + margin)

            // 画面空間衝突判定 (Screen-Space Collision Detection): 既に配置されたPOIと重なる場合はスキップ
            val collides = placedRects.any { Rect.intersects(it, poiRect) }
            if (collides) continue

            placedRects.add(poiRect)
            val marker = Marker(view).apply {
                title = "POI:${poi.name}"
                position = geo
                icon = visual.drawable
                setAnchor(visual.anchorU, visual.anchorV)
                infoWindow = null // osmdroid 標準吹き出しを完全に無効化
                setOnMarkerClickListener { _, _ ->
                    val dist = currentLocation?.let { loc ->
                        haversineMeters(loc.latitude, loc.longitude, poi.latitude, poi.longitude)
                    } ?: 0.0
                    onSelectPoiSpot(
                        NearbySpot(
                            name = poi.name,
                            category = cat.name.lowercase(),
                            latitude = poi.latitude,
                            longitude = poi.longitude,
                            distanceM = dist,
                        ),
                    )
                    true
                }
            }
            view.overlays.add(marker)
        }
        view.invalidate()
    }

    // 上部住所表示 (オフライン逆ジオコーディング。GPS実測のみ・debounce付き)。
    LaunchedEffect(currentLocation, mapView) {
        delay(1_000L)
        val location = currentLocation
        if (location == null) {
            onLocationLabelChanged(AddressDisplayController.ADDRESS_LOADING)
            return@LaunchedEffect
        }
        val dbSelection = SearchDbSelector.select(location.latitude, location.longitude)
        val dbFile = when (dbSelection) {
            is SearchDbSelection.Available -> File(cycleMapDataDir(context), dbSelection.fileName)
            is SearchDbSelection.Unavailable -> null
        }
        if (dbSelection is SearchDbSelection.Available) {
            Log.i(
                "CycleMapGeo",
                "GEO_DB location=${location.latitude},${location.longitude} " +
                    "prefecture=${dbSelection.prefectureName} file=${dbSelection.fileName} exists=${dbFile?.isFile}",
            )
        } else {
            val unsupportedName = (dbSelection as? SearchDbSelection.Unavailable)?.prefectureName
            Log.w(
                "CycleMapGeo",
                "GEO_DB location=${location.latitude},${location.longitude} " +
                    "reason=unsupported prefecture=${unsupportedName ?: "(unknown)"}",
            )
        }
        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.NaN
        val prefectureName = (dbSelection as? SearchDbSelection.Available)?.prefectureName
        val resolvedLabel = withContext(Dispatchers.IO) {
            if (dbFile != null && dbFile.isFile && prefectureName != null) {
                val geocoder = SearchDbReverseGeocoder(dbFile, prefectureName)
                addressController.update(location.latitude, location.longitude, accuracy, geocoder)
            } else {
                AddressDisplayController.ADDRESS_UNKNOWN
            }
        }
        onLocationLabelChanged(resolvedLabel)
    }

    // Compass Orientation: 端末コンパスは地図回転にだけ使う。矢印の向きには使わない。
    LaunchedEffect(isHeadingUp, mapView, vectorMap) {
        if (isHeadingUp) {
            orientationAnimator.rotateTo(headingDegrees)
        } else {
            orientationAnimator.reset()
        }
    }

    // Tile Source Switching
    LaunchedEffect(selectedLayer, mapView) {
        mapView?.let { view ->
            val source = when (selectedLayer) {
                MapLayer.VECTOR_LIBERTY -> gsiTileSource()
                MapLayer.GSI -> gsiTileSource()
                MapLayer.OSM -> osmTileSource()
                MapLayer.TERRAIN -> gsiReliefTileSource()
            }
            view.setTileSource(source)
            view.minZoomLevel = source.minimumZoomLevel.toDouble()
            view.maxZoomLevel = source.maximumZoomLevel.toDouble()
            view.tileProvider.ensureCapacity(512)
            // 全レイヤーで等倍表示 (DPI拡大は256pxタイルがぼやけるため使わない。GSIと同様)。
            view.setTilesScaledToDpi(false)
            view.overlayManager.tilesOverlay.apply {
                loadingBackgroundColor = android.graphics.Color.parseColor(
                    if (selectedLayer == MapLayer.GSI) "#F4F1EA" else "#E7EDF2",
                )
                loadingLineColor = android.graphics.Color.parseColor("#C5CBD3")
            }
            if (view.zoomLevelDouble > view.maxZoomLevel) {
                view.controller.setZoom(view.maxZoomLevel)
            } else if (view.zoomLevelDouble < view.minZoomLevel) {
                view.controller.setZoom(view.minZoomLevel)
            }
            view.invalidate()
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val view = mapView ?: return@DisposableEffect onDispose { }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            view.onResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> view.onPause()
                Lifecycle.Event.ON_DESTROY -> {
                    view.onPause()
                    if ((context as? android.app.Activity)?.isFinishing == true) {
                        view.onDetach()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
        }
    }
}
