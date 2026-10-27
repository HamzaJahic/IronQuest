package com.ironquest.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.FieldValue
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.theme.IronTheme
import kotlin.math.roundToInt

enum class TargetField { KCAL, PROTEIN, CARBS, FAT, WATER }

/** Editor texts of the daily targets; blank means "no target". */
data class DailyTargetsTexts(
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val water: String = "",
)

/** Validation shared by the You tab dialog, the Food Day link and the setup screen, using the plan editor's limits. */
object DailyTargetsForm {

    fun texts(targets: MacroTargets) = DailyTargetsTexts(
        kcal = targets.kcal?.formatAmount().orEmpty(),
        protein = targets.proteinG?.formatAmount().orEmpty(),
        carbs = targets.carbsG?.formatAmount().orEmpty(),
        fat = targets.fatG?.formatAmount().orEmpty(),
        water = targets.waterMl?.toString().orEmpty(),
    )

    fun invalidFields(texts: DailyTargetsTexts): Set<TargetField> = buildSet {
        for ((field, text, max) in entries(texts)) {
            if (NutritionRules.parseField(text, max) == FieldValue.Invalid) add(field)
        }
    }

    /** Null when any field is invalid. Fiber is not part of this form and stays unset. */
    fun parse(texts: DailyTargetsTexts): MacroTargets? {
        if (invalidFields(texts).isNotEmpty()) return null

        fun value(text: String, max: Double): Double? = (NutritionRules.parseField(text, max) as? FieldValue.Value)?.value
        return MacroTargets(
            proteinG = value(texts.protein, NutritionRules.MAX_TARGET_GRAMS),
            carbsG = value(texts.carbs, NutritionRules.MAX_TARGET_GRAMS),
            fatG = value(texts.fat, NutritionRules.MAX_TARGET_GRAMS),
            kcal = value(texts.kcal, NutritionRules.MAX_KCAL),
            waterMl = value(texts.water, NutritionRules.MAX_WATER_ML)?.roundToInt(),
        )
    }

    private fun entries(texts: DailyTargetsTexts) = listOf(
        Triple(TargetField.KCAL, texts.kcal, NutritionRules.MAX_KCAL),
        Triple(TargetField.PROTEIN, texts.protein, NutritionRules.MAX_TARGET_GRAMS),
        Triple(TargetField.CARBS, texts.carbs, NutritionRules.MAX_TARGET_GRAMS),
        Triple(TargetField.FAT, texts.fat, NutritionRules.MAX_TARGET_GRAMS),
        Triple(TargetField.WATER, texts.water, NutritionRules.MAX_WATER_ML),
    )
}

private val DailyTargetsTextsSaver = listSaver<DailyTargetsTexts, String>(
    save = { listOf(it.kcal, it.protein, it.carbs, it.fat, it.water) },
    restore = { DailyTargetsTexts(it[0], it[1], it[2], it[3], it[4]) },
)

/** Own daily targets (used when there is no plan, or the plan is off). Fiber from [initial] is kept as is. */
@Composable
fun DailyTargetsDialog(initial: MacroTargets, onSave: (MacroTargets) -> Unit, onDismiss: () -> Unit) {
    var texts by rememberSaveable(stateSaver = DailyTargetsTextsSaver) { mutableStateOf(DailyTargetsForm.texts(initial)) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val invalid = if (showErrors) DailyTargetsForm.invalidFields(texts) else emptySet()
    val error = stringResource(R.string.food_targets_error)

    IronDialog(
        title = stringResource(R.string.food_targets_title),
        confirmLabel = stringResource(R.string.common_save),
        onConfirm = {
            val parsed = DailyTargetsForm.parse(texts)
            if (parsed == null) showErrors = true else onSave(parsed.copy(fiberG = initial.fiberG))
        },
        onDismiss = onDismiss,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.food_targets_hint), style = IronTheme.type.body, color = IronTheme.colors.muted)
            IronTextField(stringResource(R.string.food_targets_kcal), texts.kcal, { texts = texts.copy(kcal = it) }, isError = TargetField.KCAL in invalid, errorText = error, keyboardType = KeyboardType.Decimal)
            IronTextField(stringResource(R.string.food_field_protein_g), texts.protein, { texts = texts.copy(protein = it) }, isError = TargetField.PROTEIN in invalid, errorText = error, keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                IronTextField(stringResource(R.string.food_field_carbs_g), texts.carbs, { texts = texts.copy(carbs = it) }, modifier = Modifier.weight(1f), isError = TargetField.CARBS in invalid, keyboardType = KeyboardType.Decimal)
                IronTextField(stringResource(R.string.food_field_fat_g), texts.fat, { texts = texts.copy(fat = it) }, modifier = Modifier.weight(1f), isError = TargetField.FAT in invalid, keyboardType = KeyboardType.Decimal)
            }
            IronTextField(stringResource(R.string.food_targets_water), texts.water, { texts = texts.copy(water = it) }, isError = TargetField.WATER in invalid, errorText = error, keyboardType = KeyboardType.Number)
        }
    }
}
