package com.ironquest.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.domain.model.StatType

/**
 * Colour tokens: ten palette x mode sets. Read them through `IronTheme.colors`;
 * never hardcode colours in screens. `reward` is only for level / XP / goal-done moments.
 */
@Immutable
data class IronColors(
    val isDark: Boolean,
    val paper: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val reward: Color,
    val up: Color,
    val upBg: Color,
    val hold: Color,
    val holdBg: Color,
    val deload: Color,
    val deloadBg: Color,
    val con: Color,
    val str: Color,
    val sta: Color,
) {
    /**
     * Red for text, icons and borders on `paper`/`raised`. [deload] is the text colour for use on
     * [deloadBg] (white in Highlighter, unreadable on paper), so there the red fill itself is used.
     */
    val danger: Color get() = if (deload == Color.White) deloadBg else deload

    fun stat(stat: StatType): Color = when (stat) {
        StatType.STRENGTH -> str
        StatType.CONSTITUTION -> con
        StatType.STAMINA -> sta
    }

    companion object {
        fun of(palette: Palette, dark: Boolean): IronColors = when (palette) {
            Palette.FIELD_NOTES -> if (dark) FIELD_NOTES_DARK else FIELD_NOTES_LIGHT
            Palette.HIGHLIGHTER -> if (dark) HIGHLIGHTER_DARK else HIGHLIGHTER_LIGHT
            Palette.PLUM -> if (dark) PLUM_DARK else PLUM_LIGHT
            Palette.CHALKBOARD -> if (dark) CHALKBOARD_DARK else CHALKBOARD_LIGHT
            Palette.STEEL -> if (dark) STEEL_DARK else STEEL_LIGHT
        }

        /** The swatch shown for a palette in the picker (its light-mode signature colour, as in the mockup). */
        fun swatch(palette: Palette): Color = when (palette) {
            Palette.FIELD_NOTES -> FIELD_NOTES_LIGHT.accent
            Palette.HIGHLIGHTER -> HIGHLIGHTER_LIGHT.onAccent
            Palette.PLUM -> PLUM_LIGHT.accent
            Palette.CHALKBOARD -> CHALKBOARD_DARK.paper
            Palette.STEEL -> STEEL_LIGHT.accent
        }

        // FIELD_NOTES · light
        private val FIELD_NOTES_LIGHT = IronColors(
            isDark = false,
            paper = Color(0xFFECEEE4),
            raised = Color(0xFFF5F6EF),
            ink = Color(0xFF1F2A1F),
            muted = Color(0xFF62705F),
            line = Color(0xFFCDD3C2),
            accent = Color(0xFF2F5D3A),
            onAccent = Color(0xFFF2F4EC),
            reward = Color(0xFFB8892B),
            up = Color(0xFF2F5D3A),
            upBg = Color(0xFFD7E3D2),
            hold = Color(0xFF7A6A2E),
            holdBg = Color(0xFFECE4C8),
            deload = Color(0xFF9A3B2E),
            deloadBg = Color(0xFFF0D9D3),
            con = Color(0xFF8A5A2B),
            str = Color(0xFF2F5D3A),
            sta = Color(0xFF2E5F7A),
        )

        // FIELD_NOTES · dark
        private val FIELD_NOTES_DARK = IronColors(
            isDark = true,
            paper = Color(0xFF161B16),
            raised = Color(0xFF1E251D),
            ink = Color(0xFFE3E8DC),
            muted = Color(0xFF97A392),
            line = Color(0xFF2C352B),
            accent = Color(0xFF8DBF8F),
            onAccent = Color(0xFF102012),
            reward = Color(0xFFD9AE55),
            up = Color(0xFF9CCB9A),
            upBg = Color(0xFF22331F),
            hold = Color(0xFFD6C27A),
            holdBg = Color(0xFF332E1C),
            deload = Color(0xFFE59A8C),
            deloadBg = Color(0xFF3A221E),
            con = Color(0xFFD4A373),
            str = Color(0xFF9CCB9A),
            sta = Color(0xFF8EC1DA),
        )

        // HIGHLIGHTER · light
        private val HIGHLIGHTER_LIGHT = IronColors(
            isDark = false,
            paper = Color(0xFFF2F1EE),
            raised = Color(0xFFFFFFFF),
            ink = Color(0xFF111111),
            muted = Color(0xFF6B6A66),
            line = Color(0xFFD8D6D0),
            accent = Color(0xFF111111),
            onAccent = Color(0xFFE8FF3A),
            reward = Color(0xFFC9DE00),
            up = Color(0xFF3E4A00),
            upBg = Color(0xFFE8FF3A),
            hold = Color(0xFF111111),
            holdBg = Color(0xFFE2E0DA),
            deload = Color(0xFFFFFFFF),
            deloadBg = Color(0xFFD7263D),
            con = Color(0xFF111111),
            str = Color(0xFF111111),
            sta = Color(0xFF111111),
        )

        // HIGHLIGHTER · dark
        private val HIGHLIGHTER_DARK = IronColors(
            isDark = true,
            paper = Color(0xFF111111),
            raised = Color(0xFF1C1C1B),
            ink = Color(0xFFF2F1EE),
            muted = Color(0xFF9A9893),
            line = Color(0xFF2A2A28),
            accent = Color(0xFFE8FF3A),
            onAccent = Color(0xFF111111),
            reward = Color(0xFFE8FF3A),
            up = Color(0xFF111111),
            upBg = Color(0xFFE8FF3A),
            hold = Color(0xFFF2F1EE),
            holdBg = Color(0xFF2A2A28),
            deload = Color(0xFFFFFFFF),
            deloadBg = Color(0xFFD7263D),
            con = Color(0xFFF2F1EE),
            str = Color(0xFFF2F1EE),
            sta = Color(0xFFF2F1EE),
        )

        // PLUM · light
        private val PLUM_LIGHT = IronColors(
            isDark = false,
            paper = Color(0xFFF5F2F4),
            raised = Color(0xFFFFFFFF),
            ink = Color(0xFF231A24),
            muted = Color(0xFF76687A),
            line = Color(0xFFDED5DD),
            accent = Color(0xFF6B2D6B),
            onAccent = Color(0xFFFFFFFF),
            reward = Color(0xFFC08A2E),
            up = Color(0xFF2E6B4F),
            upBg = Color(0xFFD9EBE1),
            hold = Color(0xFF7A5A1E),
            holdBg = Color(0xFFF0E4CC),
            deload = Color(0xFFA3324A),
            deloadBg = Color(0xFFF3D6DD),
            con = Color(0xFF9B4F2E),
            str = Color(0xFF6B2D6B),
            sta = Color(0xFF2E5A8A),
        )

        // PLUM · dark
        private val PLUM_DARK = IronColors(
            isDark = true,
            paper = Color(0xFF1C161D),
            raised = Color(0xFF261E28),
            ink = Color(0xFFEDE4EC),
            muted = Color(0xFFA596A8),
            line = Color(0xFF33293A),
            accent = Color(0xFFD7A1D6),
            onAccent = Color(0xFF2A0F2A),
            reward = Color(0xFFE3B866),
            up = Color(0xFF8FD1AE),
            upBg = Color(0xFF1E3329),
            hold = Color(0xFFE2C27E),
            holdBg = Color(0xFF382D1A),
            deload = Color(0xFFF09AAE),
            deloadBg = Color(0xFF3D1E27),
            con = Color(0xFFE4A07D),
            str = Color(0xFFD7A1D6),
            sta = Color(0xFF93BDEB),
        )

        // CHALKBOARD · light
        private val CHALKBOARD_LIGHT = IronColors(
            isDark = false,
            paper = Color(0xFFE3E7E6),
            raised = Color(0xFFEEF1F0),
            ink = Color(0xFF1D2A28),
            muted = Color(0xFF5E6E6B),
            line = Color(0xFFC4CDCB),
            accent = Color(0xFF2C4A45),
            onAccent = Color(0xFFF4F1E8),
            reward = Color(0xFFB58B2A),
            up = Color(0xFF2E6B4F),
            upBg = Color(0xFFD3E6DC),
            hold = Color(0xFF7A5A1E),
            holdBg = Color(0xFFEDE2C6),
            deload = Color(0xFFA3324A),
            deloadBg = Color(0xFFF1D6DB),
            con = Color(0xFFA0582F),
            str = Color(0xFF2C4A45),
            sta = Color(0xFF2E5A8A),
        )

        // CHALKBOARD · dark
        private val CHALKBOARD_DARK = IronColors(
            isDark = true,
            paper = Color(0xFF1E2B28),
            raised = Color(0xFF263632),
            ink = Color(0xFFF1EEE4),
            muted = Color(0xFFA9B5B0),
            line = Color(0xFF33443F),
            accent = Color(0xFFF1EEE4),
            onAccent = Color(0xFF1E2B28),
            reward = Color(0xFFF2D27A),
            up = Color(0xFFA8E0B8),
            upBg = Color(0xFF2A3D36),
            hold = Color(0xFFF2D27A),
            holdBg = Color(0xFF3A3A2A),
            deload = Color(0xFFF5A3A3),
            deloadBg = Color(0xFF40302E),
            con = Color(0xFFF5C29B),
            str = Color(0xFFA8E0B8),
            sta = Color(0xFFA9CFF2),
        )

        // STEEL · light
        private val STEEL_LIGHT = IronColors(
            isDark = false,
            paper = Color(0xFFE9EBEC),
            raised = Color(0xFFF5F6F7),
            ink = Color(0xFF1B1E20),
            muted = Color(0xFF61676B),
            line = Color(0xFFCBD0D3),
            accent = Color(0xFFF26B1D),
            onAccent = Color(0xFFFFFFFF),
            reward = Color(0xFFF26B1D),
            up = Color(0xFF1F6E43),
            upBg = Color(0xFFD3E8DB),
            hold = Color(0xFF6B5A1E),
            holdBg = Color(0xFFECE3C6),
            deload = Color(0xFFB3261E),
            deloadBg = Color(0xFFF3D6D3),
            con = Color(0xFF8A5A2B),
            str = Color(0xFF1B1E20),
            sta = Color(0xFF2E5A8A),
        )

        // STEEL · dark
        private val STEEL_DARK = IronColors(
            isDark = true,
            paper = Color(0xFF17191B),
            raised = Color(0xFF202326),
            ink = Color(0xFFE6E8EA),
            muted = Color(0xFF9AA1A6),
            line = Color(0xFF2C3034),
            accent = Color(0xFFFF8A3D),
            onAccent = Color(0xFF1B0F05),
            reward = Color(0xFFFF8A3D),
            up = Color(0xFF8FD1AE),
            upBg = Color(0xFF1E3329),
            hold = Color(0xFFE2C27E),
            holdBg = Color(0xFF36301C),
            deload = Color(0xFFF28B82),
            deloadBg = Color(0xFF3D201E),
            con = Color(0xFFE4A07D),
            str = Color(0xFFE6E8EA),
            sta = Color(0xFF93BDEB),
        )
    }
}
