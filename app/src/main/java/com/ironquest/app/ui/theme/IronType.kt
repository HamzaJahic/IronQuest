package com.ironquest.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ironquest.app.domain.appearance.AppStyle

/** Tabular figures, so numbers line up and don't jitter when they change. */
private const val TNUM = "tnum"

/**
 * Text styles of one [AppStyle]. Colours are not set here: components pick `ink`/`muted`/… from
 * `IronTheme.colors`. Styles that show numbers carry tabular figures.
 */
@Immutable
data class IronType(
    /** Screen title ("You", "Upper 1"). Upper-casing for Bold is applied by `PageTitle`. */
    val pageTitle: TextStyle,
    val sectionHead: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    /** Secondary lines under a row or title ("Level 7 · 1,260 XP"). */
    val secondary: TextStyle,
    /** Small labels: summary-strip captions, tab labels, chips. */
    val label: TextStyle,
    /** Big numbers in the summary strip and the level mark. */
    val number: TextStyle,
    val button: TextStyle,
) {
    companion object {
        fun of(style: AppStyle): IronType = when (style) {
            AppStyle.DIARY -> Diary
            AppStyle.BOLD -> Bold
            AppStyle.SOFT -> Soft
        }

        private fun base(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Int) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp, fontFeatureSettings = TNUM)

        // Sizes: body 15 / secondary 13 everywhere (14 / 12 felt cramped); secondary is Medium so
        // small grey text keeps its strokes. Diary's page title is Lora (Fraunces' hairlines were too
        // thin); everything else is Inter.
        private val Diary = IronType(
            pageTitle = base(IronFonts.Lora, FontWeight.Bold, 32, 38),
            sectionHead = base(IronFonts.Inter, FontWeight.SemiBold, 16, 22),
            body = base(IronFonts.Inter, FontWeight.Normal, 15, 22),
            bodyStrong = base(IronFonts.Inter, FontWeight.SemiBold, 15, 22),
            secondary = base(IronFonts.Inter, FontWeight.Medium, 13, 18),
            label = base(IronFonts.Inter, FontWeight.Medium, 12, 16),
            number = base(IronFonts.Inter, FontWeight.SemiBold, 24, 28),
            button = base(IronFonts.Inter, FontWeight.SemiBold, 16, 22),
        )

        private val Bold = IronType(
            pageTitle = base(IronFonts.Archivo, FontWeight.ExtraBold, 52, 48).copy(letterSpacing = (-0.02).em),
            sectionHead = base(IronFonts.Archivo, FontWeight.Bold, 12, 16).copy(letterSpacing = 0.06.em),
            body = base(IronFonts.Inter, FontWeight.Normal, 15, 22),
            bodyStrong = base(IronFonts.Inter, FontWeight.SemiBold, 15, 22),
            secondary = base(IronFonts.Inter, FontWeight.Medium, 13, 18),
            label = base(IronFonts.Archivo, FontWeight.Bold, 11, 14).copy(letterSpacing = 0.05.em),
            number = base(IronFonts.Archivo, FontWeight.ExtraBold, 26, 28).copy(letterSpacing = (-0.03).em),
            button = base(IronFonts.Archivo, FontWeight.ExtraBold, 15, 20),
        )

        private val Soft = IronType(
            pageTitle = base(IronFonts.Nunito, FontWeight.ExtraBold, 26, 32),
            sectionHead = base(IronFonts.Nunito, FontWeight.ExtraBold, 16, 22),
            body = base(IronFonts.Nunito, FontWeight.Medium, 15, 22),
            bodyStrong = base(IronFonts.Nunito, FontWeight.Bold, 15, 22),
            secondary = base(IronFonts.Nunito, FontWeight.Bold, 13, 18),
            label = base(IronFonts.Nunito, FontWeight.Bold, 12, 16),
            number = base(IronFonts.Nunito, FontWeight.ExtraBold, 20, 24),
            button = base(IronFonts.Nunito, FontWeight.ExtraBold, 15, 20),
        )
    }
}
