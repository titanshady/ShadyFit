package com.fittrack.data.remote.api

import com.fittrack.data.remote.dto.FoodSearchResponse
import com.fittrack.data.remote.dto.FoodProductResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FoodApiService {

    @GET("api/v2/search")
    suspend fun searchFood(
        @Query("search_terms") query: String,
        @Query("fields") fields: String = "product_name,brands,nutriments,image_front_small_url,serving_size,_id",
        @Query("page_size") pageSize: Int = 30,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "unique_scans_n"
    ): FoodSearchResponse

    // Barcode lookup
    @GET("api/v2/product/{barcode}")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,brands,nutriments,image_front_small_url,serving_size"
    ): FoodProductResponse
}
