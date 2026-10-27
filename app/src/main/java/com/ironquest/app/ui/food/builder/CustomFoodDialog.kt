package com.ironquest.app.ui.food.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.food.UserFood
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.nutrition.FieldValue
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.ui.food.formatAmount
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.SegmentedControl
import kotlin.math.roundToLong

enum class UserFoodField { NAME, PROTEIN, CARBS, FAT, FIBER, KCAL, PIECE_GRAMS }

/** What the dialog's text fields hold. With [perPiece] the macros and kcal are per piece, not per 100 g. */
data class UserFoodFields(
    val name: String = "",
    val perPiece: Boolean = false,
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val kcal: String = "",
    val pieceGrams: String = "",
)

object UserFoodForm {
    fun initial(name: String, food: UserFood?): UserFoodFields {
        if (food == null) return UserFoodFields(name = name)
        val macros = food.per100g
        return UserFoodFields(
            name = food.name,
            protein = macros.proteinG.formatAmount(),
            carbs = macros.carbsG.formatAmount(),
            fat = macros.fatG.formatAmount(),
            fiber = macros.fiberG.formatAmount(),
            kcal = macros.kcal?.formatAmount().orEmpty(),
            pieceGrams = food.pieceGrams?.formatAmount().orEmpty(),
        )
    }

    /** Every field that blocks saving; empty means [toDraft] succeeds. */
    fun errors(fields: UserFoodFields): Set<UserFoodField> = validate(fields).errors

    /** Macros are stored per 100 g; per-piece input is converted with the piece weight. Null when something is invalid. */
    fun toDraft(fields: UserFoodFields, id: Long?): UserFoodDraft? {
        val result = validate(fields)
        val macros = result.macros
        if (result.errors.isNotEmpty() || macros == null) return null
        return UserFoodDraft(id, fields.name.trim(), macros, result.pieceGrams)
    }

    private class Validation(val errors: Set<UserFoodField>, val macros: Macros?, val pieceGrams: Double?)

    private fun validate(fields: UserFoodFields): Validation {
        val errors = mutableSetOf<UserFoodField>()
        if (fields.name.isBlank()) errors += UserFoodField.NAME
        val pieceGrams = when (val parsed = NutritionRules.parseField(fields.pieceGrams, NutritionRules.MAX_MEAL_GRAMS)) {
            is FieldValue.Value -> parsed.value.takeIf { it > 0.0 }
            FieldValue.Blank, FieldValue.Invalid -> null
        }
        val pieceText = fields.pieceGrams.isNotBlank()
        if ((fields.perPiece || pieceText) && pieceGrams == null) errors += UserFoodField.PIECE_GRAMS
        val factor = if (fields.perPiece && pieceGrams != null) PER_100_G / pieceGrams else 1.0
        fun grams(text: String, field: UserFoodField): Double {
            val parsed = NutritionRules.parseField(text, NutritionRules.MAX_MEAL_GRAMS)
            if (parsed is FieldValue.Invalid) {
                errors += field
                return 0.0
            }
            val scaled = ((parsed as? FieldValue.Value)?.value ?: 0.0) * factor
            if (scaled > NutritionRules.MAX_MEAL_GRAMS) errors += field
            return tidy(scaled)
        }
        val protein = grams(fields.protein, UserFoodField.PROTEIN)
        val carbs = grams(fields.carbs, UserFoodField.CARBS)
        val fat = grams(fields.fat, UserFoodField.FAT)
        val fiber = grams(fields.fiber, UserFoodField.FIBER)
        val kcal = when (val parsed = NutritionRules.parseField(fields.kcal, NutritionRules.MAX_KCAL)) {
            FieldValue.Blank -> null
            FieldValue.Invalid -> {
                errors += UserFoodField.KCAL
                null
            }
            is FieldValue.Value -> (parsed.value * factor).also { if (it > NutritionRules.MAX_KCAL) errors += UserFoodField.KCAL }.let(::tidy)
        }
        return Validation(errors, Macros(protein, carbs, fat, fiber, kcal), pieceGrams)
    }

    private fun tidy(value: Double): Double = (value * 100).roundToLong() / 100.0

    private const val PER_100_G = 100.0
}

/** Create ([initial] null) or edit a food of your own; [onDelete] is only given when editing. */
@Composable
fun CustomFoodDialog(
    initialName: String,
    initial: UserFood?,
    onSave: (UserFoodDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var fields by rememberSaveable(stateSaver = FieldsSaver) { mutableStateOf(UserFoodForm.initial(initialName, initial)) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val errors = if (showErrors) UserFoodForm.errors(fields) else emptySet()
    val gramsError = stringResource(R.string.food_error_grams_range)
    val unitLabels = mapOf(false to stringResource(R.string.food_custom_food_per_100g), true to stringResource(R.string.food_custom_food_per_piece))
    IronDialog(
        title = stringResource(if (initial == null) R.string.food_custom_food_title_new else R.string.food_custom_food_title_edit),
        confirmLabel = stringResource(R.string.common_save),
        onConfirm = {
            val draft = UserFoodForm.toDraft(fields, initial?.id)
            if (draft == null) showErrors = true else onSave(draft)
        },
        onDismiss = onDismiss,
        onDelete = onDelete,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IronTextField(
                stringResource(R.string.food_field_name),
                fields.name,
                { fields = fields.copy(name = it) },
                isError = UserFoodField.NAME in errors,
                errorText = stringResource(R.string.food_error_required),
                capitalization = KeyboardCapitalization.Sentences,
            )
            SegmentedControl(
                options = listOf(false, true),
                selected = fields.perPiece,
                label = { unitLabels.getValue(it) },
                onSelect = { fields = fields.copy(perPiece = it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IronTextField(stringResource(R.string.food_field_protein_g), fields.protein, { fields = fields.copy(protein = it) }, Modifier.weight(1f), UserFoodField.PROTEIN in errors, gramsError, keyboardType = KeyboardType.Decimal)
                IronTextField(stringResource(R.string.food_field_carbs_g), fields.carbs, { fields = fields.copy(carbs = it) }, Modifier.weight(1f), UserFoodField.CARBS in errors, gramsError, keyboardType = KeyboardType.Decimal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IronTextField(stringResource(R.string.food_field_fat_g), fields.fat, { fields = fields.copy(fat = it) }, Modifier.weight(1f), UserFoodField.FAT in errors, gramsError, keyboardType = KeyboardType.Decimal)
                IronTextField(stringResource(R.string.food_field_fiber_g), fields.fiber, { fields = fields.copy(fiber = it) }, Modifier.weight(1f), UserFoodField.FIBER in errors, gramsError, keyboardType = KeyboardType.Decimal)
            }
            IronTextField(
                stringResource(R.string.food_field_kcal_optional),
                fields.kcal,
                { fields = fields.copy(kcal = it) },
                Modifier.fillMaxWidth(),
                UserFoodField.KCAL in errors,
                stringResource(R.string.food_error_kcal_range),
                keyboardType = KeyboardType.Decimal,
            )
            IronTextField(
                stringResource(if (fields.perPiece) R.string.food_custom_food_piece_grams else R.string.food_custom_food_piece_grams_optional),
                fields.pieceGrams,
                { fields = fields.copy(pieceGrams = it) },
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                UserFoodField.PIECE_GRAMS in errors,
                stringResource(R.string.food_custom_food_piece_grams_error),
                keyboardType = KeyboardType.Decimal,
            )
        }
    }
}

private val FieldsSaver = listSaver<UserFoodFields, String>(
    save = { listOf(it.name, it.perPiece.toString(), it.protein, it.carbs, it.fat, it.fiber, it.kcal, it.pieceGrams) },
    restore = { UserFoodFields(it[0], it[1].toBoolean(), it[2], it[3], it[4], it[5], it[6], it[7]) },
)
