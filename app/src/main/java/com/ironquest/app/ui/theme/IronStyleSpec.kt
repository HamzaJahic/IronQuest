package com.ironquest.app.ui.theme

import androidx.compose.runtime.Immutable
import com.ironquest.app.domain.appearance.AppStyle
import java.util.Locale

/** The layout switches of a style; fonts and shapes live in [IronType] / [IronShapes]. */
@Immutable
data class IronStyleSpec(
    val style: AppStyle,
    val listTreatment: ListTreatment,
    val titleCase: TitleCase,
    val levelPlacement: LevelPlacement,
    val summaryVariant: SummaryVariant,
    val buttonVariant: ButtonVariant,
) {
    /** Upper-cases [text] for Bold's labels/titles; other styles keep it as written. */
    fun cased(text: String): String = if (titleCase == TitleCase.UPPERCASE) text.uppercase(Locale.getDefault()) else text

    companion object {
        fun of(style: AppStyle): IronStyleSpec = when (style) {
            AppStyle.DIARY -> IronStyleSpec(style, ListTreatment.RULED_DASHED, TitleCase.SENTENCE, LevelPlacement.CORNER, SummaryVariant.THIN_RULE, ButtonVariant.ROUNDED)
            AppStyle.BOLD -> IronStyleSpec(style, ListTreatment.RULED_SOLID, TitleCase.UPPERCASE, LevelPlacement.TOP_BAR, SummaryVariant.HEAVY_RULE, ButtonVariant.BLOCK_ARROW)
            AppStyle.SOFT -> IronStyleSpec(style, ListTreatment.TILES, TitleCase.SENTENCE, LevelPlacement.PILL, SummaryVariant.PILLS, ButtonVariant.PILL)
        }
    }
}

/** How lists are drawn: Diary dashed rules, Bold solid top rules, Soft rows grouped in `raised` tiles. */
enum class ListTreatment { RULED_DASHED, RULED_SOLID, TILES }

enum class TitleCase { SENTENCE, UPPERCASE }

/** Diary: corner "Level / 7" in serif. Bold: top bar "LV 7". Soft: pill "Level 7". */
enum class LevelPlacement { CORNER, TOP_BAR, PILL }

/** Diary: 1.5 dp ink top rule. Bold: 3 dp ink top rule, upper-case labels. Soft: pills. */
enum class SummaryVariant { THIN_RULE, HEAVY_RULE, PILLS }

/** Diary: rounded accent fill. Bold: ink block, accent text, "START →". Soft: accent pill. */
enum class ButtonVariant { ROUNDED, BLOCK_ARROW, PILL }
