package com.ironquest.app.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun `defaults are Diary, Field notes, System`() {
        assertEquals(AppearanceSettings(AppStyle.DIARY, Palette.FIELD_NOTES, ThemeMode.SYSTEM), AppearanceSettings.Default)
    }

    @Test
    fun `withStyle resets the palette to the style default and keeps the mode`() {
        val custom = AppearanceSettings(AppStyle.DIARY, Palette.STEEL, ThemeMode.DARK)
        assertEquals(AppearanceSettings(AppStyle.BOLD, Palette.HIGHLIGHTER, ThemeMode.DARK), custom.withStyle(AppStyle.BOLD))
        assertEquals(AppearanceSettings(AppStyle.SOFT, Palette.PLUM, ThemeMode.DARK), custom.withStyle(AppStyle.SOFT))
        // Picking the same style again also resets the palette.
        assertEquals(Palette.FIELD_NOTES, custom.withStyle(AppStyle.DIARY).palette)
    }
}
