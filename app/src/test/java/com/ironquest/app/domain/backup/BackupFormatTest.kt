package com.ironquest.app.domain.backup

import com.ironquest.app.data.backup.BackupJson
import com.ironquest.app.domain.nutrition.FoodSettings
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {

    private fun sample(version: Int = BackupFormat.CURRENT_VERSION) = BackupData(
        version = version,
        exportedAtMillis = 1_700_000_000_000L,
        appSchemaVersion = 5,
        tables = BackupTable.entries.associate { it.sqlName to emptyList<BackupRow>() } + mapOf(
            "workouts" to listOf(mapOf("id" to 1L, "templateId" to "UPPER_1", "completedAtMillis" to null, "xpEarned" to 160L)),
            "meals" to listOf(mapOf("id" to 2L, "name" to "Oats, \"whey\"\nbowl", "proteinG" to 35.5, "kcal" to null)),
        ),
        builtInProgramHidden = true,
    )

    private fun failure(block: () -> Unit): BackupError = try {
        block()
        fail("expected a BackupException")
        error("unreachable")
    } catch (failure: BackupException) {
        failure.error
    }

    @Test
    fun `a backup survives encoding and decoding`() {
        val back = BackupJson.decode(BackupJson.encode(sample()))
        assertEquals(1L, back.rows(BackupTable.WORKOUTS).single()["id"])
        assertNull(back.rows(BackupTable.WORKOUTS).single()["completedAtMillis"])
        val meal = back.rows(BackupTable.MEALS).single()
        assertEquals("Oats, \"whey\"\nbowl", meal["name"])
        assertEquals(35.5, meal["proteinG"] as Double, 0.0)
        assertEquals(true, back.builtInProgramHidden)
        assertEquals(1_700_000_000_000L, back.exportedAtMillis)
    }

    @Test
    fun `files that are not backups are refused with a typed error`() {
        assertEquals(BackupError.NotABackup, failure { BackupJson.decode("hello") })
        assertEquals(BackupError.NotABackup, failure { BackupJson.decode("""{"format":"something-else","version":1,"tables":{}}""") })
        assertEquals(BackupError.NoVersion, failure { BackupJson.decode("""{"format":"ironquest-backup","tables":{}}""") })
        assertEquals(BackupError.NoData, failure { BackupJson.decode("""{"format":"ironquest-backup","version":1}""") })
        assertEquals(
            BackupError.Damaged("table meals, row 1"),
            failure { BackupJson.decode("""{"format":"ironquest-backup","version":1,"tables":{"meals":[5]}}""") },
        )
        assertEquals(
            BackupError.Damaged("table meals"),
            failure { BackupJson.decode("""{"format":"ironquest-backup","version":1,"tables":{"meals":5}}""") },
        )
    }

    @Test
    fun `a backup from a newer app is refused, not half-read`() {
        assertEquals(BackupError.MadeByNewerVersion, failure { BackupMigrations.prepare(sample(version = BackupFormat.CURRENT_VERSION + 1)) })
    }

    @Test
    fun `a version below one is not valid`() {
        assertEquals(BackupError.NotValid, failure { BackupMigrations.prepare(sample(version = 0)) })
    }

    @Test
    fun `a missing table is reported instead of silently emptying it`() {
        val data = sample().let { it.copy(tables = it.tables - "meals") }
        assertEquals(BackupError.Incomplete(listOf("meals")), failure { BackupMigrations.prepare(data) })
    }

    @Test
    fun `unknown tables and columns are tolerated`() {
        val data = sample().let { it.copy(tables = it.tables + ("future_table" to listOf(mapOf("x" to 1L)))) }
        assertEquals(1, BackupMigrations.prepare(data).rows(BackupTable.WORKOUTS).size)
    }

    @Test
    fun `the summary counts what the file holds`() {
        val summary = BackupMigrations.summarize(BackupMigrations.prepare(sample()))
        assertEquals(1, summary.workouts)
        assertEquals(1, summary.meals)
        assertEquals(0, summary.programs)
    }

    @Test
    fun `a version 1 file is upgraded with the new tables empty`() {
        val v1Tables = BackupTable.entries.filter { it.since == 1 }.associate { it.sqlName to emptyList<BackupRow>() }
        val old = BackupData(version = 1, exportedAtMillis = 0L, appSchemaVersion = 5, tables = v1Tables)
        val prepared = BackupMigrations.prepare(old)
        assertEquals(BackupFormat.CURRENT_VERSION, prepared.version)
        assertEquals(2, prepared.version)
        assertEquals(emptyList<BackupRow>(), prepared.rows(BackupTable.USER_FOODS))
        assertEquals(emptyList<BackupRow>(), prepared.rows(BackupTable.MEAL_LOG_ITEMS))
        assertNull(prepared.food)
    }

    @Test
    fun `food settings survive encoding and decoding`() {
        val food = FoodSettings(FoodTrackingMode.ON_THE_FLY, MacroTargets(proteinG = 140.0, waterMl = 2500))
        val back = BackupJson.decode(BackupJson.encode(sample().copy(food = food)))
        assertEquals(food, back.food)
        assertEquals(true, back.builtInProgramHidden)
    }

    @Test
    fun `an unknown food mode decodes to the default and no settings means no food`() {
        val text = BackupJson.encode(sample().copy(food = FoodSettings(FoodTrackingMode.PLAN)))
            .replace("PLAN", "SOMETHING_NEW")
        assertEquals(FoodTrackingMode.BOTH, BackupJson.decode(text).food?.mode)
        assertNull(BackupJson.decode(BackupJson.encode(sample().copy(builtInProgramHidden = null))).food)
    }

    @Test
    fun `every table of the database is in the backup`() {
        // Keep in step with app/schemas: a new entity must be added to BackupTable (and a version step if needed).
        val expected = setOf(
            "exercises", "workouts", "sets", "player_profile", "meals", "meal_tags", "meal_plans", "plan_slots",
            "meal_logs", "nutrition_days", "user_foods", "meal_log_items", "supplements", "supplement_intakes", "programs", "program_days", "program_exercises",
        )
        assertEquals(expected, BackupTable.entries.map { it.sqlName }.toSet())
    }
}
