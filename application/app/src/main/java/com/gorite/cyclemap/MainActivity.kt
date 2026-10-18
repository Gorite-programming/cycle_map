package com.gorite.cyclemap

import com.gorite.cyclemap.utils.*
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.ui.theme.CycleMapTheme
import com.gorite.cyclemap.ui.theme.DayNightResolver
import com.gorite.cyclemap.ui.screens.MapScreen
import com.gorite.cyclemap.ui.theme.ThemeMode

/**
 * CycleMap Android エントリーポイント。
 * 画面ロジックはすべて [MapScreen] に委譲している。
 * 昼夜連動ナイトモードおよび Activity ライフサイクルの処理を行う。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compose のレンダリング前に osmdroid および MapLibre ネイティブエンジンを初期化
        configureOsmdroid(this, cycleMapDataDir(this))
        org.maplibre.android.MapLibre.getInstance(this)

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val isSystemDark = isSystemInDarkTheme()
            val prefs = remember(context) { context.getSharedPreferences("cyclemap_prefs", Context.MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(ThemeMode.fromKey(prefs.getString("theme_mode", ThemeMode.AUTO.name)))
            }
            var currentLocation by remember { mutableStateOf<android.location.Location?>(null) }
            var isNavigatingState by remember { mutableStateOf(false) }

            // 停止中・室内待機時でも日没・日の出時刻の到来に合わせて自動切替するための定期タイマー (60秒)
            var timeTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000L)
                    timeTick = System.currentTimeMillis()
                }
            }

            val isDark = remember(themeMode, currentLocation, timeTick) {
                DayNightResolver.resolveIsDark(
                    mode = themeMode,
                    isSystemInDarkTheme = false,
                    latitude = currentLocation?.latitude ?: 34.3965,
                    longitude = currentLocation?.longitude ?: 132.4596,
                    epochMillis = timeTick,
                )
            }

            LaunchedEffect(isDark, isNavigatingState) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark || isNavigatingState) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                )
            }

            CycleMapTheme(darkTheme = isDark) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    MapScreen(
                        modifier = Modifier.fillMaxSize(),
                        systemInsets = innerPadding,
                        themeMode = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            prefs.edit().putString("theme_mode", newMode.name).apply()
                        },
                        isDarkTheme = isDark,
                        onLocationUpdate = { loc -> currentLocation = loc },
                        onNavigationStateChanged = { isNavigating ->
                            isNavigatingState = isNavigating
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) LocationTrackingService.stopTracking(this)
        super.onDestroy()
    }
}
