package com.gorite.cyclemap.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@VisibleForTesting
internal val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

/**
 * ダークモード/ライトモード設定に関係なく、常にダークテーマの配色を取得する。
 */
@VisibleForTesting
internal fun getAppColorScheme(
    context: Context? = null,
    dynamicColor: Boolean = true,
): ColorScheme {
    return if (dynamicColor && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        DarkColorScheme
    }
}

/**
 * CycleMap 専用テーマ。
 * システム設定（ライト/ダークモード）に関係なく、現在のダークモード配色を常に強制する。
 */
@Composable
fun CycleMapTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = getAppColorScheme(context, dynamicColor)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}