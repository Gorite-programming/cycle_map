package com.gorite.cyclemap.real

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.gorite.cyclemap.contract.BusinessStatus
import com.gorite.cyclemap.contract.PlaceDetail
import com.gorite.cyclemap.contract.PlaceDetailProvider
import com.gorite.cyclemap.search.OpenStatus
import com.gorite.cyclemap.search.OpeningHoursParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 契約 [PlaceDetailProvider] の本番 (Real) 実装。
 * - SQLite 検索DB (<name>.search.db) の places テーブルから POI 詳細を取得
 * - PRAGMA table_info(places) で opening_hours 列の有無を動的に検出 (旧DBでのクラッシュ防止)
 * - [OpeningHoursParser] と連携し、日またぎ (22:00-02:00) や 24/7 に対応した営業状態判定
 * - 端末タイムゾーン (LocalDateTime.now()) を使用、判定不能は安全に Unknown (表示なし)
 */
class RealPlaceDetailProvider(
    private val searchDbFileProvider: () -> File?,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : PlaceDetailProvider {

    // DBファイルパスごとの列存在キャッシュ
    private val columnSupportCache = ConcurrentHashMap<String, Set<String>>()

    override suspend fun getPlaceDetail(placeId: String): PlaceDetail? = withContext(Dispatchers.IO) {
        val dbFile = searchDbFileProvider() ?: return@withContext null
        if (!dbFile.exists() || !dbFile.canRead()) return@withContext null

        try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val columns = getColumnsForTable(db, "places")
                val hasOpeningHours = columns.contains("opening_hours")
                val hasPhone = columns.contains("phone")
                val hasWebsite = columns.contains("website")
                val hasAddress = columns.contains("address")

                val selectFields = buildList {
                    add("rowid AS id")
                    add("name")
                    add("category")
                    add("lat")
                    add("lon")
                    if (hasAddress) add("address") else add("'' AS address")
                    if (hasOpeningHours) add("opening_hours") else add("NULL AS opening_hours")
                    if (hasPhone) add("phone") else add("NULL AS phone")
                    if (hasWebsite) add("website") else add("NULL AS website")
                }.joinToString(", ")

                val isRowId = placeId.toLongOrNull() != null
                val query = if (isRowId) {
                    "SELECT $selectFields FROM places WHERE rowid = ? LIMIT 1"
                } else {
                    "SELECT $selectFields FROM places WHERE name = ? LIMIT 1"
                }

                db.rawQuery(query, arrayOf(placeId)).use { cursor ->
                    if (!cursor.moveToNext()) return@withContext null

                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
                    val lat = cursor.getDouble(cursor.getColumnIndexOrThrow("lat"))
                    val lon = cursor.getDouble(cursor.getColumnIndexOrThrow("lon"))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow("address")).ifBlank { "周辺エリア" }
                    val openingHoursText = cursor.getString(cursor.getColumnIndexOrThrow("opening_hours"))
                    val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"))
                    val website = cursor.getString(cursor.getColumnIndexOrThrow("website"))

                    val businessStatus = evaluateBusinessStatus(openingHoursText)

                    PlaceDetail(
                        id = id,
                        name = name,
                        category = category,
                        address = address,
                        distanceMeters = null,
                        lat = lat,
                        lon = lon,
                        businessStatus = businessStatus,
                        openingHoursText = openingHoursText,
                        phone = phone,
                        website = website
                    )
                }
            }
        } catch (_: SQLiteException) {
            null
        }
    }

    private fun evaluateBusinessStatus(openingHoursText: String?): BusinessStatus {
        if (openingHoursText.isNullOrBlank()) return BusinessStatus.Unknown
        val now = clock()
        val status = OpeningHoursParser.evaluate(openingHoursText, now)
        return when (status) {
            OpenStatus.Open -> BusinessStatus.Open
            OpenStatus.Closed -> BusinessStatus.Closed
            OpenStatus.Unknown -> BusinessStatus.Unknown
        }
    }

    private fun getColumnsForTable(db: SQLiteDatabase, tableName: String): Set<String> {
        val path = db.path
        columnSupportCache[path]?.let { return it }

        val cols = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        cols.add(cursor.getString(nameIndex))
                    }
                }
            }
        } catch (_: Exception) {
            // エラー時はデフォルトのみ
        }
        columnSupportCache[path] = cols
        return cols
    }
}
