package com.ironquest.app.ui.you

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.domain.appearance.ThemeMode
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.kit.ChoiceChips
import com.ironquest.app.ui.kit.CollapsibleSection
import com.ironquest.app.ui.food.DailyTargetsDialog
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.PaletteSwatches
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SegmentedControl
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.text.uiText
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun YouScreen(
    contentPadding: PaddingValues,
    onOpenPrograms: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenFoodSettings: () -> Unit,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backup by viewModel.backupUi.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { text -> snackbar.showSnackbar(text.resolve(context.resources)) }
    }
    // The system pickers list device storage and Google Drive alike, so "save" and "load" cover both.
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val text = viewModel.createBackup() ?: return@launch
                viewModel.say(uiText(if (writeBackupFile(context, uri, text)) R.string.you_backup_exported else R.string.you_backup_save_failed))
            }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val read = readBackupFile(context, uri)) {
                    is BackupRead.Loaded -> viewModel.onBackupRead(read.text)
                    is BackupRead.Failed -> viewModel.say(read.message)
                }
            }
        }
    }
    val backupActions = BackupActions(
        onSave = { save.launch(viewModel.suggestedBackupName()) },
        onLoad = { load.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
    )
    var dialogOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        YouContent(
            state = state,
            contentPadding = contentPadding,
            onOpenPrograms = onOpenPrograms,
            onOpenPlans = onOpenPlans,
            onOpenFoodSettings = onOpenFoodSettings,
            onSaveTargets = viewModel::setOwnTargets,
            onStyle = viewModel::setStyle,
            onPalette = viewModel::setPalette,
            onMode = viewModel::setThemeMode,
            onGoalSwitch = viewModel::setGoalSwitch,
            onGoalTime = viewModel::setGoalReminderTime,
            onWipeData = viewModel::wipeData,
            backupActions = backupActions,
            onShowTipsAgain = viewModel::showTipsAgain,
            onDialogOpenChange = { open -> dialogOpen = open },
        )
        IronSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()))
    }
    BackupDialogs(backup, onConfirm = viewModel::confirmRestore, onCancel = viewModel::cancelRestore, onDismissMessage = viewModel::dismissBackupMessage)
    val backupDialogOpen = backup.pending != null || backup.busy || backup.message != null
    OnboardingGate(OnboardingScreen.YOU, if (dialogOpen || backupDialogOpen) setOf(OnboardingFact.BLOCKED) else emptySet())
}

@Composable
fun YouContent(
    state: YouUiState,
    contentPadding: PaddingValues,
    onOpenPrograms: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenFoodSettings: () -> Unit,
    onSaveTargets: (MacroTargets) -> Unit,
    onStyle: (AppStyle) -> Unit,
    onPalette: (Palette) -> Unit,
    onMode: (ThemeMode) -> Unit,
    onGoalSwitch: (GoalType, GoalSwitch, Boolean) -> Unit,
    onGoalTime: (GoalReminder, Int) -> Unit,
    onWipeData: () -> Unit = {},
    backupActions: BackupActions = BackupActions({}, {}),
    onShowTipsAgain: () -> Unit = {},
    onDialogOpenChange: (Boolean) -> Unit = {},
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    var wipeDialogOpen by remember { mutableStateOf(false) }
    var timeDialogOpen by remember { mutableStateOf(false) }
    var targetsDialogOpen by rememberSaveable { mutableStateOf(false) }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    LaunchedEffect(wipeDialogOpen || timeDialogOpen || targetsDialogOpen) { currentOnDialogOpenChange(wipeDialogOpen || timeDialogOpen || targetsDialogOpen) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        PageTitle(stringResource(R.string.you_title))
        state.profile?.let { profile ->
            Column(Modifier.onboardingAnchor(OnboardingTarget.YOU_LEVEL)) {
                LevelSection(profile)
                StatsSection(profile)
            }
        }

        var openSection by rememberSaveable { mutableStateOf<String?>(null) }
        fun toggle(name: String) {
            openSection = if (openSection == name) null else name
        }

        val programName = state.programName?.resolve()
        val noneActive = stringResource(R.string.you_none_active)
        CollapsibleSection(
            stringResource(R.string.you_plans_title),
            expanded = openSection == "plans",
            onToggle = { toggle("plans") },
            modifier = Modifier.onboardingAnchor(OnboardingTarget.YOU_PLANS),
            summary = listOfNotNull(programName, state.activePlanName).joinToString(stringResource(R.string.common_dot_separator)).ifEmpty { noneActive },
        ) {
            RowGroup {
                RuledRow(onClick = onOpenPrograms) {
                    Text(stringResource(R.string.you_program), style = type.secondary, color = colors.muted, modifier = Modifier.padding(end = 12.dp))
                    Text(
                        programName ?: noneActive,
                        style = type.body,
                        color = if (programName != null) colors.ink else colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text("›", style = type.body, color = colors.muted)
                }
                if (state.food.mode.showsPlan) {
                    RuledRow(onClick = onOpenPlans) {
                        Text(stringResource(R.string.you_meal_plan), style = type.secondary, color = colors.muted, modifier = Modifier.padding(end = 12.dp))
                        Text(
                            state.activePlanName ?: noneActive,
                            style = type.body,
                            color = if (state.activePlanName != null) colors.ink else colors.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text("›", style = type.body, color = colors.muted)
                    }
                }
                RuledRow(onClick = onOpenFoodSettings, modifier = Modifier.onboardingAnchor(OnboardingTarget.YOU_FOOD_TRACKING)) {
                    Text(stringResource(R.string.you_food_tracking), style = type.secondary, color = colors.muted, modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(state.food.mode.labelRes()), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                    Text("›", style = type.body, color = colors.muted)
                }
                if (state.food.mode == FoodTrackingMode.ON_THE_FLY || state.activePlanName == null) {
                    val targetsSummary = targetsSummary(state.food.ownTargets)
                    RuledRow(onClick = { targetsDialogOpen = true }) {
                        Text(stringResource(R.string.you_food_targets), style = type.secondary, color = colors.muted, modifier = Modifier.padding(end = 12.dp))
                        Text(
                            targetsSummary ?: stringResource(R.string.you_food_targets_not_set),
                            style = type.body,
                            color = if (targetsSummary != null) colors.ink else colors.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text("›", style = type.body, color = colors.muted)
                    }
                }
            }
        }

        CollapsibleSection(stringResource(R.string.you_goals_title), expanded = openSection == "goals", onToggle = { toggle("goals") }, Modifier.onboardingAnchor(OnboardingTarget.YOU_GOALS)) {
            GoalsSection(state.goals, onGoalSwitch, onGoalTime, onDialogOpenChange = { open -> timeDialogOpen = open })
        }

        val appearance = state.appearance
        val resources = LocalContext.current.resources
        LocalConfiguration.current // re-resolve the labels when the app language changes
        fun label(@StringRes id: Int) = resources.getString(id)
        CollapsibleSection(
            stringResource(R.string.you_appearance_title),
            expanded = openSection == "appearance",
            onToggle = { toggle("appearance") },
            modifier = Modifier.onboardingAnchor(OnboardingTarget.YOU_APPEARANCE),
            summary = listOf(label(appearance.style.labelRes()), label(appearance.palette.labelRes())).joinToString(stringResource(R.string.common_dot_separator)),
        ) {
            Text(stringResource(R.string.you_appearance_style), style = type.secondary, color = colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            ChoiceChips(AppStyle.entries, appearance.style, { label(it.labelRes()) }, onStyle)
            Text(
                stringResource(R.string.you_appearance_palette, label(appearance.palette.labelRes())),
                style = type.secondary,
                color = colors.muted,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
            )
            PaletteSwatches(appearance.palette, { label(it.labelRes()) }, onPalette)
            Text(stringResource(R.string.you_appearance_mode), style = type.secondary, color = colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            SegmentedControl(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), appearance.mode, { label(it.labelRes()) }, onMode)
            TipsSection(onShowTipsAgain)
        }

        CollapsibleSection(stringResource(R.string.you_backup_title), expanded = openSection == "backup", onToggle = { toggle("backup") }, Modifier.onboardingAnchor(OnboardingTarget.YOU_BACKUP)) {
            BackupSection(backupActions)
        }

        CollapsibleSection(stringResource(R.string.you_data_title), expanded = openSection == "data", onToggle = { toggle("data") }) {
            WipeSection(onWipeData, onDialogOpenChange = { open -> wipeDialogOpen = open })
        }
        Spacer(Modifier.height(8.dp))
    }
    if (targetsDialogOpen) {
        DailyTargetsDialog(
            initial = state.food.ownTargets,
            onSave = { targets ->
                onSaveTargets(targets)
                targetsDialogOpen = false
            },
            onDismiss = { targetsDialogOpen = false },
        )
    }
}

/** "2,200 kcal · 140 g protein" from whichever of the two are set, null when neither is. */
@Composable
private fun targetsSummary(targets: MacroTargets): String? {
    val locale = currentLocale()
    val parts = listOfNotNull(
        targets.kcal?.let { stringResource(R.string.you_food_targets_kcal, Formats.integer(it.roundToInt(), locale)) },
        targets.proteinG?.let { stringResource(R.string.you_food_targets_protein, Formats.decimal(it, locale, maxDecimals = 1)) },
    )
    if (parts.isEmpty()) return null
    return parts.joinToString(stringResource(R.string.common_dot_separator))
}

@StringRes
internal fun FoodTrackingMode.labelRes() = when (this) {
    FoodTrackingMode.PLAN -> R.string.setup_food_mode_plan
    FoodTrackingMode.ON_THE_FLY -> R.string.setup_food_mode_on_the_fly
    FoodTrackingMode.BOTH -> R.string.setup_food_mode_both
}

@StringRes
private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.LIGHT -> R.string.you_mode_light
    ThemeMode.DARK -> R.string.you_mode_dark
    ThemeMode.SYSTEM -> R.string.you_mode_system
}

class BackupActions(val onSave: () -> Unit, val onLoad: () -> Unit)
