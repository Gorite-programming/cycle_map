package com.gorite.cyclemap.ui.cycling

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// 見本準拠のダーク調サイコンUI (地図上の操作系 + ルート情報カード 共通色・ヘルパー)
// ---------------------------------------------------------------------------

/** ダークネイビー基調の共通色。 */
val CyclingNavy = Color(0xFF0F1E33)
val CyclingNavySoft = Color(0xFF1B2F4D)
val CyclingSubText = Color(0xFF9FB3C8)
val CyclingPink = Color(0xFFEC407A)
val CyclingPinkLight = Color(0xFFF48FB1)

/** 秒 → "H:MM" (見本の残り時間表記)。 */
fun formatHMM(seconds: Double): String {
    if (!seconds.isFinite()) return "--:--"
    val totalMin = (seconds / 60).toInt().coerceAtLeast(0)
    return "${totalMin / 60}:${"%02d".format(totalMin % 60)}"
}

/** 秒 → 自然な日本語の残り時間表記 (例: "約5分", "約1時間15分")。 */
fun formatRemainingTime(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "--"
    val totalMin = ((seconds + 30) / 60).toInt().coerceAtLeast(1)
    val hours = totalMin / 60
    val mins = totalMin % 60
    return when {
        hours > 0 && mins > 0 -> "約${hours}時間${mins}分"
        hours > 0 -> "約${hours}時間"
        else -> "約${mins}分"
    }
}
