package com.ironquest.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.AppearanceSettings
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.domain.appearance.ThemeMode
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalSettings
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.PlayerProfile
import com.ironquest.app.domain.nutrition.FoodSettings
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.domain.repository.OnboardingRepository
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import com.ironquest.app.ui.catalog.programNameText
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backup dialogs: a valid file waiting for confirmation, a result message, a restore in progress. */
data class BackupUiState(
    val pending: com.ironquest.app.domain.backup.BackupSummary? = null,
    val message: UiText? = null,
    val busy: Boolean = false,
)

data class YouUiState(
    val profile: PlayerProfile? = null,
    /** Name of the active program; null when none is active. */
    val programName: UiText? = null,
    /** Name of the active meal plan; null when none is active. */
    val activePlanName: String? = null,
    val appearance: AppearanceSettings = AppearanceSettings.Default,
    val goals: GoalSettings = GoalSettings.Default,
    /** Tracking mode and own daily targets. */
    val food: FoodSettings = FoodSettings(),
)

@HiltViewModel
class YouViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    programRepository: ProgramRepository,
    nutritionRepository: NutritionRepository,
    private val settingsRepository: AppSettingsRepository,
    private val dataReset: com.ironquest.app.domain.repository.DataResetRepository,
    private val backups: com.ironquest.app.domain.repository.BackupRepository,
    private val onboarding: OnboardingRepository,
) : ViewModel() {

    private val _events = Channel<UiText>(Channel.BUFFERED)

    /** One-shot snackbar lines. */
    val events: Flow<UiText> = _events.receiveAsFlow()

    private val settingsState = combine(
        settingsRepository.observeAppearance(),
        settingsRepository.observeGoals(),
        settingsRepository.observeFood(),
    ) { appearance, goals, food -> Triple(appearance, goals, food) }

    val uiState: StateFlow<YouUiState> = combine(
        workoutRepository.observeProfile(),
        programRepository.observeActiveProgram().map { it?.let(::programNameText) },
        nutritionRepository.observePlans().map { plans -> plans.firstOrNull { it.isActive }?.name },
        settingsState,
    ) { profile, programName, planName, (appearance, goals, food) ->
        YouUiState(profile, programName, planName, appearance, goals, food)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState())

    private val _backupUi = kotlinx.coroutines.flow.MutableStateFlow(BackupUiState())
    val backupUi: StateFlow<BackupUiState> = _backupUi

    fun suggestedBackupName(): String = backups.suggestedFileName()

    /** The backup as text, or null (with a message) when it couldn't be built. */
    suspend fun createBackup(): String? = try {
        backups.createBackup()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        say(uiText(R.string.you_backup_create_failed))
        null
    }

    /** A file was picked: check it and, if valid, ask before replacing anything. */
    fun onBackupRead(text: String) {
        viewModelScope.launch {
            when (val inspection = backups.inspect(text)) {
                is com.ironquest.app.domain.backup.BackupInspection.Valid -> pendingText = text.also { _backupUi.update { it.copy(pending = inspection.summary) } }
                is com.ironquest.app.domain.backup.BackupInspection.Invalid -> say(inspection.error.toText(nothingChanged = false))
            }
        }
    }

    private var pendingText: String? = null

    fun confirmRestore() {
        val text = pendingText ?: return
        pendingText = null
        _backupUi.update { it.copy(pending = null, busy = true) }
        viewModelScope.launch {
            val message = when (val result = backups.restore(text)) {
                is com.ironquest.app.domain.backup.RestoreResult.Restored -> uiText(R.string.you_backup_imported)
                is com.ironquest.app.domain.backup.RestoreResult.Failed -> result.error.toText(nothingChanged = true)
            }
            _backupUi.update { it.copy(busy = false, message = message) }
        }
    }

    fun cancelRestore() {
        pendingText = null
        _backupUi.update { it.copy(pending = null) }
    }

    fun say(message: UiText) = _backupUi.update { it.copy(message = message) }

    fun dismissBackupMessage() = _backupUi.update { it.copy(message = null) }

    /** Callers confirm twice first. */
    fun wipeData() {
        viewModelScope.launch { dataReset.wipeLoggedData() }
    }

    /** Every tip shows again, each the next time its screen opens. */
    fun showTipsAgain() {
        onboarding.resetAll()
        _events.trySend(uiText(R.string.you_tips_reset_done))
    }

    fun setStyle(style: AppStyle) = settingsRepository.setStyle(style)

    fun setPalette(palette: Palette) = settingsRepository.setPalette(palette)

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    /** Goal reminders re-arm on their own: ReminderCoordinator watches the goal settings. */
    fun setGoalSwitch(type: GoalType, switch: GoalSwitch, on: Boolean) = settingsRepository.setGoalSwitch(type, switch, on)

    fun setOwnTargets(targets: MacroTargets) = settingsRepository.setOwnTargets(targets)

    fun setGoalReminderTime(reminder: GoalReminder, minutes: Int) = settingsRepository.setGoalReminderTime(reminder, minutes)
}
