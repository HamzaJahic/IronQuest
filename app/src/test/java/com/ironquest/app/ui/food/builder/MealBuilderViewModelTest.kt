package com.ironquest.app.ui.food.builder

import androidx.lifecycle.SavedStateHandle
import com.ironquest.app.R
import com.ironquest.app.domain.food.BasicFood
import com.ironquest.app.domain.food.FoodAmount
import com.ironquest.app.domain.food.FoodKey
import com.ironquest.app.domain.food.FoodSearchUseCase
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.QuickMealName
import com.ironquest.app.domain.food.UnitKind
import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.food.toChoice
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.fakes.FakeBasicFoodSource
import com.ironquest.app.fakes.FakeFoodRepository
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.fakes.FakeOnlineFoodSearch
import com.ironquest.app.ui.food.MutableClock
import com.ironquest.app.ui.text.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MealBuilderViewModelTest {

    private val today = FakeNutritionRepository.TODAY
    private val clock = MutableClock(today.atTime(12, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val nutrition = FakeNutritionRepository()
    private val foods = FakeFoodRepository()

    private val egg = BasicFood(
        usdaId = 1,
        name = "Egg, whole",
        aliases = listOf("egg"),
        per100g = Macros(proteinG = 12.0, carbsG = 1.0, fatG = 10.0, kcal = 140.0),
        units = listOf(FoodUnit.GRAM, FoodUnit(UnitKind.PIECE, 50.0)),
    )
    private val rice = BasicFood(
        usdaId = 2,
        name = "Rice, cooked",
        aliases = emptyList(),
        per100g = Macros(proteinG = 3.0, carbsG = 28.0, fatG = 0.5, kcal = 130.0),
        units = listOf(FoodUnit.GRAM),
    )
    private val basic = FakeBasicFoodSource(listOf(egg, rice))
    private val onlineSearch = FakeOnlineFoodSearch()
    private val search = FoodSearchUseCase(nutrition, foods, basic, onlineSearch)

    private val eggItem = MealLogItem(FoodKey.builtIn(1), "Egg, whole", 3.0, FoodUnit(UnitKind.PIECE, 50.0), Macros(18.0, 1.5, 15.0, kcal = 210.0))
    private val riceItem = MealLogItem(FoodKey.builtIn(2), "Rice, cooked", 150.0, FoodUnit.GRAM, Macros(4.5, 42.0, 0.75, kcal = 195.0))
    private val oats = Meal(7, "Oats", Macros(35.0, 75.0, 9.0, 9.0, kcal = 560.0), emptyList(), null, null)

    private fun runVmTest(
        date: LocalDate = today,
        logId: Long = 0,
        block: suspend TestScope.(MealBuilderViewModel, MutableList<MealBuilderEvent>) -> Unit,
    ) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val handle = SavedStateHandle(mapOf(MealBuilderViewModel.ARG_DATE to date.toEpochDay(), MealBuilderViewModel.ARG_LOG_ID to logId))
                val vm = MealBuilderViewModel(handle, nutrition, foods, search, clock)
                val events = mutableListOf<MealBuilderEvent>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
                block(vm, events)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun MealBuilderViewModel.addEgg(amount: String = "3") {
        onAction(MealBuilderAction.PickFood(egg.toChoice()))
        onAction(MealBuilderAction.PendingAmountChanged(amount))
        onAction(MealBuilderAction.ConfirmPending)
    }

    @Test
    fun `create food saves it, refreshes the results and opens its amount`() = runVmTest { vm, _ ->
        vm.onAction(MealBuilderAction.QueryChanged("skyr"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.DEBOUNCE_MS + 1)
        vm.onAction(MealBuilderAction.CreateFood(UserFoodDraft(null, "Skyr", Macros(proteinG = 11.0), pieceGrams = null)))
        assertEquals(listOf("Skyr"), foods.saved.map { it.name })
        val state = vm.uiState.value
        assertEquals("Skyr", state.pending?.choice?.name)
        assertEquals(listOf("Skyr"), (state.search as BuilderSearch.Local).results.foods.map { it.name })
    }

    private val skyr = OnlineFood("222", "Skyr vanilla", "Arla", Macros(proteinG = 10.0, carbsG = 4.0, fatG = 0.2, kcal = 60.0), servingGrams = 150.0)

    private fun MealBuilderViewModel.onlineState() = (uiState.value.search as BuilderSearch.Local).online

    @Test
    fun `online - few local results are searched online once after 400 ms`() = runVmTest { vm, _ ->
        onlineSearch.respond = { Result.success(listOf(skyr)) }
        vm.onAction(MealBuilderAction.QueryChanged("skyr"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.ONLINE_DEBOUNCE_MS - 1)
        assertTrue(onlineSearch.queries.isEmpty())
        testScheduler.advanceTimeBy(2)
        assertEquals(listOf("skyr"), onlineSearch.queries)
        assertEquals(listOf("Skyr vanilla"), (vm.onlineState() as OnlineState.Results).foods.map { it.name })
    }

    @Test
    fun `online - five or more local results wait for SearchOnline`() = runVmTest { vm, _ ->
        foods.userFoods.value = (1L..5L).map { id ->
            com.ironquest.app.domain.food.UserFood(id, "Oat $id", Macros(proteinG = 1.0), null, null, com.ironquest.app.domain.food.UserFoodSource.CUSTOM, null, null)
        }
        vm.onAction(MealBuilderAction.QueryChanged("oat"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.ONLINE_DEBOUNCE_MS + 1)
        assertTrue(onlineSearch.queries.isEmpty())
        assertEquals(OnlineState.NotAsked, vm.onlineState())
        vm.onAction(MealBuilderAction.SearchOnline)
        assertEquals(listOf("oat"), onlineSearch.queries)
        assertTrue(vm.onlineState() is OnlineState.Results)
    }

    @Test
    fun `online - a failure keeps the local results`() = runVmTest { vm, _ ->
        onlineSearch.respond = { Result.failure(FoodSearchException(FoodSearchError.OFFLINE)) }
        vm.onAction(MealBuilderAction.QueryChanged("egg"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.ONLINE_DEBOUNCE_MS + 1)
        val local = vm.uiState.value.search as BuilderSearch.Local
        assertEquals(OnlineState.Failed(FoodSearchError.OFFLINE), local.online)
        assertEquals(listOf("Egg, whole"), local.results.foods.map { it.name })
    }

    @Test
    fun `online - picking a product saves it and opens its amount with the serving unit`() = runVmTest { vm, _ ->
        onlineSearch.respond = { Result.success(listOf(skyr)) }
        vm.onAction(MealBuilderAction.QueryChanged("skyr"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.ONLINE_DEBOUNCE_MS + 1)
        val choice = (vm.onlineState() as OnlineState.Results).foods.single()
        vm.onAction(MealBuilderAction.PickFood(choice))
        assertEquals(listOf("222"), foods.savedOnline.map { it.barcode })
        val pending = checkNotNull(vm.uiState.value.pending)
        assertTrue(FoodKey.mineId(pending.choice?.key.orEmpty()) != null)
        assertTrue(pending.choice?.units.orEmpty().any { it.kind == UnitKind.SERVING && it.gramsPerUnit == 150.0 })
    }

    @Test
    fun `online - a newer query cancels the old online search and a blank one resets`() = runVmTest { vm, _ ->
        onlineSearch.delayMs = 1_000
        vm.onAction(MealBuilderAction.QueryChanged("sky"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.ONLINE_DEBOUNCE_MS + 100)
        vm.onAction(MealBuilderAction.QueryChanged("skyr"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.ONLINE_DEBOUNCE_MS + 2_000)
        assertEquals(listOf("sky", "skyr"), onlineSearch.queries)
        assertEquals("skyr", vm.uiState.value.query)
        assertTrue(vm.onlineState() is OnlineState.Results)

        onlineSearch.queries.clear()
        vm.onAction(MealBuilderAction.QueryChanged("s"))
        vm.onAction(MealBuilderAction.QueryChanged("sk"))
        vm.onAction(MealBuilderAction.QueryChanged("sky"))
        testScheduler.advanceTimeBy(MealBuilderViewModel.ONLINE_DEBOUNCE_MS + 2_000)
        assertEquals(listOf("sky"), onlineSearch.queries)
        vm.onAction(MealBuilderAction.QueryChanged(""))
        assertEquals(BuilderSearch.Idle, vm.uiState.value.search)
    }

    @Test
    fun `1 - new meal is named by the time of day today and Snack on a past day`() {
        runVmTest { vm, _ -> assertEquals(QuickMealName.LUNCH, vm.uiState.value.nameChoice) }
        runVmTest(date = today.minusDays(1)) { vm, _ -> assertEquals(QuickMealName.SNACK, vm.uiState.value.nameChoice) }
    }

    @Test
    fun `2 - editing loads the items and the stored name`() {
        val log = MealLogEntry(55, null, null, "Brunch", 1.0, eggItem.macros + riceItem.macros, listOf(eggItem, riceItem))
        nutrition.logs.value = mapOf(today to listOf(log))
        runVmTest(logId = 55) { vm, _ ->
            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals(55L, state.logId)
            assertNull(state.nameChoice)
            assertEquals("Brunch", state.customName)
            assertEquals(listOf(eggItem, riceItem), state.items.map { it.item })
            assertFalse(state.hasChanges)
            vm.onAction(MealBuilderAction.RemoveItem(state.items.first().key))
            assertTrue(vm.uiState.value.hasChanges)
        }
    }

    @Test
    fun `3 - typing searches after the debounce and a blank query goes idle`() = runVmTest { vm, _ ->
        vm.onAction(MealBuilderAction.QueryChanged("egg"))
        assertEquals(BuilderSearch.Idle, vm.uiState.value.search)
        testScheduler.advanceTimeBy(MealBuilderViewModel.DEBOUNCE_MS + 1)
        val local = vm.uiState.value.search as BuilderSearch.Local
        assertEquals(listOf("Egg, whole"), local.results.foods.map { it.name })
        vm.onAction(MealBuilderAction.QueryChanged(" "))
        assertEquals(BuilderSearch.Idle, vm.uiState.value.search)
    }

    @Test
    fun `4 - picking a food opens its amount with pieces first`() = runVmTest { vm, _ ->
        vm.onAction(MealBuilderAction.PickFood(egg.toChoice()))
        val pending = checkNotNull(vm.uiState.value.pending)
        assertEquals(UnitKind.PIECE, pending.unit.kind)
        assertEquals("1", pending.amountText)
        vm.onAction(MealBuilderAction.CancelPending)
        vm.onAction(MealBuilderAction.PickFood(rice.toChoice()))
        val grams = checkNotNull(vm.uiState.value.pending)
        assertEquals(FoodUnit.GRAM, grams.unit)
        assertEquals("100", grams.amountText)
    }

    @Test
    fun `5 - confirming adds the item and clears pending and query, invalid text adds nothing`() = runVmTest { vm, _ ->
        vm.onAction(MealBuilderAction.QueryChanged("egg"))
        vm.onAction(MealBuilderAction.PickFood(egg.toChoice()))
        vm.onAction(MealBuilderAction.PendingAmountChanged("0"))
        vm.onAction(MealBuilderAction.ConfirmPending)
        assertTrue(vm.uiState.value.pendingInvalid)
        assertTrue(vm.uiState.value.items.isEmpty())
        vm.onAction(MealBuilderAction.PendingAmountChanged("3"))
        assertFalse(vm.uiState.value.pendingInvalid)
        vm.onAction(MealBuilderAction.ConfirmPending)
        val state = vm.uiState.value
        assertNull(state.pending)
        assertEquals("", state.query)
        val item = state.items.single().item
        assertEquals(FoodKey.builtIn(1), item.foodKey)
        assertEquals(3.0, item.amount, 0.0)
        assertEquals(FoodAmount.macrosFor(egg.per100g, 3.0, FoodUnit(UnitKind.PIECE, 50.0)), item.macros)
        assertEquals(item.macros, state.total)
        assertTrue(state.canFinish)
    }

    @Test
    fun `6 - a recent food reopens with its amount, and a gone food uses the snapshot`() = runVmTest { vm, _ ->
        vm.onAction(MealBuilderAction.PickRecentFood(eggItem))
        val pending = checkNotNull(vm.uiState.value.pending)
        assertEquals(eggItem.unit, pending.unit)
        assertEquals("3", pending.amountText)
        assertEquals(egg.name, pending.choice?.name)
        vm.onAction(MealBuilderAction.CancelPending)

        val gone = eggItem.copy(foodKey = FoodKey.mine(99))
        vm.onAction(MealBuilderAction.PickRecentFood(gone))
        val snapshotPending = checkNotNull(vm.uiState.value.pending)
        assertNull(snapshotPending.choice)
        assertEquals(gone, snapshotPending.snapshot)
        vm.onAction(MealBuilderAction.PendingAmountChanged("6"))
        vm.onAction(MealBuilderAction.ConfirmPending)
        assertEquals(gone.withAmount(6.0), vm.uiState.value.items.single().item)
    }

    @Test
    fun `7 - a recent meal appends its items without saving`() = runVmTest { vm, _ ->
        val log = MealLogEntry(3, null, null, "Brunch", 1.0, eggItem.macros + riceItem.macros, listOf(eggItem, riceItem))
        vm.onAction(MealBuilderAction.PickRecentMeal(log))
        val items = vm.uiState.value.items
        assertEquals(listOf(eggItem, riceItem), items.map { it.item })
        assertEquals(2, items.map { it.key }.toSet().size)
        assertTrue(nutrition.savedQuickMeals.isEmpty())
    }

    @Test
    fun `7b - editing an item replaces it, converting units through the food`() = runVmTest { vm, _ ->
        vm.addEgg("3")
        val key = vm.uiState.value.items.single().key
        vm.onAction(MealBuilderAction.EditItem(key))
        val pending = checkNotNull(vm.uiState.value.pending)
        assertEquals(key, pending.editingKey)
        assertEquals("3", pending.amountText)
        assertEquals(listOf(UnitKind.GRAM, UnitKind.PIECE), pending.units.map { it.kind })

        vm.onAction(MealBuilderAction.PendingAmountChanged("4"))
        vm.onAction(MealBuilderAction.ConfirmPending)
        assertEquals(4.0, vm.uiState.value.items.single().item.amount, 0.0)
        assertEquals(egg.per100g * 2.0, vm.uiState.value.items.single().item.macros)

        vm.onAction(MealBuilderAction.EditItem(key))
        vm.onAction(MealBuilderAction.PendingUnitChanged(FoodUnit.GRAM))
        vm.onAction(MealBuilderAction.PendingAmountChanged("200"))
        vm.onAction(MealBuilderAction.ConfirmPending)
        val replaced = vm.uiState.value.items.single()
        assertEquals(key, replaced.key)
        assertEquals(FoodUnit.GRAM, replaced.item.unit)
        assertEquals(FoodAmount.macrosFor(egg.per100g, 200.0, FoodUnit.GRAM), replaced.item.macros)
    }

    @Test
    fun `8 - a library meal on an empty builder asks for a portion and logs it directly`() {
        nutrition.meals.value = listOf(oats)
        runVmTest { vm, events ->
            vm.onAction(MealBuilderAction.PickMeal(oats))
            assertEquals(oats, vm.uiState.value.pendingMeal)
            vm.onAction(MealBuilderAction.LogMealPortion("1.5"))
            val log = nutrition.logs.value.getValue(today).single()
            assertEquals(oats.id, log.mealId)
            assertEquals(1.5, log.portion, 0.0)
            assertTrue(events.single() is MealBuilderEvent.Finished)
        }
    }

    @Test
    fun `8 - a library meal with items present becomes an item with an editable portion`() = runVmTest { vm, _ ->
        vm.addEgg()
        vm.onAction(MealBuilderAction.PickMeal(oats))
        val state = vm.uiState.value
        assertNull(state.pendingMeal)
        val added = state.items.last()
        assertEquals(MealLogItem(FoodKey.meal(7), "Oats", 1.0, FoodUnit.MEAL_PORTION, oats.macros * 1.0), added.item)
        val pending = checkNotNull(state.pending)
        assertEquals(added.key, pending.editingKey)
        vm.onAction(MealBuilderAction.PendingAmountChanged("2"))
        vm.onAction(MealBuilderAction.ConfirmPending)
        assertEquals(added.item.withAmount(2.0), vm.uiState.value.items.last().item)
    }

    @Test
    fun `9 - Done saves once, keeps isSaving and emits Finished`() = runVmTest { vm, events ->
        vm.addEgg()
        vm.onAction(MealBuilderAction.Done("Lunch"))
        vm.onAction(MealBuilderAction.Done("Lunch"))
        assertEquals(1, nutrition.savedQuickMeals.size)
        val (logId, name, items) = nutrition.savedQuickMeals.single()
        assertNull(logId)
        assertEquals("Lunch", name)
        assertEquals(1, items.size)
        assertTrue(vm.uiState.value.isSaving)
        assertTrue(events.single() is MealBuilderEvent.Finished)
    }

    @Test
    fun `9 - a failed save re-enables the screen and shows a message`() = runVmTest { vm, events ->
        vm.addEgg()
        nutrition.failNext = IllegalStateException("disk full")
        vm.onAction(MealBuilderAction.Done("Lunch"))
        assertFalse(vm.uiState.value.isSaving)
        val message = events.single() as MealBuilderEvent.Message
        assertTrue(EnglishText.resolve(message.text).contains("disk full"))
    }

    @Test
    fun `11 - save as meal stores the total as a library meal`() {
        val log = MealLogEntry(55, null, null, "Brunch", 1.0, eggItem.macros + riceItem.macros, listOf(eggItem, riceItem))
        nutrition.logs.value = mapOf(today to listOf(log))
        runVmTest(logId = 55) { vm, events ->
            vm.onAction(MealBuilderAction.SaveAsMeal("Brunch", "Egg — 3 pcs"))
            val draft = nutrition.savedMeals.single()
            assertEquals("Brunch", draft.name)
            assertEquals(eggItem.macros + riceItem.macros, draft.macros)
            assertEquals("Egg — 3 pcs", draft.recipeText)
            assertNull(draft.id)
            assertEquals(UiText.Res(R.string.food_builder_saved_as_meal, emptyList()), (events.single() as MealBuilderEvent.Message).text)
        }
    }

    @Test
    fun `11 - save as meal refuses a total above the editor limits`() {
        val huge = eggItem.copy(macros = Macros(5_000.0, 0.0, 0.0, kcal = 20_000.0))
        val log = MealLogEntry(55, null, null, "Brunch", 1.0, huge.macros, listOf(huge))
        nutrition.logs.value = mapOf(today to listOf(log))
        runVmTest(logId = 55) { vm, events ->
            vm.onAction(MealBuilderAction.SaveAsMeal("Brunch", ""))
            assertTrue(nutrition.savedMeals.isEmpty())
            assertEquals(UiText.Res(R.string.food_calc_over_limit, emptyList()), (events.single() as MealBuilderEvent.Message).text)
        }
    }
}
