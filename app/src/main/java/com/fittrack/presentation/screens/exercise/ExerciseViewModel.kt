package com.fittrack.presentation.screens.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.repository.ExerciseRepository
import com.fittrack.domain.model.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedBodyPart = MutableStateFlow<String?>(null)
    val selectedBodyPart: StateFlow<String?> = _selectedBodyPart

    private val _bodyParts = MutableStateFlow<List<String>>(emptyList())
    val bodyParts: StateFlow<List<String>> = _bodyParts

    private val _selectedExercise = MutableStateFlow<Exercise?>(null)
    val selectedExercise: StateFlow<Exercise?> = _selectedExercise

    // Filtered exercises
    val filteredExercises: StateFlow<List<Exercise>> = combine(
        _exercises, _searchQuery, _selectedBodyPart
    ) { exercises, query, bodyPart ->
        exercises.filter { ex ->
            (query.isBlank() || ex.name.contains(query, ignoreCase = true) ||
                    ex.target.contains(query, ignoreCase = true)) &&
                    (bodyPart == null || ex.bodyPart.equals(bodyPart, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Always load local exercises immediately so the list is never empty
        _exercises.value = repository.getLocalExercises()
        loadBodyParts()
        loadExercisesFromApi()
    }

    private fun loadExercisesFromApi(bodyPart: String? = null, offset: Int = 0) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getExercises(bodyPart, offset)
                .onSuccess { list ->
                    if (list.isNotEmpty()) {
                        _exercises.value = if (offset == 0) list else _exercises.value + list
                        _error.value = null
                    }
                }
                .onFailure { error ->
                    // Show the real error so we can diagnose it
                    _error.value = "Erro API: ${error.message ?: error.javaClass.simpleName}"
                }
            _isLoading.value = false
        }
    }

    private fun loadBodyParts() {
        viewModelScope.launch {
            repository.getBodyParts().onSuccess { parts ->
                _bodyParts.value = parts
            }.onFailure {
                _bodyParts.value = listOf("chest", "back", "shoulders", "upper arms",
                    "upper legs", "lower legs", "waist", "cardio")
            }
        }
    }

    fun loadExercises(bodyPart: String? = null, offset: Int = 0) {
        loadExercisesFromApi(bodyPart, offset)
    }

    fun filterByBodyPart(bodyPart: String?) {
        _selectedBodyPart.value = bodyPart
        if (bodyPart != null) {
            // Filter local exercises immediately, then try API
            _exercises.value = repository.getLocalExercises()
                .filter { it.bodyPart.equals(bodyPart, ignoreCase = true) }
                .ifEmpty { repository.getLocalExercises() }
            loadExercisesFromApi(bodyPart)
        } else {
            _exercises.value = repository.getLocalExercises()
            loadExercisesFromApi()
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            viewModelScope.launch {
                repository.searchExercises(query).onSuccess { results ->
                    if (results.isNotEmpty()) _exercises.value = results
                }
            }
        }
    }

    fun selectExercise(exercise: Exercise) { _selectedExercise.value = exercise }
    fun clearSelection() { _selectedExercise.value = null }
}
