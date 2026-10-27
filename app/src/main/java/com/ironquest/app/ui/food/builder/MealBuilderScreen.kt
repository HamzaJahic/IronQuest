package com.ironquest.app.ui.food.builder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.food.QuickMealName
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.food.PortionDialog
import com.ironquest.app.ui.kit.AddLink
import com.ironquest.app.ui.kit.DiscardChangesDialog
import com.ironquest.app.ui.kit.HeaderAction
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.ScreenTop
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.kit.ToggleChip
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MealBuilderScreen(
    onBack: () -> Unit,
    onFinished: (NutritionAward) -> Unit,
    viewModel: MealBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MealBuilderEvent.Finished -> currentOnFinished(event.award)
                is MealBuilderEvent.Message -> snackbar.showSnackbar(event.text.resolve(resources))
            }
        }
    }
    val onAction = remember(viewModel) { viewModel::onAction }
    // The name the new food starts with; non-null while the dialog is open.
    var creatingFood by rememberSaveable { mutableStateOf<String?>(null) }
    var contentDialogOpen by remember { mutableStateOf(false) }
    if (!state.isLoading) OnboardingGate(OnboardingScreen.MEAL_BUILDER, state.onboardingFacts(contentDialogOpen || creatingFood != null))
    MealBuilderContent(
        state = state,
        snackbar = snackbar,
        onAction = onAction,
        onBack = onBack,
        onCreateFood = { name -> creatingFood = name },
        onDialogOpenChange = { open -> contentDialogOpen = open },
    )
    creatingFood?.let { name ->
        CustomFoodDialog(
            initialName = name,
            initial = null,
            onSave = { draft ->
                creatingFood = null
                onAction(MealBuilderAction.CreateFood(draft))
            },
            onDelete = null,
            onDismiss = { creatingFood = null },
        )
    }
}

@Composable
internal fun MealBuilderContent(
    state: MealBuilderUiState,
    snackbar: SnackbarHostState,
    onAction: (MealBuilderAction) -> Unit,
    onBack: () -> Unit,
    onCreateFood: (String) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    val dialogOpen = confirmDiscard || editingName
    LaunchedEffect(dialogOpen) { onDialogOpenChange(dialogOpen) }
    val guardBack = state.hasChanges && !state.isSaving
    BackHandler(enabled = guardBack) { confirmDiscard = true }
    val handleBack = { if (guardBack) confirmDiscard = true else onBack() }
    val choiceLabel = state.nameChoice?.let { stringResource(it.labelRes) }
    val fallbackName = stringResource(QuickMealName.SNACK.labelRes)
    val resolvedName = choiceLabel ?: state.customName.trim().ifEmpty { fallbackName }
    Scaffold(
        containerColor = IronTheme.colors.paper,
        topBar = {
            ScreenTop(
                title = stringResource(if (state.logId == null) R.string.food_builder_title_new else R.string.food_builder_title_edit),
                onBack = handleBack,
                actions = {
                    if (state.logId != null) {
                        HeaderAction(stringResource(R.string.food_builder_save_as_meal), { onAction(MealBuilderAction.SaveAsMeal(resolvedName, recipeText = "")) }, enabled = state.items.isNotEmpty())
                    }
                    HeaderAction(
                        stringResource(R.string.food_builder_done),
                        { onAction(MealBuilderAction.Done(resolvedName)) },
                        enabled = state.canFinish,
                        modifier = Modifier.onboardingAnchor(OnboardingTarget.BUILDER_DONE),
                    )
                },
            )
        },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        BuilderList(state, onAction, onCreateFood, onEditName = { editingName = true }, padding)
    }
    state.pendingMeal?.let {
        PortionDialog(
            initial = 1.0,
            onConfirm = { text -> onAction(MealBuilderAction.LogMealPortion(text)) },
            onDismiss = { onAction(MealBuilderAction.CancelMealPortion) },
        )
    }
    if (editingName) {
        CustomNameDialog(
            initial = state.customName,
            onConfirm = { text ->
                editingName = false
                onAction(MealBuilderAction.CustomName(text))
            },
            onDismiss = { editingName = false },
        )
    }
    if (confirmDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                confirmDiscard = false
                onBack()
            },
            onKeepEditing = { confirmDiscard = false },
        )
    }
}

@Composable
private fun BuilderList(
    state: MealBuilderUiState,
    onAction: (MealBuilderAction) -> Unit,
    onCreateFood: (String) -> Unit,
    onEditName: () -> Unit,
    padding: PaddingValues,
) {
    val colors = IronTheme.colors
    val nameDescription = stringResource(
        R.string.food_builder_name_cd,
        state.nameChoice?.let { stringResource(it.labelRes) } ?: state.customName,
    )
    LazyColumn(
        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = nameDescription },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickMealName.entries.forEach { name ->
                    ToggleChip(stringResource(name.labelRes), selected = state.nameChoice == name, onClick = { onAction(MealBuilderAction.ChooseName(name)) })
                }
                ToggleChip(
                    if (state.nameChoice == null && state.customName.isNotBlank()) state.customName else stringResource(R.string.food_builder_name_custom),
                    selected = state.nameChoice == null,
                    onClick = onEditName,
                )
            }
        }
        item {
            if (state.items.isEmpty()) {
                Text(stringResource(R.string.food_builder_empty), style = IronTheme.type.secondary, color = colors.muted, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                RowGroup(Modifier.padding(top = 8.dp)) {
                    state.items.forEach { row ->
                        BuilderItemRow(row.item, onEdit = { onAction(MealBuilderAction.EditItem(row.key)) }, onRemove = { onAction(MealBuilderAction.RemoveItem(row.key)) })
                    }
                }
                Text(macroPreview(state.total), style = IronTheme.type.secondary, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            IronTextField(
                label = stringResource(R.string.food_builder_search_label),
                value = state.query,
                onValueChange = { onAction(MealBuilderAction.QueryChanged(it)) },
                placeholder = stringResource(R.string.food_builder_search_placeholder),
                capitalization = KeyboardCapitalization.Sentences,
                modifier = Modifier.padding(top = 16.dp).onboardingAnchor(OnboardingTarget.BUILDER_SEARCH),
            )
        }
        state.pending?.let { pending ->
            item {
                PendingAmountRow(
                    pending = pending,
                    invalid = state.pendingInvalid,
                    onAmountChange = { onAction(MealBuilderAction.PendingAmountChanged(it)) },
                    onUnitChange = { onAction(MealBuilderAction.PendingUnitChanged(it)) },
                    onConfirm = { onAction(MealBuilderAction.ConfirmPending) },
                    onCancel = { onAction(MealBuilderAction.CancelPending) },
                    unitsModifier = Modifier.onboardingAnchor(OnboardingTarget.BUILDER_UNITS),
                )
            }
        }
        val search = state.search
        if (search is BuilderSearch.Local) {
            if (search.results.meals.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.food_builder_your_meals)) }
                // not UI text: lazy list key
                items(search.results.meals, key = { meal -> "meal-${meal.id}" }) { meal ->
                    MealResultRow(meal, onClick = { onAction(MealBuilderAction.PickMeal(meal)) })
                }
            }
            if (search.results.foods.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.food_builder_foods)) }
                // not UI text: lazy list key
                items(search.results.foods, key = { choice -> "food-${choice.key}" }) { choice ->
                    FoodResultRow(choice, onClick = { onAction(MealBuilderAction.PickFood(choice)) })
                }
            }
            val online = search.online
            when (online) {
                OnlineState.NotAsked -> item {
                    TextAction(stringResource(R.string.food_builder_search_online), onClick = { onAction(MealBuilderAction.SearchOnline) })
                }
                OnlineState.Loading -> item {
                    Text(stringResource(R.string.food_builder_online_loading), style = IronTheme.type.secondary, color = colors.muted, modifier = Modifier.padding(top = 16.dp))
                }
                is OnlineState.Failed -> item {
                    val message = if (online.error == FoodSearchError.RATE_LIMITED) R.string.food_builder_online_busy else R.string.food_builder_online_unavailable
                    Text(stringResource(message), style = IronTheme.type.secondary, color = colors.muted, modifier = Modifier.padding(top = 16.dp))
                }
                is OnlineState.Results -> if (online.foods.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.food_builder_online)) }
                    // not UI text: lazy list key
                    items(online.foods, key = { choice -> "online-${choice.key}" }) { choice ->
                        FoodResultRow(choice, onClick = { onAction(MealBuilderAction.PickFood(choice)) })
                    }
                    item { Text(stringResource(R.string.food_builder_online_attribution), style = IronTheme.type.secondary, color = colors.muted, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            // Nothing found only once the online side has nothing to add either (it is loading, or it has results).
            val onlineHasResults = online is OnlineState.Results && online.foods.isNotEmpty()
            if (search.results.isEmpty && online != OnlineState.Loading && !onlineHasResults) {
                item { Text(stringResource(R.string.food_builder_no_matches), style = IronTheme.type.secondary, color = colors.muted, modifier = Modifier.padding(top = 16.dp)) }
            }
            item { AddLink(
                    stringResource(R.string.food_builder_create, state.query.trim()),
                    onClick = { onCreateFood(state.query.trim()) },
                    Modifier.onboardingAnchor(OnboardingTarget.BUILDER_CREATE),
                ) }
        }
        if (state.showsRecents) {
            if (state.recentMeals.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.food_builder_recent_meals), Modifier.onboardingAnchor(OnboardingTarget.BUILDER_RECENTS)) }
                // not UI text: lazy list key
                items(state.recentMeals, key = { log -> "recent-meal-${log.id}" }) { log ->
                    RecentMealRow(log, onClick = { onAction(MealBuilderAction.PickRecentMeal(log)) })
                }
            }
            if (state.recentFoods.isNotEmpty()) {
                item {
                    // The meals header carries the anchor when there are recent meals.
                    val anchor = if (state.recentMeals.isEmpty()) Modifier.onboardingAnchor(OnboardingTarget.BUILDER_RECENTS) else Modifier
                    SectionHeader(stringResource(R.string.food_builder_recent_foods), anchor)
                }
                // No key: the same food can appear twice with equal amounts.
                items(state.recentFoods) { item ->
                    RecentFoodRow(item, onClick = { onAction(MealBuilderAction.PickRecentFood(item)) })
                }
            }
        }
    }
}

@Composable
private fun CustomNameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    IronDialog(
        title = stringResource(R.string.food_builder_name_dialog_title),
        confirmLabel = stringResource(R.string.common_save),
        onConfirm = { onConfirm(text.trim()) },
        confirmEnabled = text.isNotBlank(),
        onDismiss = onDismiss,
    ) {
        IronTextField(stringResource(R.string.food_builder_name_dialog_title), text, { text = it }, capitalization = KeyboardCapitalization.Sentences)
    }
}
