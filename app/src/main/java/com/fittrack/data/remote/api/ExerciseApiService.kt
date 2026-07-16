package com.fittrack.data.remote.api

import com.fittrack.data.remote.dto.ExerciseDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import com.fittrack.BuildConfig

interface ExerciseApiService {

    // ExerciseDB via RapidAPI
    @Headers(
        "X-RapidAPI-Key: ${BuildConfig.EXERCISEDB_API_KEY}",
        "X-RapidAPI-Host: exercisedb.p.rapidapi.com"
    )
    @GET("exercises")
    suspend fun getAllExercises(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): List<ExerciseDto>

    @Headers(
        "X-RapidAPI-Key: ${BuildConfig.EXERCISEDB_API_KEY}",
        "X-RapidAPI-Host: exercisedb.p.rapidapi.com"
    )
    @GET("exercises/bodyPart/{bodyPart}")
    suspend fun getExercisesByBodyPart(
        @Path("bodyPart") bodyPart: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<ExerciseDto>

    @Headers(
        "X-RapidAPI-Key: ${BuildConfig.EXERCISEDB_API_KEY}",
        "X-RapidAPI-Host: exercisedb.p.rapidapi.com"
    )
    @GET("exercises/exercise/{id}")
    suspend fun getExerciseById(@Path("id") id: String): ExerciseDto

    @Headers(
        "X-RapidAPI-Key: ${BuildConfig.EXERCISEDB_API_KEY}",
        "X-RapidAPI-Host: exercisedb.p.rapidapi.com"
    )
    @GET("exercises/name/{name}")
    suspend fun searchExercises(
        @Path("name") name: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): List<ExerciseDto>

    @Headers(
        "X-RapidAPI-Key: ${BuildConfig.EXERCISEDB_API_KEY}",
        "X-RapidAPI-Host: exercisedb.p.rapidapi.com"
    )
    @GET("exercises/target/{target}")
    suspend fun getExercisesByTarget(
        @Path("target") target: String,
        @Query("limit") limit: Int = 50
    ): List<ExerciseDto>

    @Headers(
        "X-RapidAPI-Key: ${BuildConfig.EXERCISEDB_API_KEY}",
        "X-RapidAPI-Host: exercisedb.p.rapidapi.com"
    )
    @GET("exercises/bodyPartList")
    suspend fun getBodyPartList(): List<String>
}
