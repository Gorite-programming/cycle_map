package com.gorite.cyclemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.ui.theme.CycleMapTheme

/**
 * CycleMap Android エントリーポイント。
 * 画面ロジックはすべて [MapScreen] に委譲している。
 * このクラスは Activity ライフサイクルの処理のみを行う。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 通常探索時は黒アイコン (SystemBarStyle.light)、ナビ案内時 (深緑ヘッダー) は白アイコン (SystemBarStyle.dark)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // Compose のレンダリング前に osmdroid および MapLibre ネイティブエンジンを初期化
        configureOsmdroid(this, cycleMapDataDir(this))
        org.maplibre.android.MapLibre.getInstance(this)
        setContent {
            CycleMapTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    MapScreen(
                        modifier = Modifier.fillMaxSize(),
                        systemInsets = innerPadding,
                        onNavigationStateChanged = { isNavigating ->
                            enableEdgeToEdge(
                                statusBarStyle = if (isNavigating) {
                                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                } else {
                                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                                },
                                navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                            )
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
