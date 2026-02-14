package com.google.mediapipe.examples.llminference

import com.google.mediapipe.examples.llminference.ui.theme.AppTheme
import com.google.mediapipe.examples.llminference.ui.theme.ThemeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ThemeManager and AppTheme
 */
class ThemeManagerTest {

    @Before
    fun setUp() {
        // Reset to default before each test
        ThemeManager.currentTheme = AppTheme.PURPLISH_BLUE
    }

    @Test
    fun appTheme_hasThreeThemes() {
        val themes = AppTheme.entries
        assertEquals(3, themes.size)
    }

    @Test
    fun appTheme_displayNames_areCorrect() {
        assertEquals("Light", AppTheme.WHITE.displayName)
        assertEquals("Dark", AppTheme.BLACK.displayName)
        assertEquals("MedGemma Purple", AppTheme.PURPLISH_BLUE.displayName)
    }

    @Test
    fun themeManager_defaultTheme_isPurplishBlue() {
        assertEquals(AppTheme.PURPLISH_BLUE, ThemeManager.currentTheme)
    }

    @Test
    fun themeManager_setTheme_updatesCorrectly() {
        ThemeManager.currentTheme = AppTheme.WHITE
        assertEquals(AppTheme.WHITE, ThemeManager.currentTheme)

        ThemeManager.currentTheme = AppTheme.BLACK
        assertEquals(AppTheme.BLACK, ThemeManager.currentTheme)

        ThemeManager.currentTheme = AppTheme.PURPLISH_BLUE
        assertEquals(AppTheme.PURPLISH_BLUE, ThemeManager.currentTheme)
    }

    @Test
    fun appTheme_valueOf_worksCorrectly() {
        assertEquals(AppTheme.WHITE, AppTheme.valueOf("WHITE"))
        assertEquals(AppTheme.BLACK, AppTheme.valueOf("BLACK"))
        assertEquals(AppTheme.PURPLISH_BLUE, AppTheme.valueOf("PURPLISH_BLUE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun appTheme_valueOf_throwsForInvalid() {
        AppTheme.valueOf("INVALID_THEME")
    }

    @Test
    fun appTheme_name_roundTrips() {
        for (theme in AppTheme.entries) {
            val name = theme.name
            val restored = AppTheme.valueOf(name)
            assertEquals(theme, restored)
        }
    }

    @Test
    fun appTheme_ordinal_isSequential() {
        assertEquals(0, AppTheme.WHITE.ordinal)
        assertEquals(1, AppTheme.BLACK.ordinal)
        assertEquals(2, AppTheme.PURPLISH_BLUE.ordinal)
    }
}
