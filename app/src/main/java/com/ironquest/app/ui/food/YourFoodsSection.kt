package com.ironquest.app.ui.food

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.food.UserFood
import com.ironquest.app.domain.food.UserFoodSource
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.theme.IronTheme

/** The user's own foods (typed in or saved from a barcode lookup); a tap opens the editor. */
@Composable
fun YourFoodsSection(foods: List<UserFood>, onOpen: (UserFood) -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val dot = stringResource(R.string.common_dot_separator)
    RowGroup {
        foods.forEach { food ->
            val detail = listOf(
                stringResource(R.string.food_your_foods_per_100g),
                stringResource(R.string.food_amount_kcal, food.per100g.effectiveKcal.wholeText(currentLocale())),
            ).joinToString(dot)
            RuledRow(onClick = { onOpen(food) }) {
                Column(Modifier.weight(1f)) {
                    Text(food.name, style = type.body, color = colors.ink)
                    Row {
                        Text(detail, style = type.secondary, color = colors.muted)
                        if (food.source == UserFoodSource.ONLINE) {
                            Text(dot + stringResource(R.string.food_your_foods_online), style = type.secondary, color = colors.muted)
                        }
                    }
                }
                Text("›", style = type.body, color = colors.muted)
            }
        }
    }
}
