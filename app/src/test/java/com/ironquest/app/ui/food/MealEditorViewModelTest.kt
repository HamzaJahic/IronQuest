package com.ironquest.app.ui.food

import androidx.lifecycle.SavedStateHandle
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.fakes.FakeNutritionRepository
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
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealEditorViewModelTest {

    private val repo = FakeNutritionRepository()

    private fun valid(form: MealForm) = (MealEditorViewModel.validate(form, null) as MealValidation.Valid).draft
    private fun errors(form: MealForm) = (MealEditorViewModel.validate(form, null) as MealValidation.Invalid).errors

    private fun runVmTest(mealId: String, block: suspend TestScope.(MealEditorViewModel, MutableList<MealEditorEvent>) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val vm = MealEditorViewModel(SavedStateHandle(mapOf(MealEditorViewModel.ARG_MEAL_ID to mealId)), repo)
                val events = mutableListOf<MealEditorEvent>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
                block(vm, events)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `blank name and bad numbers are reported per field`() {
        assertEquals(
            setOf(MealField.NAME, MealField.PROTEIN, MealField.CARBS),
            errors(MealForm(name = " ", protein = "abc", carbs = "2000")),
        )
    }

    @Test
    fun `comma decimals parse, blank macros are zero and blank kcal is derived`() {
        val draft = valid(MealForm(name = " Oats ", protein = "35,5"))
        assertEquals("Oats", draft.name)
        assertEquals(35.5, draft.macros.proteinG, 0.0)
        assertEquals(0.0, draft.macros.carbsG, 0.0)
        assertNull(draft.macros.kcal)
    }

    @Test
    fun `recipe link gets https and bad links are rejected`() {
        assertEquals("https://budgetbytes.com/chicken", valid(MealForm(name = "x", recipeUrl = "budgetbytes.com/chicken")).recipeUrl)
        assertNull(valid(MealForm(name = "x", recipeUrl = "  ")).recipeUrl)
        assertEquals(setOf(MealField.URL), errors(MealForm(name = "x", recipeUrl = "not a link")))
    }

    @Test
    fun `tags are trimmed, lowercased and de-duplicated`() {
        assertEquals(listOf("high-protein", "quick"), valid(MealForm(name = "x", tags = " High-Protein, quick,high-protein,, ")).tags)
    }

    @Test
    fun `saving a new meal stores it and emits Saved`() = runVmTest("new") { vm, events ->
        vm.onAction(MealEditorAction.FormChange(MealForm(name = "Shake", protein = "25")))
        vm.onAction(MealEditorAction.Save)
        assertEquals("Shake", repo.savedMeals.single().name)
        assertNull(repo.savedMeals.single().id)
        vm.onAction(MealEditorAction.Save)
        assertEquals(1, repo.savedMeals.size)
        assertEquals(MealEditorEvent.Saved, events.last())
        assertEquals(true, vm.uiState.value.isSaving)
    }

    @Test
    fun `editing loads the existing meal into the form`() {
        repo.meals.value = listOf(Meal(7, "Oats", Macros(35.0, 75.0, 9.0, 9.0, kcal = 500.0), listOf("breakfast"), "Cook it", null))
        runVmTest("7") { vm, _ ->
            val form = vm.uiState.value.form
            assertEquals("Oats", form.name)
            assertEquals("35", form.protein)
            assertEquals("500", form.kcal)
            assertEquals("breakfast", form.tags)
            assertEquals("Cook it", form.recipeText)
            assertEquals(false, vm.uiState.value.isNew)
        }
    }

    @Test
    fun `deleting a meal used by a plan reports ARCHIVED`() {
        val oats = Meal(7, "Oats", Macros(35.0), emptyList(), null, null)
        repo.meals.value = listOf(oats)
        repo.plans.value = listOf(MealPlan(10, "Cut", true, MacroTargets(), listOf(PlanSlot(21, "Breakfast", 7, "Oats", oats.macros, null, null))))
        runVmTest("7") { vm, events ->
            vm.onAction(MealEditorAction.Delete)
            assertEquals(MealEditorEvent.Deleted(DeleteOutcome.ARCHIVED), events.last())
        }
    }

    @Test
    fun `delete twice emits a single Deleted event`() {
        repo.meals.value = listOf(Meal(7, "Oats", Macros(35.0), emptyList(), null, null))
        runVmTest("7") { vm, events ->
            vm.onAction(MealEditorAction.Delete)
            vm.onAction(MealEditorAction.Delete)
            assertEquals(1, events.count { it is MealEditorEvent.Deleted })
        }
    }
}
