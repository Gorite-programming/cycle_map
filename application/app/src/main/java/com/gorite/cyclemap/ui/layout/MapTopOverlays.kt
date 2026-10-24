package com.gorite.cyclemap.ui.layout

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import com.gorite.cyclemap.navigation.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.ui.cycling.QuickSpotType
import com.gorite.cyclemap.ui.theme.Motion

@Composable
internal fun BoxScope.MapTopOverlays(
    isNavigationActive: Boolean,
    effectiveAnimationEnabled: Boolean,
    effectiveTopPadding: Dp,
    isLandscape: Boolean,
    systemInsets: PaddingValues,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSelectQuickSpot: (QuickSpotType) -> Unit,
    onSelectCategory: (String) -> Unit,
    notificationBannerGroup: @Composable (Modifier) -> Unit,
    navigationProgress: RouteProgress?,
    navigationInstructions: List<RouteInstruction>,
    gpsStatus: com.gorite.cyclemap.tracking.GpsSignalStatus,
    isRerouting: Boolean,
    offRouteDetector: OffRouteDetector,
    autoRerouteEnabled: Boolean,
    networkLink: com.gorite.cyclemap.net.Link = com.gorite.cyclemap.net.Link.Offline,
    onStopNavigation: () -> Unit = {},
) {
    // Normal Top Bar
    AnimatedVisibility(
        visible = !isNavigationActive,
        enter = Motion.topBarEnter(effectiveAnimationEnabled),
        exit = Motion.topBarExit(effectiveAnimationEnabled),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = effectiveTopPadding + 8.dp)
            .zIndex(2f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Google Maps 完全準拠 MapTopBar (≡ + 検索 + 雲/オフライン + 🎙️ + アバター + 通知バナー + カテゴリチップ)
            com.gorite.cyclemap.ui.map.MapTopBar(
                queryText = "",
                onSearchClick = onSearchClick,
                onMenuClick = onMenuClick,
                networkLink = networkLink,
                selectedCategory = null,
                onCategoryClick = onSelectCategory,
                onMicClick = onSearchClick,
                onAvatarClick = onAvatarClick,
                userAvatarText = "",
                placeholderText = "ここで検索",
                bannerContent = {
                    notificationBannerGroup(Modifier.fillMaxWidth())
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Top Center: Active Navigation Guidance & Banners (isNavigationActive: GoogleNavHeader)
    AnimatedVisibility(
        visible = isNavigationActive,
        enter = Motion.topBarEnter(effectiveAnimationEnabled),
        exit = Motion.topBarExit(effectiveAnimationEnabled),
        modifier = Modifier
            .align(if (isLandscape) Alignment.TopStart else Alignment.TopCenter)
            .then(
                if (isLandscape) {
                    Modifier.padding(
                        start = systemInsets.calculateStartPadding(LayoutDirection.Ltr) + 12.dp,
                        top = effectiveTopPadding + 8.dp,
                    )
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = effectiveTopPadding + 8.dp)
                },
            )
            .zIndex(2f),
    ) {
        val progress = navigationProgress
        val distanceMeters = progress?.distanceToNextManeuverMeters
            ?: progress?.let { (it.routeDistanceMeters - it.distanceFromStartMeters).coerceAtLeast(0.0) }
        val remaining = progress?.let { (it.routeDistanceMeters - it.distanceFromStartMeters).coerceAtLeast(0.0) }
        val guide = if (distanceMeters != null) {
            navigationInstruction(progress, remaining, navigationInstructions, ARRIVAL_RADIUS_METERS)
        } else null

        val traveled = progress?.distanceFromStartMeters ?: 0.0
        val futureInstructions = navigationInstructions.filter { it.distanceFromStartMeters > traveled }
        val nextInstruction = futureInstructions.firstOrNull()
        val nextAfterThis = futureInstructions.getOrNull(1)
        val distNextAfterThis = nextAfterThis?.let { (it.distanceFromStartMeters - traveled).coerceAtLeast(0.0) }

        val laneProvider = androidx.compose.runtime.remember {
            com.gorite.cyclemap.real.RealLaneGuidanceProvider()
        }
        var activeLaneHints by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<List<com.gorite.cyclemap.contract.LaneInfo>>(emptyList())
        }
        val currentTurnType = nextInstruction?.type.toTurnType()
        val nextTurnType = nextAfterThis?.type.toTurnType()

        androidx.compose.runtime.LaunchedEffect(nextInstruction?.nodeId, currentTurnType) {
            val hints = laneProvider.getLaneHints(nextInstruction?.nodeId ?: 0L, currentTurnType)
            val hintsFromLaneHint: List<com.gorite.cyclemap.routing.LaneHint> = hints.map {
                com.gorite.cyclemap.routing.LaneHint(
                    direction = it.direction.name.lowercase(),
                    isActiveForRoute = it.isRecommended
                )
            }
            activeLaneHints = hintsFromLaneHint.take(5).map {
                com.gorite.cyclemap.contract.LaneInfo(
                    direction = when (it.direction) {
                        "left" -> com.gorite.cyclemap.contract.TurnType.Left
                        "slightleft" -> com.gorite.cyclemap.contract.TurnType.SlightLeft
                        "sharpleft" -> com.gorite.cyclemap.contract.TurnType.SharpLeft
                        "right" -> com.gorite.cyclemap.contract.TurnType.Right
                        "slightright" -> com.gorite.cyclemap.contract.TurnType.SlightRight
                        "sharpright" -> com.gorite.cyclemap.contract.TurnType.SharpRight
                        "roundabout" -> com.gorite.cyclemap.contract.TurnType.Roundabout
                        "uturn" -> com.gorite.cyclemap.contract.TurnType.Uturn
                        else -> com.gorite.cyclemap.contract.TurnType.Straight
                    },
                    isRecommended = it.isActiveForRoute
                )
            }
        }

        if (isLandscape) {
            com.gorite.cyclemap.ui.map.LandscapeNavHudBar(
                currentStep = com.gorite.cyclemap.contract.GuidanceStep(
                    turnType = currentTurnType,
                    instructionText = guide?.text?.withoutDistancePrefix() ?: "直進",
                    streetName = null,
                    distanceToTurnMeters = distanceMeters ?: 0.0
                ),
                eta = null,
                remainingSeconds = (remaining ?: 0.0) / 4.2,
                onStopNavigation = onStopNavigation,
                animationEnabled = effectiveAnimationEnabled
            )
        } else {
            com.gorite.cyclemap.ui.map.GoogleNavHeaderView(
                currentStep = com.gorite.cyclemap.contract.GuidanceStep(
                    turnType = currentTurnType,
                    instructionText = guide?.text?.withoutDistancePrefix() ?: "直進",
                    streetName = null,
                    distanceToTurnMeters = distanceMeters ?: 0.0
                ),
                nextStep = nextAfterThis?.let {
                    com.gorite.cyclemap.contract.GuidanceStep(
                        turnType = nextTurnType,
                        instructionText = com.gorite.cyclemap.speech.RouteInstructionText.displayLabel(it),
                        streetName = null,
                        distanceToTurnMeters = distNextAfterThis ?: 0.0
                    )
                },
                laneHints = if (gpsStatus == com.gorite.cyclemap.tracking.GpsSignalStatus.HEALTHY && (distanceMeters ?: Double.MAX_VALUE) <= 600.0) {
                    activeLaneHints
                } else {
                    emptyList()
                },
                isRerouting = isRerouting,
                animationEnabled = effectiveAnimationEnabled
            )
        }
    }
}

private fun String.withoutDistancePrefix(): String = replace(
    Regex("^\\s*(?:あと)?\\d+(?:\\.\\d+)?\\s*(?:m|km)(?:先|後)?\\s*"),
    "",
).ifBlank { this }

private fun com.gorite.cyclemap.routing.InstructionType?.toTurnType(): com.gorite.cyclemap.contract.TurnType = when (this) {
    com.gorite.cyclemap.routing.InstructionType.LEFT_TURN -> com.gorite.cyclemap.contract.TurnType.Left
    com.gorite.cyclemap.routing.InstructionType.RIGHT_TURN -> com.gorite.cyclemap.contract.TurnType.Right
    com.gorite.cyclemap.routing.InstructionType.SHARP_LEFT -> com.gorite.cyclemap.contract.TurnType.SharpLeft
    com.gorite.cyclemap.routing.InstructionType.SHARP_RIGHT -> com.gorite.cyclemap.contract.TurnType.SharpRight
    com.gorite.cyclemap.routing.InstructionType.FORK_LEFT,
    com.gorite.cyclemap.routing.InstructionType.DIAGONAL_FORK_LEFT,
    com.gorite.cyclemap.routing.InstructionType.FORK_UP_LEFT,
    com.gorite.cyclemap.routing.InstructionType.FORK_DOWN_LEFT -> com.gorite.cyclemap.contract.TurnType.SlightLeft
    com.gorite.cyclemap.routing.InstructionType.FORK_RIGHT,
    com.gorite.cyclemap.routing.InstructionType.DIAGONAL_FORK_RIGHT,
    com.gorite.cyclemap.routing.InstructionType.FORK_UP_RIGHT,
    com.gorite.cyclemap.routing.InstructionType.FORK_DOWN_RIGHT -> com.gorite.cyclemap.contract.TurnType.SlightRight
    com.gorite.cyclemap.routing.InstructionType.ROUNDABOUT -> com.gorite.cyclemap.contract.TurnType.Roundabout
    com.gorite.cyclemap.routing.InstructionType.U_TURN_LEFT,
    com.gorite.cyclemap.routing.InstructionType.U_TURN_RIGHT -> com.gorite.cyclemap.contract.TurnType.Uturn
    else -> com.gorite.cyclemap.contract.TurnType.Straight
}
