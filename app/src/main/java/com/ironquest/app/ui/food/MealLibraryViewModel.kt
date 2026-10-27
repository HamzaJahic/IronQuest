package com.ironquest.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.domain.food.UserFood
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.repository.FoodRepository
import com.ironquest.app.domain.repository.NutritionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MealLibraryUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val selectedTag: String? = null,
    /** True = the Archived filter: only archived meals are listed. */
    val showArchived: Boolean = false,
    val allTags: List<String> = emptyList(),
    val meals: List<Meal> = emptyList(),
    /** True = the Your foods filter: the user's own foods are listed instead of meals. */
    val showYourFoods: Boolean = false,
    val userFoods: List<UserFood> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MealLibraryViewModel @Inject constructor(
    repository: NutritionRepository,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tag = MutableStateFlow<String?>(null)

    private val archived = MutableStateFlow(false)

    private val yourFoods = MutableStateFlow(false)

    private val source = archived.flatMapLatest { if (it) repository.observeArchivedMeals() else repository.observeMeals() }

    private val mealsState = combine(source, query, tag, archived) { meals, queryText, selectedTag, showArchived ->
        MealLibraryUiState(
            isLoading = false,
            query = queryText,
            selectedTag = selectedTag,
            showArchived = showArchived,
            allTags = meals.flatMap { it.tags }.distinct().sorted(),
            meals = filterMeals(meals, queryText, selectedTag),
        )
    }

    val uiState: StateFlow<MealLibraryUiState> = combine(mealsState, yourFoods, foodRepository.observeUserFoods()) { state, showYourFoods, userFoods ->
        state.copy(showYourFoods = showYourFoods, userFoods = filterFoods(userFoods, state.query))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealLibraryUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    /** Toggles the Archived filter; the tag filter is cleared because tags differ between the two lists. */
    fun onArchivedClick() {
        tag.value = null
        yourFoods.value = false
        archived.update { !it }
    }

    /** Your foods and Archived exclude each other: both replace the main list. */
    fun onYourFoodsClick() {
        tag.value = null
        archived.value = false
        yourFoods.update { !it }
    }

    fun saveFood(draft: UserFoodDraft) {
        viewModelScope.launch { foodRepository.saveUserFood(draft) }
    }

    fun deleteFood(id: Long) {
        viewModelScope.launch { foodRepository.deleteUserFood(id) }
    }

    /** Tapping the selected tag again clears the filter. */
    fun onTagClick(clicked: String) = tag.update { if (it == clicked) null else clicked }

    companion object {
        fun filterFoods(foods: List<UserFood>, query: String): List<UserFood> {
            val needle = query.trim().lowercase()
            return foods.filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
        }

        fun filterMeals(meals: List<Meal>, query: String, tag: String?): List<Meal> {
            val needle = query.trim().lowercase()
            return meals.filter { meal ->
                (tag == null || tag in meal.tags) &&
                    (needle.isEmpty() || meal.name.lowercase().contains(needle) || meal.tags.any { it.contains(needle) })
            }
        }
    }
}
