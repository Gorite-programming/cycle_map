package com.gorite.cyclemap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 階層選択 (selectHierarchy) の検証。候補座標は Hiroshima.search.db の実測値。
 * GPS→町→区(町基準)→市(区の親市)→郡(市基準) の順に絞り、各レベル独立の最近傍にしない。
 */
class HierarchySelectTest {

    private fun hav(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p = Math.PI / 180.0
        val a = sin((lat2 - lat1) * p / 2).pow2() +
            cos(lat1 * p) * cos(lat2 * p) * sin((lon2 - lon1) * p / 2).pow2()
        return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun Double.pow2(): Double = this * this

    private fun cand(
        name: String, category: String, lat: Double, lon: Double, gpsLat: Double, gpsLon: Double,
    ) = ReverseGeocoder.PlaceCandidate(name, category, lat, lon, hav(gpsLat, gpsLon, lat, lon))

    // 実DB値 (Hiroshima.search.db)。
    private val hiroshimaCity = Triple("広島市", "place:city", 34.39172 to 132.45176)
    private val hatsukaichi = Triple("廿日市市", "place:city", 34.34850 to 132.33183)
    private val akiota = Triple("安芸太田町", "place:town", 34.57665 to 132.22714)
    private val shinsekikogenTown = Triple("神石高原町", "place:town", 34.70358 to 133.25155)
    private val higashi = Triple("東区", "place:suburb", 34.39533 to 132.48248)
    private val minami = Triple("南区", "place:suburb", 34.37986 to 132.46901)
    private val naka = Triple("中区", "place:suburb", 34.38629 to 132.45505)
    private val saeki = Triple("佐伯区", "place:suburb", 34.36448 to 132.36085)
    private val asaminami = Triple("安佐南区", "place:suburb", 34.45184 to 132.47165)
    private val nishi = Triple("西区", "place:suburb", 34.39397 to 132.43440)
    private val toujou = Triple("東城町", "place:suburb", 34.89506 to 133.27627)
    private val yamagataCounty = Triple("山県郡", "place:county", 34.67219 to 132.33672)
    private val shinsekikogenCounty = Triple("神石郡", "place:county", 34.75603 to 133.27217)
    private val akiCounty = Triple("安芸郡", "place:county", 34.35715 to 132.56479)

    private fun Triple<String, String, Pair<Double, Double>>.toCand(gpsLat: Double, gpsLon: Double) =
        cand(first, second, third.first, third.second, gpsLat, gpsLon)

    @Test
    fun station_resolvesWithoutMixing() {
        // 広島駅 (松原町): 行政界ポリゴン判定により、旧方式の幾何限界 (東区役所が近いことによる東区誤判定) が
        // 解消され、正しい行政区である「南区」が選択される。
        val gpsLat = 34.3976
        val gpsLon = 132.4756
        val cands = listOf(
            cand("松原町", "place:neighbourhood", 34.39639, 132.47366, gpsLat, gpsLon),
            higashi.toCand(gpsLat, gpsLon), minami.toCand(gpsLat, gpsLon), naka.toCand(gpsLat, gpsLon),
            hiroshimaCity.toCand(gpsLat, gpsLon),
            cand("府中町", "place:town", 34.39258, 132.50452, gpsLat, gpsLon),
            akiCounty.toCand(gpsLat, gpsLon),
        )
        val result = ReverseGeocoder.selectHierarchy(cands, gpsLat, gpsLon, "広島県")!!
        assertEquals("広島市", result.city)
        assertEquals("南区", result.ward)
        assertEquals("松原町", result.town)
        assertEquals("広島県広島市南区松原町", result.formattedAddress)
        assertTrue(ReverseGeocoder.isHierarchyConsistent(result, "広島県"))
    }

    @Test
    fun iguchi_doesNotMixCityAndWard() {
        // 井口: 旧方式 (GPS最近傍city) は廿日市市を選択して佐伯区と混在した。
        // 新方式は区の親市 (佐伯区→広島市) を使う。
        val gpsLat = 34.3650
        val gpsLon = 132.3600
        val cands = listOf(
            cand("五日市一丁目", "place:neighbourhood", 34.36640, 132.35959, gpsLat, gpsLon),
            saeki.toCand(gpsLat, gpsLon), nishi.toCand(gpsLat, gpsLon),
            hatsukaichi.toCand(gpsLat, gpsLon), hiroshimaCity.toCand(gpsLat, gpsLon),
        )
        // 回帰の根拠: GPS最近傍の市は廿日市市であることを確認。
        val nearestCity = cands.filter { it.category == "place:city" }.minByOrNull { it.distanceM }!!
        assertEquals("廿日市市", nearestCity.name)
        val result = ReverseGeocoder.selectHierarchy(cands, gpsLat, gpsLon, "広島県")!!
        assertEquals("広島市", result.city)
        assertEquals("佐伯区", result.ward)
        assertEquals("五日市一丁目", result.town)
        assertEquals("広島県広島市佐伯区五日市一丁目", result.formattedAddress)
        assertTrue(ReverseGeocoder.isHierarchyConsistent(result, "広島県"))
    }

    @Test
    fun kokutaiji_naka() {
        val gpsLat = 34.3853
        val gpsLon = 132.4556
        val cands = listOf(
            cand("国泰寺町一丁目", "place:neighbourhood", 34.38581, 132.45670, gpsLat, gpsLon),
            naka.toCand(gpsLat, gpsLon), minami.toCand(gpsLat, gpsLon),
            hiroshimaCity.toCand(gpsLat, gpsLon),
        )
        val result = ReverseGeocoder.selectHierarchy(cands, gpsLat, gpsLon, "広島県")!!
        assertEquals("広島県広島市中区国泰寺町一丁目", result.formattedAddress)
    }

    @Test
    fun gion_asaminami() {
        val gpsLat = 34.4300
        val gpsLon = 132.4600
        val cands = listOf(
            cand("長束二丁目", "place:neighbourhood", 34.42862, 132.45916, gpsLat, gpsLon),
            asaminami.toCand(gpsLat, gpsLon), higashi.toCand(gpsLat, gpsLon),
            hiroshimaCity.toCand(gpsLat, gpsLon),
        )
        val result = ReverseGeocoder.selectHierarchy(cands, gpsLat, gpsLon, "広島県")!!
        assertEquals("広島県広島市安佐南区長束二丁目", result.formattedAddress)
    }

    @Test
    fun togouchi_countyAndNoWard() {
        val gpsLat = 34.5745
        val gpsLon = 132.2280
        val cands = listOf(
            cand("戸河内", "place:quarter", 34.57643, 132.22574, gpsLat, gpsLon),
            akiota.toCand(gpsLat, gpsLon),
            yamagataCounty.toCand(gpsLat, gpsLon),
            // 圏外の区 (17km超) は採用されないことの確認用。
            cand("芸北町", "place:suburb", 34.72655, 132.27711, gpsLat, gpsLon),
        )
        val result = ReverseGeocoder.selectHierarchy(cands, gpsLat, gpsLon, "広島県")!!
        assertEquals("広島県山県郡安芸太田町戸河内", result.formattedAddress)
        assertNull(result.ward)
        assertEquals("山県郡", result.county)
    }

    @Test
    fun shinsekikogen_unmappedWardFallsBack() {
        // ポリゴン未定義地域でのフォールバック: 東城町は対応表に無い区 → 市は空間最近傍 (神石高原町)。区名重複は省略される。
        val gpsLat = 34.8500
        val gpsLon = 133.2500
        val cands = listOf(
            cand("東城町新免", "place:quarter", 34.84408, 133.26142, gpsLat, gpsLon),
            toujou.toCand(gpsLat, gpsLon),
            shinsekikogenTown.toCand(gpsLat, gpsLon),
            shinsekikogenCounty.toCand(gpsLat, gpsLon),
        )
        // 未定義県/ポリゴン外地域でのフォールバック: 東城町は対応表に無い区 → 市は空間最近傍 (神石高原町)。区名重複は省略される。
        val result = ReverseGeocoder.selectHierarchy(cands, gpsLat, gpsLon, "未登録県")!!
        assertEquals("神石高原町", result.city)
        assertEquals("神石郡", result.county)
        assertEquals("未登録県神石郡神石高原町東城町新免", result.formattedAddress)
    }

    @Test
    fun toujou_resolvesShobaraByPolygon() {
        // 庄原市東城町新免: 境界線ポリゴン判定により正しく庄原市と判定される。
        val gpsLat = 34.8500
        val gpsLon = 133.2500
        val cands = listOf(
            cand("東城町新免", "place:quarter", 34.84408, 133.26142, gpsLat, gpsLon),
            toujou.toCand(gpsLat, gpsLon),
            shinsekikogenTown.toCand(gpsLat, gpsLon),
            cand("庄原市", "place:city", 34.8583, 133.0167, gpsLat, gpsLon),
        )
        val result = ReverseGeocoder.selectHierarchy(cands, gpsLat, gpsLon, "広島県")!!
        assertEquals("庄原市", result.city)
        assertNull(result.ward)
        assertEquals("東城町新免", result.town)
        assertEquals("広島県庄原市東城町新免", result.formattedAddress)
    }

    @Test
    fun consistencyHelper_detectsMixing() {
        val mixed = AddressResult(
            prefecture = "広島県", city = "廿日市市", ward = "佐伯区",
            formattedAddress = "広島県廿日市市佐伯区五日市一丁目",
        )
        assertFalse(ReverseGeocoder.isHierarchyConsistent(mixed, "広島県"))
        val ok = AddressResult(
            prefecture = "広島県", city = "広島市", ward = "佐伯区",
            formattedAddress = "広島県広島市佐伯区五日市一丁目",
        )
        assertTrue(ReverseGeocoder.isHierarchyConsistent(ok, "広島県"))
        val noWard = AddressResult(
            prefecture = "広島県", city = "安芸太田町",
            formattedAddress = "広島県山県郡安芸太田町戸河内",
        )
        assertTrue(ReverseGeocoder.isHierarchyConsistent(noWard, "広島県"))
    }
}
