package com.gorite.cyclemap.ui.map

import com.gorite.cyclemap.contract.BusinessStatus
import com.gorite.cyclemap.contract.EtaInfo
import com.gorite.cyclemap.contract.GuidanceStep
import com.gorite.cyclemap.contract.LaneInfo
import com.gorite.cyclemap.contract.PlaceDetail
import com.gorite.cyclemap.contract.RouteOption
import com.gorite.cyclemap.contract.TravelModeType
import com.gorite.cyclemap.contract.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 契約モデルおよび UI 状態の単体テスト
 */
class ContractUiTest {

    @Test
    fun testTravelModeTypes() {
        val modes = TravelModeType.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(TravelModeType.Bicycle))
        assertTrue(modes.contains(TravelModeType.Walk))
        assertTrue(modes.contains(TravelModeType.Car))
    }

    @Test
    fun testBusinessStatusValues() {
        val open = BusinessStatus.Open
        val closing = BusinessStatus.ClosingSoon("21:00")
        val closed = BusinessStatus.Closed
        val unknown = BusinessStatus.Unknown

        assertEquals("21:00", closing.closingTime)
        assertTrue(open is BusinessStatus)
        assertTrue(closing is BusinessStatus)
        assertTrue(closed is BusinessStatus)
        assertTrue(unknown is BusinessStatus)
    }

    @Test
    fun testPlaceDetailContract() {
        val place = PlaceDetail(
            id = "poi_123",
            name = "平和記念公園",
            category = "公園・観光名所",
            address = "広島市中区中島町1-1",
            distanceMeters = 350.0,
            lat = 34.3928,
            lon = 132.4526,
            businessStatus = BusinessStatus.Open,
            openingHoursText = "24時間営業",
            phone = "082-241-4004",
            website = "https://www.city.hiroshima.lg.jp"
        )

        assertEquals("poi_123", place.id)
        assertEquals("平和記念公園", place.name)
        assertEquals(BusinessStatus.Open, place.businessStatus)
        assertEquals(350.0, place.distanceMeters ?: 0.0, 0.01)
        assertNotNull(place.phone)
        assertNotNull(place.website)

        val unknownPlace = PlaceDetail(
            id = "poi_456",
            name = "無名地点",
            category = "地点",
            address = "広島市中区",
            distanceMeters = null,
            lat = 34.0,
            lon = 132.0,
            businessStatus = BusinessStatus.Unknown,
            openingHoursText = null,
            phone = null,
            website = null
        )
        assertEquals("広島市中区", unknownPlace.address)
        assertNull(unknownPlace.phone)
        assertEquals(BusinessStatus.Unknown, unknownPlace.businessStatus)
    }

    @Test
    fun testRouteOptionComparison() {
        val route1 = RouteOption(
            id = "route_fast",
            title = "最短ルート",
            travelMode = TravelModeType.Bicycle,
            distanceMeters = 3200.0,
            durationSeconds = 640.0,
            elevationGainMeters = 15.0,
            isPrimary = true
        )
        val route2 = RouteOption(
            id = "route_scenic",
            title = "景観ルート",
            travelMode = TravelModeType.Bicycle,
            distanceMeters = 3900.0,
            durationSeconds = 780.0,
            elevationGainMeters = 20.0,
            isPrimary = false
        )

        assertTrue(route1.isPrimary)
        assertFalse(route2.isPrimary)
        assertTrue(route1.distanceMeters < route2.distanceMeters)
        assertTrue(route1.durationSeconds < route2.durationSeconds)
    }

    @Test
    fun testGuidanceStepAndLanes() {
        val step = GuidanceStep(
            turnType = TurnType.Right,
            instructionText = "紙屋町交差点を右折",
            streetName = "相生通り",
            distanceToTurnMeters = 120.0
        )
        assertEquals(TurnType.Right, step.turnType)
        assertEquals("紙屋町交差点を右折", step.instructionText)
        assertEquals("相生通り", step.streetName)
        assertEquals(120.0, step.distanceToTurnMeters, 0.01)

        val lanes = listOf(
            LaneInfo(TurnType.Straight, isRecommended = false),
            LaneInfo(TurnType.Right, isRecommended = true)
        )
        assertEquals(2, lanes.size)
        assertFalse(lanes[0].isRecommended)
        assertTrue(lanes[1].isRecommended)
    }

    @Test
    fun testEtaInfoContract() {
        val eta = EtaInfo(
            remainingDistanceMeters = 6200.0,
            remainingSeconds = 1500.0,
            arrivalEpochMillis = 1700000000000L,
            currentSpeedKmh = 18.5
        )
        assertEquals(1700000000000L, eta.arrivalEpochMillis)
        assertEquals(1500.0, eta.remainingSeconds, 0.01)
        assertEquals(6200.0, eta.remainingDistanceMeters, 0.01)
        assertEquals(18.5, eta.currentSpeedKmh, 0.01)
    }
}
