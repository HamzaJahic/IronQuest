package com.ironquest.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.food.builder.CustomFoodDialog
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.HeaderAction
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.ScreenTop
import com.ironquest.app.ui.kit.ToggleChip
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.ListTreatment

@Composable
fun MealLibraryScreen(
    onBack: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    onNewMeal: () -> Unit,
    onImport: () -> Unit,
    /** One-shot message handed over by the screen that popped back to this one (e.g. "Meal deleted"). */
    message: String? = null,
    onMessageShown: () -> Unit = {},
    viewModel: MealLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            onMessageShown()
            snackbar.showSnackbar(message)
        }
    }
    MealLibraryContent(
        state, snackbar, onBack, onOpenMeal, onNewMeal, onImport,
        viewModel::onQueryChange, viewModel::onTagClick, viewModel::onArchivedClick, viewModel::onYourFoodsClick,
        viewModel::saveFood, viewModel::deleteFood,
    )
    if (!state.isLoading) OnboardingGate(OnboardingScreen.MEALS, emptySet())
}

@Composable
private fun MealLibraryContent(
    state: MealLibraryUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    onNewMeal: () -> Unit,
    onImport: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onArchivedClick: () -> Unit,
    onYourFoodsClick: () -> Unit,
    onSaveFood: (UserFoodDraft) -> Unit,
    onDeleteFood: (Long) -> Unit,
) {
    var editingFoodId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val colors = IronTheme.colors
    val tiles = IronTheme.style.listTreatment == ListTreatment.TILES
    Scaffold(
        containerColor = colors.paper,
        topBar = {
            ScreenTop(stringResource(R.string.food_header_meals), onBack) {
                HeaderAction(stringResource(R.string.common_import), onImport, modifier = Modifier.onboardingAnchor(OnboardingTarget.MEALS_IMPORT))
                HeaderAction(stringResource(R.string.common_new), onNewMeal)
            }
        },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            IronTextField(stringResource(R.string.food_picker_search_hint), state.query, onQueryChange, modifier = Modifier.padding(bottom = 8.dp).onboardingAnchor(OnboardingTarget.MEALS_SEARCH))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ToggleChip(stringResource(R.string.food_meal_archived_label), state.showArchived, onArchivedClick, Modifier.onboardingAnchor(OnboardingTarget.MEALS_ARCHIVED)) }
                item { ToggleChip(stringResource(R.string.food_your_foods_label), state.showYourFoods, onYourFoodsClick) }
                if (!state.showYourFoods) items(state.allTags) { tag -> ToggleChip(tag, tag == state.selectedTag, { onTagClick(tag) }) }
            }
            if (state.showYourFoods) {
                if (!state.isLoading && state.userFoods.isEmpty()) {
                    Text(
                        stringResource(if (state.query.isBlank()) R.string.food_your_foods_empty else R.string.food_your_foods_no_match),
                        style = IronTheme.type.body,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                    item { YourFoodsSection(state.userFoods, onOpen = { food -> editingFoodId = food.id }) }
                }
                return@Column
            }
            if (!state.isLoading && state.meals.isEmpty()) {
                val noFilters = state.query.isBlank() && state.selectedTag == null
                Text(
                    stringResource(
                        when {
                            state.showArchived && noFilters -> R.string.food_library_empty_archived
                            noFilters -> R.string.food_library_empty
                            else -> R.string.food_picker_no_match
                        },
                    ),
                    style = IronTheme.type.body,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (tiles) 8.dp else 0.dp),
            ) {
                items(state.meals, key = { it.id }) { meal -> MealRow(meal) { onOpenMeal(meal.id) } }
            }
        }
    }
    val editing = state.userFoods.firstOrNull { it.id == editingFoodId }
    if (editing != null) {
        CustomFoodDialog(
            initialName = editing.name,
            initial = editing,
            onSave = { draft ->
                editingFoodId = null
                onSaveFood(draft)
            },
            onDelete = { confirmDeleteId = editing.id },
            onDismiss = { editingFoodId = null },
        )
    }
    val deleting = state.userFoods.firstOrNull { it.id == confirmDeleteId }
    if (deleting != null) {
        IronDialog(
            title = stringResource(R.string.food_your_foods_delete_title, deleting.name),
            text = stringResource(R.string.food_your_foods_delete_body),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                confirmDeleteId = null
                editingFoodId = null
                onDeleteFood(deleting.id)
            },
            onDismiss = { confirmDeleteId = null },
        )
    }
}

/** Soft draws each meal as its own tile; Diary/Bold as ruled rows. */
@Composable
private fun MealRow(meal: Meal, onClick: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val dot = stringResource(R.string.common_dot_separator)
    val macros = listOf(macroLine(meal.macros), stringResource(R.string.food_amount_kcal, meal.macros.effectiveKcal.wholeText(currentLocale())))
    RowGroup {
        RuledRow(onClick = onClick) {
            Column(Modifier.weight(1f)) {
                Text(meal.name, style = type.body, color = colors.ink)
                Text(
                    listOfNotNull(
                        macros.joinToString(dot),
                        meal.tags.takeIf { it.isNotEmpty() }?.joinToString(dot),
                    ).joinToString(stringResource(R.string.food_library_row_separator)),
                    style = type.secondary,
                    color = colors.muted,
                )
            }
            Text("›", style = type.body, color = colors.muted)
        }
    }
}
