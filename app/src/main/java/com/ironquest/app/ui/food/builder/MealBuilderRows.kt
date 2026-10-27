package com.ironquest.app.ui.food.builder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.food.FoodAmount
import com.ironquest.app.domain.food.FoodChoice
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.ui.food.amountText
import com.ironquest.app.ui.food.macroLine
import com.ironquest.app.ui.food.wholeText
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme

/** "234 kcal · P 20 · C 1 · F 16" */
@Composable
internal fun macroPreview(macros: Macros): String {
    val locale = currentLocale()
    return stringResource(
        R.string.food_builder_macro_preview,
        macros.effectiveKcal.wholeText(locale),
        macros.proteinG.amountText(locale),
        macros.carbsG.amountText(locale),
        macros.fatG.amountText(locale),
    )
}

@Composable
private fun MealLogItem.amountLine(): String = stringResource(R.string.food_builder_item_line, amount.amountText(currentLocale()), unit.label())

/** One food of the meal being built: name, "3 pcs", kcal, and a remove button. */
@Composable
internal fun BuilderItemRow(item: MealLogItem, onEdit: () -> Unit, onRemove: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    RuledRow(onClick = onEdit) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(item.name, style = type.bodyStrong, color = colors.ink)
            Text(item.amountLine(), style = type.secondary, color = colors.muted)
        }
        Text(stringResource(R.string.food_amount_kcal, item.macros.effectiveKcal.wholeText(currentLocale())), style = type.secondary, color = colors.muted)
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.food_builder_remove_cd, item.name), tint = colors.muted)
        }
    }
}

/** The amount of the picked food, inline under the search field. */
@Composable
internal fun PendingAmountRow(
    pending: PendingFood,
    invalid: Boolean,
    onAmountChange: (String) -> Unit,
    onUnitChange: (FoodUnit) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    unitsModifier: Modifier = Modifier,
) {
    val amount = FoodAmount.parse(pending.amountText, pending.unit)
    val per100g = pending.choice?.per100g
    val preview = when {
        amount == null -> null
        per100g != null -> FoodAmount.macrosFor(per100g, amount, pending.unit)
        else -> pending.snapshot?.let { snapshot -> if (snapshot.unit == pending.unit) snapshot.withAmount(amount).macros else null }
    }
    AmountRow(
        name = pending.choice?.name ?: pending.snapshot?.name.orEmpty(),
        amountText = pending.amountText,
        unit = pending.unit,
        units = pending.units,
        preview = preview,
        invalid = invalid,
        onAmountChange = onAmountChange,
        onUnitChange = onUnitChange,
        onConfirm = onConfirm,
        onCancel = onCancel,
        unitsModifier = unitsModifier,
    )
}

/** A search result that is one of your library meals. */
@Composable
internal fun MealResultRow(meal: Meal, onClick: () -> Unit) {
    val locale = currentLocale()
    ResultRow(
        name = meal.name,
        detail = stringResource(R.string.food_day_quick_meal_line, macroLine(meal.macros), stringResource(R.string.food_amount_kcal, meal.macros.effectiveKcal.wholeText(locale))),
        onClick = onClick,
    )
}

/** A search result that is a single food (per 100 g). */
@Composable
internal fun FoodResultRow(choice: FoodChoice, onClick: () -> Unit) {
    val per100g = stringResource(R.string.food_builder_per_100g, choice.per100g.effectiveKcal.wholeText(currentLocale()))
    ResultRow(name = choice.name, detail = choice.detail ?: per100g, onClick = onClick)
}

@Composable
internal fun RecentMealRow(log: MealLogEntry, onClick: () -> Unit) {
    val foods = joinedText(log.items.take(RECENT_FOODS_SHOWN).map { UiText.Verbatim(it.name) }, R.string.common_list_separator).resolve()
    val kcal = stringResource(R.string.food_amount_kcal, log.eaten.effectiveKcal.wholeText(currentLocale()))
    ResultRow(name = stringResource(R.string.food_day_quick_meal_line, log.mealName, foods), detail = kcal, onClick = onClick)
}

@Composable
internal fun RecentFoodRow(item: MealLogItem, onClick: () -> Unit) {
    ResultRow(name = item.name, detail = item.amountLine(), onClick = onClick)
}

@Composable
private fun ResultRow(name: String, detail: String, onClick: () -> Unit) {
    RuledRow(onClick = onClick) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(name, style = IronTheme.type.body, color = IronTheme.colors.ink)
            Text(detail, style = IronTheme.type.secondary, color = IronTheme.colors.muted)
        }
    }
}

private const val RECENT_FOODS_SHOWN = 3
