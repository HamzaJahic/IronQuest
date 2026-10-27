package com.ironquest.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.dao.NutritionDao
import com.ironquest.app.data.local.entity.MealEntity
import com.ironquest.app.data.local.entity.MealLogEntity
import com.ironquest.app.data.local.entity.MealPlanEntity
import com.ironquest.app.data.local.entity.PlanSlotEntity
import com.ironquest.app.data.local.entity.SupplementEntity
import com.ironquest.app.data.local.entity.SupplementIntakeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Constraints and FK actions on `meal_logs`, checked at the DAO level (no repository guards). */
@RunWith(AndroidJUnit4::class)
class NutritionDaoTest {

    private lateinit var db: IronQuestDatabase
    private lateinit var dao: NutritionDao

    @Before
    fun setUp() {
        // No SeedCallback: every row here is inserted by the test.
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), IronQuestDatabase::class.java).build()
        dao = db.nutritionDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun meal(name: String = "Oats"): Long = dao.insertMeal(
        MealEntity(
            name = name, proteinG = 35.0, carbsG = 75.0, fatG = 9.0, fiberG = 9.0, kcal = null,
            recipeText = null, recipeUrl = null, archived = false, createdAtMillis = 0,
        ),
    )

    private suspend fun planWithSlot(mealId: Long): Pair<Long, Long> {
        val planId = dao.insertPlan(
            MealPlanEntity(
                name = "Cut", isActive = true, targetProteinG = null, targetCarbsG = null, targetFatG = null,
                targetFiberG = null, targetKcal = null, waterTargetMl = null, position = 0,
            ),
        )
        val slotId = dao.insertSlot(PlanSlotEntity(planId = planId, label = "Breakfast", mealId = mealId, timeOfDayMin = null, reminderOffsetMin = null, position = 0))
        return planId to slotId
    }

    private fun log(day: Long, slotId: Long?, mealId: Long?) = MealLogEntity(
        dateEpochDay = day, slotId = slotId, mealId = mealId, mealName = "Oats", portion = 1.0,
        proteinG = 35.0, carbsG = 75.0, fatG = 9.0, fiberG = 9.0, kcal = 521.0, xpAwarded = 5, loggedAtMillis = 0,
    )

    @Test
    fun theSameSlotCannotBeLoggedTwiceOnOneDay() = runTest {
        val mealId = meal()
        val (_, slotId) = planWithSlot(mealId)
        dao.insertLog(log(DAY, slotId, mealId))

        val duplicate = runCatching { dao.insertLog(log(DAY, slotId, mealId)) }

        assertTrue(duplicate.exceptionOrNull() is SQLiteConstraintException)
        // A different day is fine.
        dao.insertLog(log(DAY + 1, slotId, mealId))
    }

    @Test
    fun offPlanLogsWithNullSlotsCanRepeat() = runTest {
        val mealId = meal()
        dao.insertLog(log(DAY, null, mealId))
        dao.insertLog(log(DAY, null, mealId))
        assertEquals(2, dao.observeLogs(DAY).first().size)
    }

    @Test
    fun deletingAPlanSetsLogSlotIdsToNull() = runTest {
        val mealId = meal()
        val (planId, slotId) = planWithSlot(mealId)
        val logId = dao.insertLog(log(DAY, slotId, mealId))

        dao.deletePlan(planId) // CASCADE removes the slot, which SET NULLs the log

        val kept = checkNotNull(dao.getLog(logId))
        assertNull(kept.slotId)
        assertEquals(mealId, kept.mealId)
    }

    @Test
    fun deletingAMealSetsLogMealIdsToNull() = runTest {
        val mealId = meal()
        val logId = dao.insertLog(log(DAY, null, mealId))

        dao.deleteMeal(mealId)

        val kept = checkNotNull(dao.getLog(logId))
        assertNull(kept.mealId)
        assertEquals("Oats", kept.mealName) // the snapshot survives
    }

    @Test
    fun intakeReadsStayInsideTheRequestedWindow() = runTest {
        val id = dao.insertSupplement(SupplementEntity(name = "Creatine", dose = "5 g", reminderTimeMin = null, archived = false, position = 0, createdAtEpochDay = null))
        listOf(DAY - 1, DAY, DAY + 5, DAY + 6).forEach { dao.insertIntake(SupplementIntakeEntity(id, it, 2, 0)) }

        val days = dao.observeIntakesBetween(DAY, DAY + 5).first().map { it.dateEpochDay }

        assertEquals(listOf(DAY, DAY + 5), days.sorted())
    }

    private companion object {
        const val DAY = 20_000L
    }
}
