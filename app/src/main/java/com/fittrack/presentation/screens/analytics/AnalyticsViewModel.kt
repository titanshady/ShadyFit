package com.fittrack.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.local.dao.AnalyticsDao
import com.fittrack.data.local.dao.ExerciseFrequency
import com.fittrack.data.local.dao.VolumePoint
import com.fittrack.data.local.entity.PersonalRecordEntity
import com.fittrack.data.repository.NutritionRepository
import com.fittrack.data.repository.WorkoutRepository
import com.fittrack.domain.model.StreakInfo
import com.fittrack.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val nutritionRepository: NutritionRepository,
    private val analyticsDao: AnalyticsDao
) : ViewModel() {

    val totalWorkouts: StateFlow<Int> =
        workoutRepository.getTotalWorkoutsCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val volumeOverTime: StateFlow<List<VolumePoint>> =
        analyticsDao.getVolumeOverTime()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topExercises: StateFlow<List<ExerciseFrequency>> =
        analyticsDao.getTopExercises()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val personalRecords: StateFlow<List<PersonalRecordEntity>> =
        workoutRepository.getPersonalRecords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<UserProfile?> =
        nutritionRepository.getProfile()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val totalVolume: StateFlow<Float> = volumeOverTime.map { pts ->
        pts.sumOf { it.totalVolume.toDouble() }.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    // -- Roadmap 5.1 / 5.2: streak + weekly frequency (also feeds Dashboard) -----
    val streakInfo: StateFlow<StreakInfo> = profile.flatMapLatest { p ->
        workoutRepository.getStreakInfo(p?.weeklyGoal ?: 3)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakInfo())

    // -- Roadmap 5.3 / 5.4: most trained muscle group + favorite exercise --------
    // Both are simple aggregations of getTopExercises(), which already exists and is
    // already sorted by frequency — no new query needed (see roadmap note).
    val mostTrainedMuscleGroup: StateFlow<String?> = topExercises.map { list ->
        list.groupBy { it.bodyPart }
            .maxByOrNull { (_, exs) -> exs.sumOf { it.count } }
            ?.key
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favoriteExerciseName: StateFlow<String?> = topExercises.map { it.firstOrNull()?.exerciseName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // -- Roadmap 3.4 / 3.5: volume grouped by ISO week + week-over-week diff -----
    val weeklyVolume: StateFlow<List<Pair<String, Float>>> =
        workoutRepository.getWeeklyVolume()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** (thisWeek, lastWeek, percentChange) or null if there isn't at least two weeks of data. */
    val weekOverWeekChange: StateFlow<Triple<Float, Float, Float>?> = weeklyVolume.map { weeks ->
        if (weeks.size < 2) return@map null
        val thisWeek = weeks.last().second
        val lastWeek = weeks[weeks.size - 2].second
        val pctChange = if (lastWeek > 0f) ((thisWeek - lastWeek) / lastWeek) * 100f else 0f
        Triple(thisWeek, lastWeek, pctChange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val averageDurationMinutes: StateFlow<Float?> =
        workoutRepository.getAverageDurationMinutes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
