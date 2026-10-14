package com.gorite.cyclemap

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gorite.cyclemap.routing.NavigationStats
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.ui.cycling.ElevationSample
import com.gorite.cyclemap.ui.cycling.GoogleNavFooter
import com.gorite.cyclemap.ui.cycling.GoogleNavTab
import com.gorite.cyclemap.ui.cycling.GoogleNavTabBar
import com.gorite.cyclemap.ui.cycling.LocationSummarySheet
import com.gorite.cyclemap.ui.cycling.RideMetrics
import com.gorite.cyclemap.ui.cycling.RouteInfoCardContent
import com.gorite.cyclemap.ui.cycling.buildRouteElevationProfile
import com.gorite.cyclemap.ui.cycling.elevationAt
import com.gorite.cyclemap.ui.cycling.elevationGain
import com.gorite.cyclemap.ui.cycling.forwardGradeAt
import com.gorite.cyclemap.ui.cycling.gradeAt
import com.gorite.cyclemap.ui.theme.Motion
import org.osmdroid.util.GeoPoint

@Composable
internal fun BoxScope.MapBottomOverlays(
    isNavigationActive: Boolean,
    routeSummary: RouteSummary?,
    navStats: NavigationStats?,
    speedKmh: Double,
    isRecording: Boolean,
    gpxPointCount: Int,
    hasLocationPermission: Boolean,
    onWarningMessage: (String) -> Unit,
    onStopNavigation: () -> Unit,
    onClearRoute: () -> Unit,
    onShowLicense: () -> Unit,
    isLandscape: Boolean,
    systemInsets: PaddingValues,
    isCalculatingRoute: Boolean,
    isSwitchingGraph: Boolean,
    effectiveAnimationEnabled: Boolean,
    destination: GeoPoint?,
    realElevProfile: List<ElevationSample>,
    navigationRoute: List<RoutePoint>,
    navigationProgress: RouteProgress?,
    onStartNavigation: () -> Unit,
    downloadCorridorTiles: () -> Unit,
    corridorTileProgressText: String?,
    selectedRoutePreference: RoutePreference,
    onRoutePreferenceChange: (RoutePreference) -> Unit,
    isArrived: Boolean,
    locationLabel: String,
    googleNavTab: GoogleNavTab,
    onTabSelected: (GoogleNavTab) -> Unit,
    onExploreClick: () -> Unit,
    onSavedClick: () -> Unit,
    onRecordHistoryClick: () -> Unit,
    context: Context,
) {
    if (isNavigationActive && routeSummary != null) {
        // アクティブナビ案内フッター (GoogleNavFooterView)
        com.gorite.cyclemap.ui.map.GoogleNavFooterView(
            eta = navStats?.let {
                val nowMs = System.currentTimeMillis()
                com.gorite.cyclemap.contract.EtaInfo(
                    remainingDistanceMeters = it.remainingMeters,
                    remainingSeconds = it.durationRemainingSeconds,
                    arrivalEpochMillis = nowMs + (it.durationRemainingSeconds * 1000).toLong(),
                    currentSpeedKmh = speedKmh
                )
            },
            remainingDistanceMeters = routeSummary.distanceMeters,
            remainingSeconds = routeSummary.distanceMeters / 4.2,
            isMuted = false,
            onToggleMute = {},
            onStopNavigation = onStopNavigation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = systemInsets.calculateBottomPadding())
                .zIndex(2f),
        )
    } else if (routeSummary != null) {
        // ルート案内前プレビューモード (白背景・Google スタイル SCR-10)
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = systemInsets.calculateBottomPadding())
                .zIndex(2f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // ドラッグハンドル
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFFDADCE0), RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.height(10.dp))

                // ルート探索中プログレス表示
                AnimatedVisibility(
                    visible = isCalculatingRoute || isSwitchingGraph,
                    enter = Motion.bannerEnter(effectiveAnimationEnabled),
                    exit = Motion.bannerExit(effectiveAnimationEnabled),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                if (isSwitchingGraph) "県データグラフを切替・読み込み中…" else "最適サイクリングルートを探索中…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ルートサマリー
                AnimatedVisibility(
                    visible = !isCalculatingRoute && !isSwitchingGraph,
                    enter = Motion.bannerEnter(effectiveAnimationEnabled),
                    exit = Motion.bannerExit(effectiveAnimationEnabled),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val summary = routeSummary
                        val elevProfile = if (realElevProfile.isNotEmpty()) realElevProfile else remember(summary, navigationRoute) {
                            buildRouteElevationProfile(navigationRoute.map { it.latitude to it.longitude }, summary.distanceMeters)
                        }
                        val progressM = navigationProgress?.distanceFromStartMeters
                        val rideMetrics = navStats?.let { stats ->
                            RideMetrics(
                                speedKmh = speedKmh,
                                totalDistanceM = summary.distanceMeters,
                                remainingM = stats.remainingMeters,
                                elapsedS = stats.elapsedSeconds,
                                remainingS = stats.durationRemainingSeconds.takeIf { it.isFinite() },
                                averageKmh = stats.effectiveSpeedMps * 3.6,
                                elevationGainM = progressM?.let { elevationGain(elevProfile, it) },
                                currentElevationM = progressM?.let { elevationAt(elevProfile, it) },
                                gradePct = progressM?.let { gradeAt(elevProfile, it) },
                                forwardGradePct = progressM?.let { forwardGradeAt(elevProfile, it) },
                            )
                        } ?: RideMetrics(
                            speedKmh = speedKmh,
                            totalDistanceM = summary.distanceMeters,
                            remainingM = summary.distanceMeters,
                            elapsedS = 0.0,
                            remainingS = summary.distanceMeters / PRE_START_ASSUMED_SPEED_MPS,
                            averageKmh = PRE_START_ASSUMED_SPEED_MPS * 3.6,
                            elevationGainM = elevationGain(elevProfile, 0.0),
                            currentElevationM = elevationAt(elevProfile, 0.0),
                            gradePct = gradeAt(elevProfile, 0.0),
                            forwardGradePct = forwardGradeAt(elevProfile, 0.0),
                        )
                        RouteInfoCardContent(
                            metrics = rideMetrics,
                            profile = elevProfile,
                            totalDistanceM = summary.distanceMeters,
                            progressDistanceM = progressM,
                            started = false,
                            onStart = onStartNavigation,
                            onClearRoute = onClearRoute,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onDownloadCorridorTiles = downloadCorridorTiles,
                            tileDownloadStatus = corridorTileProgressText,
                            selectedPreference = selectedRoutePreference,
                            onPreferenceChange = onRoutePreferenceChange,
                        )
                        HorizontalDivider(
                            color = Color(0xFFE8EAED),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }
    } else {
        // 通常探索時: 下部スタック
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = if (isLandscape) {
                        systemInsets.calculateBottomPadding() + 8.dp
                    } else 0.dp,
                )
                .zIndex(2f),
        ) {
            // 到着カード
            AnimatedVisibility(
                visible = isArrived,
                enter = Motion.bannerEnter(effectiveAnimationEnabled),
                exit = Motion.bannerExit(effectiveAnimationEnabled),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F4EA)),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "目的地に到着しました",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF137333),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = onClearRoute,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF137333)),
                        ) {
                            Text("終了", color = Color.White)
                        }
                    }
                }
            }

            // Main HUD: Speedometer & GPX Controls (通常探索時)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Large Speedometer (ルート未設定時のみ)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "%.1f".format(speedKmh.coerceAtLeast(0.0)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "km/h",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }

                // Right: GPX Controls
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRecording) Color(0xFFD93025) else MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                if (!hasLocationPermission) {
                                    onWarningMessage("GPX記録には位置情報の許可が必要です")
                                } else if (isRecording) {
                                    LocationTrackingService.stopRecording(context)
                                } else {
                                    LocationTrackingService.startRecording(context)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) Color.White else Color(0xFFD93025)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "記録中 (${gpxPointCount}pt)" else "GPX記録",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (!isLandscape) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    // 5. 地名・天候サマリーシート (SCR-01, SCR-02)
                    LocationSummarySheet(
                        locationName = locationLabel.ifEmpty { "井口明神" },
                        onClick = onExploreClick,
                    )

                    // 6. 3タブナビゲーションバー (スポット / 保存済み / 記録)
                    GoogleNavTabBar(
                        selectedTab = googleNavTab,
                        onTabSelected = { tab ->
                            onTabSelected(tab)
                            when (tab) {
                                GoogleNavTab.EXPLORE -> onExploreClick()
                                GoogleNavTab.SAVED -> onSavedClick()
                                GoogleNavTab.RECORD -> onRecordHistoryClick()
                            }
                        },
                    )
                }
            }
        }
    }
}
