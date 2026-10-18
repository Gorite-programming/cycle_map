package com.gorite.cyclemap.ui.components

import android.content.Context
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.gorite.cyclemap.data.DownloadStatusManager
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.Prefecture
import com.gorite.cyclemap.search.SearchResult
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

@Composable
internal fun MapDownloadDialogs(
    context: Context,
    showAreaDownloadConfirmDialog: Boolean,
    selectedAreaBounds: BoundingBox?,
    isDownloading: Boolean,
    onStartAreaDownload: (BoundingBox, MapSourceType) -> Unit,
    onDismissAreaDownload: () -> Unit,
    showDestinationSearch: Boolean,
    currentLocation: Location?,
    onDismissDestinationSearch: () -> Unit,
    onDestinationSelected: (GeoPoint) -> Unit,
    pendingSelectedSpot: GeoPoint?,
    onAddWaypoint: (GeoPoint) -> Unit,
    onChangeDestination: (GeoPoint) -> Unit,
    onDismissPendingSpot: () -> Unit,
    showPrefectureListDialog: Boolean,
    onDismissPrefectureListDialog: () -> Unit,
    onPrefectureSelected: (Prefecture) -> Unit,
    showAlreadyDownloadedDialog: Boolean,
    selectedPrefectureForAction: Prefecture?,
    onRedownloadPrefecture: () -> Unit,
    onDismissAlreadyDownloaded: () -> Unit,
    showDownloadConfirmDialog: Boolean,
    onDownloadPrefectureGsi: (Prefecture) -> Unit,
    onDownloadPrefectureOsm: (Prefecture) -> Unit,
    onDismissDownloadConfirm: () -> Unit,
) {
    // Area download confirmation dialog (high-zoom z15-z16 for user-selected bbox)
    if (showAreaDownloadConfirmDialog && selectedAreaBounds != null) {
        val areaBounds = selectedAreaBounds
        AreaDownloadConfirmDialog(
            context = context,
            areaBounds = areaBounds,
            isDownloading = isDownloading,
            onStartDownload = { sourceType ->
                if (!isDownloading) {
                    onStartAreaDownload(areaBounds, sourceType)
                }
            },
            onDismiss = onDismissAreaDownload,
        )
    }

    if (showDestinationSearch) {
        DestinationSearchDialog(
            context = context,
            liveLocation = currentLocation?.let { it.latitude to it.longitude },
            onDismiss = onDismissDestinationSearch,
            onResultSelected = { result ->
                onDestinationSelected(GeoPoint(result.latitude, result.longitude))
            },
        )
    }

    if (pendingSelectedSpot != null) {
        val spot = pendingSelectedSpot
        WaypointOrDestinationDialog(
            spot = spot,
            onAddWaypoint = { onAddWaypoint(spot) },
            onChangeDestination = { onChangeDestination(spot) },
            onDismiss = onDismissPendingSpot,
        )
    }

    // 1. Prefecture List Dialog (47 Prefectures grouped by Region)
    if (showPrefectureListDialog) {
        PrefectureListDialog(
            context = context,
            onDismiss = onDismissPrefectureListDialog,
            onPrefectureSelected = onPrefectureSelected,
        )
    }

    // 2. Already Downloaded Dialog
    if (showAlreadyDownloadedDialog && selectedPrefectureForAction != null) {
        AlreadyDownloadedPrefectureDialog(
            context = context,
            pref = selectedPrefectureForAction,
            onRedownload = onRedownloadPrefecture,
            onDismiss = onDismissAlreadyDownloaded,
        )
    }

    // 3. Download Confirm / Source Selection Dialog
    if (showDownloadConfirmDialog && selectedPrefectureForAction != null) {
        val pref = selectedPrefectureForAction
        PrefectureDownloadConfirmDialog(
            pref = pref,
            isDownloading = isDownloading,
            onDownloadGsi = {
                if (!isDownloading) {
                    onDownloadPrefectureGsi(pref)
                }
            },
            onDownloadOsm = {
                if (!isDownloading) {
                    onDownloadPrefectureOsm(pref)
                }
            },
            onDismiss = onDismissDownloadConfirm,
        )
    }
}
