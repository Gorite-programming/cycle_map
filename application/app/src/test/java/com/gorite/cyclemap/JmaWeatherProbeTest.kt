/**
 * 気象庁の非公式 forecast JSON を取得する実験スクリプト。
 * 市区町村名 -> area.json の class20s -> 親 office -> forecast JSONを取得し、
 * 市区町村が属する class10 の天気・降水確率を表示する。
 *
 * 実行: cd re_test && ./gradlew :app:testDebugUnitTest --tests com.gorite.cyclemap.JmaWeatherProbeTest
 * 引数を省略した地域名は山口県・山口市。緯度経度は表示用で、逆ジオコーディング
 * 自体は行わず、最後の県名・市区町村名を指定して流れを試験する。
 * Gradle付属のKotlin compilerとアプリのGson依存で実行する。
 * 逆ジオコーディングは行わず、都道府県・市区町村を引数で指定して照合する。
 */

import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Test

class JmaWeatherProbeTest {
    @org.junit.Ignore("外部ネットワーク接続が必要な手動検証用テスト")
    @Test
    fun fetchForecastForConfiguredTestCity() = runJmaWeatherProbe()
}

private const val AREA_URL = "https://www.jma.go.jp/bosai/common/const/area.json"
private const val FORECAST_AREA_URL = "https://www.jma.go.jp/bosai/forecast/const/forecast_area.json"
private const val FORECAST_URL = "https://www.jma.go.jp/bosai/forecast/data/forecast/%s.json"

private fun getJson(url: String): JsonElement {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 20_000
    connection.setRequestProperty("User-Agent", "CycleMap-weather-test/1.0")
    connection.requestMethod = "GET"
    try {
        check(connection.responseCode == HttpURLConnection.HTTP_OK) {
            "HTTP ${connection.responseCode} for $url"
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use {
            JsonParser.parseReader(it)
        }
    } finally {
        connection.disconnect()
    }
}

private fun JsonObject.str(key: String): String? = this.get(key)?.takeIf { it.isJsonPrimitive }?.asString

private data class CityMatch(
    val cityCode: String,
    val cityName: String,
    val officeCode: String,
    val officeName: String,
    val class10Code: String,
    val class10Name: String,
)

private data class ForecastArea(
    val code: String,
    val name: String,
    val weather: String?,
    val weatherCode: String?,
    val pop: String?,
)

private fun resolveCity(area: JsonObject, prefecture: String, city: String): List<CityMatch> {
    val offices = area.getAsJsonObject("offices")
    val class10s = area.getAsJsonObject("class10s")
    val class15s = area.getAsJsonObject("class15s")
    val class20s = area.getAsJsonObject("class20s")
    return class20s.entrySet().mapNotNull { (cityCode, cityElement) ->
        val cityObj = cityElement.asJsonObject
        val cityName = cityObj.str("name") ?: return@mapNotNull null
        if (cityName != city) return@mapNotNull null
        val c15 = class15s.get(cityObj.str("parent"))?.asJsonObject ?: return@mapNotNull null
        val c10Code = c15.str("parent") ?: return@mapNotNull null
        val c10 = class10s.get(c10Code)?.asJsonObject ?: return@mapNotNull null
        val officeCode = c10.str("parent") ?: return@mapNotNull null
        val office = offices.get(officeCode)?.asJsonObject ?: return@mapNotNull null
        val officeName = office.str("name") ?: return@mapNotNull null
        if (officeName != prefecture && !officeName.startsWith(prefecture)) return@mapNotNull null
        CityMatch(cityCode, cityName, officeCode, officeName, c10Code, c10.str("name") ?: "")
    }
}

private fun findWeatherArea(json: JsonArray, class10Code: String): ForecastArea? {
    val short = json.firstOrNull()?.asJsonObject ?: return null
    val series = short.getAsJsonArray("timeSeries") ?: return null
    for (s in series) {
        val ts = s.asJsonObject
        val areas = ts.getAsJsonArray("areas") ?: continue
        val target = areas.firstOrNull { area ->
            area.asJsonObject.getAsJsonObject("area")?.str("code") == class10Code
        }?.asJsonObject ?: continue
        val info = target.getAsJsonObject("area")
        val weather = target.getAsJsonArray("weathers")?.firstOrNull()?.asString
        val code = target.getAsJsonArray("weatherCodes")?.firstOrNull()?.asString
        val pop = target.getAsJsonArray("pops")?.firstOrNull()?.asString
        return ForecastArea(class10Code, info?.str("name") ?: "", weather, code, pop)
    }
    return null
}

private fun findTemperatureSite(
    mapping: JsonObject,
    officeCode: String,
    class10Code: String,
): String? {
    val mappings = mapping.getAsJsonArray(officeCode) ?: return null
    return mappings.firstOrNull { it.asJsonObject.str("class10") == class10Code }
        ?.asJsonObject?.getAsJsonArray("amedas")?.firstOrNull()?.asString
}

private fun findTemperature(json: JsonArray, siteCode: String): Pair<String, String>? {
    val short = json.firstOrNull()?.asJsonObject ?: return null
    val series = short.getAsJsonArray("timeSeries") ?: return null
    for (s in series) {
        val ts = s.asJsonObject
        val areas = ts.getAsJsonArray("areas") ?: continue
        val target = areas.firstOrNull { area ->
            area.asJsonObject.getAsJsonObject("area")?.str("code") == siteCode
        }?.asJsonObject ?: continue
        val times = ts.getAsJsonArray("timeDefines") ?: continue
        val temps = target.getAsJsonArray("temps") ?: continue
        if (temps.size() == 0) continue
        val time = if (times.size() > 0) times[0].asString else ""
        val temp = temps[0].asString
        if (temp.isNotBlank()) return time to temp
    }
    return null
}

private fun runJmaWeatherProbe() {
    val lat = 34.1858
    val lon = 131.4714
    val prefecture = "山口県"
    val city = "山口市"
    println("位置: $lat, $lon (逆ジオコーディング結果はテスト引数で指定: $prefecture $city)")

    val area = getJson(AREA_URL).asJsonObject
    val matches = resolveCity(area, prefecture, city)
    check(matches.isNotEmpty()) { "area.json に '$prefecture $city' が見つかりません" }
    check(matches.size == 1) { "候補が複数: $matches。県名・市区町村名を確認してください" }
    val match = matches.single()
    println("地域コード解決: $match")

    val forecastAreaMap = getJson(FORECAST_AREA_URL).asJsonObject
    val amedasCode = findTemperatureSite(forecastAreaMap, match.officeCode, match.class10Code)
    val url = FORECAST_URL.format(match.officeCode)
    println("予報URL: $url")
    val forecast = getJson(url).asJsonArray

    val weather = findWeatherArea(forecast, match.class10Code)
    if (weather == null) {
        println("市区町村が属する予報区(${match.class10Name})を予報JSONから特定できません。府県予報の先頭エリアを代替表示します。")
        val first = forecast.first().asJsonObject.getAsJsonArray("timeSeries").first().asJsonObject.getAsJsonArray("areas").first().asJsonObject
        println("天気: ${first.getAsJsonArray("weathers")?.firstOrNull()?.asString ?: "不明"}")
    } else {
        println("予報区: ${weather.name} / 天気: ${weather.weather ?: "不明"} / 降水確率: ${weather.pop ?: "未掲載"}% / code=${weather.weatherCode}")
    }

    if (amedasCode != null) {
        val temperature = findTemperature(forecast, amedasCode)
        if (temperature != null) println("気温観測地点 $amedasCode: ${temperature.second}℃ (${temperature.first})")
        else println("気温観測地点 $amedasCode: 予報JSON内に値がありません")
    } else {
        println("市区町村の予報エリアに対応する気温観測地点が見つかりません")
    }
    println("出典: 気象庁ホームページ")
}

