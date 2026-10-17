package com.gorite.cyclemap.weather

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 気象庁予報JSON（forecast/{officeCode}.json）を解析するパーサー。
 */
object JmaWeatherParser {

    /**
     * JSON文字列から WeatherForecastData を生成する。
     */
    fun parse(
        jsonString: String,
        resolvedArea: ResolvedArea,
        fetchedAtMillis: Long = System.currentTimeMillis()
    ): WeatherForecastData? {
        val rootElement = try {
            JsonParser.parseString(jsonString)
        } catch (_: Exception) {
            return null
        }
        if (!rootElement.isJsonArray) return null
        val rootArray = rootElement.asJsonArray
        if (rootArray.size() == 0) return null

        val shortForecast = rootArray[0].asJsonObject
        val reportDatetime = shortForecast.get("reportDatetime")?.asString ?: ""
        val timeSeriesArray = shortForecast.getAsJsonArray("timeSeries") ?: return null

        var weatherText = ""
        var weatherCode = ""
        var currentPop: Int? = null
        var currentTemp: Double? = null
        val popList = mutableListOf<PopEntry>()
        val tempList = mutableListOf<TempEntry>()

        for (seriesElem in timeSeriesArray) {
            val series = seriesElem.asJsonObject
            val timeDefines = series.getAsJsonArray("timeDefines") ?: continue
            val areas = series.getAsJsonArray("areas") ?: continue

            // 1. 天気・天気コードの抽出（area.code == class10Code）
            for (areaElem in areas) {
                val areaObj = areaElem.asJsonObject
                val code = areaObj.getAsJsonObject("area")?.get("code")?.asString ?: continue
                if (code == resolvedArea.class10Code) {
                    val weathers = areaObj.getAsJsonArray("weathers")
                    if (weathers != null && weathers.size() > 0 && weatherText.isEmpty()) {
                        weatherText = sanitizeWeatherText(weathers[0].asString)
                    }
                    val codes = areaObj.getAsJsonArray("weatherCodes")
                    if (codes != null && codes.size() > 0 && weatherCode.isEmpty()) {
                        weatherCode = codes[0].asString
                    }

                    // 2. 降水確率の抽出
                    val pops = areaObj.getAsJsonArray("pops")
                    if (pops != null && pops.size() > 0) {
                        for (i in 0 until pops.size()) {
                            val popVal = pops[i].asString.toIntOrNull()
                            val timeStr = if (i < timeDefines.size()) formatTimePeriod(timeDefines[i].asString) else "期間 $i"
                            popList.add(PopEntry(timeStr, popVal))
                        }
                        if (currentPop == null && popList.isNotEmpty()) {
                            currentPop = popList.first().popPercent
                        }
                    }
                }

                // 3. 気温の抽出（area.code == amedasCode）
                if (resolvedArea.amedasCode != null && code == resolvedArea.amedasCode) {
                    val temps = areaObj.getAsJsonArray("temps")
                    if (temps != null && temps.size() > 0) {
                        for (i in 0 until temps.size()) {
                            val tempStr = temps[i].asString
                            val tempVal = tempStr.toDoubleOrNull()
                            val label = if (i < timeDefines.size()) formatTempLabel(timeDefines[i].asString, i) else "気温 $i"
                            tempList.add(TempEntry(label, tempVal))
                        }
                        if (currentTemp == null && tempList.isNotEmpty()) {
                            currentTemp = tempList.first().tempCelsius
                        }
                    }
                }
            }
        }

        // 天気情報が取得できなかった場合はパース失敗とする
        if (weatherCode.isEmpty() && weatherText.isEmpty()) {
            return null
        }

        return WeatherForecastData(
            officeCode = resolvedArea.officeCode,
            class10Code = resolvedArea.class10Code,
            amedasCode = resolvedArea.amedasCode,
            targetAreaName = resolvedArea.areaName,
            weatherText = weatherText,
            weatherCode = weatherCode,
            currentPop = currentPop,
            currentTemp = currentTemp,
            popList = popList,
            tempList = tempList,
            reportDatetime = reportDatetime,
            fetchedAtMillis = fetchedAtMillis
        )
    }

    private fun sanitizeWeatherText(raw: String): String {
        return raw.replace(Regex("[\\s\\u3000]+"), " ").trim()
    }

    private fun formatTimePeriod(isoDateTime: String): String {
        // 例: "2026-10-16T18:00:00+09:00" -> "18時〜"
        return try {
            val timePart = isoDateTime.substringAfter('T').substringBefore('+')
            val hour = timePart.substringBefore(':')
            "${hour}時〜"
        } catch (_: Exception) {
            isoDateTime
        }
    }

    private fun formatTempLabel(isoDateTime: String, index: Int): String {
        return try {
            val timePart = isoDateTime.substringAfter('T').substringBefore('+')
            val hour = timePart.substringBefore(':').toIntOrNull() ?: return "気温"
            when (hour) {
                in 0..9 -> "朝の最低気温"
                in 10..15 -> "日中の最高気温"
                else -> "${hour}時頃の気温"
            }
        } catch (_: Exception) {
            "気温"
        }
    }
}
