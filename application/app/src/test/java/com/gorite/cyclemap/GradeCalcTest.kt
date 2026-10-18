package com.gorite.cyclemap

import com.gorite.cyclemap.ui.cycling.ElevationSample
import com.gorite.cyclemap.ui.cycling.forwardGradeAt
import com.gorite.cyclemap.ui.cycling.gradeAt
import com.gorite.cyclemap.ui.cycling.gradeMetrics
import com.gorite.cyclemap.ui.cycling.gradeSummary
import com.gorite.cyclemap.ui.cycling.maxDownhillGrade
import com.gorite.cyclemap.ui.cycling.maxGrade
import com.gorite.cyclemap.ui.cycling.maxUphillGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 勾配計算 (現在周辺・この先・最大上り/下り) の検証。 */
class GradeCalcTest {

    private fun ramp(totalM: Double, gainM: Double, stepM: Double = 10.0): List<ElevationSample> {
        val count = (totalM / stepM).toInt()
        return List(count + 1) { i ->
            val d = i * stepM
            ElevationSample(d, gainM * d / totalM)
        }
    }

    @Test
    fun currentGrade_usesCenteredWindow() {
        // +10% の一様坂: 中央では前後80m平均 = +10%
        val profile = ramp(1000.0, 100.0)
        assertEquals(10.0, gradeAt(profile, 500.0)!!, 1e-9)
    }

    @Test
    fun currentGrade_clampsAtStart() {
        // 始点では 0〜40m で計算 (区間短縮)
        val profile = ramp(1000.0, 100.0)
        assertEquals(10.0, gradeAt(profile, 0.0)!!, 1e-9)
    }

    @Test
    fun forwardGrade_measuresAhead() {
        val profile = ramp(1000.0, 100.0)
        assertEquals(10.0, forwardGradeAt(profile, 500.0)!!, 1e-9)
    }

    @Test
    fun forwardGrade_shortensNearFinish() {
        // 残り20mしかない地点では20mで計算
        val profile = ramp(100.0, 10.0, stepM = 5.0)
        assertEquals(10.0, forwardGradeAt(profile, 80.0)!!, 1e-9)
    }

    @Test
    fun forwardGrade_atFinish_returnsNull() {
        val profile = ramp(100.0, 10.0)
        assertNull(forwardGradeAt(profile, 100.0))
    }

    @Test
    fun downhill_isNegative() {
        val profile = ramp(1000.0, -50.0)
        assertEquals(-5.0, gradeAt(profile, 500.0)!!, 1e-9)
        assertEquals(-5.0, forwardGradeAt(profile, 500.0)!!, 1e-9)
    }

    @Test
    fun summary_separatesUphillAndDownhill() {
        // 上り+10%後に下り-10%: 最大上りと最大下りを別々に取得
        val up = ramp(500.0, 50.0).dropLast(1)
        val down = (0..50).map { i -> ElevationSample(500.0 + i * 10.0, 50.0 - i * 1.0) }
        val summary = gradeSummary(up + down)!!
        assertEquals(10.0, summary.maxUphillPct!!, 0.5)
        assertEquals(-10.0, summary.maxDownhillPct!!, 0.5)
        assertTrue(summary.maxDownhillPct!! < 0.0)
        assertEquals(summary.maxUphillPct, maxGrade(up + down))
        assertEquals(summary.maxUphillPct, maxUphillGrade(up + down))
        assertEquals(summary.maxDownhillPct, maxDownhillGrade(up + down))
    }

    @Test
    fun summary_catchesShortSteepPitch() {
        // 5km平坦の中に15mの20%坂: 100分割(500m刻み)では見落とすが10m走査では拾う
        val profile = ArrayList<ElevationSample>()
        var d = 0.0
        while (d < 2500.0) {
            profile += ElevationSample(d, 0.0)
            d += 10.0
        }
        // 2500m〜2515mで3m上る (20%)
        var e = 0.0
        while (d <= 2515.0) {
            e += 1.0
            profile += ElevationSample(d, e)
            d += 5.0
        }
        while (d <= 5000.0) {
            profile += ElevationSample(d, 3.0)
            d += 10.0
        }
        val summary = gradeSummary(profile)!!
        // 80m平滑で希釈される (15m*20%/80m=3.75%) が、0にはならない
        assertTrue("short pitch detected: ${summary.maxUphillPct}", summary.maxUphillPct!! > 3.0)
    }

    @Test
    fun emptyOrSingle_returnsNull() {
        assertNull(gradeAt(emptyList(), 0.0))
        assertNull(forwardGradeAt(emptyList(), 0.0))
        assertNull(gradeSummary(emptyList()))
        assertNull(maxGrade(emptyList()))
        val single = listOf(ElevationSample(0.0, 10.0))
        assertNull(gradeAt(single, 0.0))
        assertNull(forwardGradeAt(single, 0.0))
        assertNull(gradeSummary(single))
    }

    @Test
    fun metrics_assemblesAllFields() {
        val profile = ramp(1000.0, 100.0)
        val summary = gradeSummary(profile)!!
        val metrics = gradeMetrics(profile, 500.0, summary)
        assertEquals(10.0, metrics.currentGradePct!!, 1e-9)
        assertEquals(10.0, metrics.forwardGradePct!!, 1e-9)
        assertEquals(summary.maxUphillPct!!, metrics.maxUphillPct!!, 1e-9)
        assertEquals(summary.maxDownhillPct!!, metrics.maxDownhillPct!!, 1e-9)
    }
}
