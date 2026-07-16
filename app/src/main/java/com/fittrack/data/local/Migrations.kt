package com.fittrack.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations.
 *
 * The original project used `.fallbackToDestructiveMigration()`, which is convenient during
 * early development but wipes the user's entire local database on any schema change once the
 * app is actually being used — since this is a single-user local app, that means losing every
 * workout, food log and personal record the person has logged. From this version on, schema
 * changes go through an explicit [Migration] instead so existing data survives updates.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // WorkoutExerciseEntity: notes per exercise (roadmap 2.5)
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

        // ExerciseSetEntity: optional RPE (roadmap 3.6)
        db.execSQL("ALTER TABLE exercise_sets ADD COLUMN rpe INTEGER")

        // UserProfileEntity: configurable weekly workout goal (roadmap 5.2 / 12.7)
        db.execSQL("ALTER TABLE user_profile ADD COLUMN weeklyGoal INTEGER NOT NULL DEFAULT 3")

        // Favorite exercises (roadmap 7.1)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_exercises (
                exerciseId TEXT NOT NULL PRIMARY KEY,
                exerciseName TEXT NOT NULL,
                bodyPart TEXT NOT NULL,
                addedAt TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Room-backed exercise cache, replacing the old in-memory mutableMapOf (roadmap 7.5)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_exercises (
                exerciseId TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                bodyPart TEXT NOT NULL,
                equipment TEXT NOT NULL,
                target TEXT NOT NULL,
                secondaryMuscles TEXT NOT NULL,
                gifUrl TEXT NOT NULL,
                instructions TEXT NOT NULL,
                cachedAt TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_exercise_queries (
                queryKey TEXT NOT NULL PRIMARY KEY,
                exerciseIds TEXT NOT NULL,
                cachedAt TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

// Roadmap: swapping ExerciseDB for a permanent local Wger sync (see ExerciseRepository).
// cached_exercises stays exactly as it was — same columns, now just permanent instead of
// TTL'd — but cached_exercise_queries (the old "which ids came back for this search" table)
// is no longer needed at all, since every read is now a plain offline query against
// cached_exercises directly.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS cached_exercise_queries")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
