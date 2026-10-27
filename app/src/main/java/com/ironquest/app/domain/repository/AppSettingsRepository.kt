package com.ironquest.app.domain.repository

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
import kotlinx.coroutines.flow.Flow

/** On-device app settings: appearance and goal switches. */
interface AppSettingsRepository {
    /** Emits the current value immediately, then on every change. */
    fun observeAppearance(): Flow<AppearanceSettings>

    /** Synchronous read, so the first frame can draw with the user's look (no flash of the default). */
    fun currentAppearance(): AppearanceSettings

    /** Also resets the palette to the style's default. */
    fun setStyle(style: AppStyle)

    fun setPalette(palette: Palette)

    fun setThemeMode(mode: ThemeMode)

    /** Emits the current value immediately, then on every change. */
    fun observeGoals(): Flow<GoalSettings>

    fun currentGoals(): GoalSettings

    fun setGoalSwitch(type: GoalType, switch: GoalSwitch, on: Boolean)

    /** [minutes] after midnight, 0..1439. */
    fun setGoalReminderTime(reminder: GoalReminder, minutes: Int)

    /** True once the user deleted the built-in Upper/Lower program (the catalog itself stays in code). */
    fun observeBuiltInProgramHidden(): Flow<Boolean>

    fun currentBuiltInProgramHidden(): Boolean

    fun setBuiltInProgramHidden(hidden: Boolean)

    /** Emits the current value immediately, then on every change. */
    fun observeFood(): Flow<FoodSettings>

    fun currentFood(): FoodSettings

    fun setFoodTrackingMode(mode: FoodTrackingMode)

    /** Every field optional; an all-null value means "no own targets". */
    fun setOwnTargets(targets: MacroTargets)

    /** True only on a fresh install until the first-launch food setup is finished. */
    fun isFoodSetupPending(): Boolean

    /**
     * Called once per process start, before the database is opened. Records the decision the first
     * time only: [freshInstall] true → setup pending; false (an update) → done, mode stays BOTH.
     */
    fun initFoodSetup(freshInstall: Boolean)

    fun finishFoodSetup()

    /** Today's "Do next" choice as stored; whether it still applies is `NextWorkout.isActive`'s call. */
    fun observeNextWorkoutChoice(): Flow<NextWorkoutChoice?>

    fun currentNextWorkoutChoice(): NextWorkoutChoice?

    /** null clears it ("Back to the rotation"). */
    fun setNextWorkoutChoice(choice: NextWorkoutChoice?)
}
