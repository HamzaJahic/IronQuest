package com.ironquest.app.domain.nutrition

/** The macro a Week chart shows. Its labels are in `ui/food/FoodLabels.kt`. */
enum class MacroKind {
    PROTEIN,
    CARBS,
    FAT,
    FIBER,
    KCAL,
    ;

    fun of(summary: DaySummary): MacroLine = when (this) {
        PROTEIN -> summary.protein
        CARBS -> summary.carbs
        FAT -> summary.fat
        FIBER -> summary.fiber
        KCAL -> summary.kcal
    }
}
