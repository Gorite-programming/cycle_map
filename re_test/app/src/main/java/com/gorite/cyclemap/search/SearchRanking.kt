package com.gorite.cyclemap.search

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SearchCandidate(
    val id: String,
    val name: String,
    val searchText: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double = 0.0,
    val score: Double = 0.0
)

object SearchRanking {

    fun rankCandidates(
        candidates: List<SearchCandidate>,
        query: String,
        userLat: Double?,
        userLon: Double?,
        limit: Int = 20
    ): List<SearchCandidate> {
        val normQuery = SearchNormalizer.normalize(query)
        if (normQuery.isEmpty()) return emptyList()

        return candidates
            .mapNotNull { candidate ->
                val normName = SearchNormalizer.normalize(candidate.name)
                val normSearchText = SearchNormalizer.normalize(candidate.searchText)

                // 1. テキストマッチ度スコア (含まれない場合は候補から除外)
                val textScore = when {
                    normName == normQuery -> 1000.0
                    normName.startsWith(normQuery) -> 500.0
                    normSearchText.startsWith(normQuery) -> 400.0
                    normName.contains(normQuery) -> 200.0
                    normSearchText.contains(normQuery) -> 100.0
                    else -> return@mapNotNull null
                }

                // 2. 現在地距離スコア
                val distMeters = if (userLat != null && userLon != null) {
                    calculateDistanceMeters(userLat, userLon, candidate.latitude, candidate.longitude)
                } else {
                    candidate.distanceMeters
                }

                // 距離ブースト: 1km以内 +100点, 5km以内 +50点, 遠いほど減衰
                val distanceBoost = when {
                    distMeters <= 1000.0 -> 100.0
                    distMeters <= 5000.0 -> 50.0
                    distMeters <= 20000.0 -> 20.0
                    else -> 0.0
                }

                val totalScore = textScore + distanceBoost - (distMeters / 1000.0 * 2.0)

                candidate.copy(distanceMeters = distMeters, score = totalScore)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // 地球半径(m)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        // BUG-82 ガード: a を [0.0, 1.0] にクランプ
        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        return r * c
    }
}
