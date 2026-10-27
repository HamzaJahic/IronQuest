package com.ironquest.app.ui.food.builder

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ironquest.app.R
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.QuickMealName
import com.ironquest.app.domain.food.UnitKind
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText

@StringRes
private fun FoodUnit.labelRes(): Int? = when (kind) {
    UnitKind.GRAM -> R.string.food_unit_g
    UnitKind.MILLILITRE -> R.string.food_unit_ml
    UnitKind.PIECE -> R.string.food_unit_pcs
    UnitKind.SERVING -> R.string.food_unit_serving
    UnitKind.MEAL_PORTION -> R.string.food_unit_portion
    UnitKind.PORTION -> null
}

/** A PORTION shows its label as stored (USDA's English word, like imported names). */
@Composable
fun FoodUnit.label(): String = labelRes()?.let { stringResource(it) } ?: label.orEmpty()

fun FoodUnit.labelText(): UiText = labelRes()?.let { uiText(it) } ?: UiText.Verbatim(label.orEmpty())

@get:StringRes
val QuickMealName.labelRes: Int
    get() = when (this) {
        QuickMealName.BREAKFAST -> R.string.food_builder_meal_breakfast
        QuickMealName.LUNCH -> R.string.food_builder_meal_lunch
        QuickMealName.DINNER -> R.string.food_builder_meal_dinner
        QuickMealName.SNACK -> R.string.food_builder_meal_snack
    }
