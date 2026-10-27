package com.ironquest.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironquest.app.data.local.entity.PlayerProfileEntity
import com.ironquest.app.domain.progression.LevelCurve

// not UI text: frozen v1→v2 seed (existing installs got English names; do not translate or edit)
private val FROZEN_V2_MEALS = listOf(
    NutritionSeed.StarterMeal("Oats with whey & banana", 35.0, 75.0, 9.0, 9.0, listOf("breakfast", "high-protein")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Scrambled eggs on toast", 26.0, 30.0, 20.0, 3.0, listOf("breakfast")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Greek yogurt & berries", 22.0, 25.0, 4.0, 4.0, listOf("snack", "quick")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Protein shake", 25.0, 4.0, 2.0, 0.0, listOf("snack", "quick", "high-protein")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Chicken & rice bowl", 45.0, 60.0, 12.0, 3.0, listOf("lunch", "high-protein", "meal-prep")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Tuna wrap", 32.0, 38.0, 10.0, 4.0, listOf("lunch", "quick")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Salmon, potatoes & veg", 38.0, 45.0, 22.0, 7.0, listOf("dinner")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Beef chili", 40.0, 40.0, 18.0, 11.0, listOf("dinner", "meal-prep")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Turkey pasta", 42.0, 70.0, 12.0, 6.0, listOf("dinner", "meal-prep")), // not UI text: frozen v1→v2 seed
    NutritionSeed.StarterMeal("Cottage cheese & nuts", 24.0, 8.0, 15.0, 2.0, listOf("snack", "high-protein")), // not UI text: frozen v1→v2 seed
)

private const val FROZEN_V2_SUPPLEMENT_NAME = "Creatine" // not UI text: frozen v1→v2 seed
private const val FROZEN_V2_SUPPLEMENT_DOSE = "5 g" // not UI text: frozen v1→v2 seed

/** v1 → v2: adds meal tracking ("Food") and seeds starter meals + Creatine. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Copied from app/schemas/.../2.json. Room runs migrations inside a transaction.
        listOf(
            "CREATE TABLE IF NOT EXISTS `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `fiberG` REAL NOT NULL, `kcal` REAL, `recipeText` TEXT, `recipeUrl` TEXT, `archived` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `meal_tags` (`mealId` INTEGER NOT NULL, `tag` TEXT NOT NULL, PRIMARY KEY(`mealId`, `tag`), FOREIGN KEY(`mealId`) REFERENCES `meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_meal_tags_tag` ON `meal_tags` (`tag`)",
            "CREATE TABLE IF NOT EXISTS `meal_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `targetProteinG` REAL, `targetCarbsG` REAL, `targetFatG` REAL, `targetFiberG` REAL, `targetKcal` REAL, `waterTargetMl` INTEGER, `position` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `plan_slots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `label` TEXT NOT NULL, `mealId` INTEGER NOT NULL, `timeOfDayMin` INTEGER, `reminderOffsetMin` INTEGER, `position` INTEGER NOT NULL, FOREIGN KEY(`planId`) REFERENCES `meal_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`mealId`) REFERENCES `meals`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_plan_slots_planId` ON `plan_slots` (`planId`)",
            "CREATE INDEX IF NOT EXISTS `index_plan_slots_mealId` ON `plan_slots` (`mealId`)",
            "CREATE TABLE IF NOT EXISTS `meal_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `slotId` INTEGER, `mealId` INTEGER, `mealName` TEXT NOT NULL, `portion` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `fiberG` REAL NOT NULL, `kcal` REAL NOT NULL, `xpAwarded` INTEGER NOT NULL, `loggedAtMillis` INTEGER NOT NULL, FOREIGN KEY(`slotId`) REFERENCES `plan_slots`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`mealId`) REFERENCES `meals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_logs_dateEpochDay_slotId` ON `meal_logs` (`dateEpochDay`, `slotId`)",
            "CREATE INDEX IF NOT EXISTS `index_meal_logs_slotId` ON `meal_logs` (`slotId`)",
            "CREATE INDEX IF NOT EXISTS `index_meal_logs_mealId` ON `meal_logs` (`mealId`)",
            "CREATE TABLE IF NOT EXISTS `nutrition_days` (`dateEpochDay` INTEGER NOT NULL, `planId` INTEGER, `targetProteinG` REAL, `targetCarbsG` REAL, `targetFatG` REAL, `targetFiberG` REAL, `targetKcal` REAL, `waterTargetMl` INTEGER, `waterMl` INTEGER NOT NULL, `proteinBonusXp` INTEGER NOT NULL, `waterBonusXp` INTEGER NOT NULL, PRIMARY KEY(`dateEpochDay`))",
            "CREATE TABLE IF NOT EXISTS `supplements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `reminderTimeMin` INTEGER, `archived` INTEGER NOT NULL, `position` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `supplement_intakes` (`supplementId` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `xpAwarded` INTEGER NOT NULL, `loggedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`supplementId`, `dateEpochDay`), FOREIGN KEY(`supplementId`) REFERENCES `supplements`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        ).forEach(db::execSQL)
        NutritionSeed.insert(db, FROZEN_V2_MEALS, FROZEN_V2_SUPPLEMENT_NAME, FROZEN_V2_SUPPLEMENT_DOSE)
    }
}

/**
 * v2 → v3: supplements.createdAtEpochDay (adherence counts from it) and
 * player_profile.highestLevelReached (level-ups are only reported above it).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Column definitions copied from app/schemas/.../3.json; both columns are last in their
        // createSql, so ADD COLUMN yields the exact v3 table.
        db.execSQL("ALTER TABLE `supplements` ADD COLUMN `createdAtEpochDay` INTEGER")
        db.execSQL("ALTER TABLE `player_profile` ADD COLUMN `highestLevelReached` INTEGER NOT NULL DEFAULT 1")

        // Best guess for existing supplements: the day of their first intake; never taken → NULL
        // (adherence then counts from the range start, as before).
        db.execSQL(
            """
            UPDATE supplements SET createdAtEpochDay =
                (SELECT MIN(i.dateEpochDay) FROM supplement_intakes i WHERE i.supplementId = supplements.id)
            """.trimIndent(),
        )

        // The level the character is at now; the curve lives in Kotlin (LevelCurve), not SQL.
        db.query("SELECT totalXp FROM player_profile WHERE id = ${PlayerProfileEntity.SINGLETON_ID}").use { cursor ->
            if (cursor.moveToFirst()) {
                val level = LevelCurve.CHARACTER.progressFor(cursor.getLong(0)).level
                db.execSQL(
                    "UPDATE player_profile SET highestLevelReached = ? WHERE id = ${PlayerProfileEntity.SINGLETON_ID}",
                    arrayOf<Any>(level),
                )
            }
        }
    }
}

/** v3 → v4: imported training programs (CSV import). The built-in split stays in ProgramCatalog. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Copied from app/schemas/.../4.json.
        listOf(
            "CREATE TABLE IF NOT EXISTS `programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `program_days` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `programId` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`programId`) REFERENCES `programs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_program_days_programId` ON `program_days` (`programId`)",
            "CREATE TABLE IF NOT EXISTS `program_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `targetSets` INTEGER NOT NULL, `repMin` INTEGER NOT NULL, `repMax` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`dayId`) REFERENCES `program_days`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_program_exercises_dayId_exerciseId` ON `program_exercises` (`dayId`, `exerciseId`)",
            "CREATE INDEX IF NOT EXISTS `index_program_exercises_exerciseId` ON `program_exercises` (`exerciseId`)",
        ).forEach(db::execSQL)
    }
}

/** v4 → v5: supersets. `program_exercises.supersetGroup` (nullable; existing rows stay standalone). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Column definition copied from app/schemas/.../5.json; it is last in the createSql.
        db.execSQL("ALTER TABLE `program_exercises` ADD COLUMN `supersetGroup` TEXT")
    }
}

/** v5 → v6: quick meals (meal_log_items) and your own foods (user_foods). Additive; no existing row changes. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Copied from app/schemas/.../6.json.
        listOf(
            "CREATE TABLE IF NOT EXISTS `meal_log_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `logId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `foodKey` TEXT NOT NULL, `foodName` TEXT NOT NULL, `amount` REAL NOT NULL, `unitKey` TEXT NOT NULL, `gramsPerUnit` REAL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `fiberG` REAL NOT NULL, `kcal` REAL NOT NULL, FOREIGN KEY(`logId`) REFERENCES `meal_logs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_meal_log_items_logId` ON `meal_log_items` (`logId`)",
            "CREATE INDEX IF NOT EXISTS `index_meal_log_items_foodKey` ON `meal_log_items` (`foodKey`)",
            "CREATE TABLE IF NOT EXISTS `user_foods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `fiberG` REAL NOT NULL, `kcal` REAL, `pieceGrams` REAL, `servingGrams` REAL, `source` TEXT NOT NULL, `barcode` TEXT, `createdAtMillis` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_user_foods_barcode` ON `user_foods` (`barcode`)",
        ).forEach(db::execSQL)
    }
}
