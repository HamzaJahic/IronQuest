package com.ironquest.app.data.local

import androidx.annotation.StringRes
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironquest.app.R

/**
 * Starter meals + Creatine. Inserted exactly once — from MIGRATION_1_2 for existing installs
 * and from SeedCallback.onCreate for fresh ones. Never re-run on open: these become user data,
 * and a starter meal the user deleted must stay deleted.
 *
 * Fresh installs get the names and tags in the device language (`strings_seed.xml`); the numbers
 * live here. MIGRATION_1_2 inserts its own frozen English copy.
 */
internal object NutritionSeed {

    data class StarterMeal(
        val name: String,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        val fiberG: Double,
        val tags: List<String>,
    )

    private class Template(
        @StringRes val name: Int,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        val fiberG: Double,
        val tags: List<Int>,
    )

    private val templates = listOf(
        Template(R.string.seed_meal_1, 35.0, 75.0, 9.0, 9.0, listOf(R.string.seed_tag_breakfast, R.string.seed_tag_high_protein)),
        Template(R.string.seed_meal_2, 26.0, 30.0, 20.0, 3.0, listOf(R.string.seed_tag_breakfast)),
        Template(R.string.seed_meal_3, 22.0, 25.0, 4.0, 4.0, listOf(R.string.seed_tag_snack, R.string.seed_tag_quick)),
        Template(R.string.seed_meal_4, 25.0, 4.0, 2.0, 0.0, listOf(R.string.seed_tag_snack, R.string.seed_tag_quick, R.string.seed_tag_high_protein)),
        Template(R.string.seed_meal_5, 45.0, 60.0, 12.0, 3.0, listOf(R.string.seed_tag_lunch, R.string.seed_tag_high_protein, R.string.seed_tag_meal_prep)),
        Template(R.string.seed_meal_6, 32.0, 38.0, 10.0, 4.0, listOf(R.string.seed_tag_lunch, R.string.seed_tag_quick)),
        Template(R.string.seed_meal_7, 38.0, 45.0, 22.0, 7.0, listOf(R.string.seed_tag_dinner)),
        Template(R.string.seed_meal_8, 40.0, 40.0, 18.0, 11.0, listOf(R.string.seed_tag_dinner, R.string.seed_tag_meal_prep)),
        Template(R.string.seed_meal_9, 42.0, 70.0, 12.0, 6.0, listOf(R.string.seed_tag_dinner, R.string.seed_tag_meal_prep)),
        Template(R.string.seed_meal_10, 24.0, 8.0, 15.0, 2.0, listOf(R.string.seed_tag_snack, R.string.seed_tag_high_protein)),
    )

    /** The starter meals with names and tags from [text] (a resource lookup). */
    fun meals(text: (Int) -> String): List<StarterMeal> = templates.map {
        StarterMeal(text(it.name), it.proteinG, it.carbsG, it.fatG, it.fiberG, it.tags.map(text))
    }

    /** Caller owns the transaction. Explicit ids 1..n so tags can reference them directly. */
    fun seed(db: SupportSQLiteDatabase, text: (Int) -> String) {
        insert(db, meals(text), text(R.string.seed_supplement_name), text(R.string.seed_supplement_dose))
    }

    /** Caller owns the transaction. Explicit ids 1..n so tags can reference them directly. */
    fun insert(db: SupportSQLiteDatabase, meals: List<StarterMeal>, supplementName: String, supplementDose: String) {
        meals.forEachIndexed { index, meal ->
            val id = index + 1L
            // Kotlin defaults are not SQL DEFAULTs, so every NOT NULL column is spelled out.
            db.execSQL(
                """
                INSERT INTO meals (id, name, proteinG, carbsG, fatG, fiberG, kcal, recipeText, recipeUrl, archived, createdAtMillis)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, 0, 0)
                """.trimIndent(),
                arrayOf(id, meal.name, meal.proteinG, meal.carbsG, meal.fatG, meal.fiberG),
            )
            meal.tags.forEach { tag ->
                db.execSQL("INSERT INTO meal_tags (mealId, tag) VALUES (?, ?)", arrayOf(id, tag))
            }
        }
        db.execSQL(
            "INSERT INTO supplements (name, dose, reminderTimeMin, archived, position) VALUES (?, ?, NULL, 0, 0)",
            arrayOf(supplementName, supplementDose),
        )
    }
}
