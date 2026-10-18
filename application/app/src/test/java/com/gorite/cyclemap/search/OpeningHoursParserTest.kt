package com.gorite.cyclemap.search

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class OpeningHoursParserTest {

    // 2026-10-12 is Monday, 13 Tue, 14 Wed, 15 Thu, 16 Fri, 17 Sat, 18 Sun

    @Test
    fun test01_NullAndEmptyReturnUnknown() {
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate(null))
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate(""))
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate("   "))
    }

    @Test
    fun test02_24_7_IsOpenAnytime() {
        val dt = LocalDateTime.of(2026, 10, 12, 3, 15) // Mon 03:15
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("24/7", dt))
    }

    @Test
    fun test03_24_7_CaseInsensitive() {
        val dt = LocalDateTime.of(2026, 10, 18, 23, 50) // Sun 23:50
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("24/7", dt))
    }

    @Test
    fun test04_ExplicitClosed() {
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("closed"))
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("off"))
    }

    @Test
    fun test05_SimpleDailyRange_DuringHours() {
        val dt = LocalDateTime.of(2026, 10, 12, 10, 0) // Mon 10:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("09:00-18:00", dt))
    }

    @Test
    fun test06_SimpleDailyRange_BeforeHours() {
        val dt = LocalDateTime.of(2026, 10, 12, 8, 30) // Mon 08:30
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("09:00-18:00", dt))
    }

    @Test
    fun test07_SimpleDailyRange_AfterHours() {
        val dt = LocalDateTime.of(2026, 10, 12, 18, 30) // Mon 18:30
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("09:00-18:00", dt))
    }

    @Test
    fun test08_WeekdayRange_DuringWeekday() {
        val dt = LocalDateTime.of(2026, 10, 14, 12, 0) // Wed 12:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("Mo-Fr 09:00-18:00", dt))
    }

    @Test
    fun test09_WeekdayRange_WeekendClosed() {
        val dt = LocalDateTime.of(2026, 10, 17, 12, 0) // Sat 12:00
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate("Mo-Fr 09:00-18:00", dt))
    }

    @Test
    fun test10_WeekdayWithExplicitSaturdayOff() {
        val dt = LocalDateTime.of(2026, 10, 17, 12, 0) // Sat 12:00
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("Mo-Fr 09:00-18:00; Sa off", dt))
    }

    @Test
    fun test11_SaturdayOpenDifferentHours() {
        val dt = LocalDateTime.of(2026, 10, 17, 11, 0) // Sat 11:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("Mo-Fr 09:00-18:00; Sa 10:00-14:00", dt))
    }

    @Test
    fun test12_SaturdayClosedAfterShortHours() {
        val dt = LocalDateTime.of(2026, 10, 17, 15, 0) // Sat 15:00
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("Mo-Fr 09:00-18:00; Sa 10:00-14:00", dt))
    }

    @Test
    fun test13_OvernightHours_BeforeMidnight() {
        val dt = LocalDateTime.of(2026, 10, 12, 23, 0) // Mon 23:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("Mo 22:00-02:00", dt))
    }

    @Test
    fun test14_OvernightHours_AfterMidnight() {
        val dt = LocalDateTime.of(2026, 10, 13, 1, 30) // Tue 01:30 (continuation of Mon night)
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("Mo 22:00-02:00", dt))
    }

    @Test
    fun test15_OvernightHours_AfterClosing() {
        val dt = LocalDateTime.of(2026, 10, 13, 2, 30) // Tue 02:30
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate("Mo 22:00-02:00", dt))
    }

    @Test
    fun test16_CommaSeparatedDays() {
        val dt = LocalDateTime.of(2026, 10, 13, 10, 0) // Tue 10:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("Mo,Tu,Th 09:00-17:00", dt))
    }

    @Test
    fun test17_CommaSeparatedDays_ExcludedDay() {
        val dt = LocalDateTime.of(2026, 10, 14, 10, 0) // Wed 10:00 (excluded)
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate("Mo,Tu,Th 09:00-17:00", dt))
    }

    @Test
    fun test18_SplitHoursLunchBreak_DuringLunch() {
        val dt = LocalDateTime.of(2026, 10, 12, 13, 30) // Mon 13:30 (break)
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("11:00-13:00, 17:00-21:00", dt))
    }

    @Test
    fun test19_SplitHoursLunchBreak_MorningOpen() {
        val dt = LocalDateTime.of(2026, 10, 12, 12, 0) // Mon 12:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("11:00-13:00, 17:00-21:00", dt))
    }

    @Test
    fun test20_SplitHoursLunchBreak_EveningOpen() {
        val dt = LocalDateTime.of(2026, 10, 12, 19, 0) // Mon 19:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("11:00-13:00, 17:00-21:00", dt))
    }

    @Test
    fun test21_BoundaryStartExact() {
        val dt = LocalDateTime.of(2026, 10, 12, 9, 0) // Mon 09:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("09:00-18:00", dt))
    }

    @Test
    fun test22_BoundaryEndExact() {
        val dt = LocalDateTime.of(2026, 10, 12, 18, 0) // Mon 18:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("09:00-18:00", dt))
    }

    @Test
    fun test23_SundayOnlyStore() {
        val dtSun = LocalDateTime.of(2026, 10, 18, 10, 0) // Sun 10:00
        val dtMon = LocalDateTime.of(2026, 10, 12, 10, 0) // Mon 10:00
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("Su 09:00-15:00", dtSun))
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate("Su 09:00-15:00", dtMon))
    }

    @Test
    fun test24_WrapAroundDaysRange_SaToMo() {
        // Sa-Mo = Sat, Sun, Mon
        val dtSat = LocalDateTime.of(2026, 10, 17, 12, 0)
        val dtSun = LocalDateTime.of(2026, 10, 18, 12, 0)
        val dtMon = LocalDateTime.of(2026, 10, 12, 12, 0)
        val dtTue = LocalDateTime.of(2026, 10, 13, 12, 0)

        val rule = "Sa-Mo 10:00-20:00"
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate(rule, dtSat))
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate(rule, dtSun))
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate(rule, dtMon))
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate(rule, dtTue))
    }

    @Test
    fun test25_InvalidGarbageSyntaxReturnsUnknown() {
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate("invalid syntax here"))
        assertEquals(OpenStatus.Unknown, OpeningHoursParser.evaluate("12:00 - ??:??"))
    }

    @Test
    fun test26_EarlyMorningBakery() {
        val dt = LocalDateTime.of(2026, 10, 12, 6, 30) // Mon 06:30
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("06:00-14:00", dt))
    }

    @Test
    fun test27_LateNightBar() {
        val dt = LocalDateTime.of(2026, 10, 12, 23, 45) // Mon 23:45
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate("20:00-03:00", dt))
    }

    @Test
    fun test28_WednesdayClosedSalon() {
        val dtWed = LocalDateTime.of(2026, 10, 14, 11, 0) // Wed 11:00
        val dtThu = LocalDateTime.of(2026, 10, 15, 11, 0) // Thu 11:00
        val rule = "Mo-Tu 10:00-19:00; We off; Th-Su 10:00-19:00"
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate(rule, dtWed))
        assertEquals(OpenStatus.Open, OpeningHoursParser.evaluate(rule, dtThu))
    }

    @Test
    fun test29_SingleMinuteBeforeOpening() {
        val dt = LocalDateTime.of(2026, 10, 12, 8, 59) // 08:59
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("09:00-18:00", dt))
    }

    @Test
    fun test30_SingleMinuteAfterClosing() {
        val dt = LocalDateTime.of(2026, 10, 12, 18, 1) // 18:01
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("09:00-18:00", dt))
    }

    @Test
    fun test31_PublicHolidayOffDoesNotCrash() {
        // PH is public holiday token in OSM
        val dt = LocalDateTime.of(2026, 10, 12, 10, 0)
        val res = OpeningHoursParser.evaluate("Mo-Fr 09:00-18:00; PH off", dt)
        assertEquals(OpenStatus.Open, res)
    }

    @Test
    fun test32_AllDaysExplicitClosed() {
        val dt = LocalDateTime.of(2026, 10, 13, 10, 0)
        assertEquals(OpenStatus.Closed, OpeningHoursParser.evaluate("Mo-Su off", dt))
    }
}
