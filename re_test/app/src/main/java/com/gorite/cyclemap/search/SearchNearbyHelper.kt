package com.gorite.cyclemap.search

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.gorite.cyclemap.contract.NearbySpot
import com.gorite.cyclemap.utils.*
import java.io.File

/**
 * 周辺POIをオフライン検索DBから取得する (ブロッキング・IOスレッドで呼ぶこと)。
 * ズームに応じた表示数制限は呼び出し側で行う。
 */
internal fun searchNearbyPlaces(
    dbFile: File,
    centerLat: Double,
    centerLon: Double,
    radiusMeters: Double,
    limit: Int = 40,
): List<NearbySpot> {
    if (!dbFile.isFile || radiusMeters <= 0) return emptyList()
    // 緯度経度のおおまかなbbox (余裕を持たせ、厳密距離はKotlin側で判定)
    val latDelta = radiusMeters / 111_320.0
    val lonDelta = radiusMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.2))
    val found = ArrayList<NearbySpot>(limit * 2)
    // LIMIT前にSQL側で近似距離順に並べ、真の最近傍の切り捨てを防ぐ (厳密な半径・ソートはKotlin側)。
    val orderNearby = NEARBY_ORDER_BY
    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery(
            """
            SELECT name, category, lat, lon
            FROM places
            WHERE lat BETWEEN ? AND ?
              AND lon BETWEEN ? AND ?
              AND (
                category LIKE 'shop:convenience%' OR category LIKE 'amenity:toilets%' OR
                category LIKE 'railway:station%' OR category LIKE 'public_transport%' OR
                category LIKE 'amenity:bus%' OR category LIKE 'highway:bus_stop%' OR
                category LIKE 'amenity:restaurant%' OR category LIKE 'amenity:cafe%' OR
                category LIKE 'amenity:fast_food%' OR category LIKE 'tourism:%' OR
                category LIKE 'amenity:parking%' OR category LIKE 'amenity:hospital%' OR
                category LIKE 'amenity:clinic%' OR category LIKE 'amenity:doctors%' OR
                category LIKE 'amenity:dentist%' OR category LIKE 'amenity:fuel%' OR
                category LIKE 'leisure:park%' OR category LIKE 'amenity:drinking_water%' OR
                category LIKE 'amenity:vending_machine%' OR category LIKE 'amenity:place_of_worship%' OR
                category LIKE 'railway:halt%' OR
                category LIKE 'shop:bicycle%' OR category LIKE 'shop:bakery%' OR
                category LIKE 'amenity:public_bath%'
              )
            $orderNearby
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                (centerLat - latDelta).toString(),
                (centerLat + latDelta).toString(),
                (centerLon - lonDelta).toString(),
                (centerLon + lonDelta).toString(),
                *(nearbyOrderArgs(centerLat, centerLon).toTypedArray()),
                (limit * 3).toString(),
            ),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val lat = cursor.getDouble(2)
                val lon = cursor.getDouble(3)
                val dist = haversineMeters(centerLat, centerLon, lat, lon)
                if (dist <= radiusMeters) {
                    found += NearbySpot(cursor.getString(0), cursor.getString(1), lat, lon, dist)
                }
            }
        }
    }
    return found.sortedBy { it.distanceM }.take(limit)
}

/**
 * サイクリスト向けクイックスポット (カテゴリキー指定) をオフラインDBから高速・高精度に検索する。
 */
internal fun searchNearbyQuickSpots(
    dbFile: File,
    centerLat: Double,
    centerLon: Double,
    radiusMeters: Double,
    categoryKeys: List<String>,
    nameFilter: String? = null,
    limit: Int = 40,
): List<NearbySpot> {
    if (!dbFile.isFile || radiusMeters <= 0 || categoryKeys.isEmpty()) return emptyList()
    val latDelta = radiusMeters / 111_320.0
    val lonDelta = radiusMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.2))
    val found = ArrayList<NearbySpot>(limit * 2)
    val orderNearby = NEARBY_ORDER_BY

    val categoryClauses = categoryKeys.joinToString(" OR ") { "category LIKE ?" }
    val nameClause = if (!nameFilter.isNullOrBlank()) " AND name LIKE ?" else ""
    val sql = """
        SELECT name, category, lat, lon
        FROM places
        WHERE lat BETWEEN ? AND ?
          AND lon BETWEEN ? AND ?
          AND ($categoryClauses)
          $nameClause
          AND category NOT LIKE 'amenity:place_of_worship%'
        $orderNearby
        LIMIT ?
    """.trimIndent()

    val args = ArrayList<String>()
    args.add((centerLat - latDelta).toString())
    args.add((centerLat + latDelta).toString())
    args.add((centerLon - lonDelta).toString())
    args.add((centerLon + lonDelta).toString())
    categoryKeys.forEach { args.add("$it%") }
    if (!nameFilter.isNullOrBlank()) {
        args.add("%$nameFilter%")
    }
    args.addAll(nearbyOrderArgs(centerLat, centerLon))
    args.add((limit * 3).toString())

    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val lat = cursor.getDouble(2)
                val lon = cursor.getDouble(3)
                val dist = haversineMeters(centerLat, centerLon, lat, lon)
                if (dist <= radiusMeters) {
                    found += NearbySpot(cursor.getString(0), cursor.getString(1), lat, lon, dist)
                }
            }
        }
    }
    return found.sortedBy { it.distanceM }.take(limit)
}

/**
 * 画面表示領域 (BBox: minLat, minLon, maxLat, maxLon) 内にある主要POIをオフライン検索DBから高速に取得する。
 * 空間インデックス (lat, lon) を直接利用して数ミリ秒で応答する。
 */
internal fun searchPoisInBounds(
    dbFile: File,
    minLat: Double,
    minLon: Double,
    maxLat: Double,
    maxLon: Double,
    categoryPrefixes: List<String>? = null,
    limit: Int = 250,
    zoom: Double = 15.0,
): List<NearbySpot> {
    if (!dbFile.isFile || minLat >= maxLat || minLon >= maxLon || limit <= 0) return emptyList()

    val centerLat = (minLat + maxLat) * 0.5
    val centerLon = (minLon + maxLon) * 0.5

    val catWhere = if (!categoryPrefixes.isNullOrEmpty()) {
        val clauses = categoryPrefixes.joinToString(" OR ") { "category LIKE ?" }
        "AND ($clauses)"
    } else {
        """
        AND (
            category LIKE 'shop:convenience%' OR
            category LIKE 'amenity:toilets%' OR
            category LIKE 'railway:station%' OR
            category LIKE 'railway:halt%' OR
            category LIKE 'public_transport:station%' OR
            category LIKE 'amenity:bus_station%' OR
            category LIKE 'amenity:restaurant%' OR
            category LIKE 'amenity:cafe%' OR
            category LIKE 'amenity:fast_food%' OR
            category LIKE 'tourism:%' OR
            category LIKE 'shop:bicycle%' OR
            category LIKE 'amenity:hospital%' OR
            category LIKE 'amenity:clinic%' OR
            category LIKE 'amenity:doctors%' OR
            category LIKE 'leisure:park%' OR
            category LIKE 'amenity:drinking_water%' OR
            category LIKE 'amenity:public_bath%' OR
            category LIKE 'amenity:fuel%'
        )
        """.trimIndent()
    }

    val zoomFilterSql = if (zoom < 16.0) {
        """
        AND category NOT LIKE 'highway:bus_stop%'
        AND category NOT LIKE 'tourism:information%'
        AND category NOT LIKE 'tourism:artwork%'
        """.trimIndent()
    } else {
        ""
    }

    val nearbyOrderExpr = NEARBY_ORDER_BY.trim().removePrefix("ORDER BY").trim()
    // Google マップ式の階層優先度で SQL 候補プールを抽出し、駅・観光拠点・自転車店・病院・コンビニが確実に含まれるようにする
    val sqlPriorityCase = """
        CASE
            WHEN category LIKE 'railway:station%' OR category LIKE 'public_transport:station%' OR category LIKE 'amenity:bus_station%' THEN 1
            WHEN category LIKE 'tourism:attraction%' OR category LIKE 'tourism:museum%' OR category LIKE 'tourism:theme_park%' OR category LIKE 'tourism:viewpoint%' THEN 2
            WHEN category LIKE 'shop:bicycle%' OR category LIKE 'amenity:hospital%' OR category LIKE 'shop:convenience%' THEN 3
            WHEN category LIKE 'amenity:public_bath%' OR category LIKE 'leisure:park%' OR category LIKE 'amenity:toilets%' OR category LIKE 'amenity:drinking_water%' THEN 4
            WHEN category LIKE 'tourism:hotel%' OR category LIKE 'amenity:fuel%' OR category LIKE 'amenity:fast_food%' OR category LIKE 'amenity:cafe%' OR category LIKE 'amenity:restaurant%' THEN 5
            ELSE 6
        END ASC,
        $nearbyOrderExpr
    """.trimIndent()

    val candidatePoolLimit = (limit * 6).coerceIn(500, 1800)

    val args = ArrayList<String>()
    args.add(minLat.toString())
    args.add(maxLat.toString())
    args.add(minLon.toString())
    args.add(maxLon.toString())
    if (!categoryPrefixes.isNullOrEmpty()) {
        categoryPrefixes.forEach { args.add(if (it.endsWith("%")) it else "$it%") }
    }
    args.addAll(nearbyOrderArgs(centerLat, centerLon))
    args.add(candidatePoolLimit.toString())

    val rawCandidates = ArrayList<SearchRanking.RawPoiCandidate>(candidatePoolLimit)
    try {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val sqlWithMeta = """
                SELECT name, category, lat, lon, osm_type, address
                FROM places
                WHERE lat BETWEEN ? AND ?
                  AND lon BETWEEN ? AND ?
                  $catWhere
                  AND category NOT LIKE 'amenity:place_of_worship%'
                  AND category NOT LIKE 'public_transport:stop_position%'
                  AND category NOT LIKE 'public_transport:platform%'
                  $zoomFilterSql
                  AND name IS NOT NULL AND trim(name) != ''
                ORDER BY $sqlPriorityCase
                LIMIT ?
            """.trimIndent()

            val cursor = try {
                db.rawQuery(sqlWithMeta, args.toTypedArray())
            } catch (_: Exception) {
                val fallbackSql = """
                    SELECT name, category, lat, lon
                    FROM places
                    WHERE lat BETWEEN ? AND ?
                      AND lon BETWEEN ? AND ?
                      $catWhere
                      AND category NOT LIKE 'amenity:place_of_worship%'
                      AND category NOT LIKE 'public_transport:stop_position%'
                      AND category NOT LIKE 'public_transport:platform%'
                      $zoomFilterSql
                      AND name IS NOT NULL AND trim(name) != ''
                    ORDER BY $sqlPriorityCase
                    LIMIT ?
                """.trimIndent()
                db.rawQuery(fallbackSql, args.toTypedArray())
            }

            cursor.use { c ->
                val hasMeta = c.columnCount >= 6
                while (c.moveToNext()) {
                    val name = if (c.isNull(0)) "" else (c.getString(0) ?: "")
                    val cat = if (c.isNull(1)) "" else (c.getString(1) ?: "")
                    if (name.isBlank() || cat.isBlank()) continue
                    val osmType = if (hasMeta && !c.isNull(4)) c.getString(4) else null
                    val addr = if (hasMeta && !c.isNull(5)) c.getString(5) else null
                    rawCandidates += SearchRanking.RawPoiCandidate(
                        name = name,
                        category = cat,
                        latitude = c.getDouble(2),
                        longitude = c.getDouble(3),
                        osmType = osmType,
                        hasAddress = !addr.isNullOrBlank(),
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.w("CycleMap", "searchPoisInBounds failed: ${e.message}")
        return emptyList()
    }

    return SearchRanking.rankAndDistributeViewportPois(
        candidates = rawCandidates,
        minLat = minLat,
        minLon = minLon,
        maxLat = maxLat,
        maxLon = maxLon,
        zoom = zoom,
        limit = limit,
    )
}
