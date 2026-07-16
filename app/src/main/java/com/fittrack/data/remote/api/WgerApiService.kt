package com.fittrack.data.remote.api

import com.fittrack.data.remote.dto.WgerExerciseInfoDto
import com.fittrack.data.remote.dto.WgerLanguageDto
import com.fittrack.data.remote.dto.WgerPageDto
import retrofit2.http.GET
import retrofit2.http.Query

// Wger's public endpoints (exercises, languages, muscles, equipment, categories)
// need no authentication and no API key — unlike ExerciseDB, which this replaces.
interface WgerApiService {

    @GET("language/")
    suspend fun getLanguages(
        @Query("limit") limit: Int = 100
    ): WgerPageDto<WgerLanguageDto>

    // "language" filters which exercises are returned to ones that actually have a
    // translation in that language — combined with checking translations client-side
    // in WgerExerciseInfoDto.toDomain(), so we never end up with an untranslated name.
    @GET("exerciseinfo/")
    suspend fun getExerciseInfoPage(
        @Query("language") languageId: Int,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): WgerPageDto<WgerExerciseInfoDto>
}
