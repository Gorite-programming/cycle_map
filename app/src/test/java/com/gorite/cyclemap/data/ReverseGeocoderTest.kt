package com.gorite.cyclemap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 逆ジオコーディングの純粋ロジック (Android SQLite を使わない部分) の検証。 */
class ReverseGeocoderTest {

    @Test
    fun sanitizeTownName_keepsChome_dropsHouseNumber() {
        assertEquals("国泰寺町一丁目", ReverseGeocoder.sanitizeTownName("国泰寺町一丁目3-5"))
        assertEquals("国泰寺町一丁目", ReverseGeocoder.sanitizeTownName("国泰寺町一丁目"))
        assertEquals("小町", ReverseGeocoder.sanitizeTownName("小町"))
        assertEquals("大手町四丁目", ReverseGeocoder.sanitizeTownName("大手町四丁目12番地"))
    }

    @Test
    fun resolveWardName_dropsDuplicatedWard() {
        assertEquals(null, ReverseGeocoder.resolveWardName("東城町", "東城町新免"))
        assertEquals("中区", ReverseGeocoder.resolveWardName("中区", "国泰寺町一丁目"))
        assertEquals("中区", ReverseGeocoder.resolveWardName("中区", null))
        assertEquals(null, ReverseGeocoder.resolveWardName(null, "国泰寺町一丁目"))
    }

    @Test
    fun findPrefectureName_prefersNarrowestBox() {
        // 広島市中心部は島根bboxとも重なるが、狭い広島県を返す。
        assertEquals("広島県", findPrefectureName(34.3853, 132.4556))
        assertEquals("広島県", findPrefectureName(34.50, 132.80))
        assertEquals("山口県", findPrefectureName(34.10, 131.40))
        assertNull(findPrefectureName(34.00, 135.00))
    }

    private class FakeGeocoder(var address: String?) : OfflineReverseGeocoder {
        var calls = 0
        override fun lookup(latitude: Double, longitude: Double): AddressResult? {
            calls++
            return address?.let {
                AddressResult(prefecture = "広島県", city = "広島市", formattedAddress = it)
            }
        }
    }

    @Test
    fun controller_confirmsAfterTwoConsecutive() {
        val controller = AddressDisplayController()
        val geocoder = FakeGeocoder("広島県広島市中区国泰寺町一丁目")
        // 初回は即時表示。
        assertEquals(
            "広島県広島市中区国泰寺町一丁目",
            controller.update(34.3853, 132.4556, 10f, geocoder),
        )
        // 100m未満の移動では再検索しない。
        assertEquals(
            "広島県広島市中区国泰寺町一丁目",
            controller.update(34.3854, 132.4557, 10f, geocoder),
        )
        assertEquals(1, geocoder.calls)
        // 遠方へ移動: 1回目は切替保留、2回連続で確定。
        geocoder.address = "広島県広島市中区大手町四丁目"
        assertEquals(
            "広島県広島市中区国泰寺町一丁目",
            controller.update(34.3900, 132.4600, 10f, geocoder),
        )
        assertEquals(
            "広島県広島市中区大手町四丁目",
            controller.update(34.3910, 132.4610, 10f, geocoder),
        )
        assertEquals(3, geocoder.calls)
    }

    @Test
    fun controller_ignoresInaccurateFix_andUnknown() {
        val controller = AddressDisplayController()
        val geocoder = FakeGeocoder("広島県広島市中区国泰寺町一丁目")
        assertEquals(
            "広島県広島市中区国泰寺町一丁目",
            controller.update(34.3853, 132.4556, 10f, geocoder),
        )
        // 精度100m超は捨てて表示維持 (検索もしない)。
        assertEquals(
            "広島県広島市中区国泰寺町一丁目",
            controller.update(34.3900, 132.4600, 500f, geocoder),
        )
        assertEquals(1, geocoder.calls)
        // 非有限値は特定不能へ (初回は即時)。
        val c2 = AddressDisplayController()
        assertEquals(
            AddressDisplayController.ADDRESS_UNKNOWN,
            c2.update(Double.NaN, Double.NaN, 10f, geocoder),
        )
        // 圏外 (null) は特定不能表示へ。初回は即時。
        val c3 = AddressDisplayController()
        assertEquals(
            AddressDisplayController.ADDRESS_UNKNOWN,
            c3.update(36.0, 140.0, 10f, FakeGeocoder(null)),
        )
    }

    @Test
    fun administrativeBoundaries_inokuchi_fallsInNishiKu_notSaekiKu() {
        val lat = 34.366772
        val lon = 132.3779602
        val city = AdministrativeBoundaries.findCityOrTown("広島県", lat, lon)
        assertEquals("広島市", city)
        val ward = AdministrativeBoundaries.findWard("広島県", "広島市", lat, lon)
        assertEquals("西区", ward)
    }

    @Test
    fun selectHierarchy_inokuchi_correctlyResolvesNishiKuByPolygon() {
        val lat = 34.366772
        val lon = 132.3779602
        // 佐伯区役所の方が直線距離で圧倒的に近い (1.58km vs 6km) が、
        // 境界線ポリゴン判定により西区が選ばれることを検証する。
        val candidates = listOf(
            ReverseGeocoder.PlaceCandidate("井口五丁目", "place:neighbourhood", lat, lon, 0.0),
            ReverseGeocoder.PlaceCandidate("佐伯区", "place:suburb", 34.3638, 132.3614, 1580.0),
            ReverseGeocoder.PlaceCandidate("西区", "place:suburb", 34.3912, 132.4338, 6000.0),
            ReverseGeocoder.PlaceCandidate("広島市", "place:city", 34.3853, 132.4556, 7500.0),
        )
        val result = ReverseGeocoder.selectHierarchy(candidates, lat, lon, "広島県")
        assertEquals("広島県", result?.prefecture)
        assertEquals("広島市", result?.city)
        assertEquals("西区", result?.ward)
        assertEquals("井口五丁目", result?.town)
        assertEquals("広島県広島市西区井口五丁目", result?.formattedAddress)
    }
}

