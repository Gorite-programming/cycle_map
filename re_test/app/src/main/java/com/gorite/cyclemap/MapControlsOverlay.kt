package com.gorite.cyclemap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gorite.cyclemap.net.Link
import com.gorite.cyclemap.ui.cycling.GoogleGpsButton
import com.gorite.cyclemap.ui.cycling.GoogleLayerButton
import com.gorite.cyclemap.ui.cycling.GoogleMapsControlColumn
import com.gorite.cyclemap.ui.cycling.GoogleZoomButton
import com.gorite.cyclemap.ui.cycling.NavStartFAB
import com.gorite.cyclemap.ui.map.FloatingCircleButton
import com.gorite.cyclemap.ui.map.MapControls
import com.gorite.cyclemap.ui.map.NetworkStatusIndicator
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.views.MapView

@Composable
internal fun BoxScope.MapControlsOverlay(
    isLandscape: Boolean,
    selectedLayer: MapLayer,
    networkLink: Link,
    vectorMap: MapLibreMap?,
    mapView: MapView?,
    currentZoomLevel: Double,
    currentVectorTilt: Double,
    headingDegrees: Float,
    isHeadingUp: Boolean,
    onHeadingUpToggle: () -> Unit,
    followLocation: Boolean,
    onTargetClick: () -> Unit,
    isNavigationActive: Boolean,
    isAreaSelectMode: Boolean,
    onAreaSelectToggle: () -> Unit,
    toggleNextLayer: () -> Unit,
    onNavStartClick: () -> Unit,
    systemInsets: PaddingValues,
    effectiveTopPadding: Dp,
) {
    val canZoomIn = if (selectedLayer == MapLayer.VECTOR_LIBERTY) {
        (vectorMap?.cameraPosition?.zoom ?: 14.0) < 22.0
    } else {
        currentZoomLevel < (mapView?.maxZoomLevel ?: 21.0)
    }
    val canZoomOut = if (selectedLayer == MapLayer.VECTOR_LIBERTY) {
        (vectorMap?.cameraPosition?.zoom ?: 14.0) > 2.0
    } else {
        currentZoomLevel > (mapView?.minZoomLevel ?: 2.0)
    }
    val currentLayerLabel = when (selectedLayer) {
        MapLayer.VECTOR_LIBERTY -> "3Dベクター"
        MapLayer.GSI -> "標準"
        MapLayer.OSM -> "自転車"
        MapLayer.TERRAIN -> "地形"
    }
    val mapBearing = if (selectedLayer == MapLayer.VECTOR_LIBERTY) {
        (vectorMap?.cameraPosition?.bearing ?: 0.0).toFloat()
    } else {
        mapView?.mapOrientation ?: 0f
    }
    val onToggleTiltAction: () -> Unit = {
        val map = vectorMap
        if (map != null) {
            val targetTilt = if (currentVectorTilt > 15.0) 0.0 else 60.0
            map.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                    org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition)
                        .tilt(targetTilt)
                        .build(),
                ),
                700,
            )
        }
    }
    val onZoomInAction: () -> Unit = {
        if (selectedLayer == MapLayer.VECTOR_LIBERTY) {
            vectorMap?.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomIn())
        } else {
            mapView?.controller?.zoomIn(150L)
        }
    }
    val onZoomOutAction: () -> Unit = {
        if (selectedLayer == MapLayer.VECTOR_LIBERTY) {
            vectorMap?.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomOut())
        } else {
            mapView?.controller?.zoomOut(150L)
        }
    }

    if (false) {
        MapControls(bearingDeg = mapBearing, onResetNorth = onHeadingUpToggle, onLayerClick = toggleNextLayer, onMyLocationClick = onTargetClick, onZoomIn = onZoomInAction, onZoomOut = onZoomOutAction)
        NetworkStatusIndicator(networkLink)
    }
    if (isLandscape) {
        GoogleMapsControlColumn(
            headingDegrees = { mapBearing },
            isHeadingUp = isHeadingUp,
            onCompassClick = onHeadingUpToggle,
            currentTilt = currentVectorTilt,
            onToggleTilt = onToggleTiltAction,
            layerLabel = currentLayerLabel,
            onLayerClick = toggleNextLayer,
            following = followLocation,
            onTargetClick = onTargetClick,
            onZoomIn = onZoomInAction,
            onZoomOut = onZoomOutAction,
            canZoomIn = canZoomIn,
            canZoomOut = canZoomOut,
            isNavigationActive = isNavigationActive,
            isAreaSelectMode = isAreaSelectMode,
            onAreaSelectToggle = onAreaSelectToggle,
            isLandscape = true,
            onNavStartClick = onNavStartClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    end = systemInsets.calculateEndPadding(LayoutDirection.Ltr) + 12.dp,
                    top = effectiveTopPadding + (if (isNavigationActive) 12.dp else 68.dp),
                )
                .zIndex(2f),
        )
    } else if (!isNavigationActive) {
        // 右上 (チップ列の直下): レイヤー切替ボタン + 回転時のみコンパス (Google Maps 配置)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = effectiveTopPadding + 122.dp, end = 14.dp)
                .zIndex(2f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoogleLayerButton(
                onClick = toggleNextLayer,
                label = currentLayerLabel,
            )
            val safeBearing = if (mapBearing.isNaN()) 0f else mapBearing
            AnimatedVisibility(
                visible = kotlin.math.abs(safeBearing) > 2.0f,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingCircleButton(
                    onClick = onHeadingUpToggle,
                    contentDescription = "北を上に",
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_compass),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(-safeBearing),
                        tint = Color(0xFFD32F2F),
                    )
                }
            }
            GoogleZoomButton(
                iconRes = R.drawable.ic_ms_add,
                contentDescription = "拡大",
                onClick = onZoomInAction,
            )
            GoogleZoomButton(
                iconRes = R.drawable.ic_lucide_minus,
                contentDescription = "縮小",
                onClick = onZoomOutAction,
            )
        }

        // 右下 (ボトムシートの直上): 現在地ボタン (白丸) + 経路検索FAB (ティール角丸四角)
        val bottomSheetOffset = systemInsets.calculateBottomPadding() + 176.dp
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = bottomSheetOffset)
                .zIndex(2f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoogleGpsButton(
                following = followLocation,
                onClick = onTargetClick,
            )
            NavStartFAB(
                onClick = onNavStartClick,
            )
        }
    }
}
