package com.ironquest.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.MIGRATION_1_2
import com.ironquest.app.data.local.MIGRATION_2_3
import com.ironquest.app.data.local.MIGRATION_3_4
import com.ironquest.app.data.local.MIGRATION_4_5
import com.ironquest.app.data.local.MIGRATION_5_6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IronQuestDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsWorkoutDataAndSeedsNutritionOnce() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO player_profile (id, totalXp, constitutionXp, strengthXp, staminaXp, questsCompleted) VALUES (1, 420, 10, 20, 30, 3)")
            execSQL("INSERT INTO workouts (id, templateId, dateEpochDay, startedAtMillis, completedAtMillis, xpEarned) VALUES (1, 'UPPER_1', 20000, 1, 2, 140)")
            close()
        }

        // validateDroppedTables = true also checks every table/index matches the v2 schema.
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        fun count(sql: String): Int = db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(420, count("SELECT totalXp FROM player_profile WHERE id = 1"))
        assertEquals(1, count("SELECT COUNT(*) FROM workouts"))
        assertEquals(10, count("SELECT COUNT(*) FROM meals"))
        assertEquals(20, count("SELECT COUNT(*) FROM meal_tags"))
        assertEquals(1, count("SELECT COUNT(*) FROM supplements WHERE name = 'Creatine' AND dose = '5 g'"))
        assertEquals(0, count("SELECT COUNT(*) FROM meal_plans"))
    }

    @Test
    fun migrate2To3_backfillsSupplementStartAndHighestLevel() {
        helper.createDatabase(DB, 2).apply {
            // 420 XP: level 3 (100 + 150 = 250 reached, level 4 needs 450).
            execSQL("INSERT INTO player_profile (id, totalXp, constitutionXp, strengthXp, staminaXp, questsCompleted) VALUES (1, 420, 0, 0, 0, 0)")
            execSQL("INSERT INTO supplements (id, name, dose, reminderTimeMin, archived, position) VALUES (1, 'Creatine', '5 g', NULL, 0, 0)")
            execSQL("INSERT INTO supplements (id, name, dose, reminderTimeMin, archived, position) VALUES (2, 'Zinc', '15 mg', NULL, 0, 1)")
            execSQL("INSERT INTO supplement_intakes (supplementId, dateEpochDay, xpAwarded, loggedAtMillis) VALUES (1, 20003, 2, 0)")
            execSQL("INSERT INTO supplement_intakes (supplementId, dateEpochDay, xpAwarded, loggedAtMillis) VALUES (1, 20000, 2, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)

        fun count(sql: String): Int = db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(3, count("SELECT highestLevelReached FROM player_profile WHERE id = 1"))
        assertEquals(420, count("SELECT totalXp FROM player_profile WHERE id = 1"))
        // First intake day; never taken → NULL.
        assertEquals(20000, count("SELECT createdAtEpochDay FROM supplements WHERE id = 1"))
        assertEquals(1, count("SELECT COUNT(*) FROM supplements WHERE id = 2 AND createdAtEpochDay IS NULL"))
        assertEquals(2, count("SELECT COUNT(*) FROM supplement_intakes"))
    }

    @Test
    fun migrate1To3_chained() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO player_profile (id, totalXp, constitutionXp, strengthXp, staminaXp, questsCompleted) VALUES (1, 99, 0, 0, 0, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        fun count(sql: String): Int = db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(1, count("SELECT highestLevelReached FROM player_profile WHERE id = 1"))
        assertEquals(10, count("SELECT COUNT(*) FROM meals"))
        // The seeded Creatine has no intakes yet, so it has no start day.
        assertTrue(count("SELECT COUNT(*) FROM supplements WHERE name = 'Creatine' AND createdAtEpochDay IS NULL") == 1)
    }

    private companion object {
        const val DB = "migration-test.db"
    }

    @Test
    fun migrate3To4_addsProgramTablesAndKeepsData() {
        helper.createDatabase(DB, 3).apply {
            execSQL("INSERT INTO exercises (id, name, stat, trackingType, incrementKg) VALUES ('bench_press', 'Barbell Bench Press', 'STRENGTH', 'WEIGHT_REPS', 2.5)")
            execSQL("INSERT INTO workouts (id, templateId, dateEpochDay, startedAtMillis, completedAtMillis, xpEarned) VALUES (1, 'UPPER_1', 20000, 0, 1, 110)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_3_4)

        fun count(sql: String): Int = db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(1, count("SELECT COUNT(*) FROM workouts"))
        assertEquals(0, count("SELECT COUNT(*) FROM programs"))
        db.execSQL("INSERT INTO programs (id, name, isActive, createdAtMillis) VALUES (1, 'PPL', 1, 0)")
        db.execSQL("INSERT INTO program_days (id, programId, name, position) VALUES (1, 1, 'Push', 0)")
        db.execSQL("INSERT INTO program_exercises (dayId, exerciseId, targetSets, repMin, repMax, position) VALUES (1, 'bench_press', 4, 5, 8, 0)")
        assertEquals(1, count("SELECT COUNT(*) FROM program_exercises"))
    }

    @Test
    fun migrate4To5_addsSupersetGroupAndKeepsPrograms() {
        helper.createDatabase(DB, 4).apply {
            execSQL("INSERT INTO exercises (id, name, stat, trackingType, incrementKg) VALUES ('bench_press', 'Barbell Bench Press', 'STRENGTH', 'WEIGHT_REPS', 2.5)")
            execSQL("INSERT INTO exercises (id, name, stat, trackingType, incrementKg) VALUES ('barbell_row', 'Barbell Row', 'STRENGTH', 'WEIGHT_REPS', 2.5)")
            execSQL("INSERT INTO programs (id, name, isActive, createdAtMillis) VALUES (1, 'PPL', 1, 0)")
            execSQL("INSERT INTO program_days (id, programId, name, position) VALUES (1, 1, 'Push', 0)")
            execSQL("INSERT INTO program_exercises (dayId, exerciseId, targetSets, repMin, repMax, position) VALUES (1, 'bench_press', 4, 5, 8, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5)

        fun count(sql: String): Int = db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
        // Existing rows stay standalone.
        assertEquals(1, count("SELECT COUNT(*) FROM program_exercises WHERE supersetGroup IS NULL"))
        db.execSQL("INSERT INTO program_exercises (dayId, exerciseId, targetSets, repMin, repMax, position, supersetGroup) VALUES (1, 'barbell_row', 3, 6, 10, 1, 'a')")
        assertEquals(1, count("SELECT COUNT(*) FROM program_exercises WHERE supersetGroup = 'a'"))
    }

    @Test
    fun migrate5To6_addsFoodTablesAndKeepsLogs() {
        helper.createDatabase(DB, 5).apply {
            execSQL("INSERT INTO meal_logs (id, dateEpochDay, slotId, mealId, mealName, portion, proteinG, carbsG, fatG, fiberG, kcal, xpAwarded, loggedAtMillis) VALUES (1, 20000, NULL, NULL, 'Oats', 1.0, 10, 50, 5, 8, 300, 5, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_5_6)

        fun count(sql: String): Int = db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(1, count("SELECT COUNT(*) FROM meal_logs"))
        assertEquals(0, count("SELECT COUNT(*) FROM meal_log_items"))
        assertEquals(0, count("SELECT COUNT(*) FROM user_foods"))
    }
}
