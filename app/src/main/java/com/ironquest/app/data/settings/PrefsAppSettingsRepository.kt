package com.ironquest.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.AppearanceSettings
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.domain.appearance.ThemeMode
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalSettings
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.goals.GoalTypeSettings
import com.ironquest.app.domain.nutrition.FoodSettings
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.program.NextWorkoutChoice
import com.ironquest.app.domain.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** SharedPreferences `app_settings`. Enums stored by name; unknown names fall back to defaults. */
@Singleton
class PrefsAppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AppSettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun observeAppearance(): Flow<AppearanceSettings> = changes().map { readAppearance() }.distinctUntilChanged()

    /** Emits once now and on every preference change. */
    private fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    override fun currentAppearance(): AppearanceSettings = readAppearance()

    override fun setStyle(style: AppStyle) = write(readAppearance().withStyle(style))

    override fun setPalette(palette: Palette) = write(readAppearance().copy(palette = palette))

    override fun setThemeMode(mode: ThemeMode) = write(readAppearance().copy(mode = mode))

    override fun observeGoals(): Flow<GoalSettings> = changes().map { readGoals() }.distinctUntilChanged()

    override fun currentGoals(): GoalSettings = readGoals()

    override fun setGoalSwitch(type: GoalType, switch: GoalSwitch, on: Boolean) {
        prefs.edit().putBoolean(switchKey(type, switch), on).apply()
    }

    override fun setGoalReminderTime(reminder: GoalReminder, minutes: Int) {
        prefs.edit().putInt(timeKey(reminder), minutes.coerceIn(0, 1439)).apply()
    }

    override fun observeBuiltInProgramHidden(): Flow<Boolean> = changes().map { prefs.getBoolean(KEY_BUILT_IN_HIDDEN, false) }.distinctUntilChanged()

    override fun currentBuiltInProgramHidden(): Boolean = prefs.getBoolean(KEY_BUILT_IN_HIDDEN, false)

    override fun setBuiltInProgramHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_BUILT_IN_HIDDEN, hidden).apply()
    }

    override fun observeFood(): Flow<FoodSettings> = changes().map { readFood() }.distinctUntilChanged()

    override fun currentFood(): FoodSettings = readFood()

    override fun setFoodTrackingMode(mode: FoodTrackingMode) {
        prefs.edit().putString(KEY_FOOD_MODE, mode.name).apply()
    }

    override fun setOwnTargets(targets: MacroTargets) {
        prefs.edit()
            .putAmount(KEY_TARGET_PROTEIN, targets.proteinG)
            .putAmount(KEY_TARGET_CARBS, targets.carbsG)
            .putAmount(KEY_TARGET_FAT, targets.fatG)
            .putAmount(KEY_TARGET_FIBER, targets.fiberG)
            .putAmount(KEY_TARGET_KCAL, targets.kcal)
            .apply {
                val water = targets.waterMl
                if (water == null) remove(KEY_TARGET_WATER) else putInt(KEY_TARGET_WATER, water)
            }
            .apply()
    }

    override fun isFoodSetupPending(): Boolean = prefs.getBoolean(KEY_FOOD_SETUP_PENDING, false)

    override fun initFoodSetup(freshInstall: Boolean) {
        // commit(): MainActivity reads the flag right after the application starts.
        if (!prefs.contains(KEY_FOOD_SETUP_PENDING)) prefs.edit().putBoolean(KEY_FOOD_SETUP_PENDING, freshInstall).commit()
    }

    override fun finishFoodSetup() {
        prefs.edit().putBoolean(KEY_FOOD_SETUP_PENDING, false).apply()
    }

    override fun observeNextWorkoutChoice(): Flow<NextWorkoutChoice?> = changes().map { readNextWorkoutChoice() }.distinctUntilChanged()

    override fun currentNextWorkoutChoice(): NextWorkoutChoice? = readNextWorkoutChoice()

    override fun setNextWorkoutChoice(choice: NextWorkoutChoice?) {
        val editor = prefs.edit()
        if (choice == null) {
            editor.remove(NextWorkoutChoiceCodec.KEY_TEMPLATE).remove(NextWorkoutChoiceCodec.KEY_CHOSEN_AT)
        } else {
            editor.putString(NextWorkoutChoiceCodec.KEY_TEMPLATE, choice.templateId).putLong(NextWorkoutChoiceCodec.KEY_CHOSEN_AT, choice.chosenAtMillis)
        }
        editor.apply()
    }

    private fun readNextWorkoutChoice(): NextWorkoutChoice? {
        val stored = prefs.all
        return NextWorkoutChoiceCodec.decode(stored[NextWorkoutChoiceCodec.KEY_TEMPLATE], stored[NextWorkoutChoiceCodec.KEY_CHOSEN_AT])
    }

    private fun SharedPreferences.Editor.putAmount(key: String, value: Double?): SharedPreferences.Editor = if (value == null) remove(key) else putString(key, value.toString())

    private fun readAmount(key: String): Double? = try {
        prefs.getString(key, null)?.toDoubleOrNull()
    } catch (_: ClassCastException) {
        null
    }

    private fun readFood(): FoodSettings = FoodSettings(
        mode = enumOrNull<FoodTrackingMode>(prefs.getString(KEY_FOOD_MODE, null)) ?: FoodTrackingMode.DEFAULT,
        ownTargets = MacroTargets(
            proteinG = readAmount(KEY_TARGET_PROTEIN),
            carbsG = readAmount(KEY_TARGET_CARBS),
            fatG = readAmount(KEY_TARGET_FAT),
            fiberG = readAmount(KEY_TARGET_FIBER),
            kcal = readAmount(KEY_TARGET_KCAL),
            waterMl = try {
                if (prefs.contains(KEY_TARGET_WATER)) prefs.getInt(KEY_TARGET_WATER, 0) else null
            } catch (_: ClassCastException) {
                null
            },
        ),
    )

    private fun readGoals(): GoalSettings {
        val defaults = GoalTypeSettings()
        return GoalSettings(
            byType = GoalType.entries.associateWith { type ->
                GoalSwitch.entries.fold(defaults) { acc, switch ->
                    acc.with(switch, prefs.getBoolean(switchKey(type, switch), defaults.get(switch)))
                }
            },
            workoutReminderMin = prefs.getInt(timeKey(GoalReminder.WORKOUT), GoalSettings.DEFAULT_WORKOUT_MIN).coerceIn(0, 1439),
            foodReminderMin = prefs.getInt(timeKey(GoalReminder.FOOD), GoalSettings.DEFAULT_FOOD_MIN).coerceIn(0, 1439),
        )
    }

    private fun write(settings: AppearanceSettings) {
        prefs.edit()
            .putString(KEY_STYLE, settings.style.name)
            .putString(KEY_PALETTE, settings.palette.name)
            .putString(KEY_MODE, settings.mode.name)
            .apply()
    }

    private fun readAppearance(): AppearanceSettings {
        val style = enumOrNull<AppStyle>(prefs.getString(KEY_STYLE, null)) ?: AppearanceSettings.Default.style
        return AppearanceSettings(
            style = style,
            palette = enumOrNull<Palette>(prefs.getString(KEY_PALETTE, null)) ?: style.defaultPalette,
            mode = enumOrNull<ThemeMode>(prefs.getString(KEY_MODE, null)) ?: AppearanceSettings.Default.mode,
        )
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = name?.let { constantName -> enumValues<E>().firstOrNull { it.name == constantName } }

    private companion object {
        const val PREFS = "app_settings"
        const val KEY_STYLE = "appearance_style"
        const val KEY_PALETTE = "appearance_palette"
        const val KEY_MODE = "appearance_mode"
        const val KEY_BUILT_IN_HIDDEN = "built_in_program_hidden"
        const val KEY_FOOD_MODE = "food_tracking_mode" // not UI text: SharedPreferences key
        const val KEY_TARGET_PROTEIN = "food_target_protein" // not UI text: SharedPreferences key
        const val KEY_TARGET_CARBS = "food_target_carbs" // not UI text: SharedPreferences key
        const val KEY_TARGET_FAT = "food_target_fat" // not UI text: SharedPreferences key
        const val KEY_TARGET_FIBER = "food_target_fiber" // not UI text: SharedPreferences key
        const val KEY_TARGET_KCAL = "food_target_kcal" // not UI text: SharedPreferences key
        const val KEY_TARGET_WATER = "food_target_water" // not UI text: SharedPreferences key
        const val KEY_FOOD_SETUP_PENDING = "food_setup_pending" // not UI text: SharedPreferences key

        /** e.g. "goal_limit_break_show". Enum names, so renaming an entry resets its switch to the default. */
        fun switchKey(type: GoalType, switch: GoalSwitch) = "goal_${type.name.lowercase()}_${switch.name.lowercase()}" // not UI text: SharedPreferences key
        fun timeKey(reminder: GoalReminder) = "goal_time_${reminder.name.lowercase()}" // not UI text: SharedPreferences key
    }
}
