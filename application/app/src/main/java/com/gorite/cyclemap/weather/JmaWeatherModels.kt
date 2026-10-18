package com.gorite.cyclemap.weather

/**
 * 解決された気象庁の地域コード情報
 */
data class ResolvedArea(
    val officeCode: String,
    val class10Code: String,
    val amedasCode: String?,
    val areaName: String,
    val prefectureName: String,
    val isFallback: Boolean = false
)

/**
 * 時間帯別降水確率エントリ
 */
data class PopEntry(
    val timePeriod: String, // 例: "00-06", "06-12", "12-18", "18-24"
    val popPercent: Int?
)

/**
 * 気温エントリ
 */
data class TempEntry(
    val label: String, // 例: "日中の最高気温", "朝の最低気温"
    val tempCelsius: Double?
)

/**
 * パース済み予報データ（オフラインキャッシュ対象）
 */
data class WeatherForecastData(
    val officeCode: String,
    val class10Code: String,
    val amedasCode: String?,
    val targetAreaName: String,
    val weatherText: String,
    val weatherCode: String,
    val currentPop: Int?,
    val currentTemp: Double?,
    val popList: List<PopEntry> = emptyList(),
    val tempList: List<TempEntry> = emptyList(),
    val reportDatetime: String,
    val fetchedAtMillis: Long = System.currentTimeMillis()
) {
    /**
     * キャッシュ鮮度判定（指定ミリ秒以内ならFresh）
     */
    fun isFresh(validDurationMillis: Long = 3 * 60 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - fetchedAtMillis) < validDurationMillis
    }
}

/**
 * 天気コードの種類分類
 */
enum class WeatherCategory {
    SUNNY,
    CLOUDY,
    RAINY,
    SNOWY,
    UNKNOWN;

    companion object {
        fun fromCode(code: String): WeatherCategory {
            val trimmed = code.trim()
            if (trimmed.isEmpty()) return UNKNOWN
            return when (trimmed.firstOrNull()) {
                '1' -> SUNNY
                '2' -> CLOUDY
                '3' -> RAINY
                '4' -> SNOWY
                else -> UNKNOWN
            }
        }
    }
}

/**
 * 天気表示用UI状態
 */
sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Fresh(val data: WeatherForecastData) : WeatherUiState
    data class Stale(val data: WeatherForecastData, val isOffline: Boolean) : WeatherUiState
    data class Error(val message: String, val cached: WeatherForecastData? = null) : WeatherUiState
    data object Unavailable : WeatherUiState
}
