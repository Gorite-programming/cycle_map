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
import kotlinx.coroutines.withContext

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

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    @Volatile
    private var currentArea: ResolvedArea? = null

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
        coroutineScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                JmaAreaResolver.resolveLocation(context, latitude, longitude, cityHint)
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
                if (localCached != null) {
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
            val cached = loadCachedForOffice(area.officeCode)
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

    private suspend fun fetchForecast(area: ResolvedArea) {
        val result = client.fetchForecastJson(area.officeCode)
        result.onSuccess { jsonStr ->
            val parsed = JmaWeatherParser.parse(jsonStr, area)
            if (parsed != null) {
                saveCache(parsed)
                _uiState.value = WeatherUiState.Fresh(parsed)
            } else {
                val cached = loadCachedForOffice(area.officeCode)
                _uiState.value = WeatherUiState.Error("気象データの解析に失敗しました", cached)
            }
        }.onFailure { err ->
            val cached = loadCachedForOffice(area.officeCode)
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
                .putString(KEY_OFFICE_PREFIX + data.officeCode, json)
                .apply()
        } catch (_: Exception) {
            // 保存失敗は無視
        }
    }

    private fun loadCachedForOffice(officeCode: String): WeatherForecastData? {
        val json = prefs.getString(KEY_OFFICE_PREFIX + officeCode, null) ?: return null
        return try {
            gson.fromJson(json, WeatherForecastData::class.java)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "jma_weather_cache"
        private const val KEY_LAST_DATA = "last_forecast_data"
        private const val KEY_OFFICE_PREFIX = "office_"

        @Volatile
        private var instance: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
