package com.gorite.cyclemap

import androidx.compose.ui.graphics.Color
import com.gorite.cyclemap.ui.theme.DarkColorScheme
import com.gorite.cyclemap.ui.theme.GoogleDarkColorScheme
import com.gorite.cyclemap.ui.theme.GoogleLightColorScheme
import com.gorite.cyclemap.ui.theme.getAppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeTest {

    @Test
    fun darkColorScheme_isDefinedAndConsistent() {
        assertNotNull(DarkColorScheme)
        val bg = DarkColorScheme.background
        val surface = DarkColorScheme.surface
        assertNotNull(bg)
        assertNotNull(surface)
        assertEquals(Color(0xFF1F1F1F), bg)
        assertEquals(Color(0xFF28292A), surface)
        assertEquals(Color(0xFFE8EAED), DarkColorScheme.onSurface)
        assertEquals(Color(0xFF0D652D), DarkColorScheme.secondary)
    }

    @Test
    fun getAppColorScheme_alwaysReturnsDarkSchemeRegardlessOfContext() {
        // dynamicColor=false の場合は常に DarkColorScheme が返されること (デフォルト isDark = true)
        val scheme = getAppColorScheme(context = null, dynamicColor = false)
        assertEquals(DarkColorScheme, scheme)
    }

    @Test
    fun getAppColorScheme_returnsCorrectSchemeBasedOnIsDark() {
        assertEquals(GoogleDarkColorScheme, getAppColorScheme(isDark = true))
        assertEquals(GoogleLightColorScheme, getAppColorScheme(isDark = false))
    }
}
