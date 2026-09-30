package com.gorite.cyclemap.data

import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * オフライン逆ジオコーディング。
 *
 * 完全オフラインが絶対条件のため、Web APIには一切触らない。
 * 県別パッケージに同梱の `search.db` (OSM由来・`place:*` ノード) をローカル検索し、
 * 都道府県→市区町村→(区/郡)→町名・丁目の階層で日本語住所を組み立てる。
 * UIは [AddressResult.formattedAddress] を表示するだけで、判定ロジックを持たない。
 */
data class AddressResult(
    val prefecture: String,
    val county: String? = null,
    val city: String? = null,
    val ward: String? = null,
    val town: String? = null,
    val formattedAddress: String,
)

/**
 * オフライン逆ジオコーディングの抽象。UI・表示制御はこの背後に隠し、
 * データ源 (OSM search.db / 将来の国土地理院データ等) の差し替えを可能にする。
 * GPS更新 (~1秒) ごとに呼ばれるため、実装は bbox 等で事前絞り込みし総当たりを避けること。
 */
interface OfflineReverseGeocoder {
    /** 対応区域外・異常入力では null。呼び出し側はフォールバック表示を維持する。 */
    fun lookup(latitude: Double, longitude: Double): AddressResult?
}

object ReverseGeocoder {
    private const val CITY_MAX_METERS = 30_000.0
    private const val COUNTY_MAX_METERS = 30_000.0
    private const val WARD_MAX_METERS = 15_000.0
    private const val TOWN_MAX_METERS = 3_000.0

    private val CITY_CATEGORIES = setOf("place:city", "place:town", "place:village")
    private val WARD_CATEGORIES = setOf("place:suburb", "place:borough")
    private val TOWN_CATEGORIES = setOf("place:quarter", "place:neighbourhood", "place:hamlet")

    /** 番地・号レベルの接尾辞だけを落とす (丁目までは保持。町名自体は切り詰めない)。 */
    private val HOUSE_SUFFIX = Regex("^(.*丁目)[0-9０-９\\-‐‑–ー番地号之丶・\\s]+$")

    fun sanitizeTownName(name: String): String {
        val step1 = HOUSE_SUFFIX.matchEntire(name.trim())?.groupValues?.get(1) ?: name
        return step1.replace(Regex("""\d+番地.*$"""), "")
    }

    /** 町名が区名を内包する場合 (例: 東城町+東城町新免) は区名を落として重複表示を避ける。 */
    fun resolveWardName(wardName: String?, townName: String?): String? =
        wardName?.takeUnless { w -> townName?.startsWith(w) == true }

    /**
     * 政令指定都市の区 → 市対応。区名は全国で重複するため県DBごとに分離する
     * (例: 中区は広島市にも岡山市にもある)。中国地方拡張時は県エントリを追加する。
     * OSM place点は区役所等の代表点であり包含判定に使えないため、
     * 市と区を別自治体から混ぜないための親子対応として保持する。
     * TODO: Expand this beyond Chugoku region when adding other regions. Currently targeting Chugoku.
     */
    private val WARD_PARENT_CITY = mapOf(
        "広島県" to mapOf(
            "中区" to "広島市", "東区" to "広島市", "南区" to "広島市", "西区" to "広島市",
            "安佐南区" to "広島市", "安佐北区" to "広島市", "安芸区" to "広島市", "佐伯区" to "広島市",
        ),
        "岡山県" to mapOf(
            "北区" to "岡山市", "中区" to "岡山市", "東区" to "岡山市", "南区" to "岡山市",
        ),
    )

    /** 階層選択用の候補1件。distanceM は GPS からの距離。 */
    data class PlaceCandidate(
        val name: String,
        val category: String,
        val lat: Double,
        val lon: Double,
        val distanceM: Double,
    )

    /**
     * 候補列から行政階層として矛盾しない住所を選ぶ (純粋関数・JVMテスト可能)。
     * 階層判定順序:
     * 1. 市町村 (admin_level=7): 行政界ポリゴン判定 (面)。未定義時は最近傍探索。
     * 2. 区 (admin_level=8): 政令指定都市等の場合、行政界ポリゴン判定 (面)。未定義時は最近傍探索。
     * 3. 町・丁目: 特定された区・市町村の境界内にある候補を最優先、次いで最近傍。
     * 4. 郡: 町・村制の場合に最近傍から選択。
     */
    fun selectHierarchy(
        candidates: List<PlaceCandidate>,
        gpsLat: Double,
        gpsLon: Double,
        prefectureName: String?,
    ): AddressResult? {
        // 1. 市・町・村判定 (面・Point-in-Polygon優先)
        val boundaryCityName = AdministrativeBoundaries.findCityOrTown(prefectureName, gpsLat, gpsLon)
        val cityCandidate = if (boundaryCityName != null) {
            candidates.firstOrNull { it.name == boundaryCityName && it.category in CITY_CATEGORIES }
                ?: PlaceCandidate(boundaryCityName, "place:city", gpsLat, gpsLon, 0.0)
        } else {
            null
        }

        // 2. 区判定 (面・Point-in-Polygon優先)
        val targetCityName = boundaryCityName ?: candidates
            .filter { it.category in CITY_CATEGORIES && it.distanceM <= CITY_MAX_METERS }
            .minByOrNull { it.distanceM }?.name
        val boundaryWardName = AdministrativeBoundaries.findWard(prefectureName, targetCityName, gpsLat, gpsLon)
        val wardCandidate = if (boundaryWardName != null) {
            candidates.firstOrNull { it.name == boundaryWardName && it.category in WARD_CATEGORIES }
                ?: PlaceCandidate(boundaryWardName, "place:suburb", gpsLat, gpsLon, 0.0)
        } else {
            null
        }

        // ポリゴン判定で市または区が未確定の場合のフォールバック
        val resolvedWard = wardCandidate ?: run {
            val townAnchor = candidates
                .filter { it.category in TOWN_CATEGORIES && it.distanceM <= TOWN_MAX_METERS }
                .minByOrNull { it.distanceM }
            val wardAnchorLat = townAnchor?.lat ?: gpsLat
            val wardAnchorLon = townAnchor?.lon ?: gpsLon
            candidates
                .filter { it.category in WARD_CATEGORIES }
                .map { it to haversineMeters(wardAnchorLat, wardAnchorLon, it.lat, it.lon) }
                .filter { it.second <= WARD_MAX_METERS }
                .minByOrNull { it.second }
                ?.first
        }

        val mappedCity = resolvedWard?.name?.let { WARD_PARENT_CITY[prefectureName]?.get(it) }
        val resolvedCity = cityCandidate ?: if (mappedCity != null) {
            candidates.firstOrNull { it.name == mappedCity && it.category in CITY_CATEGORIES }
                ?: PlaceCandidate(mappedCity, "place:city", gpsLat, gpsLon, 0.0)
        } else {
            candidates
                .filter { it.category in CITY_CATEGORIES && it.distanceM <= CITY_MAX_METERS }
                .minByOrNull { it.distanceM }
        } ?: return null

        // 3. 町・丁目判定 (特定された区または市の境界内にあるノードを最優先)
        val townCandidates = candidates.filter { it.category in TOWN_CATEGORIES && it.distanceM <= TOWN_MAX_METERS }
        val activeBoundary = if (resolvedWard != null) {
            AdministrativeBoundaries.ALL.firstOrNull { it.adminLevel == 8 && it.name == resolvedWard.name }
        } else {
            AdministrativeBoundaries.ALL.firstOrNull { it.adminLevel == 7 && it.name == resolvedCity.name }
        }

        val town = if (activeBoundary != null) {
            val inBoundary = townCandidates.filter {
                AdministrativeBoundaries.contains(activeBoundary, it.lat, it.lon)
            }
            inBoundary.minByOrNull { it.distanceM } ?: townCandidates.minByOrNull { it.distanceM }
        } else {
            townCandidates.minByOrNull { it.distanceM }
        }

        // 4. 郡判定
        val county = candidates
            .filter { it.category == "place:county" }
            .map { it to haversineMeters(gpsLat, gpsLon, it.lat, it.lon) }
            .filter { it.second <= COUNTY_MAX_METERS }
            .minByOrNull { it.second }
            ?.first

        // 市区町村が掴めなければ住所として成立しない。
        val cityName = resolvedCity.name
        val isTownOrVillage = cityName.endsWith("町") || cityName.endsWith("村")
        val townName = town?.name?.let { sanitizeTownName(it) }
        // 町名が区名を内包する場合 (例: 東城町+東城町新免) は重複表示を避ける。
        val wardName = resolveWardName(resolvedWard?.name, townName)
        val formatted = buildString {
            if (!prefectureName.isNullOrEmpty()) append(prefectureName)
            if (isTownOrVillage && county != null) append(county.name)
            append(cityName)
            if (wardName != null) append(wardName)
            if (townName != null) append(townName)
        }
        return AddressResult(
            prefecture = prefectureName.orEmpty(),
            county = if (isTownOrVillage) county?.name else null,
            city = cityName,
            ward = wardName,
            town = townName,
            formattedAddress = formatted,
        )
    }

    /**
     * 階層として矛盾しないか (区が判明し親市が登録済みの場合、市が親市と一致すること)。
     * 回帰テスト用。townレベルの帰属までは点データから判定できない。
     */
    fun isHierarchyConsistent(result: AddressResult, prefectureName: String?): Boolean {
        val ward = result.ward ?: return true
        val parent = WARD_PARENT_CITY[prefectureName]?.get(ward) ?: return true
        return result.city == parent
    }

    /**
     * 緯度経度から住所を判定する。ブロッキングするためIOスレッドで呼ぶこと。
     * 対応区域がなければ null (呼び出し側は「現在地を特定できません」を表示する)。
     */
    fun reverseGeocode(
        dbFile: File,
        latitude: Double,
        longitude: Double,
        prefectureName: String?,
    ): AddressResult? {
        if (!dbFile.isFile || !latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        // 最大半径 (市区町村用) のbboxで候補を一括取得し、階層選択へ渡す。
        val latDelta = CITY_MAX_METERS / 111_320.0
        val lonDelta = CITY_MAX_METERS / (111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.2))
        val candidates = ArrayList<PlaceCandidate>()
        try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    """
                    SELECT name, category, lat, lon
                    FROM places
                    WHERE lat BETWEEN ? AND ?
                      AND lon BETWEEN ? AND ?
                      AND category LIKE 'place:%'
                    """.trimIndent(),
                    arrayOf(
                        (latitude - latDelta).toString(),
                        (latitude + latDelta).toString(),
                        (longitude - lonDelta).toString(),
                        (longitude + lonDelta).toString(),
                    ),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0) ?: continue
                        val category = cursor.getString(1) ?: continue
                        val plat = cursor.getDouble(2)
                        val plon = cursor.getDouble(3)
                        candidates += PlaceCandidate(
                            name, category, plat, plon,
                            haversineMeters(latitude, longitude, plat, plon),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }

        return selectHierarchy(candidates, latitude, longitude, prefectureName)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadius * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

/** search.db ベースの [OfflineReverseGeocoder] 実装。県別パッケージのDBをそのまま使う。 */
class SearchDbReverseGeocoder(
    private val dbFile: File,
    private val prefectureName: String?,
) : OfflineReverseGeocoder {
    override fun lookup(latitude: Double, longitude: Double): AddressResult? =
        ReverseGeocoder.reverseGeocode(dbFile, latitude, longitude, prefectureName)
}

/**
 * 緯度経度を含む都道府県名を返す。
 * bboxに複数県が一致した場合 (県境重複帯) は行政界ポリゴン (ChugokuBoundaries) で
 * 包含判定する。ポリゴン無し・いずれにも含まれない場合は従来の最狭bboxへフォールバック。
 * 見つからなければ null。
 */
fun findPrefectureName(latitude: Double, longitude: Double): String? {
    val matches = PrefectureData.ALL.filter { pref ->
        latitude <= pref.bounds.latNorth && latitude >= pref.bounds.latSouth &&
            longitude <= pref.bounds.lonEast && longitude >= pref.bounds.lonWest
    }
    if (matches.size <= 1) return matches.firstOrNull()?.name
    val byArea = matches.sortedBy { pref ->
        (pref.bounds.latNorth - pref.bounds.latSouth) * (pref.bounds.lonEast - pref.bounds.lonWest)
    }
    for (pref in byArea) {
        if (ChugokuBoundaries.contains(pref.id, latitude, longitude)) return pref.name
    }
    return byArea.firstOrNull()?.name
}

/**
 * 住所表示の安定化コントローラ (ヒステリシス + デバウンス)。
 *
 * - 一定距離 ([REGEOCODE_MIN_DISTANCE_M]) 未満の移動では再検索しない。
 * - 精度が悪い ([MAX_ACCURACY_M] 超) fixでは再検索しない (境界付近のちらつき防止)。
 * - 表示切替は同一候補が [CONFIRM_COUNT] 回連続した場合のみ。初回は即時表示する。
 * - Compose stateを持たない。呼び出し側が戻り値を state に入れる。
 * - ブロッキングするためIOスレッドで呼ぶこと。
 */
class AddressDisplayController {
    var displayed: String = ADDRESS_LOADING
        private set

    private var lastQueryLat = Double.NaN
    private var lastQueryLon = Double.NaN
    private var pending: String? = null
    private var pendingCount = 0

    fun update(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        geocoder: OfflineReverseGeocoder,
    ): String {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return confirmCandidate(ADDRESS_UNKNOWN)
        }
        // 精度が悪いfixは境界付近の誤判定になりやすいため捨てる (表示は維持)。
        if (accuracyMeters.isFinite() && accuracyMeters > MAX_ACCURACY_M) return displayed
        // 近傍に留まっている間は再検索しない。
        if (lastQueryLat.isFinite() &&
            distanceMeters(lastQueryLat, lastQueryLon, latitude, longitude) < REGEOCODE_MIN_DISTANCE_M
        ) {
            return displayed
        }
        val result = geocoder.lookup(latitude, longitude)
        if (result != null) {
            lastQueryLat = latitude
            lastQueryLon = longitude
        }
        val candidate = result?.formattedAddress ?: ADDRESS_UNKNOWN
        return confirmCandidate(candidate)
    }

    private fun confirmCandidate(candidate: String): String {
        if (candidate == displayed) {
            pending = null
            pendingCount = 0
            return displayed
        }
        // 初回 (取得中表示の間) は即時表示し、静止時でも住所が出るようにする。
        if (displayed == ADDRESS_LOADING) {
            displayed = candidate
            pending = null
            pendingCount = 0
            return displayed
        }
        if (candidate == pending) {
            pendingCount++
            if (pendingCount >= CONFIRM_COUNT) {
                displayed = candidate
                pending = null
                pendingCount = 0
            }
        } else {
            pending = candidate
            pendingCount = 1
        }
        return displayed
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadius * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    companion object {
        const val ADDRESS_LOADING = "現在地を取得中…"
        const val ADDRESS_UNKNOWN = "現在地を特定できません"
        const val REGEOCODE_MIN_DISTANCE_M = 100.0
        const val MAX_ACCURACY_M = 100f
        const val CONFIRM_COUNT = 2
    }
}
