package com.ironquest.app.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.AddLink
import com.ironquest.app.ui.kit.DateStepper
import com.ironquest.app.ui.kit.HeaderAction
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.SegmentedControl
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.you.labelRes
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

@Composable
fun FoodScreen(
    contentPadding: PaddingValues,
    onOpenMeals: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    /** Opens the meal builder for a day; a log id edits that quick meal. */
    onOpenBuilder: (LocalDate, Long?) -> Unit,
    onOpenFoodSettings: () -> Unit,
    /** Changes each time a notification tap opens Food; resets the screen to the Day view. */
    dayRequest: Int = 0,
    /** One-shot message handed over by a screen that popped back here (e.g. "Meal deleted"). */
    message: String? = null,
    onMessageShown: () -> Unit = {},
    viewModel: FoodDayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            // Level-ups are a one-line snackbar now (no dialogs).
            snackbar.showSnackbar(event.text().resolve(resources))
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            onMessageShown()
            snackbar.showSnackbar(message)
        }
    }
    // The VM outlives midnight (saved bottom-tab state); jump to the new "today" on resume.
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(FoodDayAction.RefreshToday)
        onPauseOrDispose { }
    }

    // Nav callbacks change when the tab is restored (new back-stack entry, same composition);
    // read them through rememberUpdatedState so the remembered actions never call stale ones.
    val openMeals by rememberUpdatedState(onOpenMeals)
    val openPlans by rememberUpdatedState(onOpenPlans)
    val openMeal by rememberUpdatedState(onOpenMeal)
    val openBuilder by rememberUpdatedState(onOpenBuilder)
    val openFoodSettings by rememberUpdatedState(onOpenFoodSettings)
    val permissionGate = rememberReminderPermissionGate()
    val onAction: (FoodDayAction) -> Unit = remember(viewModel, permissionGate) {
        { action ->
            if (action is FoodDayAction.SaveSupplement) {
                permissionGate(action.reminderTimeMin != null) { viewModel.onAction(action) }
            } else {
                viewModel.onAction(action)
            }
        }
    }
    val openMealsAction: () -> Unit = remember { { openMeals() } }
    val openPlansAction: () -> Unit = remember { { openPlans() } }
    val openMealAction: (Long) -> Unit = remember { { id -> openMeal(id) } }
    val openBuilderAction: (LocalDate, Long?) -> Unit = remember { { date, logId -> openBuilder(date, logId) } }
    val openFoodSettingsAction: () -> Unit = remember { { openFoodSettings() } }
    // Pure UI state; rememberSaveable survives tab switches through the nav saveState/restoreState.
    var mode by rememberSaveable { mutableStateOf(FoodMode.DAY) }
    // A notification tap lands on the Day view even if Week/Month was showing (and was saved).
    var seenDayRequest by rememberSaveable { mutableIntStateOf(dayRequest) }
    LaunchedEffect(dayRequest) {
        if (dayRequest != seenDayRequest) {
            seenDayRequest = dayRequest
            mode = FoodMode.DAY
        }
    }
    // Tapping a Week bar or Month day opens that date in the Day view.
    val openDay: (LocalDate) -> Unit = remember(viewModel) {
        { date ->
            viewModel.onAction(FoodDayAction.ShowDate(date))
            mode = FoodMode.DAY
        }
    }
    val top: @Composable () -> Unit = remember(openMealsAction, openPlansAction, mode, state.mode) {
        @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Header(openMealsAction, openPlansAction, state.mode.showsPlan)
                val modeLabels = FoodMode.entries.associateWith { stringResource(it.labelRes) }
                SegmentedControl(
                    options = FoodMode.entries,
                    selected = mode,
                    label = { modeLabels.getValue(it) },
                    onSelect = { mode = it },
                    modifier = Modifier.fillMaxWidth(0.66f).onboardingAnchor(OnboardingTarget.FOOD_MODES),
                )
            }
        }
    }
    var dayDialogOpen by remember { mutableStateOf(false) }
    val onDayDialogOpenChange: (Boolean) -> Unit = remember { { open -> dayDialogOpen = open } }
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            FoodMode.DAY -> FoodDayContent(state, contentPadding, onAction, openPlansAction, openMealAction, openBuilderAction, openFoodSettingsAction, top, onDayDialogOpenChange)
            FoodMode.WEEK -> FoodWeekPane(contentPadding, top, onOpenDay = openDay)
            FoodMode.MONTH -> FoodMonthPane(contentPadding, top, onOpenDay = openDay)
        }
        IronSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()))
    }
    when (mode) {
        FoodMode.DAY -> if (!state.isLoading) {
            OnboardingGate(OnboardingScreen.FOOD_DAY, state.onboardingFacts(dayDialogOpen))
        }
        FoodMode.WEEK -> OnboardingGate(OnboardingScreen.FOOD_WEEK, emptySet())
        FoodMode.MONTH -> OnboardingGate(OnboardingScreen.FOOD_MONTH, emptySet())
    }
}

@Composable
private fun FoodDayContent(
    state: FoodDayUiState,
    contentPadding: PaddingValues,
    onAction: (FoodDayAction) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    onOpenBuilder: (LocalDate, Long?) -> Unit,
    onOpenFoodSettings: () -> Unit,
    top: @Composable () -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    var portionFor by remember { mutableStateOf<MealLogEntry?>(null) }
    var pickingMeal by remember { mutableStateOf(false) }
    var offPlanMeal by remember { mutableStateOf<Meal?>(null) }
    var deletingSupplement by remember { mutableStateOf<SupplementStatus?>(null) }
    var editingSupplement by remember { mutableStateOf<SupplementStatus?>(null) }
    var addingSupplement by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var settingTargets by remember { mutableStateOf(false) }
    // Tips never cover the screen's own dialogs and sheets.
    val dialogOpen = portionFor != null ||
        pickingMeal ||
        offPlanMeal != null ||
        deletingSupplement != null ||
        editingSupplement != null ||
        addingSupplement ||
        pickingDate ||
        settingTargets
    LaunchedEffect(dialogOpen) { onDialogOpenChange(dialogOpen) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
        ),
    ) {
        item { DateNavigator(state, onAction, pickingDate, onPickingChange = { picking -> pickingDate = picking }) }
        item { top() }
        if (state.isLoading) return@LazyColumn
        if (!state.isToday) {
            item {
                Text(
                    stringResource(R.string.food_day_showing_today_plan),
                    style = type.secondary,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { Targets(state, onOpenPlans, onSetTargets = { settingTargets = true }) }

        item { SectionHeader(stringResource(R.string.food_day_meals_header)) }
        if (state.planName == null && state.mode.showsPlan) {
            item {
                Text(
                    stringResource(R.string.food_day_no_plan),
                    style = type.secondary,
                    color = colors.muted,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(R.string.food_day_open_plans_cd), role = Role.Button, onClick = onOpenPlans)
                        .padding(vertical = 8.dp),
                )
            }
        }
        item {
            RowGroup(Modifier.onboardingAnchor(OnboardingTarget.FOOD_CHECKLIST)) {
                state.slots.forEach { row ->
                    // not UI text: lazy list key
                    key("slot-${row.slot.id}") {
                        SlotRow(row, onToggle = { onAction(FoodDayAction.ToggleSlot(row.slot.id)) }, onPortion = { portionFor = it }, onOpenMeal = onOpenMeal)
                    }
                }
                state.offPlan.forEach { log ->
                    // not UI text: lazy list key
                    key("log-${log.id}") {
                        LogRow(log, onPortion = { portionFor = log }, onRemove = { onAction(FoodDayAction.RemoveLog(log.id)) })
                    }
                }
                state.quickMeals.forEach { log ->
                    // not UI text: lazy list key
                    key("quick-${log.id}") {
                        QuickMealRow(
                            log,
                            onOpen = { onOpenBuilder(state.date, log.id) },
                            onRemove = { onAction(FoodDayAction.RemoveLog(log.id)) },
                        )
                    }
                }
            }
        }
        item {
            // The builder also picks library meals, so it replaces the picker link when it is on.
            if (state.mode.showsBuilder) {
                AddLink(
                    stringResource(R.string.food_day_log_food_link),
                    onClick = { onOpenBuilder(state.date, null) },
                    Modifier.onboardingAnchor(OnboardingTarget.FOOD_LOG_FOOD),
                )
            } else {
                AddLink(stringResource(R.string.food_day_log_other_link), onClick = { pickingMeal = true }, Modifier.onboardingAnchor(OnboardingTarget.FOOD_LOG_OTHER))
            }
        }

        if (state.earlier.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.food_day_earlier_plan_header)) }
            // Read-only apart from removal: they belong to a plan that's no longer active.
            item {
                RowGroup {
                    state.earlier.forEach { log ->
                        key("log-${log.id}") { LogRow(log, onPortion = null, onRemove = { onAction(FoodDayAction.RemoveLog(log.id)) }) } // not UI text: lazy list key
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.food_water)) }
        item { WaterRow(state, onAction) }

        item { SectionHeader(stringResource(R.string.food_day_supplements_header)) }
        item {
            RowGroup(Modifier.onboardingAnchor(OnboardingTarget.FOOD_SUPPLEMENTS)) {
                state.supplements.forEach { supplement ->
                    // not UI text: lazy list key
                    key("sup-${supplement.id}") {
                        SupplementRow(supplement, onToggle = { onAction(FoodDayAction.ToggleSupplement(supplement.id)) }, onEdit = { editingSupplement = supplement })
                    }
                }
            }
        }
        item { AddLink(stringResource(R.string.food_day_add_supplement_link), onClick = { addingSupplement = true }) }
        item {
            TextAction(
                stringResource(R.string.food_day_tracking_link, stringResource(state.mode.labelRes())),
                onClick = onOpenFoodSettings,
                modifier = Modifier.padding(top = 16.dp).onboardingAnchor(OnboardingTarget.FOOD_TRACKING_LINK),
                tone = ActionTone.MUTED,
            )
        }
    }

    if (settingTargets) {
        DailyTargetsDialog(
            initial = MacroTargets(),
            onSave = { targets ->
                onAction(FoodDayAction.SetOwnTargets(targets))
                settingTargets = false
            },
            onDismiss = { settingTargets = false },
        )
    }
    portionFor?.let { log ->
        PortionDialog(
            initial = log.portion,
            onConfirm = { text ->
                onAction(FoodDayAction.SetPortion(log.id, text))
                portionFor = null
            },
            onDismiss = { portionFor = null },
        )
    }
    if (pickingMeal) {
        MealPickerSheet(
            meals = state.meals,
            onPick = { meal ->
                offPlanMeal = meal
                pickingMeal = false
            },
            onDismiss = { pickingMeal = false },
        )
    }
    offPlanMeal?.let { meal ->
        PortionDialog(
            initial = 1.0,
            onConfirm = { text ->
                onAction(FoodDayAction.LogOffPlan(meal.id, text))
                offPlanMeal = null
            },
            onDismiss = { offPlanMeal = null },
        )
    }
    deletingSupplement?.let { supplement ->
        IronDialog(
            title = stringResource(R.string.food_day_delete_supplement_title, supplement.name),
            text = stringResource(R.string.food_day_delete_supplement_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                onAction(FoodDayAction.DeleteSupplement(supplement.id))
                deletingSupplement = null
            },
            onDismiss = { deletingSupplement = null },
            destructive = true,
        )
    }
    if (addingSupplement || editingSupplement != null) {
        val editing = editingSupplement
        SupplementDialog(
            initial = editing,
            onSave = { name, dose, reminder ->
                onAction(FoodDayAction.SaveSupplement(editing?.id, name, dose, reminder))
                addingSupplement = false
                editingSupplement = null
            },
            onDelete = editing?.let { supplement ->
                {
                    deletingSupplement = supplement
                    editingSupplement = null
                }
            },
            onDismiss = {
                addingSupplement = false
                editingSupplement = null
            },
        )
    }
}

/** Title "Food" with Meals / Plans links on the right. */
@Composable
private fun Header(onOpenMeals: () -> Unit, onOpenPlans: () -> Unit, showsPlans: Boolean) {
    PageTitle(stringResource(R.string.food_title), trailing = {
        Row(Modifier.onboardingAnchor(OnboardingTarget.FOOD_LINKS)) {
            HeaderAction(stringResource(R.string.food_header_meals), onOpenMeals)
            if (showsPlans) HeaderAction(stringResource(R.string.food_header_plans), onOpenPlans)
        }
    })
}

@Composable
private fun DateNavigator(state: FoodDayUiState, onAction: (FoodDayAction) -> Unit, picking: Boolean, onPickingChange: (Boolean) -> Unit) {
    val label = Formats.date(state.date, stringResource(R.string.food_date_long_pattern), currentLocale())
    DateStepper(
        label = if (state.isToday) stringResource(R.string.food_day_today_label, label) else label,
        canGoForward = state.canGoForward,
        onPrevious = { onAction(FoodDayAction.PreviousDay) },
        onNext = { onAction(FoodDayAction.NextDay) },
        unit = stringResource(R.string.common_period_day),
        onLabelClick = { onPickingChange(true) },
        modifier = Modifier.onboardingAnchor(OnboardingTarget.FOOD_DATE),
    )
    if (picking) {
        DayPickerDialog(
            current = state.date,
            today = state.today,
            onPick = {
                onPickingChange(false)
                onAction(FoodDayAction.ShowDate(it))
            },
            onDismiss = { onPickingChange(false) },
        )
    }
}
