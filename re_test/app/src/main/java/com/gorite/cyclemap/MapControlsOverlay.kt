package com.gorite.cyclemap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gorite.cyclemap.ui.cycling.CompassDial
import com.gorite.cyclemap.ui.cycling.GoogleFloatingActionButton
import com.gorite.cyclemap.ui.cycling.GoogleGpsButton
import com.gorite.cyclemap.ui.cycling.GoogleLayerButton
import com.gorite.cyclemap.ui.cycling.GoogleMapsControlColumn
import com.gorite.cyclemap.ui.cycling.GoogleZoomPill
import com.gorite.cyclemap.ui.cycling.NavStartFAB
import com.gorite.cyclemap.net.Link
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

    if (isLandscape) {
        GoogleMapsControlColumn(
            headingDegrees = { headingDegrees },
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
    } else {
        // 右側統合コントロール列 (Google Maps 完全再現: レイヤー, 3D, コンパス, ズームピル)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .zIndex(2f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            com.gorite.cyclemap.ui.map.MapControls(
                bearingDeg = headingDegrees,
                onResetNorth = onHeadingUpToggle,
                onLayerClick = toggleNextLayer,
                onMyLocationClick = onTargetClick,
                onZoomIn = onZoomInAction,
                onZoomOut = onZoomOutAction,
                onToggleTilt = onToggleTiltAction,
                is3D = currentVectorTilt > 15.0,
                onAreaSelectToggle = onAreaSelectToggle,
                isAreaSelectMode = isAreaSelectMode,
            )
        }

        // 右下: ナビ開始FAB (Google Maps 青または深緑 ↱)
        if (!isNavigationActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .zIndex(2f),
            ) {
                NavStartFAB(
                    onClick = onNavStartClick,
                )
            }
        }
    }
}
