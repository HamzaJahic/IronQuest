package com.ironquest.app.ui.food.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.food.FoodAmount
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.ui.food.wholeText
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.ChoiceChips
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.theme.IronTheme

/**
 * The amount of a picked food: name, amount field, unit chips (when there is a choice), a macro preview
 * ([preview] null while the amount is invalid) and Add / Cancel. Shared by the meal builder and the
 * ingredient calculator.
 * [unitsModifier] lets only the builder carry the onboarding anchor.
 */
@Composable
fun AmountRow(
    name: String,
    amountText: String,
    unit: FoodUnit,
    units: List<FoodUnit>,
    preview: Macros?,
    invalid: Boolean,
    onAmountChange: (String) -> Unit,
    onUnitChange: (FoodUnit) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    unitsModifier: Modifier = Modifier,
) {
    val colors = IronTheme.colors
    val maxLabel = stringResource(R.string.food_builder_item_line, FoodAmount.maxFor(unit).wholeText(currentLocale()), unit.label())
    val unitLabels = units.associateWith { it.label() }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, style = IronTheme.type.sectionHead, color = colors.ink)
        IronTextField(
            label = stringResource(R.string.food_builder_amount_label),
            value = amountText,
            onValueChange = onAmountChange,
            isError = invalid,
            errorText = stringResource(R.string.food_builder_amount_invalid, maxLabel),
            keyboardType = KeyboardType.Decimal,
        )
        if (units.size > 1) {
            ChoiceChips(options = units, selected = unit, label = { option -> unitLabels[option].orEmpty() }, onSelect = onUnitChange, modifier = unitsModifier)
        }
        preview?.let { Text(macroPreview(it), style = IronTheme.type.secondary, color = colors.muted) }
        PrimaryButton(stringResource(R.string.food_builder_add), onClick = onConfirm)
        TextAction(stringResource(R.string.common_cancel), onCancel, tone = ActionTone.MUTED)
    }
}
