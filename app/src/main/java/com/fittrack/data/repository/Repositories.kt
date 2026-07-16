package com.fittrack.data.repository

import com.fittrack.data.local.dao.*
import com.fittrack.data.local.entity.*
import com.fittrack.data.remote.api.ExerciseApiService
import com.fittrack.data.remote.api.FoodApiService
import com.fittrack.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// --- Workout Repository -------------------------------------------------------

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val personalRecordDao: PersonalRecordDao,
    private val analyticsDao: AnalyticsDao
) {
    fun getAllWorkouts(): Flow<List<Workout>> =
        workoutDao.getAllWorkouts().map { list -> list.map { it.toDomain() } }

    fun getRecentWorkouts(): Flow<List<WorkoutEntity>> = workoutDao.getRecentWorkouts()

    fun getTotalWorkoutsCount(): Flow<Int> = workoutDao.getTotalWorkoutsCount()

    suspend fun getWorkoutById(id: Long): Workout? =
        workoutDao.getWorkoutById(id)?.toDomain()

    suspend fun saveWorkout(workout: Workout): Long {
        val entity = WorkoutEntity(
            id = workout.id,
            name = workout.name,
            date = workout.date,
            durationMinutes = workout.durationMinutes,
            notes = workout.notes,
            isCompleted = false
        )
        val workoutId = workoutDao.insertWorkout(entity)

        // Delete old exercises if editing
        if (workout.id != 0L) {
            workoutExerciseDao.deleteAllForWorkout(workout.id)
        }

        workout.exercises.forEachIndexed { index, we ->
            val exerciseEntity = WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = we.exerciseId,
                exerciseName = we.exerciseName,
                bodyPart = we.bodyPart,
                targetMuscle = we.targetMuscle,
                gifUrl = "",
                order = index,
                notes = we.notes
            )
            val exerciseId = workoutExerciseDao.insertExercise(exerciseEntity)
            val sets = we.sets.map { set ->
                ExerciseSetEntity(
                    workoutExerciseId = exerciseId,
                    setNumber = set.setNumber,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    completed = set.completed,
                    restSeconds = set.restSeconds,
                    rpe = set.rpe
                )
            }
            exerciseSetDao.insertSets(sets)

            // Update personal records
            sets.filter { it.completed }.forEach { set ->
                updatePersonalRecordIfNeeded(we.exerciseId, we.exerciseName, set.weightKg, set.reps)
            }
        }
        return workoutId
    }

    /** Upserts a new personal record if `weightKg` beats the current max for this exercise.
     *  Returns true if a new record was actually set — used to trigger PR celebrations in the UI. */
    private suspend fun updatePersonalRecordIfNeeded(
        exerciseId: String, exerciseName: String, weightKg: Float, reps: Int
    ): Boolean {
        val current = personalRecordDao.getMaxWeightForExercise(exerciseId) ?: 0f
        if (weightKg > current) {
            personalRecordDao.upsertRecord(
                PersonalRecordEntity(
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    maxWeightKg = weightKg,
                    repsAtMax = reps,
                    date = LocalDate.now()
                )
            )
            return true
        }
        return false
    }

    // -- Persist the actual logged session (sets, notes, RPE) when a workout finishes.
    // Previously this only flipped `isCompleted`/`durationMinutes`, silently discarding
    // everything entered in ActiveWorkoutScreen (reps, weights, completed sets, RPE, PRs).
    // Returns true if at least one new personal record was achieved during this session.
    suspend fun completeWorkout(workoutId: Long, durationMinutes: Int, exercises: List<WorkoutExercise>): Boolean {
        val workout = workoutDao.getWorkoutById(workoutId)?.workout ?: return false
        workoutDao.updateWorkout(workout.copy(isCompleted = true, durationMinutes = durationMinutes))

        val existingExercises = workoutExerciseDao.getExercisesForWorkout(workoutId)
        var achievedPr = false

        exercises.forEachIndexed { index, we ->
            val entity = existingExercises.getOrNull(index) ?: return@forEachIndexed

            if (entity.notes != we.notes) {
                workoutExerciseDao.updateExercise(entity.copy(notes = we.notes))
            }

            // Replace with what was actually logged during the session
            exerciseSetDao.deleteAllForExercise(entity.id)
            val setEntities = we.sets.map { set ->
                ExerciseSetEntity(
                    workoutExerciseId = entity.id,
                    setNumber = set.setNumber,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    completed = set.completed,
                    restSeconds = set.restSeconds,
                    rpe = set.rpe
                )
            }
            exerciseSetDao.insertSets(setEntities)

            setEntities.filter { it.completed }.forEach { set ->
                if (updatePersonalRecordIfNeeded(we.exerciseId, we.exerciseName, set.weightKg, set.reps)) {
                    achievedPr = true
                }
            }
        }
        return achievedPr
    }

    suspend fun deleteWorkout(workoutId: Long) {
        val workout = workoutDao.getWorkoutById(workoutId)?.workout ?: return
        workoutDao.deleteWorkout(workout)
    }

    // -- Roadmap 2.1: duplicate a saved workout as a brand-new template ----------
    suspend fun duplicateWorkout(workoutId: Long): Long? {
        val original = workoutDao.getWorkoutById(workoutId)?.toDomain() ?: return null
        val copy = original.copy(
            id = 0L,
            name = "${original.name} (cópia)",
            date = LocalDateTime.now(),
            durationMinutes = 0,
            exercises = original.exercises.map { ex ->
                ex.copy(sets = ex.sets.map { it.copy(completed = false) })
            }
        )
        return saveWorkout(copy)
    }

    // -- Roadmap 2.2: quick rename without reopening the full editor ------------
    suspend fun renameWorkout(workoutId: Long, newName: String) {
        val workout = workoutDao.getWorkoutById(workoutId)?.workout ?: return
        workoutDao.updateWorkout(workout.copy(name = newName))
    }

    fun getPersonalRecords(): Flow<List<PersonalRecordEntity>> =
        personalRecordDao.getAllRecords()

    fun getProgressForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>> =
        personalRecordDao.getProgressForExercise(exerciseId)

    // -- Roadmap 3.1: full history for a single exercise -------------------------
    fun getExerciseHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>> =
        analyticsDao.getExerciseHistory(exerciseId)

    // -- Roadmap 1.3: "última vez" shown during an active workout ----------------
    suspend fun getLastSession(exerciseId: String, excludeWorkoutId: Long): List<ExerciseSet> =
        exerciseSetDao.getLastSessionSets(exerciseId, excludeWorkoutId).map { it.toDomain() }

    // -- Roadmap 5.1 / 5.2: streak + weekly frequency, computed client-side ------
    fun getStreakInfo(weeklyGoal: Int): Flow<StreakInfo> =
        workoutDao.getAllCompletedWorkoutDates().map { dates ->
            computeStreak(dates.map { it.toLocalDate() }, weeklyGoal)
        }

    // -- Roadmap 3.4 / 3.5: volume grouped by ISO week, for charts + comparisons -
    fun getWeeklyVolume(): Flow<List<Pair<String, Float>>> =
        analyticsDao.getAllVolumeOverTime().map { points -> groupVolumeByWeek(points) }

    fun getAverageDurationMinutes(): Flow<Float?> = workoutDao.getAverageDurationMinutes()
}

private fun computeStreak(datesDesc: List<LocalDate>, weeklyGoal: Int): StreakInfo {
    if (datesDesc.isEmpty()) return StreakInfo(0, 0, weeklyGoal)
    val distinctDays = datesDesc.distinct().sortedDescending()

    // Current streak: consecutive calendar days ending today or yesterday
    var streak = 0
    var cursor = LocalDate.now()
    if (distinctDays.first() == cursor || distinctDays.first() == cursor.minusDays(1)) {
        cursor = distinctDays.first()
        for (day in distinctDays) {
            if (day == cursor) {
                streak++
                cursor = cursor.minusDays(1)
            } else if (day.isBefore(cursor)) {
                break
            }
        }
    }

    val weekFields = WeekFields.of(Locale.getDefault())
    val today = LocalDate.now()
    val thisWeekCount = distinctDays.count {
        it.get(weekFields.weekOfWeekBasedYear()) == today.get(weekFields.weekOfWeekBasedYear()) &&
            it.get(weekFields.weekBasedYear()) == today.get(weekFields.weekBasedYear())
    }

    return StreakInfo(currentStreakDays = streak, workoutsThisWeek = thisWeekCount, weeklyGoal = weeklyGoal)
}

private fun groupVolumeByWeek(points: List<VolumePoint>): List<Pair<String, Float>> {
    val weekFields = WeekFields.of(Locale.getDefault())
    return points
        .groupBy { p ->
            val d = p.date.toLocalDate()
            "${d.get(weekFields.weekBasedYear())}-W${d.get(weekFields.weekOfWeekBasedYear())}"
        }
        .toSortedMap()
        .map { (week, pts) -> week to pts.sumOf { it.totalVolume.toDouble() }.toFloat() }
}

// --- Exercise Repository ------------------------------------------------------

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseApi: ExerciseApiService,
    private val cachedExerciseDao: CachedExerciseDao,
    private val favoriteExerciseDao: FavoriteExerciseDao
) {
    // Free ExerciseDB plan allows only 10 requests/day — a cache that survives process death
    // is close to mandatory (roadmap item 7.5). 24h is generous enough that a normal day of
    // use won't re-hit the API, while still refreshing periodically.
    private val cacheTtlHours = 24L

    suspend fun getExercises(bodyPart: String? = null, offset: Int = 0): Result<List<Exercise>> =
        runCatching {
            val cacheKey = "list_${bodyPart}_$offset"
            readCache(cacheKey)?.let { return Result.success(it) }

            val dtos = if (bodyPart != null)
                exerciseApi.getExercisesByBodyPart(bodyPart, offset = offset)
            else
                exerciseApi.getAllExercises(offset = offset)
            val exercises = dtos.map { it.toDomain() }
            writeCache(cacheKey, exercises)
            exercises
        }

    suspend fun searchExercises(query: String): Result<List<Exercise>> =
        runCatching {
            val cacheKey = "search_${query.lowercase()}"
            readCache(cacheKey)?.let { return Result.success(it) }
            val exercises = exerciseApi.searchExercises(query).map { it.toDomain() }
            writeCache(cacheKey, exercises)
            exercises
        }

    suspend fun getExerciseById(id: String): Result<Exercise> =
        runCatching {
            cachedExerciseDao.getExerciseById(id)?.toDomain()
                ?: exerciseApi.getExerciseById(id).toDomain().also { writeCache("single_$id", listOf(it)) }
        }

    suspend fun getBodyParts(): Result<List<String>> =
        runCatching { exerciseApi.getBodyPartList() }

    private suspend fun readCache(key: String): List<Exercise>? {
        val query = cachedExerciseDao.getQuery(key) ?: return null
        val ageHours = ChronoUnit.HOURS.between(query.cachedAt, LocalDateTime.now())
        if (ageHours >= cacheTtlHours) return null
        val cachedExercises = cachedExerciseDao.getExercisesByIds(query.exerciseIds)
            .associateBy { it.exerciseId }
        // Preserve original order; if any id is missing (shouldn't normally happen) treat as a miss.
        val ordered = query.exerciseIds.mapNotNull { cachedExercises[it] }
        if (ordered.size != query.exerciseIds.size) return null
        return ordered.map { it.toDomain() }
    }

    private suspend fun writeCache(key: String, exercises: List<Exercise>) {
        if (exercises.isEmpty()) return
        val now = LocalDateTime.now()
        cachedExerciseDao.upsertExercises(exercises.map { it.toCachedEntity(now) })
        cachedExerciseDao.upsertQuery(
            CachedExerciseQueryEntity(queryKey = key, exerciseIds = exercises.map { it.id }, cachedAt = now)
        )
    }

    // -- Roadmap 7.1: favorites --------------------------------------------------
    fun getFavorites(): Flow<List<FavoriteExerciseEntity>> = favoriteExerciseDao.getAllFavorites()

    fun isFavorite(exerciseId: String): Flow<Boolean> = favoriteExerciseDao.isFavorite(exerciseId)

    suspend fun toggleFavorite(exercise: Exercise, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            favoriteExerciseDao.removeFavorite(exercise.id)
        } else {
            favoriteExerciseDao.addFavorite(
                FavoriteExerciseEntity(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    bodyPart = exercise.bodyPart,
                    addedAt = LocalDateTime.now()
                )
            )
        }
    }

    // Fallback local data when API key not configured
    fun getLocalExercises(): List<Exercise> = listOf(
        Exercise("0001", "Supino com Barra", "chest", "barbell", "pectorals",
            listOf("triceps", "delts"), instructions = listOf(
                "Deita-te no banco com os pés no chão.",
                "Agarra a barra com pega ligeiramente mais larga que os ombros.",
                "Baixa a barra ao peito de forma controlada.",
                "Empurra a barra para cima até os braços ficarem estendidos."
            )),
        Exercise("0002", "Agachamento com Barra", "upper legs", "barbell", "quads",
            listOf("glutes", "hamstrings"), instructions = listOf(
                "Coloca a barra nos trapézios.",
                "Afasta os pés à largura dos ombros.",
                "Desce até as coxas ficarem paralelas ao chão.",
                "Sobe empurrando pelo calcanhar."
            )),
        Exercise("0003", "Peso Morto", "back", "barbell", "spine",
            listOf("glutes", "hamstrings"), instructions = listOf(
                "Coloca os pés à largura dos ombros debaixo da barra.",
                "Agarra a barra com pega dupla ou alterna.",
                "Mantém as costas direitas e levanta a barra.",
                "Desce de forma controlada ao chão."
            )),
        Exercise("0004", "Puxada na Barra Fixa", "back", "body weight", "lats",
            listOf("biceps", "traps"), instructions = listOf(
                "Agarra a barra com pega pronada, mais larga que os ombros.",
                "Puxa o corpo para cima até o queixo passar a barra.",
                "Desce de forma controlada.",
                "Repete sem tocar no chão."
            )),
        Exercise("0005", "Desenvolvimento com Halteres", "shoulders", "dumbbell", "delts",
            listOf("triceps", "traps"), instructions = listOf(
                "Senta-te ou fica de pé com um haltere em cada mão.",
                "Começa com os halteres ao nível dos ombros.",
                "Empurra os halteres para cima até os braços ficarem estendidos.",
                "Baixa de forma controlada."
            )),
        Exercise("0006", "Rosca Direta com Barra", "upper arms", "barbell", "biceps",
            listOf("brachialis", "brachioradialis"), instructions = listOf(
                "Segura a barra com pega supinada, mãos à largura dos ombros.",
                "Mantém os cotovelos junto ao corpo.",
                "Curva os braços levantando a barra até aos ombros.",
                "Baixa de forma controlada."
            )),
        Exercise("0007", "Tricípite na Polia", "upper arms", "cable", "triceps",
            listOf("anconeus"), instructions = listOf(
                "Agarra a corda ou barra na polia alta.",
                "Mantém os cotovelos junto ao corpo.",
                "Estende os braços para baixo até ficarem retos.",
                "Volta à posição inicial de forma controlada."
            )),
        Exercise("0008", "Leg Press", "upper legs", "machine", "quads",
            listOf("glutes", "hamstrings"), instructions = listOf(
                "Senta-te na máquina com os pés na plataforma.",
                "Afasta os pés à largura dos ombros.",
                "Empurra a plataforma para a frente estendendo as pernas.",
                "Volta à posição inicial com controlo."
            )),
        Exercise("0009", "Prancha", "waist", "body weight", "abs",
            listOf("obliques", "spine"), instructions = listOf(
                "Deita-te de barriga para baixo.",
                "Apoia nos antebraços e pontas dos pés.",
                "Mantém o corpo em linha reta.",
                "Segura a posição o máximo de tempo possível."
            )),
        Exercise("0010", "Extensão de Gémeos", "lower legs", "machine", "calves",
            listOf("tibialis anterior"), instructions = listOf(
                "Coloca-te na máquina de extensão de gémeos.",
                "Coloca os dedos dos pés na plataforma.",
                "Eleva os calcanhares o máximo possível.",
                "Baixa de forma controlada."
            )),
    )
}

// --- Nutrition Repository -----------------------------------------------------

@Singleton
class NutritionRepository @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val userProfileDao: UserProfileDao,
    private val foodApi: FoodApiService
) {
    fun getFoodLogsForDate(date: LocalDate): Flow<List<FoodLog>> =
        foodLogDao.getFoodLogsForDate(date).map { list -> list.map { it.toDomain() } }

    fun getTotalCaloriesForDate(date: LocalDate): Flow<Float?> =
        foodLogDao.getTotalCaloriesForDate(date)

    fun getProfile(): Flow<UserProfile?> =
        userProfileDao.getProfile().map { it?.toDomain() }

    suspend fun upsertProfile(profile: UserProfile) =
        userProfileDao.upsertProfile(profile.toEntity())

    suspend fun addFoodLog(log: FoodLog): Long =
        foodLogDao.insertFoodLog(log.toEntity())

    suspend fun deleteFoodLog(log: FoodLog) =
        foodLogDao.deleteFoodLog(log.toEntity())

    fun getDailyCalorieSummary(): Flow<List<DailyCalories>> =
        foodLogDao.getDailyCalorieSummary()

    suspend fun searchFood(query: String): Result<List<FoodItem>> =
        runCatching {
            foodApi.searchFood(query).products
                .filter { it.productName.isNotBlank() }
                .map { it.toDomain() }
        }

    suspend fun getProductByBarcode(barcode: String): Result<FoodItem> =
        runCatching {
            foodApi.getProductByBarcode(barcode).product?.toDomain()
                ?: throw Exception("Produto não encontrado")
        }
}

// --- Mappers -----------------------------------------------------------------

private fun WorkoutWithExercises.toDomain() = Workout(
    id = workout.id,
    name = workout.name,
    date = workout.date,
    durationMinutes = workout.durationMinutes,
    notes = workout.notes,
    exercises = exercises.map { it.toDomain() }
)

private fun WorkoutExerciseWithSets.toDomain() = WorkoutExercise(
    id = workoutExercise.id,
    workoutId = workoutExercise.workoutId,
    exerciseId = workoutExercise.exerciseId,
    exerciseName = workoutExercise.exerciseName,
    bodyPart = workoutExercise.bodyPart,
    targetMuscle = workoutExercise.targetMuscle,
    order = workoutExercise.order,
    sets = sets.map { it.toDomain() },
    notes = workoutExercise.notes
)

private fun ExerciseSetEntity.toDomain() = ExerciseSet(
    id = id,
    workoutExerciseId = workoutExerciseId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    completed = completed,
    restSeconds = restSeconds,
    rpe = rpe
)

private fun FoodLogEntity.toDomain() = FoodLog(
    id = id,
    date = date,
    foodName = foodName,
    foodId = foodId,
    mealType = MealType.valueOf(mealType),
    grams = grams,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber
)

private fun FoodLog.toEntity() = FoodLogEntity(
    id = id,
    date = date,
    foodName = foodName,
    foodId = foodId,
    mealType = mealType.name,
    grams = grams,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber
)

private fun UserProfileEntity.toDomain() = UserProfile(
    id = id, name = name, weightKg = weightKg, heightCm = heightCm,
    age = age, gender = gender, goalCalories = goalCalories,
    goalProtein = goalProtein, goalCarbs = goalCarbs, goalFat = goalFat, goalFiber = goalFiber,
    weeklyGoal = weeklyGoal
)

private fun UserProfile.toEntity() = UserProfileEntity(
    id = id, name = name, weightKg = weightKg, heightCm = heightCm,
    age = age, gender = gender, goalCalories = goalCalories,
    goalProtein = goalProtein, goalCarbs = goalCarbs, goalFat = goalFat, goalFiber = goalFiber,
    weeklyGoal = weeklyGoal
)

private fun CachedExerciseEntity.toDomain() = Exercise(
    id = exerciseId, name = name, bodyPart = bodyPart, equipment = equipment, target = target,
    secondaryMuscles = secondaryMuscles, gifUrl = gifUrl, instructions = instructions
)

private fun Exercise.toCachedEntity(cachedAt: LocalDateTime) = CachedExerciseEntity(
    exerciseId = id, name = name, bodyPart = bodyPart, equipment = equipment, target = target,
    secondaryMuscles = secondaryMuscles, gifUrl = gifUrl, instructions = instructions, cachedAt = cachedAt
)
