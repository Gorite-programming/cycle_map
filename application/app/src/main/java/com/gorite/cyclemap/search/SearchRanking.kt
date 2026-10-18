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

    internal data class RawPoiCandidate(
        val name: String,
        val category: String,
        val latitude: Double,
        val longitude: Double,
        val osmType: String? = null,
        val hasAddress: Boolean = false,
    )

    private val MAJOR_BRAND_KEYWORDS = listOf(
        "セブン-イレブン", "セブンイレブン", "ローソン", "ファミリーマート", "デイリーヤマザキ", "ミニストップ", "ポプラ",
        "道の駅", "イオン", "ゆめタウン", "フジグラン", "マックスバリュ", "アルパーク", "アウトレット",
        "スターバックス", "ドトール", "コメダ珈琲", "タリーズ",
        "マクドナルド", "モスバーガー", "ケンタッキー", "ミスタードーナツ",
        "すき家", "吉野家", "松屋", "なか卯", "ガスト", "サイゼリヤ", "ジョイフル", "ロイヤルホスト",
        "スシロー", "くら寿司", "はま寿司", "丸亀製麺", "CoCo壱番屋", "大阪王将", "餃子の王将", "ちから", "むさし",
        "サイクルベースあさひ", "ジャイアント", "モンベル", "ダイソー",
    )

    /**
     * Google マップの Prominence Hierarchy に準拠した POI 重要度スコア (0..1150) を算出する。
     * 1. 主要交通拠点・ランドマーク (Tier 1: 880〜960)
     * 2. 医療・自転車拠点・コンビニ・大型公園・温泉 (Tier 2: 760〜850)
     * 3. トイレ・給水・宿泊・GS・主要飲食・カフェ (Tier 3: 580〜720)
     * 4. 駐車場・バス停・案内板等のマイクロPOI (Tier 4: 240〜540)
     * + 建物ポリゴン (osm_type=way)・住所登録・有名チェーン/ランドマーク名による Prominence 加点。
     */
    fun computePoiProminenceScore(
        name: String,
        category: String,
        osmType: String? = null,
        hasAddress: Boolean = false,
    ): Int {
        if (category.startsWith("public_transport:stop_position") || category.startsWith("public_transport:platform")) {
            return 0
        }

        val baseScore = when {
            category.startsWith("railway:station") || category.startsWith("public_transport:station") -> 960
            category.startsWith("amenity:bus_station") || category.startsWith("amenity:ferry_terminal") -> 920
            category.startsWith("railway:halt") -> 890
            category.startsWith("tourism:theme_park") || category.startsWith("tourism:aquarium") ||
                category.startsWith("tourism:zoo") || category.startsWith("tourism:museum") ||
                category.startsWith("tourism:attraction") || category.startsWith("tourism:viewpoint") -> 880
            category.startsWith("shop:bicycle") -> 850
            category.startsWith("amenity:hospital") -> 830
            category.startsWith("shop:convenience") -> 800
            category.startsWith("amenity:public_bath") || category.startsWith("tourism:resort") -> 780
            category.startsWith("leisure:park") -> 760
            category.startsWith("amenity:toilets") -> 720
            category.startsWith("amenity:drinking_water") -> 700
            category.startsWith("tourism:hotel") || category.startsWith("tourism:guest_house") ||
                category.startsWith("tourism:hostel") || category.startsWith("tourism:camp_site") -> 680
            category.startsWith("amenity:fuel") -> 660
            category.startsWith("amenity:fast_food") || category.startsWith("amenity:cafe") ||
                category.startsWith("shop:bakery") -> 640
            category.startsWith("amenity:restaurant") -> 620
            category.startsWith("amenity:clinic") || category.startsWith("amenity:doctors") ||
                category.startsWith("amenity:dentist") -> 580
            category.startsWith("amenity:parking") -> 540
            category.startsWith("tourism:information") || category.startsWith("tourism:artwork") ||
                category.startsWith("tourism:gallery") || category.startsWith("tourism:picnic_site") -> 320
            category.startsWith("highway:bus_stop") || category.startsWith("amenity:bus") -> 250
            category.startsWith("tourism:") -> 650
            else -> 500
        }

        var bonus = 0
        if (osmType == "way") bonus += 45
        if (hasAddress) bonus += 30
        if (name.contains("道の駅") || name.contains("サービスエリア") || name.contains("パーキングエリア")) {
            bonus += 110
        } else if (MAJOR_BRAND_KEYWORDS.any { name.contains(it) }) {
            bonus += 65
        }
        return baseScore + bonus
    }

    /**
     * 画面内の候補POI群に対して Google マップ式の
     * 1) 同一名称・近接重複排除 (駅の複数ホームや同一店舗の重複ノード統合)
     * 2) 8×8 空間グリッド分散配置 (画面中央への一極集中を防ぎ画面全体へバランス配置)
     * 3) Prominence 優先のアイコン間隔制御 (Greedy Collision Avoidance)
     * を実行し、優先度降順で最大 [limit] 件を返す。
     */
    internal fun rankAndDistributeViewportPois(
        candidates: List<RawPoiCandidate>,
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        zoom: Double,
        limit: Int = 250,
    ): List<com.gorite.cyclemap.contract.NearbySpot> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val centerLat = (minLat + maxLat) * 0.5
        val centerLon = (minLon + maxLon) * 0.5
        val latSpan = (maxLat - minLat).coerceAtLeast(0.002)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.002)

        data class ScoredPoi(
            val spot: com.gorite.cyclemap.contract.NearbySpot,
            val score: Int,
            val normName: String,
            val catPrefix: String,
            val centerDistSq: Double,
        )

        val scoredAll = ArrayList<ScoredPoi>(candidates.size)
        for (c in candidates) {
            if (zoom < 16.0 && (c.category.startsWith("highway:bus_stop") || c.category.startsWith("tourism:information") || c.category.startsWith("tourism:artwork"))) {
                continue
            }
            val score = computePoiProminenceScore(c.name, c.category, c.osmType, c.hasAddress)
            if (score <= 0) continue
            val dLat = c.latitude - centerLat
            val dLon = c.longitude - centerLon
            val distSq = dLat * dLat + dLon * dLon
            val catPrefix = c.category.substringBefore(':')
            scoredAll += ScoredPoi(
                spot = com.gorite.cyclemap.contract.NearbySpot(c.name, c.category, c.latitude, c.longitude, score.toDouble()),
                score = score,
                normName = c.name.trim(),
                catPrefix = catPrefix,
                centerDistSq = distSq,
            )
        }
        if (scoredAll.isEmpty()) return emptyList()

        // Prominence降順 → 画面中心に近い順でソート
        scoredAll.sortWith(compareByDescending<ScoredPoi> { it.score }.thenBy { it.centerDistSq })

        // Step 1: 同一駅・同一店舗の近接重複排除 (駅は ~180m、その他同名スポットは ~65m 以内を統合)
        val deduped = ArrayList<ScoredPoi>(scoredAll.size)
        for (item in scoredAll) {
            val isStation = item.spot.category.startsWith("railway:") || item.spot.category.startsWith("public_transport:")
            val dupRadiusDeg = if (isStation) 0.0017 else 0.0006
            val isDuplicate = deduped.any { existing ->
                existing.normName == item.normName &&
                    existing.catPrefix == item.catPrefix &&
                    kotlin.math.abs(existing.spot.latitude - item.spot.latitude) < dupRadiusDeg &&
                    kotlin.math.abs(existing.spot.longitude - item.spot.longitude) < dupRadiusDeg
            }
            if (!isDuplicate) {
                deduped += item
            }
        }

        // Step 2: 8×8 空間グリッドに振り分け、画面全体の偏りを防ぐ
        val gridCols = 8
        val gridRows = 8
        val cells = Array(gridRows * gridCols) { ArrayList<ScoredPoi>() }
        for (item in deduped) {
            val r = (((item.spot.latitude - minLat) / latSpan) * gridRows).toInt().coerceIn(0, gridRows - 1)
            val col = (((item.spot.longitude - minLon) / lonSpan) * gridCols).toInt().coerceIn(0, gridCols - 1)
            cells[r * gridCols + col].add(item)
        }

        // セル自体を「セル内の最高Prominenceスコア降順 → 中心距離順」に並べる
        val activeCells = cells.filter { it.isNotEmpty() }
            .sortedWith(compareByDescending<ArrayList<ScoredPoi>> { it[0].score }.thenBy { it[0].centerDistSq })

        // ラウンドロビンでグリッド分散順のリストを構築
        val gridOrdered = ArrayList<ScoredPoi>(deduped.size)
        var depth = 0
        var addedInRound = true
        while (addedInRound) {
            addedInRound = false
            for (cell in activeCells) {
                if (depth < cell.size) {
                    gridOrdered += cell[depth]
                    addedInRound = true
                }
            }
            depth++
        }

        // Step 3: Greedy Collision Placement (Pass 1: 適度な間隔を保って配置 → Pass 2: 枠が余れば緩和して最大 limit まで補充)
        val selected = ArrayList<ScoredPoi>(limit)
        val selectedSet = HashSet<ScoredPoi>(limit)

        val pass1SepLat = latSpan / if (zoom >= 15.5) 38.0 else 28.0
        val pass1SepLon = lonSpan / if (zoom >= 15.5) 26.0 else 20.0

        for (candidate in gridOrdered) {
            if (selected.size >= limit) break
            // 駅・主要ランドマーク (Tier 1: score >= 880) は衝突判定を緩和して優先表示
            val factor = if (candidate.score >= 880) 0.35 else 1.0
            val minLatDiff = pass1SepLat * factor
            val minLonDiff = pass1SepLon * factor
            val tooClose = selected.any { placed ->
                kotlin.math.abs(placed.spot.latitude - candidate.spot.latitude) < minLatDiff &&
                    kotlin.math.abs(placed.spot.longitude - candidate.spot.longitude) < minLonDiff
            }
            if (!tooClose) {
                selected += candidate
                selectedSet += candidate
            }
        }

        // Pass 2: まだ limit (250件) に達していない場合は間隔を緩和してProminence順に補充
        if (selected.size < limit) {
            val pass2SepLat = pass1SepLat * 0.35
            val pass2SepLon = pass1SepLon * 0.35
            for (candidate in deduped) {
                if (selected.size >= limit) break
                if (candidate in selectedSet) continue
                val tooClose = selected.any { placed ->
                    kotlin.math.abs(placed.spot.latitude - candidate.spot.latitude) < pass2SepLat &&
                        kotlin.math.abs(placed.spot.longitude - candidate.spot.longitude) < pass2SepLon
                }
                if (!tooClose) {
                    selected += candidate
                    selectedSet += candidate
                }
            }
        }

        // 最終的に高Prominence順に並べて返す (MapLibre の描画順・ラベル優先度と一致させる)
        selected.sortWith(compareByDescending<ScoredPoi> { it.score }.thenBy { it.centerDistSq })
        return selected.map { it.spot }
    }
}
