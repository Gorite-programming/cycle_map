package com.gorite.cyclemap.ui.theme

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Google Maps スタイルのクリーンなライト配色。
 * 白背景 (#FFFFFF)、Google Blue (#1A73E8)、Google Dark Gray (#202124)、Medium Gray (#5F6368)。
 */
@VisibleForTesting
internal val GoogleLightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF1A73E8),
    secondary = Color(0xFF1E8E3E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4EA),
    onSecondaryContainer = Color(0xFF137333),
    surface = Color.White,
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF202124),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFFC5221F),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFE8EAED),
)

@VisibleForTesting
internal fun getAppColorScheme(
    context: Context? = null,
    dynamicColor: Boolean = false,
): ColorScheme {
    return GoogleLightColorScheme
}

/**
 * CycleMap 専用テーマ (Google Maps スタイル ライトテーマ)。
 */
@Composable
fun CycleMapTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = GoogleLightColorScheme,
        typography = Typography,
        content = content,
    )
}