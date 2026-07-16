package com.fittrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fittrack.data.local.dao.*
import com.fittrack.data.local.entity.*

@Database(
    entities = [
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        ExerciseSetEntity::class,
        FoodLogEntity::class,
        UserProfileEntity::class,
        PersonalRecordEntity::class,
        FavoriteExerciseEntity::class,
        CachedExerciseEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FitTrackDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao
    abstract fun exerciseSetDao(): ExerciseSetDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun favoriteExerciseDao(): FavoriteExerciseDao
    abstract fun cachedExerciseDao(): CachedExerciseDao
}
