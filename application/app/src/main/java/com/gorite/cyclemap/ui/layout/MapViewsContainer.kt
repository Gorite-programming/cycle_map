package com.gorite.cyclemap.ui.layout

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import android.graphics.drawable.Drawable
import android.location.Location
import com.gorite.cyclemap.ui.mapview.VectorMapView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.gorite.cyclemap.data.TileSourceSelection
import com.gorite.cyclemap.data.TileSourceSelector
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
internal fun MapViewsContainer(
    modifier: Modifier = Modifier,
    mapView: org.osmdroid.views.MapView?,
    vectorMap: MapLibreMap? = null,
    selectedLayer: MapLayer,
    isAreaSelectMode: Boolean,
    areaDragState: AreaDragState,
    onAreaDragStateChange: (AreaDragState) -> Unit,
    onAreaSelected: (BoundingBox) -> Unit,
    navigationRoute: List<RoutePoint>,
    alternativeRoutes: List<List<GeoPoint>> = emptyList(),
    destination: GeoPoint? = null,
    currentLocation: Location?,
    hasHeadingSensorFix: Boolean,
    headingDegrees: Float,
    isDarkTheme: Boolean,
    isLowPowerMode: Boolean,
    selectedPoiSpot: NearbySpot?,
    followLocation: Boolean,
    isNavigationActive: Boolean,
    gpsStatus: GpsSignalStatus = GpsSignalStatus.HEALTHY,
    poiVisible: Boolean,
    poiSelected: Set<PoiCategory>,
    isLandscape: Boolean,
    systemInsets: PaddingValues,
    navDotDrawable: Drawable?,
    onMapCreated: (CycleMapView, Marker, AccuracyCircleOverlay) -> Unit,
    onMapDestroyed: () -> Unit = {},
    onUserPan: () -> Unit,
    onPoiRefresh: () -> Unit,
    onZoomLevelChanged: (Double) -> Unit,
    onPoiSpotDeselect: () -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    onTiltChanged: (Double) -> Unit,
    onBearingChanged: (Double) -> Unit = {},
    onPoiClick: (NearbySpot) -> Unit,
    onVectorMapReady: (MapLibreMap) -> Unit,
) {
    Box(modifier = modifier) {
        // Raster MapView (osmdroid): ベクター単体表示時は裏で常駐させず、ラスター選択時または範囲指定DL時のみマウント
        if (selectedLayer != MapLayer.VECTOR_LIBERTY || isAreaSelectMode) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapContext ->
                    Configuration.getInstance().userAgentValue = mapContext.packageName
                    configureOsmdroid(mapContext, cycleMapDataDir(mapContext))
                    CycleMapView(mapContext).apply {
                        isClickable = true
                        isFocusable = true
                        isNestedScrollingEnabled = false
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        setMultiTouchControls(true)
                        @Suppress("DEPRECATION")
                        setBuiltInZoomControls(false)
                        setTilesScaledToDpi(true)
                        minZoomLevel = 2.0
                        maxZoomLevel = 21.0
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        isFlingEnabled = true
                        overlayManager.tilesOverlay.apply {
                            loadingBackgroundColor = android.graphics.Color.parseColor("#F4F1EA")
                            loadingLineColor = android.graphics.Color.parseColor("#C5CBD3")
                            setLoadingDrawable(null)
                        }
                        setTileSource(gsiTileSource())
                        tileProvider.ensureCapacity(512)
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(34.18, 131.47))
                        val locationMarker = Marker(this).apply {
                            title = "現在地"
                            icon = navDotDrawable
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isFlat = true
                        }
                        val accuracyCircle = AccuracyCircleOverlay()
                        this.onUserPan = onUserPan
                        addMapListener(
                            object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    onPoiRefresh()
                                    return true
                                }

                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    onPoiRefresh()
                                    event?.zoomLevel?.let { onZoomLevelChanged(it) }
                                    return true
                                }
                            },
                        )
                        overlays.add(accuracyCircle)
                        overlays.add(locationMarker)
                        overlays.add(
                            MapEventsOverlay(
                                object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                        onPoiSpotDeselect()
                                        return false
                                    }

                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        if (areaSelectMode) return false
                                        onLongPress(p)
                                        return true
                                    }
                                },
                            ),
                        )
                        onMapCreated(this, locationMarker, accuracyCircle)
                    }
                },
                update = { view ->
                    (view as? CycleMapView)?.areaSelectMode = isAreaSelectMode
                },
                onRelease = { view ->
                    view.onPause()
                    view.onDetach()
                    onMapDestroyed()
                },
            )
        }

        // VectorMapView (MapLibre Native Android + PMTiles 完全オフライン Liberty)
        if (selectedLayer == MapLayer.VECTOR_LIBERTY) {
            val (tileLat, tileLon) = if (currentLocation != null) {
                currentLocation.latitude to currentLocation.longitude
            } else {
                FALLBACK_LATITUDE to FALLBACK_LONGITUDE
            }
            val vectorTileSelection = TileSourceSelector.select(tileLat, tileLon)
            val tileKey = when (vectorTileSelection) {
                is TileSourceSelection.Available -> vectorTileSelection.fileName
                is TileSourceSelection.Unavailable -> "unavailable"
            }
            key(tileKey) {
                VectorMapView(
                    modifier = Modifier.fillMaxSize(),
                    routePoints = navigationRoute.map { GeoPoint(it.latitude, it.longitude) },
                    alternativeRoutes = alternativeRoutes,
                    destination = destination,
                    currentLocation = currentLocation?.let { GeoPoint(it.latitude, it.longitude) },
                    bearing = if (currentLocation != null && currentLocation.hasBearing() && currentLocation.speed >= 1.0f) {
                        currentLocation.bearing
                    } else if (hasHeadingSensorFix && headingDegrees.isFinite()) {
                        headingDegrees
                    } else null,
                    speedMps = currentLocation?.speed,
                    isNightMode = isDarkTheme,
                    isPowerSaveActive = isLowPowerMode,
                    selectedPoiSpot = selectedPoiSpot,
                    followLocation = followLocation,
                    isNavigationActive = isNavigationActive,
                    gpsStatus = gpsStatus,
                    poiVisible = poiVisible,
                    poiSelected = poiSelected,
                    cameraPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = if (isNavigationActive) {
                            if (isLandscape) 50.dp else (systemInsets.calculateTopPadding() + 210.dp)
                        } else 0.dp,
                        bottom = if (isNavigationActive) {
                            if (isLandscape) 64.dp else (systemInsets.calculateBottomPadding() + 105.dp)
                        } else 0.dp,
                    ),
                    onTiltChanged = onTiltChanged,
                    onZoomLevelChanged = onZoomLevelChanged,
                    onBearingChanged = onBearingChanged,
                    onPoiClick = onPoiClick,
                    onPoiSpotDeselect = onPoiSpotDeselect,
                    onLongPress = onLongPress,
                    onUserPan = onUserPan,
                    onMapReady = onVectorMapReady,
                )
            }
        }

        // Area-select touch capture + rectangle drawing overlay (Compose layer, above map)
        if (isAreaSelectMode) {
            val latestMapViewForArea by rememberUpdatedState(mapView)
            val latestVectorMapForArea by rememberUpdatedState(vectorMap)
            val latestSelectedLayer by rememberUpdatedState(selectedLayer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .pointerInput(isAreaSelectMode, selectedLayer) {
                        var localDrag = AreaDragState()
                        detectDragGestures(
                            onDragStart = { offset ->
                                localDrag = AreaDragState(
                                    anchorX = offset.x,
                                    anchorY = offset.y,
                                    currentX = offset.x,
                                    currentY = offset.y,
                                    active = true,
                                )
                                onAreaDragStateChange(localDrag)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                localDrag = localDrag.copy(
                                    currentX = change.position.x,
                                    currentY = change.position.y,
                                )
                                onAreaDragStateChange(localDrag)
                            },
                            onDragEnd = {
                                val left = minOf(localDrag.anchorX, localDrag.currentX)
                                val right = maxOf(localDrag.anchorX, localDrag.currentX)
                                val top = minOf(localDrag.anchorY, localDrag.currentY)
                                val bottom = maxOf(localDrag.anchorY, localDrag.currentY)
                                // Only accept drag with at least 20px in each axis
                                if ((right - left) > 20f && (bottom - top) > 20f) {
                                    if (latestSelectedLayer == MapLayer.VECTOR_LIBERTY) {
                                        val vMap = latestVectorMapForArea
                                        if (vMap != null) {
                                            val topLeftLatLng = vMap.projection.fromScreenLocation(android.graphics.PointF(left, top))
                                            val bottomRightLatLng = vMap.projection.fromScreenLocation(android.graphics.PointF(right, bottom))
                                            if (topLeftLatLng != null && bottomRightLatLng != null) {
                                                val latNorth = maxOf(topLeftLatLng.latitude, bottomRightLatLng.latitude)
                                                val latSouth = minOf(topLeftLatLng.latitude, bottomRightLatLng.latitude)
                                                val lonWest = minOf(topLeftLatLng.longitude, bottomRightLatLng.longitude)
                                                val lonEast = maxOf(topLeftLatLng.longitude, bottomRightLatLng.longitude)
                                                onAreaSelected(
                                                    BoundingBox(
                                                        latNorth,
                                                        lonEast,
                                                        latSouth,
                                                        lonWest,
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        val view = latestMapViewForArea
                                        if (view != null) {
                                            val proj = view.projection
                                            if (proj != null) {
                                                val topLeftGeo = proj.fromPixels(left.toInt(), top.toInt()) as? GeoPoint
                                                val bottomRightGeo = proj.fromPixels(right.toInt(), bottom.toInt()) as? GeoPoint
                                                if (topLeftGeo != null && bottomRightGeo != null) {
                                                    onAreaSelected(
                                                        BoundingBox(
                                                            topLeftGeo.latitude,       // latNorth
                                                            bottomRightGeo.longitude,  // lonEast
                                                            bottomRightGeo.latitude,   // latSouth
                                                            topLeftGeo.longitude,      // lonWest
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                localDrag = localDrag.copy(active = false)
                                onAreaDragStateChange(localDrag)
                            },
                            onDragCancel = {
                                localDrag = localDrag.copy(active = false)
                                onAreaDragStateChange(localDrag)
                            },
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        if (areaDragState.active) {
                            val drag = areaDragState
                            val left = minOf(drag.anchorX, drag.currentX)
                            val right = maxOf(drag.anchorX, drag.currentX)
                            val top = minOf(drag.anchorY, drag.currentY)
                            val bottom = maxOf(drag.anchorY, drag.currentY)
                            // Semi-transparent fill
                            drawRect(
                                color = Color(0x330066FF),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            )
                            // Solid border
                            drawRect(
                                color = Color(0xFF0066FF),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    },
            )
        }
    }
}
