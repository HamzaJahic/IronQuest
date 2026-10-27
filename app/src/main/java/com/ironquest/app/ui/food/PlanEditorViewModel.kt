package com.ironquest.app.ui.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.FieldValue
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.SlotDraft
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

/** [key] is a stable local id for Compose keys and error marking; [id] is the DB id (null = new). */
data class SlotForm(
    val key: Long,
    val id: Long?,
    val label: String = "",
    val mealId: Long? = null,
    val mealName: String? = null,
    val timeOfDayMin: Int? = null,
    /** One of [NutritionRules.REMINDER_OFFSETS]; only set while [timeOfDayMin] is. */
    val reminderOffsetMin: Int? = null,
)

data class PlanForm(
    val name: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val kcal: String = "",
    val water: String = "",
    val slots: List<SlotForm> = emptyList(),
)

enum class PlanField { NAME, PROTEIN, CARBS, FAT, FIBER, KCAL, WATER }

sealed interface PlanValidation {
    data class Valid(val draft: PlanDraft) : PlanValidation
    data class Invalid(val fields: Set<PlanField>, val slotKeys: Set<Long>) : PlanValidation
}

data class PlanEditorUiState(
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val form: PlanForm = PlanForm(),
    /** The form as loaded (blank for a new plan); [isDirty] compares against it. */
    val loadedForm: PlanForm = PlanForm(),
    val meals: List<Meal> = emptyList(),
    val fieldErrors: Set<PlanField> = emptySet(),
    val slotErrors: Set<Long> = emptySet(),
    val isSaving: Boolean = false,
) {
    val isDirty: Boolean get() = form != loadedForm

    /** Drives the permission prompt on save and the "may be late" hint. */
    val hasReminders: Boolean get() = form.slots.any { it.reminderOffsetMin != null }
}

/** Everything the plan editor can ask the ViewModel to do. */
sealed interface PlanEditorAction {
    data class FormChange(val form: PlanForm) : PlanEditorAction

    /** [defaultLabel] is the pre-filled slot name ("Breakfast", "Meal 5"), chosen by the screen in the current language. */
    data class AddSlot(val defaultLabel: String) : PlanEditorAction
    data class UpdateSlot(val key: Long, val transform: (SlotForm) -> SlotForm) : PlanEditorAction
    data class ClearSlotTime(val key: Long) : PlanEditorAction
    data class SetSlotReminder(val key: Long, val offsetMin: Int?) : PlanEditorAction
    data class RemoveSlot(val key: Long) : PlanEditorAction
    data class MoveSlot(val key: Long, val delta: Int) : PlanEditorAction
    data object Save : PlanEditorAction
}

sealed interface PlanEditorEvent {
    /** [award] is the XP change from re-evaluating today's bonuses against edited targets. */
    data class Saved(val award: NutritionAward) : PlanEditorEvent
    data class Message(val text: UiText) : PlanEditorEvent
}

@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
) : ViewModel() {

    private val planId: Long? = savedStateHandle.get<String>(ARG_PLAN_ID)?.toLongOrNull()

    private val _uiState = MutableStateFlow(PlanEditorUiState(isLoading = planId != null, isNew = planId == null))
    val uiState: StateFlow<PlanEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlanEditorEvent>(Channel.BUFFERED)
    val events: Flow<PlanEditorEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()
    private var nextKey = 1L

    init {
        viewModelScope.launch { repository.observeMeals().collect { meals -> _uiState.update { it.copy(meals = meals) } } }
        if (planId != null) {
            viewModelScope.launch {
                val plan = repository.observePlan(planId).first()
                _uiState.update { current ->
                    val loaded = plan?.toForm() ?: PlanForm()
                    current.copy(isLoading = false, form = if (current.form == current.loadedForm) loaded else current.form, loadedForm = loaded)
                }
            }
        }
    }

    fun onAction(action: PlanEditorAction) {
        when (action) {
            is PlanEditorAction.FormChange -> onFormChange(action.form)
            is PlanEditorAction.AddSlot -> addSlot(action.defaultLabel)
            is PlanEditorAction.UpdateSlot -> updateSlot(action.key, action.transform)
            is PlanEditorAction.ClearSlotTime -> clearSlotTime(action.key)
            is PlanEditorAction.SetSlotReminder -> setSlotReminder(action.key, action.offsetMin)
            is PlanEditorAction.RemoveSlot -> removeSlot(action.key)
            is PlanEditorAction.MoveSlot -> moveSlot(action.key, action.delta)
            PlanEditorAction.Save -> save()
        }
    }

    private fun onFormChange(form: PlanForm) = _uiState.update { it.copy(form = form, fieldErrors = emptySet()) }

    private fun addSlot(defaultLabel: String) = updateSlots { slots ->
        slots + SlotForm(key = nextKey++, id = null, label = defaultLabel)
    }

    private fun updateSlot(key: Long, transform: (SlotForm) -> SlotForm) = updateSlots { slots ->
        slots.map { if (it.key == key) transform(it) else it }
    }

    /** A reminder needs a time, so clearing the time clears the reminder too. */
    private fun clearSlotTime(key: Long) = updateSlot(key) { it.copy(timeOfDayMin = null, reminderOffsetMin = null) }

    /** null switches the reminder off. Ignored without a time, or for offsets not in the list. */
    private fun setSlotReminder(key: Long, offsetMin: Int?) = updateSlot(key) { slot ->
        when {
            slot.timeOfDayMin == null -> slot
            offsetMin != null && offsetMin !in NutritionRules.REMINDER_OFFSETS -> slot
            else -> slot.copy(reminderOffsetMin = offsetMin)
        }
    }

    private fun removeSlot(key: Long) = updateSlots { slots -> slots.filterNot { it.key == key } }

    private fun moveSlot(key: Long, delta: Int) = updateSlots { slots ->
        val from = slots.indexOfFirst { it.key == key }
        val to = from + delta
        if (from == -1 || to !in slots.indices) {
            slots
        } else {
            slots.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    /** isSaving stays true after success so a second tap during the exit animation can't insert a duplicate. */
    private fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        when (val result = validate(state.form, planId)) {
            is PlanValidation.Invalid -> _uiState.update { it.copy(fieldErrors = result.fields, slotErrors = result.slotKeys) }
            is PlanValidation.Valid -> {
                _uiState.update { it.copy(isSaving = true) }
                viewModelScope.launch {
                    try {
                        val saved = writeMutex.withLock { repository.savePlan(result.draft) }
                        _events.send(PlanEditorEvent.Saved(saved.award))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        _uiState.update { it.copy(isSaving = false) }
                        _events.send(PlanEditorEvent.Message(failedText(R.string.food_plan_save_failed, error)))
                    }
                }
            }
        }
    }

    private fun updateSlots(transform: (List<SlotForm>) -> List<SlotForm>) = _uiState.update {
        it.copy(form = it.form.copy(slots = transform(it.form.slots)), slotErrors = emptySet())
    }

    private fun MealPlan.toForm() = PlanForm(
        name = name,
        protein = targets.proteinG?.formatAmount().orEmpty(),
        carbs = targets.carbsG?.formatAmount().orEmpty(),
        fat = targets.fatG?.formatAmount().orEmpty(),
        fiber = targets.fiberG?.formatAmount().orEmpty(),
        kcal = targets.kcal?.formatAmount().orEmpty(),
        water = targets.waterMl?.toString().orEmpty(),
        slots = slots.map { slot ->
            SlotForm(nextKey++, slot.id, slot.label, slot.mealId, slot.mealName, slot.timeOfDayMin, slot.reminderOffsetMin)
        },
    )

    companion object {
        const val ARG_PLAN_ID = "planId" // not UI text: navigation argument key

        /** Picker options; null = off. */
        val REMINDER_CHOICES: List<Int?> = listOf<Int?>(null) + NutritionRules.REMINDER_OFFSETS

        fun reminderLabel(offsetMin: Int?): UiText = if (offsetMin == null) uiText(R.string.food_plan_reminder_off) else uiText(R.string.food_plan_reminder_before, offsetMin)

        fun validate(form: PlanForm, id: Long?): PlanValidation {
            val fields = mutableSetOf<PlanField>()

            fun target(text: String, max: Double, field: PlanField): Double? = when (val parsed = NutritionRules.parseField(text, max)) {
                FieldValue.Blank -> null
                is FieldValue.Value -> parsed.value
                FieldValue.Invalid -> null.also { fields += field }
            }

            if (form.name.isBlank()) fields += PlanField.NAME
            val targets = MacroTargets(
                proteinG = target(form.protein, NutritionRules.MAX_TARGET_GRAMS, PlanField.PROTEIN),
                carbsG = target(form.carbs, NutritionRules.MAX_TARGET_GRAMS, PlanField.CARBS),
                fatG = target(form.fat, NutritionRules.MAX_TARGET_GRAMS, PlanField.FAT),
                fiberG = target(form.fiber, NutritionRules.MAX_TARGET_GRAMS, PlanField.FIBER),
                kcal = target(form.kcal, NutritionRules.MAX_KCAL, PlanField.KCAL),
                waterMl = target(form.water, NutritionRules.MAX_WATER_ML, PlanField.WATER)?.roundToInt(),
            )
            val badSlots = form.slots.filter { slot ->
                slot.label.isBlank() || slot.mealId == null || !NutritionRules.isValidReminder(slot.timeOfDayMin, slot.reminderOffsetMin)
            }.map { it.key }.toSet()

            if (fields.isNotEmpty() || badSlots.isNotEmpty()) return PlanValidation.Invalid(fields, badSlots)
            return PlanValidation.Valid(
                PlanDraft(
                    id = id,
                    name = form.name.trim(),
                    targets = targets,
                    slots = form.slots.map { slot ->
                        SlotDraft(slot.id, slot.label.trim(), checkNotNull(slot.mealId) { "A valid form has a meal on every slot" }, slot.timeOfDayMin, slot.reminderOffsetMin)
                    },
                ),
            )
        }
    }
}
