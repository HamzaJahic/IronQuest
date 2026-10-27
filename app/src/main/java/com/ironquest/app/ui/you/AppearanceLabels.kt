package com.ironquest.app.ui.you

import androidx.annotation.StringRes
import com.ironquest.app.R
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.Palette

/** Shared by the You tab's Appearance section and the first-launch appearance step. */
@StringRes
internal fun AppStyle.labelRes() = when (this) {
    AppStyle.DIARY -> R.string.you_style_diary
    AppStyle.BOLD -> R.string.you_style_bold
    AppStyle.SOFT -> R.string.you_style_soft
}

@StringRes
internal fun Palette.labelRes() = when (this) {
    Palette.FIELD_NOTES -> R.string.you_palette_field_notes
    Palette.HIGHLIGHTER -> R.string.you_palette_highlighter
    Palette.PLUM -> R.string.you_palette_plum
    Palette.CHALKBOARD -> R.string.you_palette_chalkboard
    Palette.STEEL -> R.string.you_palette_steel
}
