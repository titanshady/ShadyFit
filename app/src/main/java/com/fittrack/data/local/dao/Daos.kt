package com.fittrack.data.local.dao

import androidx.room.*
import com.fittrack.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

// --- Workout DAO --------------------------------------------------------------

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAllWorkouts(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: Long): WorkoutWithExercises?

    @Transaction
    @Query("SELECT * FROM workouts WHERE date >= :from ORDER BY date DESC")
    fun getWorkoutsSince(from: LocalDateTime): Flow<List<WorkoutWithExercises>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)

    @Query("SELECT COUNT(*) FROM workouts WHERE isCompleted = 1")
    fun getTotalWorkoutsCount(): Flow<Int>

    @Query("SELECT * FROM workouts WHERE isCompleted = 1 ORDER BY date DESC LIMIT 5")
    fun getRecentWorkouts(): Flow<List<WorkoutEntity>>
}

// --- Workout Exercise DAO -----------------------------------------------------

@Dao
interface WorkoutExerciseDao {
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY `order`")
    suspend fun getExercisesForWorkout(workoutId: Long): List<WorkoutExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: WorkoutExerciseEntity)

    @Delete
    suspend fun deleteExercise(exercise: WorkoutExerciseEntity)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteAllForWorkout(workoutId: Long)
}

// --- Exercise Set DAO ---------------------------------------------------------

@Dao
interface ExerciseSetDao {
    @Query("SELECT * FROM exercise_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber")
    suspend fun getSetsForExercise(workoutExerciseId: Long): List<ExerciseSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: ExerciseSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<ExerciseSetEntity>)

    @Update
    suspend fun updateSet(set: ExerciseSetEntity)

    @Delete
    suspend fun deleteSet(set: ExerciseSetEntity)

    @Query("DELETE FROM exercise_sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun deleteAllForExercise(workoutExerciseId: Long)
}

// --- Food Log DAO -------------------------------------------------------------

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_logs WHERE date = :date ORDER BY mealType")
    fun getFoodLogsForDate(date: LocalDate): Flow<List<FoodLogEntity>>

    @Query("SELECT SUM(calories) FROM food_logs WHERE date = :date")
    fun getTotalCaloriesForDate(date: LocalDate): Flow<Float?>

    @Query("SELECT SUM(protein) FROM food_logs WHERE date = :date")
    fun getTotalProteinForDate(date: LocalDate): Flow<Float?>

    @Query("SELECT * FROM food_logs WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getFoodLogsBetween(from: LocalDate, to: LocalDate): Flow<List<FoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodLog(log: FoodLogEntity): Long

    @Delete
    suspend fun deleteFoodLog(log: FoodLogEntity)

    @Query("SELECT date, SUM(calories) as cals FROM food_logs GROUP BY date ORDER BY date DESC LIMIT 30")
    fun getDailyCalorieSummary(): Flow<List<DailyCalories>>
}

data class DailyCalories(val date: LocalDate, val cals: Float)

// --- User Profile DAO --------------------------------------------------------

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)
}

// --- Personal Record DAO -----------------------------------------------------

@Dao
interface PersonalRecordDao {
    @Query("SELECT * FROM personal_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId ORDER BY date ASC")
    fun getProgressForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: PersonalRecordEntity)

    @Query("SELECT MAX(maxWeightKg) FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun getMaxWeightForExercise(exerciseId: String): Float?
}

// --- Analytics helpers -------------------------------------------------------

@Dao
interface AnalyticsDao {
    @Query("""
        SELECT es.weightKg * es.reps as volume
        FROM exercise_sets es
        JOIN workout_exercises we ON es.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE w.isCompleted = 1
    """)
    fun getAllVolumes(): Flow<List<Float>>

    @Query("""
        SELECT w.date, SUM(es.weightKg * es.reps) as totalVolume
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id
        JOIN exercise_sets es ON es.workoutExerciseId = we.id
        WHERE w.isCompleted = 1
        GROUP BY w.id
        ORDER BY w.date ASC
        LIMIT 20
    """)
    fun getVolumeOverTime(): Flow<List<VolumePoint>>

    @Query("""
        SELECT we.exerciseName, we.bodyPart, COUNT(*) as count
        FROM workout_exercises we
        JOIN workouts w ON we.workoutId = w.id
        WHERE w.isCompleted = 1
        GROUP BY we.exerciseId
        ORDER BY count DESC
        LIMIT 10
    """)
    fun getTopExercises(): Flow<List<ExerciseFrequency>>
}

data class VolumePoint(val date: LocalDateTime, val totalVolume: Float)
data class ExerciseFrequency(val exerciseName: String, val bodyPart: String, val count: Int)
