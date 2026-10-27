package com.ironquest.app.ui.food

import com.ironquest.app.domain.food.BasicFood
import com.ironquest.app.domain.food.FoodSearchUseCase
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.food.UnitKind
import com.ironquest.app.domain.food.toChoice
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.fakes.FakeBasicFoodSource
import com.ironquest.app.fakes.FakeFoodRepository
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.fakes.FakeOnlineFoodSearch
import com.ironquest.app.ui.food.builder.OnlineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientCalculatorViewModelTest {

    private val chicken = BasicFood(1, "Chicken, breast, raw", listOf("chicken"), Macros(22.5, 0.0, 1.93, 0.0, 112.0), listOf(FoodUnit.GRAM))
    private val egg = BasicFood(2, "Egg, whole", listOf("egg"), Macros(12.0, 1.0, 10.0, 0.0, 140.0), listOf(FoodUnit.GRAM, FoodUnit(UnitKind.PIECE, 50.0)))
    private val skyr = OnlineFood("222", "Skyr vanilla", "Arla", Macros(10.0, 4.0, 0.2, 0.0, 60.0), servingGrams = 150.0)

    private val foods = FakeFoodRepository()
    private val online = FakeOnlineFoodSearch()
    private val search = FoodSearchUseCase(FakeNutritionRepository(), foods, FakeBasicFoodSource(listOf(chicken, egg)), online)

    private fun runVmTest(block: suspend TestScope.(IngredientCalculatorViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest { block(IngredientCalculatorViewModel(search, foods)) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun IngredientCalculatorViewModel.local() = uiState.value.search as CalculatorSearch.Local

    @Test
    fun `typing shows built-in foods after 250 ms and searches online once after 400 ms`() = runVmTest { vm ->
        vm.onQueryChange("chicken")
        testScheduler.advanceTimeBy(IngredientCalculatorViewModel.DEBOUNCE_MS - 1)
        assertEquals(CalculatorSearch.Idle, vm.uiState.value.search)
        testScheduler.advanceTimeBy(2)
        assertEquals(listOf("Chicken, breast, raw"), vm.local().foods.map { it.name })
        assertTrue(online.queries.isEmpty())
        testScheduler.advanceTimeBy(IngredientCalculatorViewModel.ONLINE_DEBOUNCE_MS)
        assertEquals(listOf("chicken"), online.queries)
    }

    @Test
    fun `short and blank queries never go online and a blank one resets`() = runVmTest { vm ->
        vm.onQueryChange("eg")
        testScheduler.advanceTimeBy(1_000)
        assertTrue(online.queries.isEmpty())
        assertEquals(OnlineState.NotAsked, vm.local().online)
        vm.onQueryChange("")
        assertEquals(CalculatorSearch.Idle, vm.uiState.value.search)
    }

    @Test
    fun `an online failure keeps the local results`() = runVmTest { vm ->
        online.respond = { Result.failure(FoodSearchException(FoodSearchError.RATE_LIMITED)) }
        vm.onQueryChange("chicken")
        testScheduler.advanceTimeBy(IngredientCalculatorViewModel.ONLINE_DEBOUNCE_MS + 1)
        assertEquals(OnlineState.Failed(FoodSearchError.RATE_LIMITED), vm.local().online)
        assertEquals(1, vm.local().foods.size)
    }

    @Test
    fun `picking an online product saves it and opens its amount with the serving unit offered`() = runVmTest { vm ->
        online.respond = { Result.success(listOf(skyr)) }
        vm.onQueryChange("skyr")
        testScheduler.advanceTimeBy(IngredientCalculatorViewModel.ONLINE_DEBOUNCE_MS + 1)
        vm.pick((vm.local().online as OnlineState.Results).foods.single())
        assertEquals(listOf("222"), foods.savedOnline.map { it.barcode })
        val pending = checkNotNull(vm.uiState.value.pending)
        assertTrue(pending.food.key.startsWith("u:"))
        assertTrue(pending.food.units.any { it.kind == UnitKind.SERVING && it.gramsPerUnit == 150.0 })
        assertEquals(FoodUnit.GRAM, pending.unit)
    }

    @Test
    fun `a food without a piece unit starts at 100 g and one with a piece starts at 1 piece`() = runVmTest { vm ->
        vm.pick(chicken.toChoice())
        assertEquals(FoodUnit.GRAM, vm.uiState.value.pending?.unit)
        assertEquals("100", vm.uiState.value.pending?.amountText)
        vm.pick(egg.toChoice())
        assertEquals(UnitKind.PIECE, vm.uiState.value.pending?.unit?.kind)
        assertEquals("1", vm.uiState.value.pending?.amountText)
        vm.onPendingUnitChange(FoodUnit.GRAM)
        assertEquals("100", vm.uiState.value.pending?.amountText)
    }

    @Test
    fun `confirming adds a row and a piece row is counted in pieces`() = runVmTest { vm ->
        vm.pick(egg.toChoice())
        vm.onPendingAmountChange("3")
        vm.confirmPending()
        val state = vm.uiState.value
        assertNull(state.pending)
        assertEquals(UnitKind.PIECE, state.rows.single().unit.kind)
        assertEquals(18.0, checkNotNull(state.total).proteinG, 1e-9)
        assertTrue(state.canApply)
    }

    @Test
    fun `an invalid amount keeps the food pending with an error`() = runVmTest { vm ->
        vm.pick(chicken.toChoice())
        vm.onPendingAmountChange("0")
        vm.confirmPending()
        assertTrue(vm.uiState.value.pendingInvalid)
        assertTrue(vm.uiState.value.rows.isEmpty())
        vm.onPendingAmountChange("1")
        assertFalse(vm.uiState.value.pendingInvalid)
        vm.cancelPending()
        assertNull(vm.uiState.value.pending)
    }

    @Test
    fun `editing, removing and clearing rows updates the total`() = runVmTest { vm ->
        vm.pick(chicken.toChoice())
        vm.onPendingAmountChange("200")
        vm.confirmPending()
        vm.pick(egg.toChoice())
        vm.onPendingAmountChange("2")
        vm.confirmPending()
        val (chickenRow, eggRow) = vm.uiState.value.rows
        assertEquals(45.0 + 12.0, checkNotNull(vm.uiState.value.total).proteinG, 1e-9)

        vm.onRowAmountChange(eggRow.key, "abc")
        assertNull(vm.uiState.value.total)
        assertFalse(vm.uiState.value.canApply)

        vm.removeRow(eggRow.key)
        assertEquals(listOf(chickenRow.key), vm.uiState.value.rows.map { it.key })
        assertEquals(45.0, checkNotNull(vm.uiState.value.total).proteinG, 1e-9)

        vm.clearRows()
        assertTrue(vm.uiState.value.rows.isEmpty())
        assertFalse(vm.uiState.value.canApply)
    }
}
