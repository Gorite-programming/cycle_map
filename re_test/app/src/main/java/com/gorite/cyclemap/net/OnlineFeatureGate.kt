package com.gorite.cyclemap.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface FeatureResult<out T> {
    data class Success<out T>(val data: T) : FeatureResult<T>
    data class Unavailable(val reason: String) : FeatureResult<Nothing>
}

data class OnlineFeatureConfig(
    val useOnlineAssistance: Boolean = false, // 既定 OFF
    val allowMeteredConnection: Boolean = false // モバイル通信許可 (既定 OFF: Wi-Fi/Unmetered のみ)
)

class OnlineFeatureGate(
    private val linkFlow: StateFlow<Link>,
    private var config: OnlineFeatureConfig = OnlineFeatureConfig()
) {
    fun updateConfig(newConfig: OnlineFeatureConfig) {
        config = newConfig
    }

    fun getConfig(): OnlineFeatureConfig = config

    val isOnlineAssistanceEnabled: Boolean
        get() {
            if (!config.useOnlineAssistance) return false
            return when (linkFlow.value) {
                is Link.OnlineUnmetered -> true
                is Link.OnlineMetered -> config.allowMeteredConnection
                is Link.Offline -> false
            }
        }

    suspend fun <T> execute(
        timeoutMs: Long = 3000L,
        action: suspend () -> T
    ): FeatureResult<T> {
        if (!isOnlineAssistanceEnabled) {
            return FeatureResult.Unavailable("Online assistance is disabled or unavailable")
        }

        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    action()
                }
                if (result != null) {
                    FeatureResult.Success(result)
                } else {
                    FeatureResult.Unavailable("Request timed out after ${timeoutMs}ms")
                }
            } catch (e: Exception) {
                FeatureResult.Unavailable("Request failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
