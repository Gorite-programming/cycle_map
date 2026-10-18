package com.gorite.cyclemap.weather

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gorite.cyclemap.data.findPrefectureName
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 気象庁の地域コード（offices / class10 / class20 / amedas）を解決するクラス。
 */
class JmaAreaResolver(
    private val areaJson: JsonObject,
    private val forecastAreaJson: JsonObject
) {

    private val offices = areaJson.getAsJsonObject("offices") ?: JsonObject()
    private val class10s = areaJson.getAsJsonObject("class10s") ?: JsonObject()
    private val class15s = areaJson.getAsJsonObject("class15s") ?: JsonObject()
    private val class20s = areaJson.getAsJsonObject("class20s") ?: JsonObject()

    /**
     * 都道府県名と市区町村名から地域コードを解決する。
     * 市区町村が特定できない、または見つからない場合は都道府県代表の class10 にフォールバックする。
     */
    fun resolve(prefecture: String, city: String? = null): ResolvedArea? {
        val prefClean = prefecture.trim()
        if (prefClean.isEmpty()) return null

        if (!city.isNullOrBlank()) {
            val cityClean = city.trim()
            val resolved = resolveByCity(prefClean, cityClean)
            if (resolved != null) return resolved
        }

        return resolveFallback(prefClean)
    }

    /**
     * 市区町村名または逆ジオコーディング文字列から class20 -> class15 -> class10 -> office を辿る。
     */
    private fun resolveByCity(prefecture: String, city: String): ResolvedArea? {
        val normalizedHint = city
            .removePrefix(prefecture)
            .replace(" ", "")
            .replace("　", "")
            .trim()
        if (normalizedHint.isEmpty()) return null

        var prefixFallback: ResolvedArea? = null

        // class20s を走査
        for ((_, cityElement) in class20s.entrySet()) {
            val cityObj = cityElement.asJsonObject
            val cityName = cityObj.get("name")?.asString ?: continue

            val c15Code = cityObj.get("parent")?.asString ?: continue
            val c15Obj = class15s.getAsJsonObject(c15Code) ?: continue

            val c10Code = c15Obj.get("parent")?.asString ?: continue
            val c10Obj = class10s.getAsJsonObject(c10Code) ?: continue

            val officeCode = c10Obj.get("parent")?.asString ?: continue
            val officeObj = offices.getAsJsonObject(officeCode) ?: continue

            val officeName = officeObj.get("name")?.asString ?: continue
            if (officeName != prefecture && !officeName.startsWith(prefecture)) continue

            val exactOrAddressMatch = cityName == city ||
                cityName == normalizedHint ||
                (cityName.length >= 2 && normalizedHint.startsWith(cityName)) ||
                (cityName.length >= 2 && normalizedHint.contains(cityName))

            val partialCityMatch = (cityName.startsWith(normalizedHint) && normalizedHint.endsWith("市")) ||
                (normalizedHint.endsWith("区") && cityName.endsWith(normalizedHint))

            if (!exactOrAddressMatch && !partialCityMatch) continue

            val class10Name = c10Obj.get("name")?.asString ?: ""
            val amedasCode = findAmedasCode(officeCode, c10Code)
            val displayName = "$prefecture ($class10Name)"
            val candidate = ResolvedArea(
                officeCode = officeCode,
                class10Code = c10Code,
                amedasCode = amedasCode,
                areaName = displayName,
                prefectureName = prefecture,
                isFallback = false
            )
            if (exactOrAddressMatch) {
                return candidate
            } else if (prefixFallback == null) {
                prefixFallback = candidate
            }
        }
        return prefixFallback
    }

    /**
     * 都道府県代表へのフォールバック解決
     */
    private fun resolveFallback(prefecture: String): ResolvedArea? {
        // offices から prefecture に合致する officeCode を探す
        var matchedOfficeCode: String? = null
        for ((code, elem) in offices.entrySet()) {
            val name = elem.asJsonObject.get("name")?.asString ?: continue
            if (name == prefecture || name.startsWith(prefecture)) {
                matchedOfficeCode = code
                break
            }
        }
        val officeCode = matchedOfficeCode ?: return null

        // class10s のうち parent == officeCode の最初のエントリを探す
        var matchedC10Code: String? = null
        var matchedC10Name = ""
        for ((code, elem) in class10s.entrySet()) {
            val parent = elem.asJsonObject.get("parent")?.asString ?: continue
            if (parent == officeCode) {
                matchedC10Code = code
                matchedC10Name = elem.asJsonObject.get("name")?.asString ?: ""
                break
            }
        }
        val c10Code = matchedC10Code ?: return null
        val amedasCode = findAmedasCode(officeCode, c10Code)
        val displayName = if (matchedC10Name.isNotEmpty()) "$prefecture ($matchedC10Name)" else prefecture

        return ResolvedArea(
            officeCode = officeCode,
            class10Code = c10Code,
            amedasCode = amedasCode,
            areaName = displayName,
            prefectureName = prefecture,
            isFallback = true
        )
    }

    /**
     * forecast_area.json から officeCode / class10 に対応するアメダス地点コードを取得
     */
    private fun findAmedasCode(officeCode: String, class10Code: String): String? {
        val mappings = forecastAreaJson.getAsJsonArray(officeCode) ?: return null
        for (item in mappings) {
            val obj = item.asJsonObject
            if (obj.get("class10")?.asString == class10Code) {
                return obj.getAsJsonArray("amedas")?.firstOrNull()?.asString
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: JmaAreaResolver? = null

        /**
         * Assets から JmaAreaResolver をシングルトンとして初期化・取得する
         */
        fun getInstance(context: Context): JmaAreaResolver {
            return instance ?: synchronized(this) {
                instance ?: loadFromAssets(context).also { instance = it }
            }
        }

        fun loadFromAssets(context: Context): JmaAreaResolver {
            val areaJson = context.assets.open("jma/area.json").use { stream ->
                parseJsonStream(stream)
            }
            val forecastAreaJson = context.assets.open("jma/forecast_area.json").use { stream ->
                parseJsonStream(stream)
            }
            return JmaAreaResolver(areaJson, forecastAreaJson)
        }

        fun fromStreams(areaStream: InputStream, forecastAreaStream: InputStream): JmaAreaResolver {
            val areaJson = parseJsonStream(areaStream)
            val forecastAreaJson = parseJsonStream(forecastAreaStream)
            return JmaAreaResolver(areaJson, forecastAreaJson)
        }

        private fun parseJsonStream(stream: InputStream): JsonObject {
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                return JsonParser.parseReader(reader).asJsonObject
            }
        }

        /**
         * 緯度経度から地域を解決するコンビニエンス関数
         */
        fun resolveLocation(
            context: Context,
            latitude: Double,
            longitude: Double,
            cityHint: String? = null
        ): ResolvedArea? {
            val prefecture = findPrefectureName(latitude, longitude) ?: return null
            return getInstance(context).resolve(prefecture, cityHint)
        }
    }
}
