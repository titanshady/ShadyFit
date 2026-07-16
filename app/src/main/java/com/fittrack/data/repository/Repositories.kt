package com.fittrack.data.repository

import com.fittrack.data.local.ExerciseImageDownloader
import com.fittrack.data.local.dao.*
import com.fittrack.data.local.entity.*
import com.fittrack.data.remote.api.FoodApiService
import com.fittrack.data.remote.api.WgerApiService
import com.fittrack.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
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
                gifUrl = we.gifUrl,
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
    private val wgerApi: WgerApiService,
    private val cachedExerciseDao: CachedExerciseDao,
    private val favoriteExerciseDao: FavoriteExerciseDao,
    private val imageDownloader: ExerciseImageDownloader
) {
    // -- Offline reads ------------------------------------------------------------
    // Once syncAllExercisesFromWger() has run, every read here is a plain local Room
    // query — no network involved at all, matching the "100% offline" requirement.

    suspend fun getExercises(bodyPart: String? = null, offset: Int = 0): Result<List<Exercise>> =
        runCatching {
            if (offset > 0) return@runCatching emptyList() // everything is local now — no paging needed
            val entities = if (bodyPart != null) cachedExerciseDao.getByBodyPart(bodyPart) else cachedExerciseDao.getAll()
            entities.map { it.toDomain() }
        }

    suspend fun searchExercises(query: String): Result<List<Exercise>> =
        runCatching { cachedExerciseDao.search(query).map { it.toDomain() } }

    suspend fun getExerciseById(id: String): Result<Exercise> =
        runCatching {
            cachedExerciseDao.getExerciseById(id)?.toDomain()
                ?: throw NoSuchElementException("Exercise $id not found in local library")
        }

    suspend fun getBodyParts(): Result<List<String>> =
        runCatching { cachedExerciseDao.getDistinctBodyParts() }

    /** Whether the one-time (or manually re-run) Wger sync has ever completed. */
    suspend fun hasSyncedLibrary(): Boolean = cachedExerciseDao.count() > 0

    // -- Wger sync ------------------------------------------------------------------
    // Downloads every Portuguese-translated exercise (metadata + demonstration image) from
    // Wger and stores it permanently in Room, so the exercise library works fully offline
    // afterwards. This is the only place in the exercise feature that touches the network.
    //
    // onProgress reports (processed, total) so the UI can show "A transferir X de Y".
    suspend fun syncAllExercisesFromWger(onProgress: (Int, Int) -> Unit = { _, _ -> }): Result<Int> =
        runCatching {
            val ptLanguageId = findPortugueseLanguageId()
                ?: throw IllegalStateException("Wger não devolveu um idioma Português")

            var offset = 0
            val pageSize = 100
            var total = 1 // unknown until the first page comes back; avoids a 0/0 progress flash
            var synced = 0

            while (true) {
                val page = wgerApi.getExerciseInfoPage(languageId = ptLanguageId, limit = pageSize, offset = offset)
                total = page.count.takeIf { it > 0 } ?: total

                val batch = page.results.mapNotNull { dto ->
                    val localImage = imageDownloader.download(dto.id, dto.mainImageUrl)
                    dto.toDomain(ptLanguageId, localImage)
                }
                if (batch.isNotEmpty()) {
                    cachedExerciseDao.upsertExercises(batch.map { it.toCachedEntity(LocalDateTime.now()) })
                    synced += batch.size
                }
                offset += pageSize
                onProgress(offset.coerceAtMost(total), total)

                if (page.next.isNullOrBlank() || page.results.isEmpty()) break
            }
            synced
        }

    private suspend fun findPortugueseLanguageId(): Int? {
        val languages = wgerApi.getLanguages(limit = 100).results
        // Wger's short_name is a two-letter code ("pt"); some community mirrors also expose
        // regional variants like "pt-br" — accept either rather than assuming one exact code.
        return languages.firstOrNull { it.shortName.equals("pt", ignoreCase = true) }?.id
            ?: languages.firstOrNull { it.shortName.startsWith("pt", ignoreCase = true) }?.id
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

    // Fallback local data — used only before the very first Wger sync completes, so the
    // screen is never completely empty while the download is in progress.
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
        // --- Peito -----------------------------------------------------------
        Exercise("0011", "Supino Inclinado com Halteres", "chest", "dumbbell", "pectorals",
            listOf("triceps", "delts"), instructions = listOf(
                "Deita-te num banco inclinado a 30-45°.",
                "Segura um haltere em cada mão ao nível do peito.",
                "Empurra os halteres para cima até os braços ficarem estendidos.",
                "Baixa de forma controlada."
            )),
        Exercise("0012", "Crucifixo com Halteres", "chest", "dumbbell", "pectorals",
            listOf("delts"), instructions = listOf(
                "Deita-te num banco plano com um haltere em cada mão.",
                "Estende os braços acima do peito com ligeira flexão nos cotovelos.",
                "Abre os braços em arco até sentires alongamento no peito.",
                "Volta à posição inicial contraindo o peito."
            )),
        Exercise("0013", "Flexão de Braços", "chest", "body weight", "pectorals",
            listOf("triceps", "delts"), instructions = listOf(
                "Apoia as mãos no chão, ligeiramente mais largas que os ombros.",
                "Mantém o corpo em linha reta da cabeça aos pés.",
                "Desce o peito em direção ao chão.",
                "Empurra de volta à posição inicial."
            )),
        Exercise("0014", "Peck Deck", "chest", "machine", "pectorals",
            listOf("delts"), instructions = listOf(
                "Senta-te na máquina com as costas apoiadas.",
                "Agarra as pegas com os cotovelos ligeiramente flectidos.",
                "Junta os braços à frente do peito.",
                "Volta à posição inicial de forma controlada."
            )),
        Exercise("0015", "Cross Over no Cabo", "chest", "cable", "pectorals",
            listOf("delts"), instructions = listOf(
                "Fica de pé no centro da estação de cabos, um cabo em cada mão.",
                "Inclina o tronco ligeiramente à frente.",
                "Junta as mãos à frente do corpo num movimento em arco.",
                "Volta à posição inicial de forma controlada."
            )),
        // --- Costas ------------------------------------------------------------
        Exercise("0016", "Remada Curvada com Barra", "back", "barbell", "lats",
            listOf("biceps", "traps"), instructions = listOf(
                "Fica de pé com o tronco inclinado à frente, joelhos ligeiramente flectidos.",
                "Agarra a barra com pega pronada.",
                "Puxa a barra em direção ao abdómen.",
                "Baixa de forma controlada."
            )),
        Exercise("0017", "Remada Baixa no Cabo", "back", "cable", "lats",
            listOf("biceps", "traps"), instructions = listOf(
                "Senta-te na máquina com os pés apoiados na plataforma.",
                "Agarra a pega com os braços estendidos.",
                "Puxa a pega em direção ao abdómen, cotovelos junto ao corpo.",
                "Volta à posição inicial de forma controlada."
            )),
        Exercise("0018", "Puxada Alta (Pulley)", "back", "cable", "lats",
            listOf("biceps", "traps"), instructions = listOf(
                "Senta-te na máquina com as coxas fixas debaixo do apoio.",
                "Agarra a barra com pega pronada, mais larga que os ombros.",
                "Puxa a barra em direção à parte superior do peito.",
                "Volta à posição inicial de forma controlada."
            )),
        Exercise("0019", "Remada Unilateral com Haltere", "back", "dumbbell", "lats",
            listOf("biceps", "traps"), instructions = listOf(
                "Apoia um joelho e uma mão num banco.",
                "Segura o haltere com o outro braço, totalmente estendido.",
                "Puxa o haltere em direção à anca.",
                "Baixa de forma controlada."
            )),
        Exercise("0020", "Hiperextensão Lombar", "back", "body weight", "spine",
            listOf("glutes", "hamstrings"), instructions = listOf(
                "Deita-te de barriga para baixo no banco de hiperextensão.",
                "Cruza os braços à frente do peito.",
                "Baixa o tronco de forma controlada.",
                "Eleva o tronco até ficar alinhado com as pernas."
            )),
        // --- Pernas (superior) --------------------------------------------------
        Exercise("0021", "Cadeira Extensora", "upper legs", "machine", "quads",
            emptyList(), instructions = listOf(
                "Senta-te na máquina com os tornozelos atrás do apoio.",
                "Estende as pernas até ficarem retas.",
                "Segura um instante no topo.",
                "Baixa de forma controlada."
            )),
        Exercise("0022", "Cadeira Flexora", "upper legs", "machine", "hamstrings",
            emptyList(), instructions = listOf(
                "Deita-te de barriga para baixo na máquina.",
                "Coloca os tornozelos debaixo do apoio.",
                "Flecte os joelhos puxando o apoio em direção aos glúteos.",
                "Volta à posição inicial de forma controlada."
            )),
        Exercise("0023", "Afundo com Halteres", "upper legs", "dumbbell", "quads",
            listOf("glutes", "hamstrings"), instructions = listOf(
                "Fica de pé com um haltere em cada mão.",
                "Dá um passo em frente e desce o joelho de trás até perto do chão.",
                "Empurra de volta à posição inicial.",
                "Repete alternando a perna."
            )),
        Exercise("0024", "Agachamento Búlgaro", "upper legs", "dumbbell", "quads",
            listOf("glutes", "hamstrings"), instructions = listOf(
                "Fica de costas para um banco e apoia um pé nele.",
                "Segura um haltere em cada mão.",
                "Desce até a coxa da perna da frente ficar paralela ao chão.",
                "Sobe empurrando pelo calcanhar."
            )),
        Exercise("0025", "Cadeira Adutora", "upper legs", "machine", "adductors",
            emptyList(), instructions = listOf(
                "Senta-te na máquina com as pernas apoiadas nas almofadas.",
                "Junta as pernas contra a resistência.",
                "Segura um instante.",
                "Volta à posição inicial de forma controlada."
            )),
        // --- Pernas (inferior) ---------------------------------------------------
        Exercise("0026", "Elevação de Gémeos em Pé", "lower legs", "machine", "calves",
            emptyList(), instructions = listOf(
                "Fica de pé na máquina com os ombros debaixo do apoio.",
                "Eleva os calcanhares o máximo possível.",
                "Segura um instante no topo.",
                "Baixa de forma controlada além da posição neutra."
            )),
        Exercise("0027", "Elevação de Gémeos Sentado", "lower legs", "machine", "calves",
            emptyList(), instructions = listOf(
                "Senta-te na máquina com os joelhos debaixo do apoio.",
                "Eleva os calcanhares o máximo possível.",
                "Segura um instante no topo.",
                "Baixa de forma controlada."
            )),
        // --- Ombros --------------------------------------------------------------
        Exercise("0028", "Elevação Lateral com Halteres", "shoulders", "dumbbell", "delts",
            emptyList(), instructions = listOf(
                "Fica de pé com um haltere em cada mão ao lado do corpo.",
                "Eleva os braços lateralmente até à altura dos ombros.",
                "Segura um instante no topo.",
                "Baixa de forma controlada."
            )),
        Exercise("0029", "Elevação Frontal com Halteres", "shoulders", "dumbbell", "delts",
            emptyList(), instructions = listOf(
                "Fica de pé com um haltere em cada mão à frente das coxas.",
                "Eleva um braço à frente até à altura dos ombros.",
                "Baixa de forma controlada.",
                "Repete alternando o braço."
            )),
        Exercise("0030", "Remada Alta com Barra", "shoulders", "barbell", "delts",
            listOf("traps"), instructions = listOf(
                "Fica de pé com a barra à frente das coxas, pega fechada.",
                "Puxa a barra ao longo do corpo até à altura do peito.",
                "Mantém os cotovelos acima dos pulsos.",
                "Baixa de forma controlada."
            )),
        Exercise("0031", "Desenvolvimento Militar com Barra", "shoulders", "barbell", "delts",
            listOf("triceps"), instructions = listOf(
                "Fica de pé com a barra à altura dos ombros.",
                "Empurra a barra para cima até os braços ficarem estendidos.",
                "Baixa de forma controlada.",
                "Mantém o core contraído durante o movimento."
            )),
        Exercise("0032", "Encolhimento com Halteres", "shoulders", "dumbbell", "traps",
            emptyList(), instructions = listOf(
                "Fica de pé com um haltere em cada mão ao lado do corpo.",
                "Eleva os ombros em direção às orelhas.",
                "Segura um instante no topo.",
                "Baixa de forma controlada."
            )),
        // --- Braços (superior) -----------------------------------------------------
        Exercise("0033", "Rosca Martelo", "upper arms", "dumbbell", "biceps",
            listOf("brachialis", "brachioradialis"), instructions = listOf(
                "Fica de pé com um haltere em cada mão, pega neutra.",
                "Mantém os cotovelos junto ao corpo.",
                "Curva os braços levantando os halteres até aos ombros.",
                "Baixa de forma controlada."
            )),
        Exercise("0034", "Tríceps Corda no Cabo", "upper arms", "cable", "triceps",
            emptyList(), instructions = listOf(
                "Agarra a corda na polia alta, cotovelos junto ao corpo.",
                "Estende os braços para baixo, afastando as mãos no final.",
                "Segura um instante em baixo.",
                "Volta à posição inicial de forma controlada."
            )),
        Exercise("0035", "Tríceps Testa com Barra", "upper arms", "barbell", "triceps",
            emptyList(), instructions = listOf(
                "Deita-te num banco com a barra estendida acima do peito.",
                "Flecte os cotovelos baixando a barra em direção à testa.",
                "Estende os braços de volta à posição inicial.",
                "Mantém os cotovelos fixos durante o movimento."
            )),
        Exercise("0036", "Mergulho entre Bancos", "upper arms", "body weight", "triceps",
            listOf("delts"), instructions = listOf(
                "Apoia as mãos na borda de um banco atrás de ti.",
                "Estende as pernas à frente.",
                "Desce o corpo flectindo os cotovelos.",
                "Empurra de volta à posição inicial."
            )),
        Exercise("0037", "Rosca Concentrada", "upper arms", "dumbbell", "biceps",
            emptyList(), instructions = listOf(
                "Senta-te num banco com o cotovelo apoiado na coxa.",
                "Segura o haltere com o braço estendido.",
                "Curva o braço levantando o haltere até ao ombro.",
                "Baixa de forma controlada."
            )),
        Exercise("0038", "Rosca Scott", "upper arms", "barbell", "biceps",
            emptyList(), instructions = listOf(
                "Apoia os braços no banco Scott com a barra na mão.",
                "Curva os braços levantando a barra.",
                "Segura um instante no topo.",
                "Baixa de forma controlada."
            )),
        // --- Abdómen ---------------------------------------------------------------
        Exercise("0039", "Abdominal Reto", "waist", "body weight", "abs",
            emptyList(), instructions = listOf(
                "Deita-te de costas com os joelhos flectidos.",
                "Coloca as mãos atrás da cabeça ou cruzadas no peito.",
                "Eleva o tronco em direção aos joelhos.",
                "Baixa de forma controlada."
            )),
        Exercise("0040", "Elevação de Pernas", "waist", "body weight", "abs",
            emptyList(), instructions = listOf(
                "Deita-te de costas com as pernas estendidas.",
                "Eleva as pernas até formarem um ângulo de 90° com o chão.",
                "Baixa de forma controlada sem tocar no chão.",
                "Mantém a lombar pressionada contra o chão."
            )),
        Exercise("0041", "Abdominal Oblíquo", "waist", "body weight", "obliques",
            emptyList(), instructions = listOf(
                "Deita-te de costas com os joelhos flectidos.",
                "Coloca as mãos atrás da cabeça.",
                "Eleva o tronco rodando em direção a um lado.",
                "Repete alternando o lado."
            )),
        Exercise("0042", "Prancha Lateral", "waist", "body weight", "obliques",
            emptyList(), instructions = listOf(
                "Deita-te de lado apoiado no antebraço.",
                "Eleva as ancas até o corpo ficar em linha reta.",
                "Segura a posição o máximo de tempo possível.",
                "Repete do outro lado."
            )),
        Exercise("0043", "Russian Twist", "waist", "body weight", "obliques",
            emptyList(), instructions = listOf(
                "Senta-te no chão com os joelhos flectidos, tronco inclinado atrás.",
                "Eleva ligeiramente os pés do chão.",
                "Roda o tronco de um lado para o outro.",
                "Mantém o core contraído durante o movimento."
            )),
        Exercise("0044", "Abdominal na Máquina", "waist", "machine", "abs",
            emptyList(), instructions = listOf(
                "Senta-te na máquina com o peito apoiado na almofada.",
                "Agarra as pegas laterais.",
                "Flecte o tronco contraindo o abdómen.",
                "Volta à posição inicial de forma controlada."
            )),
        // --- Cardio ------------------------------------------------------------------
        Exercise("0045", "Corrida na Passadeira", "cardio", "machine", "cardiovascular system",
            emptyList(), instructions = listOf(
                "Ajusta a velocidade e inclinação desejadas.",
                "Mantém uma postura ereta durante a corrida.",
                "Respira de forma controlada.",
                "Reduz a velocidade gradualmente no final."
            )),
        Exercise("0046", "Bicicleta Estática", "cardio", "machine", "cardiovascular system",
            listOf("quads", "hamstrings"), instructions = listOf(
                "Ajusta o banco à altura adequada.",
                "Pedala a um ritmo constante.",
                "Ajusta a resistência conforme o objetivo.",
                "Mantém as costas direitas durante o exercício."
            )),
        Exercise("0047", "Burpee", "cardio", "body weight", "cardiovascular system",
            listOf("quads", "pectorals", "delts"), instructions = listOf(
                "Fica de pé e agacha-te apoiando as mãos no chão.",
                "Salta com os pés para trás em posição de prancha.",
                "Faz uma flexão e volta à posição agachada.",
                "Salta para cima com os braços estendidos."
            )),
        Exercise("0048", "Corda de Saltar", "cardio", "body weight", "cardiovascular system",
            listOf("calves"), instructions = listOf(
                "Segura a corda com as mãos ao lado do corpo.",
                "Roda a corda e salta com os dois pés juntos.",
                "Mantém um ritmo constante.",
                "Aterra suavemente sobre a ponta dos pés."
            )),
        Exercise("0049", "Elíptico", "cardio", "machine", "cardiovascular system",
            listOf("quads", "hamstrings", "delts"), instructions = listOf(
                "Coloca os pés nos pedais e segura as pegas.",
                "Move-se num ritmo constante e fluido.",
                "Ajusta a resistência e inclinação conforme o objetivo.",
                "Reduz o ritmo gradualmente no final."
            )),
        Exercise("0050", "Escalador (Mountain Climber)", "cardio", "body weight", "abs",
            listOf("quads", "delts"), instructions = listOf(
                "Coloca-te em posição de prancha alta.",
                "Traz um joelho em direção ao peito.",
                "Alterna rapidamente as pernas.",
                "Mantém o core contraído durante o movimento."
            )),
        // --- Antebraço / Pescoço -----------------------------------------------------
        Exercise("0051", "Rosca de Pulso com Barra", "lower arms", "barbell", "forearms",
            emptyList(), instructions = listOf(
                "Senta-te com os antebraços apoiados nas coxas.",
                "Segura a barra com pega supinada.",
                "Flecte os pulsos elevando a barra.",
                "Baixa de forma controlada."
            )),
        Exercise("0052", "Farmer's Walk", "lower arms", "dumbbell", "forearms",
            listOf("traps", "abs"), instructions = listOf(
                "Segura um haltere pesado em cada mão.",
                "Caminha em linha reta mantendo a postura ereta.",
                "Mantém os ombros para trás e o core contraído.",
                "Continua pela distância ou tempo definido."
            )),
        Exercise("0053", "Flexão de Pescoço com Resistência Manual", "neck", "body weight", "neck",
            emptyList(), instructions = listOf(
                "Senta-te ou fica de pé com boa postura.",
                "Coloca a mão na testa fazendo resistência.",
                "Empurra a cabeça contra a mão sem deixar mover.",
                "Segura alguns segundos e repete noutras direções."
            )),
        Exercise("0054", "Extensão de Pescoço na Máquina", "neck", "machine", "neck",
            emptyList(), instructions = listOf(
                "Senta-te na máquina de pescoço com a almofada atrás da cabeça.",
                "Empurra a cabeça para trás contra a resistência.",
                "Segura um instante.",
                "Volta à posição inicial de forma controlada."
            )),
        Exercise("0055", "Gato-Camelo (Mobilidade)", "back", "body weight", "spine",
            emptyList(), instructions = listOf(
                "Fica de quatro apoios, mãos debaixo dos ombros.",
                "Arqueia as costas para cima, olhando para o umbigo.",
                "Desce a barriga, olhando para cima.",
                "Alterna entre as duas posições de forma lenta."
            )),
    )

    // Persists the built-in fallback catalog into Room so search/filter (which read only
    // from cachedExerciseDao) keep working even when syncAllExercisesFromWger() fails —
    // e.g. wger.de being unreachable or blocked. Always upserts (not just when empty): the
    // IDs are a fixed "00xx" namespace distinct from real Wger IDs, so re-running this after
    // a real sync, or after this built-in list grows in a future update, is harmless and just
    // keeps it current. Exercises seeded this way have no gifUrl; the UI already falls back
    // to a generic icon in that case.
    suspend fun seedLocalLibraryIfEmpty() {
        cachedExerciseDao.upsertExercises(getLocalExercises().map { it.toCachedEntity(LocalDateTime.now()) })
    }
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
    gifUrl = workoutExercise.gifUrl,
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
