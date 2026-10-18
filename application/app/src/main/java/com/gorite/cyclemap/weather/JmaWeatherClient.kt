package com.gorite.cyclemap.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 気象庁APIから天気予報JSONを取得するHTTPクライアント。
 * 新規SDKは使用せず、標準の HttpURLConnection を使用する。
 */
class JmaWeatherClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val userAgent: String = "CycleMap/1.0 (Android)"
) {
    private val fetchMutex = Mutex()

    /**
     * 指定された officeCode の予報JSON文字列を取得する。
     */
    suspend fun fetchForecastJson(officeCode: String): Result<String> = withContext(Dispatchers.IO) {
        val urlString = "https://www.jma.go.jp/bosai/forecast/data/forecast/$officeCode.json"
        
        fetchMutex.withLock {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", "application/json")
                    instanceFollowRedirects = true
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(
                        IllegalStateException("気象庁APIエラー (HTTP $responseCode)")
                    )
                }

                val body = connection.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                }
                Result.success(body)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }
    }
}
