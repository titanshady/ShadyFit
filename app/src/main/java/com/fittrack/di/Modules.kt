package com.fittrack.di

import android.content.Context
import androidx.room.Room
import com.fittrack.BuildConfig
import com.fittrack.data.local.FitTrackDatabase
import com.fittrack.data.local.dao.*
import com.fittrack.data.remote.api.ExerciseApiService
import com.fittrack.data.remote.api.FoodApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitTrackDatabase =
        Room.databaseBuilder(context, FitTrackDatabase::class.java, "fittrack.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideWorkoutDao(db: FitTrackDatabase) = db.workoutDao()
    @Provides fun provideWorkoutExerciseDao(db: FitTrackDatabase) = db.workoutExerciseDao()
    @Provides fun provideExerciseSetDao(db: FitTrackDatabase) = db.exerciseSetDao()
    @Provides fun provideFoodLogDao(db: FitTrackDatabase) = db.foodLogDao()
    @Provides fun provideUserProfileDao(db: FitTrackDatabase) = db.userProfileDao()
    @Provides fun providePersonalRecordDao(db: FitTrackDatabase) = db.personalRecordDao()
    @Provides fun provideAnalyticsDao(db: FitTrackDatabase) = db.analyticsDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

    @Provides
    @Singleton
    @Named("exercise")
    fun provideExerciseRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.EXERCISEDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @Named("food")
    fun provideFoodRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.OPENFOODFACTS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideExerciseApiService(@Named("exercise") retrofit: Retrofit): ExerciseApiService =
        retrofit.create(ExerciseApiService::class.java)

    @Provides
    @Singleton
    fun provideFoodApiService(@Named("food") retrofit: Retrofit): FoodApiService =
        retrofit.create(FoodApiService::class.java)
}
