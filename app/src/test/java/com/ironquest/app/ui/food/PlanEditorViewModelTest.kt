package com.ironquest.app.ui.food

import androidx.lifecycle.SavedStateHandle
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.ui.text.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanEditorViewModelTest {

    private val repo = FakeNutritionRepository()
    private val oats = Meal(1, "Oats", Macros(35.0), emptyList(), null, null)

    private fun slot(key: Long, label: String = "Breakfast", mealId: Long? = 1, time: Int? = null, offset: Int? = null) = SlotForm(key = key, id = null, label = label, mealId = mealId, mealName = "Oats", timeOfDayMin = time, reminderOffsetMin = offset)

    private fun runVmTest(planId: String, block: suspend TestScope.(PlanEditorViewModel, MutableList<PlanEditorEvent>) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val vm = PlanEditorViewModel(SavedStateHandle(mapOf(PlanEditorViewModel.ARG_PLAN_ID to planId)), repo)
                val events = mutableListOf<PlanEditorEvent>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
                block(vm, events)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `slots without a meal or label are flagged by key`() {
        val result = PlanEditorViewModel.validate(
            PlanForm(name = "Cut", slots = listOf(slot(1), slot(2, label = " "), slot(3, mealId = null))),
            null,
        ) as PlanValidation.Invalid
        assertEquals(setOf(2L, 3L), result.slotKeys)
        assertEquals(emptySet<PlanField>(), result.fields)
    }

    @Test
    fun `a reminder without a time is invalid`() {
        val result = PlanEditorViewModel.validate(PlanForm(name = "Cut", slots = listOf(slot(1, offset = 15))), null) as PlanValidation.Invalid
        assertEquals(setOf(1L), result.slotKeys)
    }

    @Test
    fun `targets parse with blanks meaning no target`() {
        val draft = (PlanEditorViewModel.validate(PlanForm(name = " Cut ", protein = "180", water = "2500"), null) as PlanValidation.Valid).draft
        assertEquals("Cut", draft.name)
        assertEquals(MacroTargets(proteinG = 180.0, waterMl = 2500), draft.targets)
    }

    @Test
    fun `bad targets and blank name are reported`() {
        val result = PlanEditorViewModel.validate(PlanForm(name = "", protein = "abc", water = "99999"), null) as PlanValidation.Invalid
        assertEquals(setOf(PlanField.NAME, PlanField.PROTEIN, PlanField.WATER), result.fields)
    }

    @Test
    fun `editing an existing plan keeps slot ids and order`() {
        repo.meals.value = listOf(oats)
        repo.plans.value = listOf(
            MealPlan(
                10,
                "Cut",
                true,
                MacroTargets(proteinG = 180.0),
                listOf(
                    PlanSlot(21, "Breakfast", 1, "Oats", oats.macros, 450, null),
                    PlanSlot(22, "Snack", 1, "Oats", oats.macros, null, null),
                ),
            ),
        )
        runVmTest("10") { vm, events ->
            val first = vm.uiState.value.form.slots.first()
            vm.onAction(PlanEditorAction.UpdateSlot(first.key) { it.copy(label = "Brekkie") })
            vm.onAction(PlanEditorAction.MoveSlot(first.key, +1))
            vm.onAction(PlanEditorAction.Save)
            val saved = repo.savedPlans.single()
            assertEquals(listOf(22L, 21L), saved.slots.map { it.id })
            assertEquals("Brekkie", saved.slots[1].label)
            assertEquals(10L, saved.id)
            assertTrue(events.last() is PlanEditorEvent.Saved)
            assertEquals(true, vm.uiState.value.isSaving)
        }
    }

    @Test
    fun `clearing the time also clears the reminder`() = runVmTest("new") { vm, _ ->
        vm.onAction(PlanEditorAction.AddSlot("Breakfast"))
        val key = vm.uiState.value.form.slots.single().key
        vm.onAction(PlanEditorAction.UpdateSlot(key) { it.copy(timeOfDayMin = 450, reminderOffsetMin = 15) })
        vm.onAction(PlanEditorAction.ClearSlotTime(key))
        val onlySlot = vm.uiState.value.form.slots.single()
        assertNull(onlySlot.timeOfDayMin)
        assertNull(onlySlot.reminderOffsetMin)
        assertEquals("Breakfast", onlySlot.label)
    }

    @Test
    fun `saving a new plan twice inserts it once`() = runVmTest("new") { vm, _ ->
        vm.onAction(PlanEditorAction.FormChange(PlanForm(name = "Cut")))
        vm.onAction(PlanEditorAction.Save)
        vm.onAction(PlanEditorAction.Save)
        assertEquals(1, repo.savedPlans.size)
        assertEquals(true, vm.uiState.value.isSaving)
    }

    @Test
    fun `a reminder offset can only be set once the slot has a time`() = runVmTest("new") { vm, _ ->
        vm.onAction(PlanEditorAction.AddSlot("Breakfast"))
        val key = vm.uiState.value.form.slots.single().key
        vm.onAction(PlanEditorAction.SetSlotReminder(key, 15))
        assertNull(vm.uiState.value.form.slots.single().reminderOffsetMin)

        vm.onAction(PlanEditorAction.UpdateSlot(key) { it.copy(timeOfDayMin = 450) })
        vm.onAction(PlanEditorAction.SetSlotReminder(key, 15))
        assertEquals(15, vm.uiState.value.form.slots.single().reminderOffsetMin)
        assertTrue(vm.uiState.value.hasReminders)

        vm.onAction(PlanEditorAction.SetSlotReminder(key, null))
        assertNull(vm.uiState.value.form.slots.single().reminderOffsetMin)
        assertFalse(vm.uiState.value.hasReminders)
    }

    @Test
    fun `offsets outside the allowed list are ignored`() = runVmTest("new") { vm, _ ->
        vm.onAction(PlanEditorAction.AddSlot("Breakfast"))
        val key = vm.uiState.value.form.slots.single().key
        vm.onAction(PlanEditorAction.UpdateSlot(key) { it.copy(timeOfDayMin = 450) })
        vm.onAction(PlanEditorAction.SetSlotReminder(key, 7))
        assertNull(vm.uiState.value.form.slots.single().reminderOffsetMin)
    }

    @Test
    fun `the reminder offset is saved with the slot`() = runVmTest("new") { vm, _ ->
        vm.onAction(PlanEditorAction.FormChange(PlanForm(name = "Cut", slots = listOf(slot(1, time = 450, offset = 30)))))
        vm.onAction(PlanEditorAction.Save)
        assertEquals(30, repo.savedPlans.single().slots.single().reminderOffsetMin)
    }

    @Test
    fun `reminder choices and labels`() {
        assertEquals(listOf(null, 5, 10, 15, 30, 60), PlanEditorViewModel.REMINDER_CHOICES)
        assertEquals(uiText(R.string.food_plan_reminder_off), PlanEditorViewModel.reminderLabel(null))
        assertEquals(uiText(R.string.food_plan_reminder_before, 15), PlanEditorViewModel.reminderLabel(15))
    }
}
