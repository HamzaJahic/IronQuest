package com.ironquest.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.SeedCallback
import com.ironquest.app.data.local.entity.SupplementIntakeEntity
import com.ironquest.app.data.repository.OfflineNutritionRepository
import com.ironquest.app.data.repository.OfflineWorkoutRepository
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.model.LogSetRequest
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.SlotDraft
import com.ironquest.app.domain.nutrition.SupplementDraft
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.fakes.AndroidTestSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class NutritionRepositoryTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private lateinit var db: IronQuestDatabase
    private lateinit var repo: OfflineNutritionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), IronQuestDatabase::class.java)
            .addCallback(SeedCallback(ApplicationProvider.getApplicationContext<android.content.Context>()))
            .build()
        repo = OfflineNutritionRepository(db, clock, AndroidTestSettings())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun xp(): Long = checkNotNull(db.playerProfileDao().get()).totalXp

    /** Starter "Oats with whey & banana": 35 g protein. */
    private suspend fun oatsId(): Long = repo.observeMeals().first().first { it.name.startsWith("Oats") }.id

    /** Creates an active plan with one Oats slot; returns (planId, slotId). */
    private suspend fun activeOatsPlan(proteinTarget: Double? = null): Pair<Long, Long> {
        val planId = repo.savePlan(
            PlanDraft(null, "Cut", MacroTargets(proteinG = proteinTarget), listOf(SlotDraft(null, "Breakfast", oatsId(), 450, null))),
        ).planId
        repo.setActivePlan(planId)
        return planId to checkNotNull(repo.observePlan(planId).first()).slots.single().id
    }

    @Test
    fun seedsStarterMealsAndCreatine() = runTest {
        assertEquals(10, repo.observeMeals().first().size)
        assertEquals(listOf("Creatine"), repo.observeDay(today).first().supplements.map { it.name })
    }

    @Test
    fun loggingTheSameSlotTwicePaysOnce() = runTest {
        val (_, slot) = activeOatsPlan()
        val first = repo.logSlot(today, slot, 1.0)
        val second = repo.logSlot(today, slot, 1.0)
        assertEquals(5, first.xpDelta)
        assertTrue(second.alreadyDone)
        assertEquals(5L, xp())
        assertEquals(1, repo.observeDay(today).first().logs.size)
    }

    @Test
    fun proteinBonusIsPaidOnceAndRevokedOnUnlog() = runTest {
        val (_, slot) = activeOatsPlan(proteinTarget = 30.0)
        assertEquals(30, repo.logSlot(today, slot, 1.0).xpDelta)
        val logId = repo.observeDay(today).first().logs.single().id
        assertEquals(0, repo.updatePortion(logId, 1.5).xpDelta)
        assertEquals(30L, xp())
        assertEquals(-30, repo.removeLog(logId).xpDelta)
        assertEquals(0L, xp())
    }

    @Test
    fun loweringTheActivePlanTargetPaysTheBonusImmediately() = runTest {
        val (planId, slot) = activeOatsPlan(proteinTarget = 100.0)
        repo.logSlot(today, slot, 1.0)
        assertEquals(5L, xp())
        val plan = checkNotNull(repo.observePlan(planId).first())
        val result = repo.savePlan(
            PlanDraft(planId, plan.name, MacroTargets(proteinG = 30.0), plan.slots.map { SlotDraft(it.id, it.label, it.mealId, it.timeOfDayMin, null) }),
        )
        assertEquals(30L, xp())
        // C15: the re-evaluation award is returned, not swallowed.
        assertEquals(planId, result.planId)
        assertEquals(25, result.award.xpDelta)
    }

    @Test
    fun waterBonusFollowsTheTarget() = runTest {
        val planId = repo.savePlan(PlanDraft(null, "Hydrate", MacroTargets(waterMl = 500), emptyList())).planId
        repo.setActivePlan(planId)
        assertEquals(0, repo.addWater(today, 250).xpDelta)
        assertEquals(10, repo.addWater(today, 250).xpDelta)
        assertEquals(-10, repo.addWater(today, -250).xpDelta)
        assertEquals(250, repo.observeDay(today).first().waterMl)
    }

    @Test
    fun onlyOnePlanIsActiveAtATime() = runTest {
        val planA = repo.savePlan(PlanDraft(null, "A", MacroTargets(), emptyList())).planId
        val planB = repo.savePlan(PlanDraft(null, "B", MacroTargets(), emptyList())).planId
        repo.setActivePlan(planA)
        repo.setActivePlan(planB)
        assertEquals(listOf(planB), repo.observePlans().first().filter { it.isActive }.map { it.id })
        repo.setActivePlan(null)
        assertTrue(repo.observePlans().first().none { it.isActive })
    }

    @Test
    fun deletingAMealUsedByAPlanArchivesIt() = runTest {
        activeOatsPlan()
        val id = oatsId()
        assertEquals(DeleteOutcome.ARCHIVED, repo.deleteMeal(id))
        assertTrue(repo.observeMeals().first().none { it.id == id })
        assertNotNull(repo.observeMeal(id).first())
    }

    @Test
    fun deletingAnUnusedLoggedMealKeepsTheLogSnapshot() = runTest {
        val id = oatsId()
        repo.logOffPlan(today, id, 1.0)
        assertEquals(DeleteOutcome.DELETED, repo.deleteMeal(id))
        val log = repo.observeDay(today).first().logs.single()
        assertNull(log.mealId)
        assertTrue(log.mealName.startsWith("Oats"))
        assertEquals(35.0, log.macros1x.proteinG, 0.0)
    }

    @Test
    fun editingAPlanKeepsSlotIdsSoTodaysLogsStayLinked() = runTest {
        val (planId, slot) = activeOatsPlan()
        repo.logSlot(today, slot, 1.0)
        val plan = checkNotNull(repo.observePlan(planId).first())
        repo.savePlan(
            PlanDraft(planId, "Cut v2", plan.targets, plan.slots.map { SlotDraft(it.id, "Brekkie", it.mealId, it.timeOfDayMin, null) }),
        )
        assertEquals(slot, repo.observeDay(today).first().logs.single().slotId)
        assertEquals("Brekkie", checkNotNull(repo.observePlan(planId).first()).slots.single().label)
    }

    @Test
    fun futureDatesAreRejected() = runTest {
        val result = runCatching { repo.addWater(today.plusDays(1), 250) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun supplementCheckIsIdempotentAndCountsAStreak() = runTest {
        val creatine = repo.observeDay(today).first().supplements.single().id
        repo.setSupplementTaken(today.minusDays(1), creatine, true)
        assertEquals(2, repo.setSupplementTaken(today, creatine, true).xpDelta)
        assertTrue(repo.setSupplementTaken(today, creatine, true).alreadyDone)
        assertEquals(2, repo.observeDay(today).first().supplements.single().streak)
        assertEquals(DeleteOutcome.ARCHIVED, repo.deleteSupplement(creatine))
        assertEquals(4L, xp())
    }

    @Test
    fun deletingTheActivePlanRevokesTodaysBonus() = runTest {
        val (planId, slot) = activeOatsPlan(proteinTarget = 30.0)
        assertEquals(30, repo.logSlot(today, slot, 1.0).xpDelta)
        val award = repo.deletePlan(planId)
        assertEquals(-25, award.xpDelta)
        assertEquals(5L, xp())
        assertNull(repo.observeDay(today).first().logs.single().slotId)
    }

    @Test
    fun takingADeletedSupplementIsANoOp() = runTest {
        val id = repo.saveSupplement(SupplementDraft(null, "Zinc", "15 mg"))
        assertEquals(DeleteOutcome.DELETED, repo.deleteSupplement(id))
        assertTrue(repo.setSupplementTaken(today, id, true).alreadyDone)
        assertEquals(0L, xp())
    }

    @Test
    fun activatingAMissingPlanFailsAndKeepsTheActivePlan() = runTest {
        val planA = repo.savePlan(PlanDraft(null, "A", MacroTargets(), emptyList())).planId
        repo.setActivePlan(planA)
        val result = runCatching { repo.setActivePlan(9_999) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf(planA), repo.observePlans().first().filter { it.isActive }.map { it.id })
    }

    @Test
    fun newMealsGetIdsAfterTheSeed() = runTest {
        assertEquals(11L, repo.saveMeal(MealDraft(null, "New", Macros(), emptyList(), null, null)))
    }

    @Test
    fun aPastDayWithoutARowSnapshotsTheActivePlan() = runTest {
        val planId = repo.savePlan(PlanDraft(null, "Hydrate", MacroTargets(waterMl = 250), emptyList())).planId
        repo.setActivePlan(planId)
        val past = today.minusDays(3)
        assertEquals(10, repo.addWater(past, 250).xpDelta)
        assertEquals(250, repo.observeDay(past).first().targets?.waterMl)
    }

    // ----- phase 2: observeHistory -----

    @Test
    fun historySumsPortionedLogsPerDayWithinTheRange() = runTest {
        val oats = oatsId()
        val yesterday = today.minusDays(1)
        repo.logOffPlan(today, oats, 1.0)
        repo.logOffPlan(yesterday, oats, 1.5)
        repo.logOffPlan(yesterday, oats, 1.0)
        repo.logOffPlan(today.minusDays(5), oats, 1.0) // outside the range

        val history = repo.observeHistory(yesterday, today).first()

        assertEquals(setOf(yesterday, today), history.totals.keys)
        assertEquals(35.0, history.totals.getValue(today).eaten.proteinG, 1e-9)
        assertEquals(87.5, history.totals.getValue(yesterday).eaten.proteinG, 1e-9)
        assertEquals(2, history.totals.getValue(yesterday).logCount)
        val kcal1x = checkNotNull(repo.observeMeal(oats).first()).macros.effectiveKcal
        assertEquals(kcal1x * 2.5, checkNotNull(history.totals.getValue(yesterday).eaten.kcal), 1e-6)
    }

    @Test
    fun historyKeepsPastSnapshotsAndReportsLiveTargets() = runTest {
        val planId = repo.savePlan(PlanDraft(null, "Cut", MacroTargets(proteinG = 100.0), emptyList())).planId
        repo.setActivePlan(planId)
        val past = today.minusDays(2)
        repo.addWater(past, 250) // creates the past row with a snapshot of protein 100
        repo.savePlan(PlanDraft(planId, "Cut", MacroTargets(proteinG = 150.0), emptyList()))

        val history = repo.observeHistory(past, today).first()

        assertEquals(100.0, checkNotNull(history.rows.getValue(past).targets.proteinG), 1e-9)
        assertEquals(250, history.rows.getValue(past).waterMl)
        assertEquals(150.0, checkNotNull(checkNotNull(history.liveTargets).proteinG), 1e-9)
        assertNull(history.rows[today.minusDays(1)]) // untouched day: no row
    }

    @Test
    fun historyIncludesEarlierIntakesButNotLaterOnes() = runTest {
        val creatine = repo.observeDay(today).first().supplements.single().id
        repo.setSupplementTaken(today.minusDays(6), creatine, true) // before the range: kept for streaks
        repo.setSupplementTaken(today.minusDays(3), creatine, true) // in range
        repo.setSupplementTaken(today, creatine, true) // after the range: excluded

        val history = repo.observeHistory(today.minusDays(4), today.minusDays(1)).first()

        assertEquals(listOf("Creatine"), history.supplements.map { it.name })
        assertEquals(
            setOf(today.minusDays(6).toEpochDay(), today.minusDays(3).toEpochDay()),
            history.intakeEpochDays.getValue(creatine),
        )
        assertTrue(history.totals.isEmpty())
    }

    // ----- follow-ups batch 1 -----

    @Test
    fun savingAnInactivePlanReturnsNoAward() = runTest {
        val result = repo.savePlan(PlanDraft(null, "Idle", MacroTargets(proteinG = 1.0), emptyList()))
        assertEquals(NutritionAward.NONE, result.award)
    }

    @Test
    fun updatePortionOnAMissingLogIsAlreadyDone() = runTest {
        val award = repo.updatePortion(9_999, 2.0)
        assertTrue(award.alreadyDone)
        assertEquals(0, award.xpDelta)
    }

    @Test
    fun waterIsClampedAtTheDailyCap() = runTest {
        repo.addWater(today, 9_900)
        repo.addWater(today, 250)
        assertEquals(NutritionRules.MAX_WATER_DAY_ML, repo.observeDay(today).first().waterMl)
        // A first write on a new day is clamped too.
        repo.addWater(today.minusDays(1), 50_000)
        assertEquals(NutritionRules.MAX_WATER_DAY_ML, repo.observeDay(today.minusDays(1)).first().waterMl)
    }

    @Test
    fun archivedMealsAreListedSeparatelyAndCanBeRestored() = runTest {
        activeOatsPlan()
        val id = oatsId()
        repo.deleteMeal(id)
        assertTrue(checkNotNull(repo.observeMeal(id).first()).archived)
        assertEquals(listOf(id), repo.observeArchivedMeals().first().map { it.id })

        repo.restoreMeal(id)
        assertFalse(checkNotNull(repo.observeMeal(id).first()).archived)
        assertTrue(repo.observeArchivedMeals().first().isEmpty())
        assertTrue(repo.observeMeals().first().any { it.id == id })
    }

    @Test
    fun newSupplementsRecordTheDayTheyWereAdded() = runTest {
        val id = repo.saveSupplement(SupplementDraft(null, "Zinc", "15 mg"))
        assertEquals(today.toEpochDay(), checkNotNull(db.nutritionDao().getSupplement(id)).createdAtEpochDay)
        val ref = repo.observeHistory(today.minusDays(6), today).first().supplements.single { it.id == id }
        assertEquals(today.toEpochDay(), ref.createdAtEpochDay)
    }

    @Test
    fun streaksAreBoundedToTheReadWindow() = runTest {
        val creatine = repo.observeDay(today).first().supplements.single { it.name == "Creatine" }.id
        val dao = db.nutritionDao()
        (0L..NutritionRules.STREAK_WINDOW_DAYS.toLong()).forEach { back ->
            dao.insertIntake(SupplementIntakeEntity(creatine, today.toEpochDay() - back, 2, 0))
        }
        assertEquals(
            NutritionRules.STREAK_WINDOW_DAYS,
            repo.observeDay(today).first().supplements.single { it.id == creatine }.streak,
        )
    }

    @Test
    fun aCharacterLevelIsReportedOnlyTheFirstTimeItIsReached() = runTest {
        db.playerProfileDao().addCharacterXp(95) // 5 XP short of level 2; highest reached is still 1
        val oats = oatsId()
        val first = repo.logOffPlan(today, oats, 1.0)
        assertEquals(2, first.levelUp?.newLevel)
        assertEquals(2, checkNotNull(db.playerProfileDao().get()).highestLevelReached)

        repo.removeLog(repo.observeDay(today).first().logs.single().id) // back to 95 XP, level 1
        val again = repo.logOffPlan(today, oats, 1.0)
        assertNull(again.levelUp)
        assertEquals(2, checkNotNull(db.playerProfileDao().get()).highestLevelReached)
    }

    @Test
    fun aWorkoutSetReCrossingALevelLostToUnloggingIsNotReported() = runTest {
        val workouts = OfflineWorkoutRepository(
            db,
            clock,
            com.ironquest.app.data.repository.OfflineProgramRepository(db, clock, com.ironquest.app.fakes.AndroidTestSettings()),
        )
        db.playerProfileDao().addCharacterXp(95)
        val oats = oatsId()
        repo.logOffPlan(today, oats, 1.0) // reaches level 2
        repo.removeLog(repo.observeDay(today).first().logs.single().id) // drops back to level 1

        val template = ProgramCatalog.UPPER_1
        val bench = template.exercises.first()
        val result = workouts.logSet(LogSetRequest(template.id, null, bench, LoggedSet(1, 8, 60.0), ghost = null))

        assertEquals(105L, xp())
        assertNull(result.levelUp) // 10 set XP is no stat level either
    }

    private val oats = MealLogItem("b:oats", "Oats", 50.0, FoodUnit.GRAM, Macros(proteinG = 6.0, carbsG = 30.0, fatG = 3.0, kcal = 180.0))
    private val milk = MealLogItem("b:milk", "Milk", 200.0, FoodUnit.MILLILITRE, Macros(proteinG = 7.0, carbsG = 10.0, fatG = 4.0, kcal = 100.0))

    @Test
    fun saveQuickMeal_createsOneLogWithItemsAndPaysMealXp() = runTest {
        val before = xp()

        val award = repo.saveQuickMeal(today, null, "Breakfast", listOf(oats, milk))

        assertEquals(NutritionRules.MEAL_XP, award.xpDelta)
        assertEquals(before + NutritionRules.MEAL_XP, xp())
        val log = repo.observeDay(today).first().logs.single()
        assertEquals(2, log.items.size)
        assertEquals(13.0, log.macros1x.proteinG, 0.001)
        assertEquals(280.0, log.macros1x.effectiveKcal, 0.001)
    }

    @Test
    fun saveQuickMeal_rewriteReplacesItemsAndPaysNothing() = runTest {
        repo.saveQuickMeal(today, null, "Breakfast", listOf(oats, milk))
        val logId = repo.observeDay(today).first().logs.single().id
        val before = xp()

        val award = repo.saveQuickMeal(today, logId, "Breakfast", listOf(oats))

        assertEquals(0, award.xpDelta)
        assertEquals(before, xp())
        val log = repo.observeDay(today).first().logs.single()
        assertEquals(1, log.items.size)
        assertEquals(6.0, log.macros1x.proteinG, 0.001)
    }

    @Test
    fun saveQuickMeal_rewriteOfDeletedLogIsAlreadyDone() = runTest {
        assertTrue(repo.saveQuickMeal(today, 999, "Ghost", listOf(oats)).alreadyDone)
    }

    @Test
    fun removeLog_deletesItsItems() = runTest {
        repo.saveQuickMeal(today, null, "Breakfast", listOf(oats, milk))
        val logId = repo.observeDay(today).first().logs.single().id

        repo.removeLog(logId)

        assertTrue(db.foodDao().itemsFor(logId).isEmpty())
        assertNull(repo.getLog(logId))
    }
}
