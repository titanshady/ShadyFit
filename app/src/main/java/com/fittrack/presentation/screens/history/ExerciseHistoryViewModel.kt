package com.fittrack.presentation.screens.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.local.dao.ExerciseHistoryRow
import com.fittrack.data.local.entity.PersonalRecordEntity
import com.fittrack.data.repository.WorkoutRepository
import com.fittrack.domain.model.OneRepMaxPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val exerciseId: String = savedStateHandle.get<String>("exerciseId") ?: ""
    val exerciseName: String = savedStateHandle.get<String>("exerciseName")
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: ""

    // Roadmap 3.1: raw history of every set ever logged for this exercise
    val history: StateFlow<List<ExerciseHistoryRow>> =
        workoutRepository.getExerciseHistory(exerciseId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Roadmap 3.2: max weight per session, already tracked in PersonalRecordDao — just consumed here
    val progress: StateFlow<List<PersonalRecordEntity>> =
        workoutRepository.getProgressForExercise(exerciseId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Roadmap 3.3: estimated 1RM (Epley formula) + best single-set volume, computed
    // client-side from the completed sets in the history — no schema change needed.
    val oneRepMaxProgress: StateFlow<List<OneRepMaxPoint>> = history.map { rows ->
        rows.filter { it.completed && it.reps > 0 }
            .groupBy { it.date.toLocalDate() }
            .map { (date, sets) ->
                val best = sets.maxByOrNull { epley1RM(it.weightKg, it.reps) }!!
                OneRepMaxPoint(
                    date = date,
                    estimated1RM = epley1RM(best.weightKg, best.reps),
                    weightKg = best.weightKg,
                    reps = best.reps
                )
            }
            .sortedBy { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bestSingleSetVolume: StateFlow<Float> = history.map { rows ->
        rows.filter { it.completed }.maxOfOrNull { it.weightKg * it.reps } ?: 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)
}

/** Epley formula: 1RM = weight × (1 + reps / 30). Reasonably accurate for reps up to ~10-12. */
fun epley1RM(weightKg: Float, reps: Int): Float = weightKg * (1f + reps / 30f)
