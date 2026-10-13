package com.gorite.cyclemap.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RerouteState(
    val isOffRoute: Boolean = false,
    val isRerouting: Boolean = false,
    val currentRouteNodeIds: List<Long> = emptyList(),
    val rerouteAttemptCount: Int = 0
)

class AdaptiveRerouteManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val rerouteDistanceThreshold: Double = 30.0, // plans.md: 30m
    private val consecutiveSamples: Int = 3,
    private val routerAction: suspend (startNodeId: Long, goalNodeId: Long) -> List<Long>?
) {
    private var detector = OffRouteDetector(
        offRouteDistanceMeters = rerouteDistanceThreshold,
        requiredConsecutive = consecutiveSamples,
        recoverDistanceMeters = 15.0
    )

    private val _state = MutableStateFlow(RerouteState())
    val state: StateFlow<RerouteState> = _state.asStateFlow()

    private var activeRerouteJob: Job? = null
    val currentRerouteJob: Job? get() = activeRerouteJob

    fun setInitialRoute(nodeIds: List<Long>) {
        detector.reset()
        _state.value = RerouteState(currentRouteNodeIds = nodeIds)
    }

    fun onLocationUpdate(
        distanceToRouteMeters: Double,
        currentSpeedMps: Float,
        currentNodeId: Long,
        goalNodeId: Long
    ) {
        // 速度に応じた可変閾値 (BUG防止: 高速走行時はGPSジッターを吸収)
        val dynamicThreshold = if (currentSpeedMps > 8.0f) {
            (currentSpeedMps * 2.0).toDouble().coerceIn(30.0, 60.0)
        } else {
            rerouteDistanceThreshold
        }

        val wasOffRoute = detector.isOffRoute
        // If threshold needs update, detector is queried with dynamic scaling
        val isOff = detector.update(distanceToRouteMeters * (rerouteDistanceThreshold / dynamicThreshold))

        if (isOff && !wasOffRoute) {
            _state.value = _state.value.copy(isOffRoute = true)
            triggerReroute(currentNodeId, goalNodeId)
        } else if (!isOff && wasOffRoute) {
            _state.value = _state.value.copy(isOffRoute = false)
        }
    }

    private fun triggerReroute(startNodeId: Long, goalNodeId: Long) {
        if (activeRerouteJob?.isActive == true) return

        _state.value = _state.value.copy(
            isRerouting = true,
            rerouteAttemptCount = _state.value.rerouteAttemptCount + 1
        )

        activeRerouteJob = scope.launch {
            try {
                // バックグラウンドで再探索。結果到着まで旧ルートを表示し続ける (plans.md §6 P1-3)
                val newRoute = routerAction(startNodeId, goalNodeId)
                if (newRoute != null && newRoute.isNotEmpty()) {
                    detector.reset()
                    _state.value = _state.value.copy(
                        isOffRoute = false,
                        isRerouting = false,
                        currentRouteNodeIds = newRoute
                    )
                } else {
                    _state.value = _state.value.copy(isRerouting = false)
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(isRerouting = false)
            }
        }
    }
}
