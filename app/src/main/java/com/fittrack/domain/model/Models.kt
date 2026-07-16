package com.fittrack.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

// --- Workout ----------------------------------------------------------------

data class Workout(
    val id: Long = 0,
    val name: String,
    val date: LocalDateTime = LocalDateTime.now(),
    val durationMinutes: Int = 0,
    val notes: String = "",
    val exercises: List<WorkoutExercise> = emptyList()
)

data class WorkoutExercise(
    val id: Long = 0,
    val workoutId: Long = 0,
    val exerciseId: String,
    val exerciseName: String,
    val bodyPart: String,
    val targetMuscle: String,
    val order: Int = 0,
    val sets: List<ExerciseSet> = emptyList()
)

data class ExerciseSet(
    val id: Long = 0,
    val workoutExerciseId: Long = 0,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val completed: Boolean = false,
    val restSeconds: Int = 90
)

// --- Exercise (from API) ----------------------------------------------------

data class Exercise(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val target: String,           // primary muscle
    val secondaryMuscles: List<String> = emptyList(),
    val gifUrl: String = "",
    val instructions: List<String> = emptyList()
)

// --- Nutrition --------------------------------------------------------------

data class FoodItem(
    val id: String,
    val name: String,
    val brand: String = "",
    val calories: Float,          // per 100g
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float = 0f,
    val sugar: Float = 0f,
    val imageUrl: String = "",
    val servingSize: Float = 100f // grams
)

data class FoodLog(
    val id: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val foodName: String,
    val foodId: String,
    val mealType: MealType,
    val grams: Float,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float = 0f
)

enum class MealType(val label: String) {
    BREAKFAST("Pequeno-almoço"),
    LUNCH("Almoço"),
    DINNER("Jantar"),
    SNACK("Lanche")
}

// --- User Profile ------------------------------------------------------------

data class UserProfile(
    val id: Long = 1,
    val name: String = "",
    val weightKg: Float = 75f,
    val heightCm: Float = 175f,
    val age: Int = 25,
    val gender: String = "M",
    val goalCalories: Int = 2200,
    val goalProtein: Int = 160,
    val goalCarbs: Int = 250,
    val goalFat: Int = 70,
    val goalFiber: Int = 30
)

// --- Analytics ---------------------------------------------------------------

data class WeeklyStats(
    val weekStart: LocalDate,
    val totalWorkouts: Int,
    val totalVolume: Float,         // kg lifted
    val avgDuration: Int,
    val caloriesAvg: Float
)

data class ExerciseProgress(
    val exerciseName: String,
    val dataPoints: List<ProgressPoint>
)

data class ProgressPoint(
    val date: LocalDate,
    val maxWeightKg: Float,
    val totalVolume: Float          // sets × reps × weight
)

// --- Muscle Group Mapping ----------------------------------------------------

enum class MuscleGroup(val displayName: String, val bodyParts: List<String>) {
    CHEST("Peito",      listOf("chest")),
    BACK("Costas",      listOf("back")),
    SHOULDERS("Ombros", listOf("shoulders")),
    BICEPS("Bicípite",  listOf("upper arms")),
    TRICEPS("Tricípite",listOf("upper arms")),
    FOREARMS("Antebraços", listOf("lower arms")),
    CORE("Core",        listOf("waist", "core")),
    QUADS("Quadricípite",listOf("upper legs")),
    HAMSTRINGS("Posteriores", listOf("upper legs")),
    GLUTES("Glúteos",   listOf("upper legs", "lower legs")),
    CALVES("Gémeos",    listOf("lower legs")),
    NECK("Pescoço",     listOf("neck")),
    CARDIO("Cardio",    listOf("cardio"))
}
