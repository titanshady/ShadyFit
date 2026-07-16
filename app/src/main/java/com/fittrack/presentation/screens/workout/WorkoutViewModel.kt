package com.fittrack.presentation.screens.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.local.SettingsRepository
import com.fittrack.data.repository.ExerciseRepository
import com.fittrack.data.repository.NutritionRepository
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
    private val nutritionRepository: NutritionRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // -- Roadmap 1.5: optional sound when rest ends ------------------------------
    val restSoundEnabled: StateFlow<Boolean> = settingsRepository.restSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleRestSound() {
        viewModelScope.launch { settingsRepository.setRestSoundEnabled(!restSoundEnabled.value) }
    }

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

    // -- "Última vez" per exercise, keyed by exerciseId (roadmap 1.3) ----------
    private val _lastSessions = MutableStateFlow<Map<String, List<ExerciseSet>>>(emptyMap())
    val lastSessions: StateFlow<Map<String, List<ExerciseSet>>> = _lastSessions

    // -- Estimated calories for the current session (roadmap 1.7) --------------
    val estimatedCalories: StateFlow<Int> = combine(
        _exercises, _durationSeconds, nutritionRepository.getProfile()
    ) { exercises, seconds, profile ->
        val weightKg = profile?.weightKg ?: 75f
        val hours = seconds / 3600f
        val completedSets = exercises.sumOf { ex -> ex.sets.count { it.completed } }
        if (hours <= 0f || completedSets == 0) 0
        else {
            // Simplified MET model (roadmap 1.7): MET 5.0 is a reasonable average for
            // general resistance training. A dedicated per-bodyPart MET table is a
            // natural follow-up once this is validated with real usage.
            val met = 5.0f
            (met * weightKg * hours).toInt()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // -- Detail view -----------------------------------------------------------
    private val _selectedWorkout = MutableStateFlow<Workout?>(null)
    val selectedWorkout: StateFlow<Workout?> = _selectedWorkout

    // -- Duplicate/rename result -------------------------------------------------
    private val _duplicatedWorkoutId = MutableStateFlow<Long?>(null)
    val duplicatedWorkoutId: StateFlow<Long?> = _duplicatedWorkoutId

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

    fun updateSetRpe(exerciseIndex: Int, setIndex: Int, rpe: Int?) {
        val current = _exercises.value.toMutableList()
        val ex = current[exerciseIndex]
        val newSets = ex.sets.toMutableList()
        newSets[setIndex] = newSets[setIndex].copy(rpe = rpe)
        current[exerciseIndex] = ex.copy(sets = newSets)
        _exercises.value = current
    }

    fun updateExerciseNotes(exerciseIndex: Int, notes: String) {
        val current = _exercises.value.toMutableList()
        current[exerciseIndex] = current[exerciseIndex].copy(notes = notes)
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

    // -- Finish active session ---------------------------------------------------
    // Persists the session's actual logged sets (reps, weight, RPE, completed flags,
    // notes) instead of only flipping isCompleted — otherwise everything entered during
    // the workout was silently discarded. onComplete reports whether a PR was achieved,
    // so the UI can react (e.g. haptics) after the save has actually finished.
    fun finishSession(workoutId: Long, onComplete: (achievedPr: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            stopSessionTimer()
            val achievedPr = workoutRepository.completeWorkout(
                workoutId, _durationSeconds.value / 60, _exercises.value
            )
            onComplete(achievedPr)
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
                loadLastSessions(workoutId, w.exercises)
            }
        }
    }

    private fun loadLastSessions(currentWorkoutId: Long, exercises: List<WorkoutExercise>) {
        viewModelScope.launch {
            val result = mutableMapOf<String, List<ExerciseSet>>()
            exercises.forEach { ex ->
                val sets = workoutRepository.getLastSession(ex.exerciseId, currentWorkoutId)
                if (sets.isNotEmpty()) result[ex.exerciseId] = sets
            }
            _lastSessions.value = result
        }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch { workoutRepository.deleteWorkout(id) }
    }

    // -- Roadmap 2.1: duplicate ---------------------------------------------------
    fun duplicateWorkout(id: Long) {
        viewModelScope.launch {
            _duplicatedWorkoutId.value = workoutRepository.duplicateWorkout(id)
        }
    }

    fun clearDuplicatedWorkoutId() { _duplicatedWorkoutId.value = null }

    // -- Roadmap 2.2: rename -------------------------------------------------------
    fun renameWorkout(id: Long, newName: String) {
        viewModelScope.launch {
            workoutRepository.renameWorkout(id, newName)
            loadWorkoutDetail(id)
        }
    }

    fun loadWorkoutDetail(id: Long) {
        viewModelScope.launch {
            _selectedWorkout.value = workoutRepository.getWorkoutById(id)
        }
    }

    fun clearError() { _errorMessage.value = null }
    fun resetSaveState() { _saveSuccess.value = false; _savedWorkoutId.value = null }
}
