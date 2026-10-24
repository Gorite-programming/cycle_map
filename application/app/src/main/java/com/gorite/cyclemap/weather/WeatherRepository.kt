package com.gorite.cyclemap.weather

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 天気データの取得、ローカルキャッシュ、UI状態管理を統括するリポジトリ。
 */
class WeatherRepository(
    private val context: Context,
    private val client: JmaWeatherClient = JmaWeatherClient(),
    private val areaResolver: JmaAreaResolver = JmaAreaResolver.getInstance(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val fetchMutex = Mutex()

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    @Volatile
    private var currentArea: ResolvedArea? = null

    @Volatile
    private var lastLat: Double = Double.NaN

    @Volatile
    private var lastLon: Double = Double.NaN

    @Volatile
    private var lastCityHint: String? = null

    @Volatile
    private var lastOnline: Boolean? = null

    init {
        // 保存済みの最新キャッシュがあれば初期状態として即時復元
        loadInitialCache()
    }

    private fun loadInitialCache() {
        val lastJson = prefs.getString(KEY_LAST_DATA, null) ?: return
        try {
            val cached = gson.fromJson(lastJson, WeatherForecastData::class.java) ?: return
            if (cached.isFresh()) {
                _uiState.value = WeatherUiState.Fresh(cached)
            } else {
                _uiState.value = WeatherUiState.Stale(cached, isOffline = false)
            }
        } catch (_: Exception) {
            // キャッシュ破損時は無視
        }
    }

    /**
     * 座標が更新された際に呼び出し、地域コードを特定して天気情報を取得・更新する。
     */
    fun updateLocation(latitude: Double, longitude: Double, cityHint: String? = null, isOnline: Boolean) {
        val normalizedHint = cityHint?.trim()?.takeIf { it.isNotEmpty() }
        val prevLat = lastLat
        val prevLon = lastLon
        if (currentArea != null &&
            !prevLat.isNaN() &&
            !prevLon.isNaN() &&
            normalizedHint == lastCityHint &&
            isOnline == lastOnline &&
            distanceMeters(prevLat, prevLon, latitude, longitude) < LOCATION_DEBOUNCE_METERS
        ) {
            val state = _uiState.value
            if (state is WeatherUiState.Fresh && state.data.isFresh()) {
                return
            }
            if (!isOnline && state is WeatherUiState.Stale) {
                return
            }
        }

        lastLat = latitude
        lastLon = longitude
        lastCityHint = normalizedHint
        lastOnline = isOnline

        coroutineScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                val prefecture = com.gorite.cyclemap.data.findPrefectureName(latitude, longitude)
                if (prefecture != null) areaResolver.resolve(prefecture, normalizedHint) else null
            }
            if (resolved == null) {
                if (_uiState.value is WeatherUiState.Loading) {
                    _uiState.value = WeatherUiState.Unavailable
                }
                return@launch
            }

            val areaChanged = currentArea?.officeCode != resolved.officeCode ||
                    currentArea?.class10Code != resolved.class10Code

            currentArea = resolved

            if (areaChanged) {
                // 新しい地域のキャッシュを確認
                val localCached = loadCachedForOffice(resolved.officeCode)
                if (localCached != null && localCached.class10Code == resolved.class10Code) {
                    if (localCached.isFresh()) {
                        _uiState.value = WeatherUiState.Fresh(localCached)
                        if (!isOnline) return@launch
                    } else {
                        _uiState.value = WeatherUiState.Stale(localCached, isOffline = !isOnline)
                    }
                }
            }

            // オンラインであれば、キャッシュが古い場合や地域変更時に取得
            if (isOnline) {
                val currentState = _uiState.value
                val needFetch = when (currentState) {
                    is WeatherUiState.Fresh -> areaChanged || !currentState.data.isFresh()
                    is WeatherUiState.Stale -> true
                    is WeatherUiState.Error -> true
                    is WeatherUiState.Loading, WeatherUiState.Unavailable -> true
                }
                if (needFetch) {
                    fetchForecast(resolved)
                }
            } else {
                // オフライン時の状態調整
                val current = _uiState.value
                if (current is WeatherUiState.Fresh && !current.data.isFresh()) {
                    _uiState.value = WeatherUiState.Stale(current.data, isOffline = true)
                } else if (current is WeatherUiState.Loading) {
                    _uiState.value = WeatherUiState.Unavailable
                }
            }
        }
    }

    /**
     * ユーザー操作またはネットワーク復帰時の手動リフレッシュ
     */
    fun refresh(isOnline: Boolean) {
        val area = currentArea ?: return
        if (!isOnline) {
            val cached = loadCachedForOffice(area.officeCode, area.class10Code)
            if (cached != null) {
                _uiState.value = WeatherUiState.Stale(cached, isOffline = true)
            } else {
                _uiState.value = WeatherUiState.Unavailable
            }
            return
        }

        coroutineScope.launch {
            fetchForecast(area)
        }
    }

    private suspend fun fetchForecast(area: ResolvedArea) = fetchMutex.withLock {
        val existing = _uiState.value
        if (existing is WeatherUiState.Fresh &&
            existing.data.officeCode == area.officeCode &&
            existing.data.class10Code == area.class10Code &&
            existing.data.isFresh()
        ) {
            return@withLock
        }
        val result = client.fetchForecastJson(area.officeCode)
        result.onSuccess { jsonStr ->
            val parsed = JmaWeatherParser.parse(jsonStr, area)
            if (parsed != null) {
                saveCache(parsed)
                _uiState.value = WeatherUiState.Fresh(parsed)
            } else {
                val cached = loadCachedForOffice(area.officeCode, area.class10Code)
                _uiState.value = WeatherUiState.Error("気象データの解析に失敗しました", cached)
            }
        }.onFailure { err ->
            val cached = loadCachedForOffice(area.officeCode, area.class10Code)
            if (cached != null) {
                _uiState.value = WeatherUiState.Stale(cached, isOffline = true)
            } else {
                _uiState.value = WeatherUiState.Error(err.message ?: "通信エラー", null)
            }
        }
    }

    private fun saveCache(data: WeatherForecastData) {
        try {
            val json = gson.toJson(data)
            prefs.edit()
                .putString(KEY_LAST_DATA, json)
                .putString(KEY_OFFICE_PREFIX + data.officeCode + "_" + data.class10Code, json)
                .putString(KEY_OFFICE_PREFIX + data.officeCode, json)
                .apply()
        } catch (_: Exception) {
            // 保存失敗は無視
        }
    }

    private fun loadCachedForOffice(officeCode: String, class10Code: String? = null): WeatherForecastData? {
        val key = if (class10Code != null) {
            KEY_OFFICE_PREFIX + officeCode + "_" + class10Code
        } else {
            KEY_OFFICE_PREFIX + officeCode
        }
        val json = prefs.getString(key, null) ?: prefs.getString(KEY_OFFICE_PREFIX + officeCode, null) ?: return null
        return try {
            val cached = gson.fromJson(json, WeatherForecastData::class.java)
            if (class10Code != null && cached.class10Code != class10Code) null else cached
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "jma_weather_cache"
        private const val KEY_LAST_DATA = "last_forecast_data"
        private const val KEY_OFFICE_PREFIX = "office_"
        private const val LOCATION_DEBOUNCE_METERS = 1000.0

        internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        @Volatile
        private var instance: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
