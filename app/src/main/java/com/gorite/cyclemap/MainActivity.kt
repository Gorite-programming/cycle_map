package com.gorite.cyclemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
        enableEdgeToEdge()
        setContent {
            CycleMapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) LocationTrackingService.stopTracking(this)
        super.onDestroy()
    }
}
