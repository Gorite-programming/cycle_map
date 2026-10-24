package com.gorite.cyclemap.ui.cycling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationSourceTest {
    @Test
    fun labelsEstimatedDemAndMixedProfiles() {
        val estimated = listOf(ElevationSample(0.0, 10.0), ElevationSample(50.0, 12.0))
        val dem = listOf(
            ElevationSample(0.0, 10.0, ElevationSource.GSI_DEM),
            ElevationSample(50.0, 12.0, ElevationSource.GSI_DEM),
        )
        val mixed = listOf(
            ElevationSample(0.0, 10.0, ElevationSource.GSI_DEM),
            ElevationSample(50.0, 12.0, ElevationSource.ESTIMATED),
        )

        assertEquals("推定", elevationSourceLabel(estimated))
        assertEquals("DEM実測", elevationSourceLabel(dem))
        assertEquals("一部推定", elevationSourceLabel(mixed))
        assertEquals("標高未取得", elevationSourceLabel(emptyList()))
    }

    @Test
    fun syntheticFallbackSamplesAreMarkedEstimated() {
        val profile = buildRouteElevationProfile(emptyList(), 500.0)

        assertTrue(profile.isNotEmpty())
        assertEquals(setOf(ElevationSource.ESTIMATED), profile.map { it.source }.toSet())
    }
}
