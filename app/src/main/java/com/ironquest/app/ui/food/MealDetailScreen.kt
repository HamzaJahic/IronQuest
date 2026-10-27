package com.ironquest.app.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.kit.BackHeader
import com.ironquest.app.ui.kit.HeaderAction
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.SummaryCell
import com.ironquest.app.ui.kit.SummaryStrip
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MealDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: MealDetailViewModel = hiltViewModel(),
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
    MealDetailContent(state, snackbar, onBack, onEdit, viewModel::logToday, viewModel::restore)
    // Only a meal you can log gets the tip (not a missing or archived one).
    if (state.meal?.archived == false) OnboardingGate(OnboardingScreen.MEAL_DETAIL, emptySet())
}

@Composable
private fun MealDetailContent(
    state: MealDetailUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onLogToday: () -> Unit,
    onRestore: () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val meal = state.meal
    val uriHandler = LocalUriHandler.current
    val locale = currentLocale()
    val dot = stringResource(R.string.common_dot_separator)
    Scaffold(
        containerColor = colors.paper,
        topBar = {
            BackHeader(onBack) {
                if (meal != null) HeaderAction(stringResource(R.string.common_edit), { onEdit(meal.id) })
            }
        },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        if (meal == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (!state.isLoading) Text(stringResource(R.string.food_meal_detail_missing), style = type.body, color = colors.muted)
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            PageTitle(meal.name)
            if (meal.tags.isNotEmpty()) {
                Text(meal.tags.joinToString(dot), style = type.secondary, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
            }
            if (meal.archived) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(IronTheme.shapes.tile)
                        .background(colors.holdBg)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.food_meal_archived_label), style = type.bodyStrong, color = colors.hold)
                        Text(stringResource(R.string.food_meal_detail_archived_text), style = type.secondary, color = colors.hold)
                    }
                    TextAction(stringResource(R.string.food_meal_detail_restore), onRestore, strong = true)
                }
            }
            SummaryStrip(
                listOf(
                    SummaryCell(stringResource(R.string.food_macro_protein), stringResource(R.string.food_amount_g, meal.macros.proteinG.amountText(locale))),
                    SummaryCell(stringResource(R.string.food_macro_carbs), stringResource(R.string.food_amount_g, meal.macros.carbsG.amountText(locale))),
                    SummaryCell(stringResource(R.string.food_macro_fat), stringResource(R.string.food_amount_g, meal.macros.fatG.amountText(locale))),
                ),
                Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(
                    if (meal.macros.kcal == null) R.string.food_meal_detail_fiber_kcal_derived else R.string.food_meal_detail_fiber_kcal,
                    meal.macros.fiberG.amountText(locale),
                    meal.macros.effectiveKcal.wholeText(locale),
                ),
                style = type.secondary,
                color = colors.muted,
            )
            if (state.usages.isNotEmpty()) {
                SectionHeader(stringResource(R.string.food_meal_detail_used_in))
                RowGroup {
                    state.usages.forEach { usage ->
                        RuledRow {
                            Text(
                                listOfNotNull(usage.planName, usage.slotLabel, usage.timeOfDayMin?.let { formatTimeOfDay(it) }).joinToString(dot),
                                style = type.body,
                                color = colors.ink,
                            )
                        }
                    }
                }
            }
            // Optional sections: rendered only when filled in.
            meal.recipeText?.let { recipe ->
                SectionHeader(stringResource(R.string.food_meal_detail_recipe))
                Text(recipe, style = type.body, color = colors.ink)
            }
            meal.recipeUrl?.let { url ->
                SectionHeader(stringResource(R.string.food_meal_detail_recipe_link))
                Text(
                    url,
                    style = type.body.copy(textDecoration = TextDecoration.Underline),
                    color = colors.accent,
                    // openUri throws if no browser can handle the link; never crash for that.
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.food_meal_detail_open_link_cd), role = Role.Button) { runCatching { uriHandler.openUri(url) } },
                )
            }
            PrimaryButton(
                stringResource(R.string.food_meal_detail_log_today),
                onClick = onLogToday,
                enabled = !state.isLogging,
                modifier = Modifier.padding(top = 24.dp).onboardingAnchor(OnboardingTarget.MEAL_DETAIL_LOG),
            )
        }
    }
}
