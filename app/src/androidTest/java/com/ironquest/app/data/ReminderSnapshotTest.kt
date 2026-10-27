package com.ironquest.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.SeedCallback
import com.ironquest.app.data.repository.OfflineNutritionRepository
import com.ironquest.app.domain.nutrition.DayKey
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.SlotDraft
import com.ironquest.app.domain.nutrition.SupplementDraft
import com.ironquest.app.fakes.AndroidTestSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReminderSnapshotTest {

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

    private suspend fun oatsId(): Long = repo.observeMeals().first().first { it.name.startsWith("Oats") }.id

    /** Inactive plan with one 07:30 Oats slot reminding 15 min before; returns (planId, slotId). */
    private suspend fun plan(name: String): Pair<Long, Long> {
        val id = repo.savePlan(PlanDraft(null, name, MacroTargets(), listOf(SlotDraft(null, "Breakfast", oatsId(), 450, 15)))).planId
        return id to checkNotNull(repo.observePlan(id).first()).slots.single().id
    }

    /** The seeded Creatine. */
    private suspend fun creatineId(): Long = repo.observeDay(today).first().supplements.single { it.name == "Creatine" }.id

    @Test
    fun onlyTheActivePlansSlotsAreIncluded() = runTest {
        val (cut, cutSlot) = plan("Cut")
        plan("Bulk")
        assertTrue(repo.reminderSnapshot(today, today).slots.isEmpty())

        repo.setActivePlan(cut)
        val slots = repo.reminderSnapshot(today, today).slots
        assertEquals(listOf(cutSlot), slots.map { it.id })
        assertEquals(450, slots.single().timeOfDayMin)
        assertEquals(15, slots.single().reminderOffsetMin)
    }

    @Test
    fun loggedSlotsAreLimitedToTheRangeAndExcludeOffPlan() = runTest {
        val (cut, slot) = plan("Cut")
        repo.setActivePlan(cut)
        repo.logSlot(today.minusDays(1), slot, 1.0)
        repo.logSlot(today, slot, 1.0)
        repo.logOffPlan(today, oatsId(), 1.0)

        assertEquals(setOf(DayKey(today.toEpochDay(), slot)), repo.reminderSnapshot(today, today.plusDays(2)).loggedSlots)
        assertEquals(2, repo.reminderSnapshot(today.minusDays(1), today).loggedSlots.size)
    }

    @Test
    fun supplementReminderTimeRoundTripsAndIsValidated() = runTest {
        val id = creatineId()
        repo.saveSupplement(SupplementDraft(id, "Creatine", "5 g", reminderTimeMin = 480))
        assertEquals(480, repo.observeDay(today).first().supplements.single().reminderTimeMin)
        assertEquals(480, repo.reminderSnapshot(today, today).supplements.single { it.id == id }.reminderTimeMin)

        repo.saveSupplement(SupplementDraft(id, "Creatine", "5 g", reminderTimeMin = null))
        assertNull(repo.observeDay(today).first().supplements.single().reminderTimeMin)

        assertTrue(runCatching { repo.saveSupplement(SupplementDraft(id, "Creatine", "5 g", reminderTimeMin = 1440)) }.isFailure)
    }

    @Test
    fun takenSupplementsAreListedAndArchivedOnesSkipped() = runTest {
        val id = creatineId()
        repo.setSupplementTaken(today, id, true)
        assertEquals(setOf(DayKey(today.toEpochDay(), id)), repo.reminderSnapshot(today, today).takenSupplements)

        repo.deleteSupplement(id) // has an intake → archived, not deleted
        assertTrue(repo.reminderSnapshot(today, today).supplements.none { it.id == id })
    }
}
