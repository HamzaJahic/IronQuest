package com.ironquest.app.ui.food

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.food.FoodAmount
import com.ironquest.app.domain.food.FoodChoice
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.food.builder.AmountRow
import com.ironquest.app.ui.food.builder.FoodResultRow
import com.ironquest.app.ui.food.builder.OnlineState
import com.ironquest.app.ui.food.builder.label
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.IronSheet
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.launch

/** The detail line under "Online search unavailable." for each kind of search failure. */
@StringRes
fun lookupErrorRes(error: FoodSearchError): Int = when (error) {
    FoodSearchError.OFFLINE -> R.string.food_calc_error_offline
    FoodSearchError.RATE_LIMITED -> R.string.food_calc_error_rate_limited
    FoodSearchError.SERVER -> R.string.food_calc_error_server
    FoodSearchError.BAD_RESPONSE -> R.string.food_calc_error_bad_response
}

/** Stateful wrapper. [onApply] receives the totals; the caller writes them into its form. The sheet then hides itself and calls [onDismiss]. */
@Composable
fun IngredientCalculatorSheet(
    onApply: (Macros) -> Unit,
    onDismiss: () -> Unit,
    viewModel: IngredientCalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    IngredientCalculatorContent(
        state = state,
        onDismiss = {
            viewModel.cancelPending()
            onDismiss()
        },
        onQueryChange = viewModel::onQueryChange,
        onSearchOnline = viewModel::searchOnline,
        onPick = viewModel::pick,
        onPendingAmountChange = viewModel::onPendingAmountChange,
        onPendingUnitChange = viewModel::onPendingUnitChange,
        onConfirmPending = viewModel::confirmPending,
        onCancelPending = viewModel::cancelPending,
        onRowAmountChange = viewModel::onRowAmountChange,
        onRemoveRow = viewModel::removeRow,
        onClear = viewModel::clearRows,
        // The content hides the sheet after this and then calls onDismiss.
        onApply = {
            viewModel.cancelPending()
            state.total?.let(onApply)
        },
    )
    OnboardingGate(OnboardingScreen.INGREDIENTS, if (state.pending != null) setOf(OnboardingFact.BLOCKED) else emptySet())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientCalculatorContent(
    state: IngredientCalculatorUiState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchOnline: () -> Unit,
    onPick: (FoodChoice) -> Unit,
    onPendingAmountChange: (String) -> Unit,
    onPendingUnitChange: (FoodUnit) -> Unit,
    onConfirmPending: () -> Unit,
    onCancelPending: () -> Unit,
    onRowAmountChange: (Long, String) -> Unit,
    onRemoveRow: (Long) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    IronSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.food_calc_title), style = type.sectionHead, color = colors.ink)
            Text(
                stringResource(R.string.food_calc_intro),
                style = type.secondary,
                color = colors.muted,
            )
            IronTextField(stringResource(R.string.food_calc_search_label), state.query, onQueryChange, modifier = Modifier.onboardingAnchor(OnboardingTarget.INGREDIENTS_SEARCH), placeholder = stringResource(R.string.food_calc_search_placeholder))
            state.pickError?.let { error ->
                Text(error.resolve(), style = type.secondary, color = colors.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            val pending = state.pending
            if (pending != null) {
                AmountRow(
                    name = pending.food.name,
                    amountText = pending.amountText,
                    unit = pending.unit,
                    units = pending.food.units,
                    preview = pending.amount?.let { amount -> FoodAmount.macrosFor(pending.food.per100g, amount, pending.unit) },
                    invalid = state.pendingInvalid,
                    onAmountChange = onPendingAmountChange,
                    onUnitChange = onPendingUnitChange,
                    onConfirm = onConfirmPending,
                    onCancel = onCancelPending,
                )
            }
            SearchResults(state.search, onSearchOnline, onPick)

            if (state.rows.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.food_calc_ingredients_header), style = type.sectionHead, color = colors.ink, modifier = Modifier.weight(1f))
                    TextAction(stringResource(R.string.common_clear), onClear, tone = ActionTone.MUTED)
                }
                state.rows.forEach { row ->
                    key(row.key) {
                        IngredientRowItem(
                            row = row,
                            onAmountChange = { onRowAmountChange(row.key, it) },
                            onRemove = { onRemoveRow(row.key) },
                        )
                    }
                }
                TotalLine(state.total)
            }
            PrimaryButton(
                stringResource(R.string.food_calc_apply),
                // Write the totals now, animate the sheet away, then close it.
                onClick = {
                    onApply()
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                enabled = state.canApply,
                modifier = Modifier.padding(top = 8.dp).onboardingAnchor(OnboardingTarget.INGREDIENTS_APPLY),
            )
        }
    }
}

@Composable
private fun SearchResults(search: CalculatorSearch, onSearchOnline: () -> Unit, onPick: (FoodChoice) -> Unit) {
    val colors = IronTheme.colors
    when (search) {
        CalculatorSearch.Idle -> Hint(stringResource(R.string.food_calc_hint_idle))
        is CalculatorSearch.Local -> {
            if (search.foods.isNotEmpty()) {
                SectionHeader(stringResource(R.string.food_builder_foods))
                search.foods.forEach { food -> key(food.key) { FoodResultRow(food, onClick = { onPick(food) }) } }
            }
            val online = search.online
            when (online) {
                OnlineState.NotAsked -> TextAction(stringResource(R.string.food_builder_search_online), onSearchOnline)
                OnlineState.Loading -> {
                    val searchingCd = stringResource(R.string.food_calc_searching_cd)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = searchingCd
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = colors.ink,
                        trackColor = colors.line,
                    )
                }
                is OnlineState.Failed -> Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.food_calc_lookup_unavailable), style = IronTheme.type.body, color = colors.danger)
                    Text(stringResource(lookupErrorRes(online.error)), style = IronTheme.type.secondary, color = colors.muted)
                    TextAction(stringResource(R.string.food_calc_retry), onSearchOnline, strong = true)
                }
                is OnlineState.Results -> if (online.foods.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.food_builder_online))
                    online.foods.forEach { food -> key(food.key) { FoodResultRow(food, onClick = { onPick(food) }) } }
                    Text(stringResource(R.string.food_builder_online_attribution), style = IronTheme.type.secondary, color = colors.muted)
                }
            }
            val onlineHasResults = online is OnlineState.Results && online.foods.isNotEmpty()
            if (search.foods.isEmpty() && online != OnlineState.Loading && !onlineHasResults && online !is OnlineState.Failed) {
                Hint(stringResource(R.string.food_calc_no_matches))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = IronTheme.type.secondary,
        color = IronTheme.colors.muted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun IngredientRowItem(row: IngredientRow, onAmountChange: (String) -> Unit, onRemove: () -> Unit) {
    val macros = row.macros
    val locale = currentLocale()
    val amountCd = stringResource(R.string.food_calc_amount_cd, row.food.name)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                row.food.name,
                style = IronTheme.type.body,
                color = IronTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                macros?.let { stringResource(R.string.food_calc_row_macros, macroLine(it), it.fiberG.amountText(locale), it.effectiveKcal.wholeText(locale)) }
                    ?: stringResource(R.string.food_calc_row_invalid),
                style = IronTheme.type.secondary,
                color = if (macros == null) IronTheme.colors.danger else IronTheme.colors.muted,
            )
        }
        IronTextField(
            row.unit.label(),
            row.amountText,
            onAmountChange,
            modifier = Modifier.width(96.dp).semantics { contentDescription = amountCd },
            isError = macros == null,
            keyboardType = KeyboardType.Decimal,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.food_calc_remove_cd, row.food.name), tint = IronTheme.colors.muted)
        }
    }
}

@Composable
private fun TotalLine(total: Macros?) {
    val locale = currentLocale()
    Text(
        text = total?.let { stringResource(R.string.food_calc_total, macroLine(it), it.fiberG.amountText(locale), it.effectiveKcal.wholeText(locale)) }
            ?: stringResource(R.string.food_calc_total_invalid),
        style = IronTheme.type.bodyStrong,
        color = if (total != null) IronTheme.colors.ink else IronTheme.colors.danger,
    )
    if (total != null && IngredientCalculator.exceedsEditorLimits(total)) {
        Text(
            stringResource(R.string.food_calc_over_limit),
            style = IronTheme.type.secondary,
            color = IronTheme.colors.hold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
