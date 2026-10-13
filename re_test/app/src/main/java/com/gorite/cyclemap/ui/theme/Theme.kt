package com.gorite.cyclemap.ui.theme

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

/**
 * Google Maps 夜間ナビゲーション仕様のダーク配色。
 * 背景 (#1F1F1F)、サーフェス (#28292A)、テキスト白 (#E8EAED)、案内ヘッダー深緑 (#0D652D)。
 */
@VisibleForTesting
internal val GoogleDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF1F1F1F),
    primaryContainer = Color(0xFF0D652D),
    onPrimaryContainer = Color(0xFFE8EAED),
    secondary = Color(0xFF0D652D),
    onSecondary = Color(0xFFE8EAED),
    secondaryContainer = Color(0xFF137333),
    onSecondaryContainer = Color(0xFFE8EAED),
    surface = Color(0xFF28292A),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF3C4043),
    onSurfaceVariant = Color(0xFFBDC1C6),
    background = Color(0xFF1F1F1F),
    onBackground = Color(0xFFE8EAED),
    error = Color(0xFFF28B82),
    onError = Color(0xFF1F1F1F),
    errorContainer = Color(0xFF5C0000),
    onErrorContainer = Color(0xFFFCE8E6),
    outline = Color(0xFF5F6368),
    outlineVariant = Color(0xFF3C4043),
)

@VisibleForTesting
internal val DarkColorScheme = GoogleDarkColorScheme

@VisibleForTesting
internal fun getAppColorScheme(
    context: Context? = null,
    dynamicColor: Boolean = false,
    isDark: Boolean = true,
): ColorScheme {
    return if (isDark) {
        GoogleDarkColorScheme
    } else {
        GoogleLightColorScheme
    }
}

/**
 * CycleMap 専用テーマ (Google Maps スタイル 昼夜連動テーマ)。
 */
@Composable
fun CycleMapTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = getAppColorScheme(
        context = context,
        dynamicColor = dynamicColor,
        isDark = darkTheme,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}