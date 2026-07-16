package com.fittrack.presentation.screens.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.repository.ExerciseRepository
import com.fittrack.data.repository.WorkoutRepository
import com.fittrack.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // -- All workouts list -----------------------------------------------------
    val workouts: StateFlow<List<Workout>> =
        workoutRepository.getAllWorkouts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -- Template builder state (CreateWorkoutScreen) --------------------------
    private val _workoutName = MutableStateFlow(
        "Treino ${java.time.format.DateTimeFormatter.ofPattern("dd/MM").format(LocalDateTime.now())}"
    )
    val workoutName: StateFlow<String> = _workoutName

    private val _exercises = MutableStateFlow<List<WorkoutExercise>>(emptyList())
    val exercises: StateFlow<List<WorkoutExercise>> = _exercises

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private val _savedWorkoutId = MutableStateFlow<Long?>(null)
    val savedWorkoutId: StateFlow<Long?> = _savedWorkoutId

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // -- Active session timer (only during ActiveWorkoutScreen) ----------------
    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds

    private val _restCountdown = MutableStateFlow(0)
    val restCountdown: StateFlow<Int> = _restCountdown
    private var restJob: Job? = null

    private var timerJob: Job? = null
    private var editingWorkoutId: Long = -1L

    // -- Detail view -----------------------------------------------------------
    private val _selectedWorkout = MutableStateFlow<Workout?>(null)
    val selectedWorkout: StateFlow<Workout?> = _selectedWorkout

    init {
        val workoutId = savedStateHandle.get<Long>("workoutId") ?: -1L
        if (workoutId != -1L) {
            editingWorkoutId = workoutId
            loadWorkoutIntoEditor(workoutId)
        }
    }

    private fun loadWorkoutIntoEditor(id: Long) {
        viewModelScope.launch {
            workoutRepository.getWorkoutById(id)?.let { w ->
                _workoutName.value = w.name
                _exercises.value = w.exercises
            }
        }
    }

    fun setWorkoutName(name: String) { _workoutName.value = name }

    fun addExercise(exercise: Exercise) {
        val workoutExercise = WorkoutExercise(
            exerciseId    = exercise.id,
            exerciseName  = exercise.name,
            bodyPart      = exercise.bodyPart,
            targetMuscle  = exercise.target,
            order         = _exercises.value.size,
            sets          = listOf(ExerciseSet(setNumber = 1, reps = 10, weightKg = 20f))
        )
        _exercises.value = _exercises.value + workoutExercise
    }

    fun removeExercise(index: Int) {
        _exercises.value = _exercises.value.toMutableList().also { it.removeAt(index) }
    }

    fun addSet(exerciseIndex: Int) {
        val current = _exercises.value.toMutableList()
        val ex = current[exerciseIndex]
        val last = ex.sets.lastOrNull()
        current[exerciseIndex] = ex.copy(
            sets = ex.sets + ExerciseSet(
                setNumber = ex.sets.size + 1,
                reps      = last?.reps ?: 10,
                weightKg  = last?.weightKg ?: 20f
            )
        )
        _exercises.value = current
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        val current = _exercises.value.toMutableList()
        val ex = current[exerciseIndex]
        if (ex.sets.size > 1) {
            current[exerciseIndex] = ex.copy(
                sets = ex.sets.toMutableList().also { it.removeAt(setIndex) }
                    .mapIndexed { i, s -> s.copy(setNumber = i + 1) }
            )
            _exercises.value = current
        }
    }

    fun updateSet(exerciseIndex: Int, setIndex: Int, reps: Int, weight: Float) {
        val current = _exercises.value.toMutableList()
        val ex = current[exerciseIndex]
        val newSets = ex.sets.toMutableList()
        newSets[setIndex] = newSets[setIndex].copy(reps = reps, weightKg = weight)
        current[exerciseIndex] = ex.copy(sets = newSets)
        _exercises.value = current
    }

    fun toggleSetCompleted(exerciseIndex: Int, setIndex: Int) {
        val current = _exercises.value.toMutableList()
        val ex = current[exerciseIndex]
        val newSets = ex.sets.toMutableList()
        val set = newSets[setIndex]
        newSets[setIndex] = set.copy(completed = !set.completed)
        current[exerciseIndex] = ex.copy(sets = newSets)
        _exercises.value = current

        // Start rest countdown when a set is completed
        if (!set.completed) startRestCountdown(set.restSeconds)
    }

    // -- Timer - only called from ActiveWorkoutScreen --------------------------
    fun startSessionTimer() {
        if (timerJob?.isActive == true) return
        _durationSeconds.value = 0
        timerJob = viewModelScope.launch {
            while (true) { delay(1_000); _durationSeconds.value++ }
        }
    }

    fun stopSessionTimer() { timerJob?.cancel() }

    private fun startRestCountdown(seconds: Int) {
        restJob?.cancel()
        _restCountdown.value = seconds
        restJob = viewModelScope.launch {
            while (_restCountdown.value > 0) {
                delay(1_000)
                _restCountdown.value--
            }
        }
    }

    fun skipRest() { restJob?.cancel(); _restCountdown.value = 0 }

    // -- Save template (no timer involved) ------------------------------------
    fun saveWorkoutTemplate() {
        viewModelScope.launch {
            try {
                val workout = Workout(
                    id        = if (editingWorkoutId != -1L) editingWorkoutId else 0L,
                    name      = _workoutName.value,
                    date      = LocalDateTime.now(),
                    exercises = _exercises.value
                )
                val id = workoutRepository.saveWorkout(workout)
                _savedWorkoutId.value = id
                _saveSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao guardar: ${e.message}"
            }
        }
    }

    // -- Finish active session -------------------------------------------------
    fun finishSession(workoutId: Long) {
        viewModelScope.launch {
            stopSessionTimer()
            workoutRepository.completeWorkout(workoutId, _durationSeconds.value / 60)
        }
    }

    // -- Load exercises into session from a saved template --------------------
    fun loadSessionFromTemplate(workoutId: Long) {
        viewModelScope.launch {
            workoutRepository.getWorkoutById(workoutId)?.let { w ->
                _workoutName.value = w.name
                // Reset all sets to not completed
                _exercises.value = w.exercises.map { ex ->
                    ex.copy(sets = ex.sets.map { s -> s.copy(completed = false) })
                }
            }
        }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch { workoutRepository.deleteWorkout(id) }
    }

    fun loadWorkoutDetail(id: Long) {
        viewModelScope.launch {
            _selectedWorkout.value = workoutRepository.getWorkoutById(id)
        }
    }

    fun clearError() { _errorMessage.value = null }
    fun resetSaveState() { _saveSuccess.value = false; _savedWorkoutId.value = null }
}
