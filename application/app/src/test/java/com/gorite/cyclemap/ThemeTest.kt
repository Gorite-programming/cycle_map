package com.gorite.cyclemap

import com.gorite.cyclemap.ui.theme.DarkColorScheme
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
    }

    @Test
    fun getAppColorScheme_alwaysReturnsDarkSchemeRegardlessOfContext() {
        // dynamicColor=false の場合は常に DarkColorScheme が返されること
        val scheme = getAppColorScheme(context = null, dynamicColor = false)
        assertEquals(DarkColorScheme, scheme)
    }
}
