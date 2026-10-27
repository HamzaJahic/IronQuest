package com.ironquest.app.ui.you

import com.ironquest.app.R
import com.ironquest.app.domain.backup.BackupInspection
import com.ironquest.app.domain.backup.RestoreResult
import com.ironquest.app.domain.onboarding.OnboardingTopic
import com.ironquest.app.domain.repository.BackupRepository
import com.ironquest.app.domain.repository.DataResetRepository
import com.ironquest.app.fakes.FakeAppSettingsRepository
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.fakes.FakeOnboardingRepository
import com.ironquest.app.fakes.FakeProgramRepository
import com.ironquest.app.fakes.FakeWorkoutRepository
import com.ironquest.app.ui.text.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouViewModelTest {

    private val onboarding = FakeOnboardingRepository()

    private val unusedReset = object : DataResetRepository {
        override suspend fun wipeLoggedData() = error("not used")
    }

    private val unusedBackups = object : BackupRepository {
        override fun suggestedFileName(): String = error("not used")
        override suspend fun createBackup(): String = error("not used")
        override suspend fun inspect(text: String): BackupInspection = error("not used")
        override suspend fun restore(text: String): RestoreResult = error("not used")
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = YouViewModel(
        FakeWorkoutRepository(),
        FakeProgramRepository(),
        FakeNutritionRepository(),
        FakeAppSettingsRepository(),
        unusedReset,
        unusedBackups,
        onboarding,
    )

    @Test
    fun `show tips again forgets every seen tip and says so`() = runTest {
        onboarding.markAllSeen()
        val vm = viewModel()

        vm.showTipsAgain()

        assertEquals(emptyMap<OnboardingTopic, Int>(), onboarding.seen.value)
        assertEquals(uiText(R.string.you_tips_reset_done), vm.events.first())
    }

    @Test
    fun `state carries the food settings and saving targets writes them`() = runTest {
        val settings = FakeAppSettingsRepository()
        val vm = YouViewModel(FakeWorkoutRepository(), FakeProgramRepository(), FakeNutritionRepository(), settings, unusedReset, unusedBackups, onboarding)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        settings.setFoodTrackingMode(FoodTrackingMode.ON_THE_FLY)

        vm.setOwnTargets(MacroTargets(proteinG = 140.0, kcal = 2200.0))

        assertEquals(FoodTrackingMode.ON_THE_FLY, vm.uiState.value.food.mode)
        assertEquals(MacroTargets(proteinG = 140.0, kcal = 2200.0), vm.uiState.value.food.ownTargets)
        job.cancel()
    }
}
