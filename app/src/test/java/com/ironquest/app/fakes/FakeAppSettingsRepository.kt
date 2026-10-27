package com.ironquest.app.fakes

import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.AppearanceSettings
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.domain.appearance.ThemeMode
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalSettings
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.nutrition.FoodSettings
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.program.NextWorkoutChoice
import com.ironquest.app.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppSettingsRepository : AppSettingsRepository {
    val appearance = MutableStateFlow(AppearanceSettings.Default)
    val goals = MutableStateFlow(GoalSettings.Default)

    override fun observeAppearance(): Flow<AppearanceSettings> = appearance
    override fun currentAppearance(): AppearanceSettings = appearance.value
    override fun setStyle(style: AppStyle) {
        appearance.value = appearance.value.withStyle(style)
    }
    override fun setPalette(palette: Palette) {
        appearance.value = appearance.value.copy(palette = palette)
    }
    override fun setThemeMode(mode: ThemeMode) {
        appearance.value = appearance.value.copy(mode = mode)
    }

    override fun observeGoals(): Flow<GoalSettings> = goals
    override fun currentGoals(): GoalSettings = goals.value

    override fun setGoalSwitch(type: GoalType, switch: GoalSwitch, on: Boolean) {
        val current = goals.value
        goals.value = current.copy(byType = current.byType + (type to current.of(type).with(switch, on)))
    }

    val builtInHidden = MutableStateFlow(false)
    override fun observeBuiltInProgramHidden(): Flow<Boolean> = builtInHidden
    override fun currentBuiltInProgramHidden(): Boolean = builtInHidden.value
    override fun setBuiltInProgramHidden(hidden: Boolean) {
        builtInHidden.value = hidden
    }

    override fun setGoalReminderTime(reminder: GoalReminder, minutes: Int) {
        goals.value = when (reminder) {
            GoalReminder.WORKOUT -> goals.value.copy(workoutReminderMin = minutes)
            GoalReminder.FOOD -> goals.value.copy(foodReminderMin = minutes)
        }
    }

    val food = MutableStateFlow(FoodSettings())
    var setupPending = false
    override fun observeFood(): Flow<FoodSettings> = food
    override fun currentFood(): FoodSettings = food.value
    override fun setFoodTrackingMode(mode: FoodTrackingMode) {
        food.value = food.value.copy(mode = mode)
    }
    override fun setOwnTargets(targets: MacroTargets) {
        food.value = food.value.copy(ownTargets = targets)
    }
    override fun isFoodSetupPending(): Boolean = setupPending
    override fun initFoodSetup(freshInstall: Boolean) {
        setupPending = freshInstall
    }
    override fun finishFoodSetup() {
        setupPending = false
    }
    val nextChoice = MutableStateFlow<NextWorkoutChoice?>(null)
    override fun observeNextWorkoutChoice(): Flow<NextWorkoutChoice?> = nextChoice
    override fun currentNextWorkoutChoice(): NextWorkoutChoice? = nextChoice.value
    override fun setNextWorkoutChoice(choice: NextWorkoutChoice?) {
        nextChoice.value = choice
    }
}
