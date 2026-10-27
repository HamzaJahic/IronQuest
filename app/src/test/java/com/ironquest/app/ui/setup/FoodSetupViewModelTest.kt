package com.ironquest.app.ui.setup

import androidx.lifecycle.SavedStateHandle
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.AppearanceSettings
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.domain.appearance.ThemeMode
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.fakes.FakeAppSettingsRepository
import com.ironquest.app.ui.food.TargetField
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
class FoodSetupViewModelTest {

    private val settings = FakeAppSettingsRepository().apply { setupPending = true }

    private fun runVmTest(fromSettings: Boolean = false, block: suspend TestScope.(FoodSetupViewModel, MutableList<FoodSetupEvent>) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val handle = SavedStateHandle(mapOf(FoodSetupViewModel.ARG_FROM_SETTINGS to fromSettings))
                val vm = FoodSetupViewModel(handle, settings)
                val events = mutableListOf<FoodSetupEvent>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
                block(vm, events)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `first launch opens on appearance, continue goes on to food`() = runVmTest { vm, _ ->
        assertEquals(SetupStep.APPEARANCE, vm.uiState.value.step)
        vm.onAction(FoodSetupAction.Continue)
        assertEquals(SetupStep.CHOOSE, vm.uiState.value.step)
        vm.onAction(FoodSetupAction.Back)
        assertEquals(SetupStep.APPEARANCE, vm.uiState.value.step)
    }

    @Test
    fun `the settings page skips appearance`() = runVmTest(fromSettings = true) { vm, _ ->
        assertEquals(SetupStep.CHOOSE, vm.uiState.value.step)
        vm.onAction(FoodSetupAction.Back)
        assertEquals(SetupStep.CHOOSE, vm.uiState.value.step)
    }

    @Test
    fun `style and palette are saved at once, a style resets the palette`() = runVmTest { vm, _ ->
        vm.onAction(FoodSetupAction.SetPalette(Palette.STEEL))
        assertEquals(Palette.STEEL, settings.appearance.value.palette)
        vm.onAction(FoodSetupAction.SetStyle(AppStyle.BOLD))
        assertEquals(AppearanceSettings(style = AppStyle.BOLD, palette = AppStyle.BOLD.defaultPalette), settings.appearance.value)
        assertEquals(settings.appearance.value, vm.uiState.value.appearance)
        assertTrue(settings.setupPending)
    }

    @Test
    fun `light or dark is saved at once and kept when the style changes`() = runVmTest { vm, _ ->
        vm.onAction(FoodSetupAction.SetThemeMode(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, settings.appearance.value.mode)
        vm.onAction(FoodSetupAction.SetStyle(AppStyle.SOFT))
        assertEquals(ThemeMode.DARK, vm.uiState.value.appearance.mode)
    }

    @Test
    fun `choosing log as I go saves the mode and opens targets`() = runVmTest { vm, _ ->
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.ON_THE_FLY))
        assertEquals(FoodTrackingMode.ON_THE_FLY, settings.food.value.mode)
        assertEquals(SetupStep.TARGETS, vm.uiState.value.step)
    }

    @Test
    fun `choosing both or plan asks about the plan`() = runVmTest { vm, _ ->
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.BOTH))
        assertEquals(SetupStep.PLAN_NEXT, vm.uiState.value.step)
        vm.onAction(FoodSetupAction.Back)
        assertEquals(SetupStep.CHOOSE, vm.uiState.value.step)
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.PLAN))
        assertEquals(SetupStep.PLAN_NEXT, vm.uiState.value.step)
        assertTrue(settings.setupPending)
    }

    @Test
    fun `valid targets are saved and setup finishes`() = runVmTest { vm, events ->
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.ON_THE_FLY))
        vm.onAction(FoodSetupAction.TargetChanged(TargetField.KCAL, "2200"))
        vm.onAction(FoodSetupAction.TargetChanged(TargetField.PROTEIN, "140"))
        vm.onAction(FoodSetupAction.TargetChanged(TargetField.WATER, "3000"))
        vm.onAction(FoodSetupAction.SaveTargets)
        assertEquals(MacroTargets(proteinG = 140.0, kcal = 2200.0, waterMl = 3000), settings.food.value.ownTargets)
        assertFalse(settings.setupPending)
        assertEquals(listOf<FoodSetupEvent>(FoodSetupEvent.Exit(SetupExit.TODAY)), events)
    }

    @Test
    fun `invalid protein is flagged and nothing is saved`() = runVmTest { vm, events ->
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.ON_THE_FLY))
        vm.onAction(FoodSetupAction.TargetChanged(TargetField.PROTEIN, "lots"))
        vm.onAction(FoodSetupAction.SaveTargets)
        assertTrue(vm.uiState.value.targetsInvalid)
        assertEquals(MacroTargets(), settings.food.value.ownTargets)
        assertTrue(settings.setupPending)
        assertTrue(events.isEmpty())
        vm.onAction(FoodSetupAction.TargetChanged(TargetField.PROTEIN, "120"))
        assertFalse(vm.uiState.value.targetsInvalid)
    }

    @Test
    fun `skip finishes without targets`() = runVmTest { vm, events ->
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.ON_THE_FLY))
        vm.onAction(FoodSetupAction.Skip)
        assertFalse(settings.setupPending)
        assertEquals(MacroTargets(), settings.food.value.ownTargets)
        assertEquals(listOf<FoodSetupEvent>(FoodSetupEvent.Exit(SetupExit.TODAY)), events)
    }

    @Test
    fun `finish with import plan exits there`() = runVmTest { vm, events ->
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.PLAN))
        vm.onAction(FoodSetupAction.Finish(SetupExit.IMPORT_PLAN))
        assertFalse(settings.setupPending)
        assertEquals(listOf<FoodSetupEvent>(FoodSetupEvent.Exit(SetupExit.IMPORT_PLAN)), events)
    }

    @Test
    fun `from settings the current mode is shown`() = runVmTest(fromSettings = true) { vm, _ ->
        assertEquals(FoodTrackingMode.BOTH, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.fromSettings)
    }

    @Test
    fun `from settings choosing plan exits and leaves setup alone`() = runVmTest(fromSettings = true) { vm, events ->
        vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.PLAN))
        assertEquals(FoodTrackingMode.PLAN, settings.food.value.mode)
        assertTrue(settings.setupPending)
        assertEquals(listOf<FoodSetupEvent>(FoodSetupEvent.Exit(SetupExit.TODAY)), events)
    }

    @Test
    fun `from settings log as I go opens targets prefilled and keeps the other targets`() {
        settings.food.value = settings.food.value.copy(ownTargets = MacroTargets(proteinG = 150.0, carbsG = 200.0, fiberG = 30.0))
        runVmTest(fromSettings = true) { vm, events ->
            vm.onAction(FoodSetupAction.Choose(FoodTrackingMode.ON_THE_FLY))
            assertEquals(SetupStep.TARGETS, vm.uiState.value.step)
            assertEquals("150", vm.uiState.value.texts.protein)
            vm.onAction(FoodSetupAction.TargetChanged(TargetField.KCAL, "2000"))
            vm.onAction(FoodSetupAction.SaveTargets)
            assertEquals(MacroTargets(proteinG = 150.0, carbsG = 200.0, fiberG = 30.0, kcal = 2000.0), settings.food.value.ownTargets)
            assertTrue(settings.setupPending)
            assertEquals(listOf<FoodSetupEvent>(FoodSetupEvent.Exit(SetupExit.TODAY)), events)
        }
    }

    @Test
    fun `setup starts with no mode chosen`() = runVmTest { vm, _ ->
        assertNull(vm.uiState.value.mode)
        assertEquals(SetupStep.APPEARANCE, vm.uiState.value.step)
    }
}
