package com.gorite.cyclemap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.ui.cycling.CategoryChipRow
import com.gorite.cyclemap.ui.cycling.GoogleNavHeader
import com.gorite.cyclemap.ui.cycling.QuickSpotType
import com.gorite.cyclemap.ui.cycling.TopSearchBar
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
    isRerouting: Boolean,
    offRouteDetector: OffRouteDetector,
    autoRerouteEnabled: Boolean,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top Search Bar: 白カプセル (≡ + ここで検索 + 🎙️ + 拓真アバター)
            TopSearchBar(
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                onMicClick = onSearchClick,
                onAvatarClick = onAvatarClick,
                userAvatarText = "拓真",
                placeholderText = "ここで検索",
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // DEF-01: 通常時は検索バーの直下に通知バナーを配置
            notificationBannerGroup(Modifier.padding(horizontal = 12.dp))

            // Google Maps 完全再現: 水平スクロール カテゴリチップ列
            if (!isLandscape) {
                CategoryChipRow(
                    onConsultClick = onSearchClick,
                    onHomeClick = onHomeClick,
                    onSelectQuickSpot = onSelectQuickSpot,
                    onSelectCategory = onSelectCategory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                    Modifier.fillMaxWidth()
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
        val nextAfterThis = futureInstructions.getOrNull(1)
        val distNextAfterThis = nextAfterThis?.let { (it.distanceFromStartMeters - traveled).coerceAtLeast(0.0) }

        GoogleNavHeader(
            currentGuide = guide,
            distanceMeters = distanceMeters,
            nextAfterThis = nextAfterThis,
            distanceToNextAfterThis = distNextAfterThis,
            isRerouting = isRerouting,
            isOffRoute = offRouteDetector.isOffRoute && !isRerouting,
            autoRerouteEnabled = autoRerouteEnabled,
            effectiveAnimationEnabled = effectiveAnimationEnabled,
            isLandscape = isLandscape,
            statusBarTopPadding = effectiveTopPadding,
            notificationBannerGroup = { notificationBannerGroup(Modifier) },
        )
    }
}
