package com.ironquest.app.ui.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.FieldValue
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.failedText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.math.roundToInt

/** Raw text of every editor field. */
data class MealForm(
    val name: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val kcal: String = "",
    /** Comma-separated. */
    val tags: String = "",
    val recipeText: String = "",
    val recipeUrl: String = "",
)

enum class MealField { NAME, PROTEIN, CARBS, FAT, FIBER, KCAL, URL }

sealed interface MealValidation {
    data class Valid(val draft: MealDraft) : MealValidation
    data class Invalid(val errors: Set<MealField>) : MealValidation
}

data class MealEditorUiState(
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val form: MealForm = MealForm(),
    /** The form as loaded (blank for a new meal); [isDirty] compares against it. */
    val loadedForm: MealForm = MealForm(),
    val errors: Set<MealField> = emptySet(),
    val isSaving: Boolean = false,
) {
    val isDirty: Boolean get() = form != loadedForm

    /** "≈ 528" hint shown in the empty kcal field. */
    val derivedKcalHint: UiText? get() = MealEditorViewModel.derivedKcalHint(form)
}

/** Everything the meal editor can ask the ViewModel to do. */
sealed interface MealEditorAction {
    data class FormChange(val form: MealForm) : MealEditorAction

    /** Totals from the ingredient calculator: overwrite the macro fields. */
    data class ApplyEstimate(val total: Macros) : MealEditorAction
    data object Save : MealEditorAction
    data object Delete : MealEditorAction
}

sealed interface MealEditorEvent {
    data object Saved : MealEditorEvent
    data class Deleted(val outcome: DeleteOutcome) : MealEditorEvent
    data class Message(val text: UiText) : MealEditorEvent
}

@HiltViewModel
class MealEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
) : ViewModel() {

    /** "new" (or anything non-numeric) → create. */
    private val mealId: Long? = savedStateHandle.get<String>(ARG_MEAL_ID)?.toLongOrNull()

    private val _uiState = MutableStateFlow(MealEditorUiState(isLoading = mealId != null, isNew = mealId == null))
    val uiState: StateFlow<MealEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<MealEditorEvent>(Channel.BUFFERED)
    val events: Flow<MealEditorEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()

    init {
        if (mealId != null) {
            viewModelScope.launch {
                val meal = repository.observeMeal(mealId).first()
                _uiState.update { current ->
                    val loaded = meal?.toForm() ?: MealForm()
                    // Keep edits typed while loading; only the baseline is replaced.
                    current.copy(isLoading = false, form = if (current.form == current.loadedForm) loaded else current.form, loadedForm = loaded)
                }
            }
        }
    }

    fun onAction(action: MealEditorAction) {
        when (action) {
            is MealEditorAction.FormChange -> onFormChange(action.form)
            // Read the latest form, not a captured state, so applying never reverts newer typing.
            is MealEditorAction.ApplyEstimate -> onFormChange(IngredientCalculator.applyTo(uiState.value.form, action.total))
            MealEditorAction.Save -> save()
            MealEditorAction.Delete -> delete()
        }
    }

    private fun onFormChange(form: MealForm) = _uiState.update { it.copy(form = form, errors = emptySet()) }

    /** isSaving stays true after success so a second tap during the exit animation can't insert a duplicate. */
    private fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        when (val result = validate(state.form, mealId)) {
            is MealValidation.Invalid -> _uiState.update { it.copy(errors = result.errors) }
            is MealValidation.Valid -> {
                _uiState.update { it.copy(isSaving = true) }
                viewModelScope.launch {
                    try {
                        writeMutex.withLock { repository.saveMeal(result.draft) }
                        _events.send(MealEditorEvent.Saved)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        _uiState.update { it.copy(isSaving = false) }
                        _events.send(MealEditorEvent.Message(failedText(R.string.food_meal_save_failed, error)))
                    }
                }
            }
        }
    }

    private fun delete() {
        val id = mealId ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val outcome = writeMutex.withLock { repository.deleteMeal(id) }
                _events.send(MealEditorEvent.Deleted(outcome))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(MealEditorEvent.Message(failedText(R.string.food_meal_delete_failed, error)))
            }
        }
    }

    companion object {
        const val ARG_MEAL_ID = "mealId" // not UI text: navigation argument key

        fun validate(form: MealForm, id: Long?): MealValidation {
            val errors = mutableSetOf<MealField>()

            fun grams(text: String, field: MealField): Double = when (val parsed = NutritionRules.parseField(text, NutritionRules.MAX_MEAL_GRAMS)) {
                FieldValue.Blank -> 0.0
                is FieldValue.Value -> parsed.value
                FieldValue.Invalid -> 0.0.also { errors += field }
            }

            if (form.name.isBlank()) errors += MealField.NAME
            val protein = grams(form.protein, MealField.PROTEIN)
            val carbs = grams(form.carbs, MealField.CARBS)
            val fat = grams(form.fat, MealField.FAT)
            val fiber = grams(form.fiber, MealField.FIBER)
            val kcal = when (val parsed = NutritionRules.parseField(form.kcal, NutritionRules.MAX_KCAL)) {
                FieldValue.Blank -> null
                is FieldValue.Value -> parsed.value
                FieldValue.Invalid -> null.also { errors += MealField.KCAL }
            }
            val url = form.recipeUrl.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                NutritionRules.normalizeUrl(raw) ?: null.also { errors += MealField.URL }
            }
            if (errors.isNotEmpty()) return MealValidation.Invalid(errors)

            return MealValidation.Valid(
                MealDraft(
                    id = id,
                    name = form.name.trim(),
                    macros = Macros(protein, carbs, fat, fiber, kcal),
                    tags = form.tags.split(',').mapNotNull(NutritionRules::normalizeTag).distinct(),
                    recipeText = form.recipeText.trim().ifEmpty { null },
                    recipeUrl = url,
                ),
            )
        }

        fun derivedKcalHint(form: MealForm): UiText? {
            fun gramsOrNull(text: String): Double? = when (val parsed = NutritionRules.parseField(text, NutritionRules.MAX_MEAL_GRAMS)) {
                FieldValue.Blank -> 0.0
                is FieldValue.Value -> parsed.value
                FieldValue.Invalid -> null
            }
            val protein = gramsOrNull(form.protein) ?: return null
            val carbs = gramsOrNull(form.carbs) ?: return null
            val fat = gramsOrNull(form.fat) ?: return null
            return uiText(R.string.food_meal_editor_kcal_hint, Macros(protein, carbs, fat).effectiveKcal.roundToInt())
        }

        fun Meal.toForm() = MealForm(
            name = name,
            protein = macros.proteinG.formatAmount(),
            carbs = macros.carbsG.formatAmount(),
            fat = macros.fatG.formatAmount(),
            fiber = macros.fiberG.formatAmount(),
            kcal = macros.kcal?.formatAmount().orEmpty(),
            tags = tags.joinToString(", "),
            recipeText = recipeText.orEmpty(),
            recipeUrl = recipeUrl.orEmpty(),
        )
    }
}
