package com.gorite.cyclemap

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.gorite.cyclemap.ui.cycling.NearbySpot
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
    limit: Int = 300,
): List<NearbySpot> {
    if (!dbFile.isFile || minLat >= maxLat || minLon >= maxLon || limit <= 0) return emptyList()

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

    val sql = """
        SELECT name, category, lat, lon
        FROM places
        WHERE lat BETWEEN ? AND ?
          AND lon BETWEEN ? AND ?
          $catWhere
          AND category NOT LIKE 'amenity:place_of_worship%'
          AND name IS NOT NULL AND trim(name) != ''
        LIMIT ?
    """.trimIndent()

    val args = ArrayList<String>()
    args.add(minLat.toString())
    args.add(maxLat.toString())
    args.add(minLon.toString())
    args.add(maxLon.toString())
    if (!categoryPrefixes.isNullOrEmpty()) {
        categoryPrefixes.forEach { args.add(if (it.endsWith("%")) it else "$it%") }
    }
    args.add(limit.toString())

    val found = ArrayList<NearbySpot>(limit)
    try {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    found += NearbySpot(
                        name = cursor.getString(0),
                        category = cursor.getString(1),
                        latitude = cursor.getDouble(2),
                        longitude = cursor.getDouble(3),
                        distanceM = 0.0,
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.w("CycleMap", "searchPoisInBounds failed: ${e.message}")
        return emptyList()
    }
    return found
}
