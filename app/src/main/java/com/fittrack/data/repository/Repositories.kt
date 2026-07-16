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
import javax.inject.Inject
import javax.inject.Singleton

// --- Workout Repository -------------------------------------------------------

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val personalRecordDao: PersonalRecordDao
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
                order = index
            )
            val exerciseId = workoutExerciseDao.insertExercise(exerciseEntity)
            val sets = we.sets.map { set ->
                ExerciseSetEntity(
                    workoutExerciseId = exerciseId,
                    setNumber = set.setNumber,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    completed = set.completed,
                    restSeconds = set.restSeconds
                )
            }
            exerciseSetDao.insertSets(sets)

            // Update personal records
            sets.filter { it.completed }.forEach { set ->
                val current = personalRecordDao.getMaxWeightForExercise(we.exerciseId) ?: 0f
                if (set.weightKg > current) {
                    personalRecordDao.upsertRecord(
                        PersonalRecordEntity(
                            exerciseId = we.exerciseId,
                            exerciseName = we.exerciseName,
                            maxWeightKg = set.weightKg,
                            repsAtMax = set.reps,
                            date = LocalDate.now()
                        )
                    )
                }
            }
        }
        return workoutId
    }

    suspend fun completeWorkout(workoutId: Long, durationMinutes: Int) {
        val workout = workoutDao.getWorkoutById(workoutId)?.workout ?: return
        workoutDao.updateWorkout(workout.copy(isCompleted = true, durationMinutes = durationMinutes))
    }

    suspend fun deleteWorkout(workoutId: Long) {
        val workout = workoutDao.getWorkoutById(workoutId)?.workout ?: return
        workoutDao.deleteWorkout(workout)
    }

    fun getPersonalRecords(): Flow<List<PersonalRecordEntity>> =
        personalRecordDao.getAllRecords()

    fun getProgressForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>> =
        personalRecordDao.getProgressForExercise(exerciseId)
}

// --- Exercise Repository ------------------------------------------------------

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseApi: ExerciseApiService
) {
    private val cache = mutableMapOf<String, List<Exercise>>()

    suspend fun getExercises(bodyPart: String? = null, offset: Int = 0): Result<List<Exercise>> =
        runCatching {
            val cacheKey = "${bodyPart}_$offset"
            cache[cacheKey]?.let { return Result.success(it) }
            val dtos = if (bodyPart != null)
                exerciseApi.getExercisesByBodyPart(bodyPart, offset = offset)
            else
                exerciseApi.getAllExercises(offset = offset)
            val exercises = dtos.map { it.toDomain() }
            cache[cacheKey] = exercises
            exercises
        }

    suspend fun searchExercises(query: String): Result<List<Exercise>> =
        runCatching {
            exerciseApi.searchExercises(query).map { it.toDomain() }
        }

    suspend fun getExerciseById(id: String): Result<Exercise> =
        runCatching { exerciseApi.getExerciseById(id).toDomain() }

    suspend fun getBodyParts(): Result<List<String>> =
        runCatching { exerciseApi.getBodyPartList() }

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
    sets = sets.map { it.toDomain() }
)

private fun ExerciseSetEntity.toDomain() = ExerciseSet(
    id = id,
    workoutExerciseId = workoutExerciseId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    completed = completed,
    restSeconds = restSeconds
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
    goalProtein = goalProtein, goalCarbs = goalCarbs, goalFat = goalFat, goalFiber = goalFiber
)

private fun UserProfile.toEntity() = UserProfileEntity(
    id = id, name = name, weightKg = weightKg, heightCm = heightCm,
    age = age, gender = gender, goalCalories = goalCalories,
    goalProtein = goalProtein, goalCarbs = goalCarbs, goalFat = goalFat, goalFiber = goalFiber
)
