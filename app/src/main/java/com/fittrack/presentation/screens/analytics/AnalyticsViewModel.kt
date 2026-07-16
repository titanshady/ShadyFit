package com.fittrack.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.local.dao.AnalyticsDao
import com.fittrack.data.local.dao.ExerciseFrequency
import com.fittrack.data.local.dao.VolumePoint
import com.fittrack.data.local.entity.PersonalRecordEntity
import com.fittrack.data.repository.NutritionRepository
import com.fittrack.data.repository.WorkoutRepository
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
}
