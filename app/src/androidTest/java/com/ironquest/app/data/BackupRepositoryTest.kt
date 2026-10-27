package com.ironquest.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironquest.app.data.backup.OfflineBackupRepository
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.SeedCallback
import com.ironquest.app.data.repository.OfflineNutritionRepository
import com.ironquest.app.domain.backup.BackupError
import com.ironquest.app.domain.backup.BackupInspection
import com.ironquest.app.domain.backup.RestoreResult
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.fakes.AndroidTestSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private val today = LocalDate.of(2026, 10, 12)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private lateinit var db: IronQuestDatabase
    private lateinit var nutrition: OfflineNutritionRepository
    private lateinit var backup: OfflineBackupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), IronQuestDatabase::class.java)
            .addCallback(SeedCallback(ApplicationProvider.getApplicationContext<android.content.Context>()))
            .build()
        nutrition = OfflineNutritionRepository(db, clock, AndroidTestSettings())
        backup = OfflineBackupRepository(db, clock, AndroidTestSettings(), rearmer = { })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun mealNames() = nutrition.observeMeals().first().map { it.name }.sorted()

    @Test
    fun restore_brings_back_exactly_what_was_backed_up() = runTest {
        nutrition.saveMeal(MealDraft(null, "Backup test meal", Macros(10.0, 20.0, 5.0, 2.0, null), listOf("x"), "steps", null))
        db.playerProfileDao().addXp(250, 0, 250, 0)
        val before = mealNames()
        val text = backup.createBackup()

        // Change things after the backup.
        nutrition.observeMeals().first().forEach { nutrition.deleteMeal(it.id) }
        db.playerProfileDao().addXp(999, 0, 0, 0)

        val result = backup.restore(text)
        assertTrue(result.toString(), result is RestoreResult.Restored)
        assertEquals(before, mealNames())
        assertEquals(250L, checkNotNull(db.playerProfileDao().get()).totalXp)
    }

    @Test
    fun a_quick_meal_keeps_its_items_through_backup_and_restore() = runTest {
        val item = MealLogItem("custom:oats", "Oats", 80.0, FoodUnit.GRAM, Macros(10.0, 50.0, 6.0, 8.0, null))
        nutrition.saveQuickMeal(today, null, "Breakfast", listOf(item))
        val text = backup.createBackup()

        nutrition.observeDay(today).first().logs.forEach { nutrition.removeLog(it.id) }
        assertEquals(0, db.query("SELECT COUNT(*) FROM meal_log_items", null).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) })

        val result = backup.restore(text)
        assertTrue(result.toString(), result is RestoreResult.Restored)
        val logs = nutrition.observeDay(today).first().logs
        assertEquals(listOf(item.name), logs.single().items.map { it.name })
    }

    @Test
    fun a_broken_file_changes_nothing() = runTest {
        val before = mealNames()
        val xp = checkNotNull(db.playerProfileDao().get()).totalXp
        assertEquals(RestoreResult.Failed(BackupError.NotABackup), backup.restore("not a backup"))
        // Valid shape, but a set that points at a workout that isn't in the file.
        val inconsistent = """{"format":"ironquest-backup","version":1,"tables":{
            "player_profile":[],"exercises":[],"programs":[],"program_days":[],"program_exercises":[],
            "meals":[],"meal_tags":[],"meal_plans":[],"plan_slots":[],"supplements":[],"supplement_intakes":[],
            "workouts":[],"sets":[{"id":1,"workoutId":99,"exerciseId":"bench_press","setIndex":0,"reps":5,"weightKg":50,
            "targetRepMin":5,"targetRepMax":8,"xpAwarded":10,"loggedAtMillis":1}],"nutrition_days":[],"meal_logs":[]}}"""
        assertEquals(RestoreResult.Failed(BackupError.Inconsistent("sets")), backup.restore(inconsistent))
        assertEquals(before, mealNames())
        assertEquals(xp, checkNotNull(db.playerProfileDao().get()).totalXp)
    }

    @Test
    fun inspect_reads_without_writing() = runTest {
        val text = backup.createBackup()
        assertTrue(backup.inspect(text) is BackupInspection.Valid)
        assertEquals(BackupInspection.Invalid(BackupError.NotABackup), backup.inspect("{}"))
    }
}
