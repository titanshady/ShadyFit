package com.fittrack.presentation.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.repository.NutritionRepository
import com.fittrack.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    val foodLogs: StateFlow<List<FoodLog>> = _selectedDate.flatMapLatest { date ->
        repository.getFoodLogsForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<UserProfile?> = repository.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Daily totals
    val dailyNutrition: StateFlow<DailyNutrition> = foodLogs.map { logs ->
        DailyNutrition(
            calories = logs.sumOf { it.calories.toDouble() }.toFloat(),
            protein  = logs.sumOf { it.protein.toDouble() }.toFloat(),
            carbs    = logs.sumOf { it.carbs.toDouble() }.toFloat(),
            fat      = logs.sumOf { it.fat.toDouble() }.toFloat(),
            fiber    = logs.sumOf { it.fiber.toDouble() }.toFloat()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyNutrition())

    // Food search
    private val _searchResults = MutableStateFlow<List<FoodItem>>(emptyList())
    val searchResults: StateFlow<List<FoodItem>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Logs grouped by meal
    val logsByMeal: StateFlow<Map<MealType, List<FoodLog>>> = foodLogs.map { logs ->
        MealType.values().associateWith { meal -> logs.filter { it.mealType == meal } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setDate(date: LocalDate) { _selectedDate.value = date }

    fun searchFood(query: String) {
        _searchQuery.value = query
        if (query.length < 2) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            repository.searchFood(query)
                .onSuccess { _searchResults.value = it }
                .onFailure { _searchError.value = "Erro na pesquisa: ${it.message}" }
            _isSearching.value = false
        }
    }

    fun addFoodLog(food: FoodItem, mealType: MealType, grams: Float) {
        viewModelScope.launch {
            val ratio = grams / 100f
            repository.addFoodLog(FoodLog(
                date      = _selectedDate.value,
                foodName  = food.name,
                foodId    = food.id,
                mealType  = mealType,
                grams     = grams,
                calories  = food.calories * ratio,
                protein   = food.protein * ratio,
                carbs     = food.carbs * ratio,
                fat       = food.fat * ratio,
                fiber     = food.fiber * ratio
            ))
        }
    }

    fun deleteFoodLog(log: FoodLog) {
        viewModelScope.launch { repository.deleteFoodLog(log) }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch { repository.upsertProfile(profile) }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }
}

data class DailyNutrition(
    val calories: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f
)
