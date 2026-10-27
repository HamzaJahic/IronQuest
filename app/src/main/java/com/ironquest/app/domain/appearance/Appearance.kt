package com.ironquest.app.domain.appearance

/** Palette set by [AppearanceSettings.withStyle]; the user may change it afterwards. */
enum class AppStyle(val defaultPalette: Palette) {
    DIARY(Palette.FIELD_NOTES),
    BOLD(Palette.HIGHLIGHTER),
    SOFT(Palette.PLUM),
}

enum class Palette { FIELD_NOTES, HIGHLIGHTER, PLUM, CHALKBOARD, STEEL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val style: AppStyle = AppStyle.DIARY,
    val palette: Palette = style.defaultPalette,
    val mode: ThemeMode = ThemeMode.SYSTEM,
) {
    /** Choosing a style also resets the palette to that style's default (even the same style). */
    fun withStyle(style: AppStyle): AppearanceSettings = copy(style = style, palette = style.defaultPalette)

    companion object {
        val Default = AppearanceSettings()
    }
}
