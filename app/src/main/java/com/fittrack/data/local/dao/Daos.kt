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

    // Used to compute streaks / weekly frequency client-side (roadmap 5.1, 5.2) — grouping
    // by consecutive calendar days is awkward in SQLite, much simpler in Kotlin.
    @Query("SELECT date FROM workouts WHERE isCompleted = 1 ORDER BY date DESC")
    fun getAllCompletedWorkoutDates(): Flow<List<LocalDateTime>>

    @Query("SELECT AVG(durationMinutes) FROM workouts WHERE isCompleted = 1 AND durationMinutes > 0")
    fun getAverageDurationMinutes(): Flow<Float?>
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

    // "Última vez" (roadmap 1.3) — sets from the most recent *other* completed session
    // that included this exercise, so ActiveWorkoutScreen can show "última vez: 60kg x 8".
    @Query(
        """
        SELECT es.* FROM exercise_sets es
        JOIN workout_exercises we ON es.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND w.isCompleted = 1 AND w.id != :excludeWorkoutId
          AND w.id = (
              SELECT w2.id FROM workouts w2
              JOIN workout_exercises we2 ON we2.workoutId = w2.id
              WHERE we2.exerciseId = :exerciseId AND w2.isCompleted = 1 AND w2.id != :excludeWorkoutId
              ORDER BY w2.date DESC LIMIT 1
          )
        ORDER BY es.setNumber ASC
        """
    )
    suspend fun getLastSessionSets(exerciseId: String, excludeWorkoutId: Long): List<ExerciseSetEntity>
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

// --- Favorite Exercise DAO ----------------------------------------------------

@Dao
interface FavoriteExerciseDao {
    @Query("SELECT * FROM favorite_exercises ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteExerciseEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_exercises WHERE exerciseId = :exerciseId)")
    fun isFavorite(exerciseId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteExerciseEntity)

    @Query("DELETE FROM favorite_exercises WHERE exerciseId = :exerciseId")
    suspend fun removeFavorite(exerciseId: String)
}

// --- Cached Exercise DAO (Room-backed replacement for the old in-memory map) --

// Roadmap: permanent local exercise library synced from Wger — see ExerciseRepository.
// syncAllExercisesFromWger(). No longer a time-limited cache (hence no more "queries" table
// keyed by request params): once synced, every read here is a plain offline Room query.
//
// LAZY LOADING:
// - Sync stores only metadata (hasImage = false, gifUrl = "")
// - Search/filter returns exercises without images (fast, no network)
// - When user clicks: downloadExerciseImage() fetches GIF lazily
// - After download: hasImage = true, gifUrl = "file://..." (persisted)
@Dao
interface CachedExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercises(exercises: List<CachedExerciseEntity>)

    @Query("SELECT * FROM cached_exercises ORDER BY name ASC")
    suspend fun getAll(): List<CachedExerciseEntity>

    @Query("SELECT * FROM cached_exercises WHERE bodyPart = :bodyPart ORDER BY name ASC")
    suspend fun getByBodyPart(bodyPart: String): List<CachedExerciseEntity>

    @Query("""
        SELECT * FROM cached_exercises
        WHERE name LIKE '%' || :query || '%' OR target LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    suspend fun search(query: String): List<CachedExerciseEntity>

    @Query("SELECT * FROM cached_exercises WHERE exerciseId = :id")
    suspend fun getExerciseById(id: String): CachedExerciseEntity?

    @Query("SELECT DISTINCT bodyPart FROM cached_exercises ORDER BY bodyPart ASC")
    suspend fun getDistinctBodyParts(): List<String>

    @Query("SELECT COUNT(*) FROM cached_exercises")
    suspend fun count(): Int

    // --- Lazy loading support ---

    /** Update exercise with downloaded image info. Called after imageDownloader.download() succeeds. */
    @Query("UPDATE cached_exercises SET gifUrl = :localImagePath, hasImage = 1 WHERE exerciseId = :exerciseId")
    suspend fun markImageDownloaded(exerciseId: String, localImagePath: String)

    /** Check if a specific exercise's image is already cached locally. */
    @Query("SELECT hasImage FROM cached_exercises WHERE exerciseId = :exerciseId")
    suspend fun hasImageCached(exerciseId: String): Boolean

    /** Get all exercises whose images haven't been downloaded yet (useful for preload strategies). */
    @Query("SELECT * FROM cached_exercises WHERE hasImage = 0 ORDER BY name ASC")
    suspend fun getExercisesWithoutImages(): List<CachedExerciseEntity>
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

    // Same as above but without the LIMIT 20 — used for weekly/monthly aggregation and
    // week-over-week comparison (roadmap 3.4, 3.5), which needs the full history.
    @Query("""
        SELECT w.date, SUM(es.weightKg * es.reps) as totalVolume
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id
        JOIN exercise_sets es ON es.workoutExerciseId = we.id
        WHERE w.isCompleted = 1
        GROUP BY w.id
        ORDER BY w.date ASC
    """)
    fun getAllVolumeOverTime(): Flow<List<VolumePoint>>

    @Query("""
        SELECT we.exerciseId, we.exerciseName, we.bodyPart, COUNT(*) as count
        FROM workout_exercises we
        JOIN workouts w ON we.workoutId = w.id
        WHERE w.isCompleted = 1
        GROUP BY we.exerciseId
        ORDER BY count DESC
        LIMIT 10
    """)
    fun getTopExercises(): Flow<List<ExerciseFrequency>>

    // Full history of a single exercise across every completed workout (roadmap 3.1),
    // newest first, used by the exercise history screen.
    @Query("""
        SELECT w.id as workoutId, w.date as date, es.setNumber as setNumber,
               es.reps as reps, es.weightKg as weightKg, es.completed as completed, es.rpe as rpe
        FROM exercise_sets es
        JOIN workout_exercises we ON es.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND w.isCompleted = 1
        ORDER BY w.date DESC, es.setNumber ASC
    """)
    fun getExerciseHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>>
}

data class VolumePoint(val date: LocalDateTime, val totalVolume: Float)
data class ExerciseFrequency(val exerciseId: String, val exerciseName: String, val bodyPart: String, val count: Int)
data class ExerciseHistoryRow(
    val workoutId: Long,
    val date: LocalDateTime,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val completed: Boolean,
    val rpe: Int?
)
