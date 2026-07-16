package com.fittrack.data.local.entity

import androidx.room.*
import java.time.LocalDate
import java.time.LocalDateTime

// --- Type Converters ---------------------------------------------------------

class Converters {
    @TypeConverter fun fromDateTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it) }
    @TypeConverter fun toDateTime(dt: LocalDateTime?): String? = dt?.toString()

    @TypeConverter fun fromDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it) }
    @TypeConverter fun toDate(d: LocalDate?): String? = d?.toString()

    @TypeConverter fun fromStringList(value: String?): List<String> =
        value?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
    @TypeConverter fun toStringList(list: List<String>?): String =
        list?.joinToString("||") ?: ""
}

// --- Workout ------------------------------------------------------------------

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: LocalDateTime,
    val durationMinutes: Int = 0,
    val notes: String = "",
    val isCompleted: Boolean = false
)

// --- Workout Exercise --------------------------------------------------------

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val bodyPart: String,
    val targetMuscle: String,
    val gifUrl: String = "",
    val order: Int = 0
)

// --- Exercise Set ------------------------------------------------------------

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutExerciseId")]
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val completed: Boolean = false,
    val restSeconds: Int = 90
)

// --- Food Log -----------------------------------------------------------------

@Entity(tableName = "food_logs")
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val foodName: String,
    val foodId: String = "",
    val mealType: String,
    val grams: Float,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float = 0f,
    val imageUrl: String = ""
)

// --- User Profile -------------------------------------------------------------

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Long = 1,
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

// --- Personal Record ----------------------------------------------------------

@Entity(tableName = "personal_records", indices = [Index("exerciseId")])
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    val exerciseName: String,
    val maxWeightKg: Float,
    val repsAtMax: Int,
    val date: LocalDate
)

// --- Relation helpers ---------------------------------------------------------

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val exercises: List<WorkoutExerciseWithSets>
)

data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutExerciseId"
    )
    val sets: List<ExerciseSetEntity>
)
