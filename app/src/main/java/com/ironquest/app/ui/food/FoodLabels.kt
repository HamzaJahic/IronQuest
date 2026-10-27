package com.ironquest.app.ui.food

import androidx.annotation.StringRes
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.MacroKind

/** Display text of a Week-chart macro; the enum itself carries none. */
@StringRes
fun MacroKind.labelRes(): Int = when (this) {
    MacroKind.PROTEIN -> R.string.food_macro_protein
    MacroKind.CARBS -> R.string.food_macro_carbs
    MacroKind.FAT -> R.string.food_macro_fat
    MacroKind.FIBER -> R.string.food_macro_fiber
    MacroKind.KCAL -> R.string.food_macro_calories
}

/** The toggle label: "P", "C", "F", "FIB", "KCAL". */
@StringRes
fun MacroKind.shortRes(): Int = when (this) {
    MacroKind.PROTEIN -> R.string.food_macro_protein_short
    MacroKind.CARBS -> R.string.food_macro_carbs_short
    MacroKind.FAT -> R.string.food_macro_fat_short
    MacroKind.FIBER -> R.string.food_macro_fiber_short
    MacroKind.KCAL -> R.string.food_macro_calories_short
}

@StringRes
fun MacroKind.unitRes(): Int = when (this) {
    MacroKind.KCAL -> R.string.unit_kcal
    else -> R.string.unit_g
}

@StringRes
fun DayMark.labelRes(): Int = when (this) {
    DayMark.ON_TARGET -> R.string.food_mark_on_target
    DayMark.UNDER -> R.string.food_mark_under
    DayMark.OVER -> R.string.food_mark_over
    DayMark.NO_TARGET -> R.string.food_mark_no_target
    DayMark.NO_DATA -> R.string.food_mark_no_log
}
