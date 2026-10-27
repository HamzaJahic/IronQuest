package com.ironquest.app.data.local

/** The English seed as it was before the strings moved to resources (frozen copy, compared in NutritionSeedTest). */
internal object SeedGolden {
    data class Meal(val name: String, val proteinG: Double, val carbsG: Double, val fatG: Double, val fiber: Double, val tags: List<String>)

    val meals = listOf(
        Meal("Oats with whey & banana", 35.0, 75.0, 9.0, 9.0, listOf("breakfast", "high-protein")),
        Meal("Scrambled eggs on toast", 26.0, 30.0, 20.0, 3.0, listOf("breakfast")),
        Meal("Greek yogurt & berries", 22.0, 25.0, 4.0, 4.0, listOf("snack", "quick")),
        Meal("Protein shake", 25.0, 4.0, 2.0, 0.0, listOf("snack", "quick", "high-protein")),
        Meal("Chicken & rice bowl", 45.0, 60.0, 12.0, 3.0, listOf("lunch", "high-protein", "meal-prep")),
        Meal("Tuna wrap", 32.0, 38.0, 10.0, 4.0, listOf("lunch", "quick")),
        Meal("Salmon, potatoes & veg", 38.0, 45.0, 22.0, 7.0, listOf("dinner")),
        Meal("Beef chili", 40.0, 40.0, 18.0, 11.0, listOf("dinner", "meal-prep")),
        Meal("Turkey pasta", 42.0, 70.0, 12.0, 6.0, listOf("dinner", "meal-prep")),
        Meal("Cottage cheese & nuts", 24.0, 8.0, 15.0, 2.0, listOf("snack", "high-protein")),
    )

    const val SUPPLEMENT_NAME = "Creatine"
    const val SUPPLEMENT_DOSE = "5 g"
}
