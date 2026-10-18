package com.gorite.cyclemap.real

import android.database.sqlite.SQLiteDatabase
import com.gorite.cyclemap.contract.BusinessStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

@Ignore("Requires Robolectric or instrumented test on Android runtime")
class RealPlaceDetailProviderTest {

    private lateinit var tempDbFile: File
    private lateinit var legacyDbFile: File

    @Before
    fun setUp() {
        // 新形式DB: opening_hours, phone, website 列あり
        tempDbFile = File.createTempFile("test_places", ".db")
        SQLiteDatabase.openOrCreateDatabase(tempDbFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE places (
                    rowid INTEGER PRIMARY KEY,
                    name TEXT,
                    category TEXT,
                    lat REAL,
                    lon REAL,
                    address TEXT,
                    opening_hours TEXT,
                    phone TEXT,
                    website TEXT
                )
                """.trimIndent()
            )
            // 24/7
            db.execSQL(
                "INSERT INTO places VALUES (1, 'ローソン広島駅前店', 'shop:convenience', 34.39, 132.45, '広島市東区', '24/7', '082-111-2222', 'https://lawson.jp')"
            )
            // 日またぎ 22:00-02:00
            db.execSQL(
                "INSERT INTO places VALUES (2, '夜更かしバー', 'amenity:bar', 34.39, 132.45, '広島市中区', 'Mo-Su 22:00-02:00', NULL, NULL)"
            )
        }

        // 旧形式DB: opening_hours 列が無い
        legacyDbFile = File.createTempFile("legacy_places", ".db")
        SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE places (
                    rowid INTEGER PRIMARY KEY,
                    name TEXT,
                    category TEXT,
                    lat REAL,
                    lon REAL,
                    address TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO places VALUES (1, '旧形式の公民館', 'amenity:community_centre', 34.39, 132.45, '広島市南区')"
            )
        }
    }

    @After
    fun tearDown() {
        tempDbFile.delete()
        legacyDbFile.delete()
    }

    @Test
    fun getPlaceDetail_24_7_returnsOpen() = runBlocking {
        val provider = RealPlaceDetailProvider(
            searchDbFileProvider = { tempDbFile },
            clock = { LocalDateTime.of(2026, 10, 13, 15, 30) } // 昼間
        )

        val detail = provider.getPlaceDetail("1")
        assertNotNull(detail)
        assertEquals("ローソン広島駅前店", detail?.name)
        assertEquals(BusinessStatus.Open, detail?.businessStatus)
        assertEquals("082-111-2222", detail?.phone)
    }

    @Test
    fun getPlaceDetail_overnightOpeningHours_evaluatedCorrectly() = runBlocking {
        // 深夜 01:00 (日またぎ営業中)
        val nightClock = { LocalDateTime.of(2026, 10, 13, 1, 0) }
        val nightProvider = RealPlaceDetailProvider(
            searchDbFileProvider = { tempDbFile },
            clock = nightClock
        )
        val nightDetail = nightProvider.getPlaceDetail("2")
        assertNotNull(nightDetail)
        assertEquals(BusinessStatus.Open, nightDetail?.businessStatus)

        // 昼 14:00 (営業時間外 Closed)
        val dayClock = { LocalDateTime.of(2026, 10, 13, 14, 0) }
        val dayProvider = RealPlaceDetailProvider(
            searchDbFileProvider = { tempDbFile },
            clock = dayClock
        )
        val dayDetail = dayProvider.getPlaceDetail("2")
        assertNotNull(dayDetail)
        assertEquals(BusinessStatus.Closed, dayDetail?.businessStatus)
    }

    @Test
    fun getPlaceDetail_legacyDbWithoutOpeningHours_safeFallbackToUnknown() = runBlocking {
        val provider = RealPlaceDetailProvider(
            searchDbFileProvider = { legacyDbFile }
        )

        val detail = provider.getPlaceDetail("1")
        assertNotNull(detail)
        assertEquals("旧形式の公民館", detail?.name)
        assertEquals(BusinessStatus.Unknown, detail?.businessStatus)
        assertNull(detail?.openingHoursText)
        assertNull(detail?.phone)
    }

    @Test
    fun getPlaceDetail_nonExistentId_returnsNull() = runBlocking {
        val provider = RealPlaceDetailProvider(
            searchDbFileProvider = { tempDbFile }
        )

        val detail = provider.getPlaceDetail("9999")
        assertNull(detail)
    }
}
