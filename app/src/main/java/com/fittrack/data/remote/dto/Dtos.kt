package com.fittrack.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.fittrack.domain.model.FoodItem

// --- Food DTOs ----------------------------------------------------------------

// Open Food Facts v2 /api/v2/search returns { products: [...] }
data class FoodSearchResponse(
    @SerializedName("products") val products: List<FoodProductDto> = emptyList(),
    @SerializedName("count")    val count: Int = 0,
    @SerializedName("page")     val page: Int = 1
)

// Open Food Facts v2 /api/v2/product/{barcode} returns { product: {...} }
data class FoodProductResponse(
    @SerializedName("product") val product: FoodProductDto? = null,
    @SerializedName("status")  val status: Int = 0
)

data class FoodProductDto(
    @SerializedName("product_name")           val productName: String = "",
    @SerializedName("brands")                 val brands: String = "",
    @SerializedName("image_front_small_url")  val imageUrl: String = "",
    @SerializedName("image_url")              val imageUrlFallback: String = "",
    @SerializedName("serving_size")           val servingSize: String = "",
    @SerializedName("nutriments")             val nutriments: NutrimentsDto = NutrimentsDto(),
    @SerializedName("_id")                    val id: String = ""
) {
    fun toDomain(): FoodItem {
        val serving = servingSize.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 100f
        val image = imageUrl.ifBlank { imageUrlFallback }
        // Prefer _100g values, fall back to non-suffixed
        val kcal   = nutriments.energyKcal100g   ?: nutriments.energyKcalServing ?: nutriments.energyKcal ?: 0f
        val protein= nutriments.proteins100g      ?: nutriments.proteinsServing   ?: nutriments.proteins   ?: 0f
        val carbs  = nutriments.carbohydrates100g ?: nutriments.carbsServing      ?: nutriments.carbs      ?: 0f
        val fat    = nutriments.fat100g           ?: nutriments.fatServing        ?: nutriments.fat        ?: 0f
        val fiber  = nutriments.fiber100g         ?: nutriments.fiberServing      ?: nutriments.fiber      ?: 0f
        val sugar  = nutriments.sugars100g        ?: nutriments.sugarsServing     ?: nutriments.sugars     ?: 0f

        return FoodItem(
            id          = id,
            name        = productName.ifBlank { "Produto desconhecido" },
            brand       = brands,
            calories    = kcal,
            protein     = protein,
            carbs       = carbs,
            fat         = fat,
            fiber       = fiber,
            sugar       = sugar,
            imageUrl    = image,
            servingSize = serving
        )
    }
}

data class NutrimentsDto(
    // per 100g
    @SerializedName("energy-kcal_100g")   val energyKcal100g: Float?    = null,
    @SerializedName("proteins_100g")      val proteins100g: Float?       = null,
    @SerializedName("carbohydrates_100g") val carbohydrates100g: Float?  = null,
    @SerializedName("fat_100g")           val fat100g: Float?            = null,
    @SerializedName("fiber_100g")         val fiber100g: Float?          = null,
    @SerializedName("sugars_100g")        val sugars100g: Float?         = null,
    // per serving
    @SerializedName("energy-kcal_serving")   val energyKcalServing: Float? = null,
    @SerializedName("proteins_serving")      val proteinsServing: Float?   = null,
    @SerializedName("carbohydrates_serving") val carbsServing: Float?      = null,
    @SerializedName("fat_serving")           val fatServing: Float?        = null,
    @SerializedName("fiber_serving")         val fiberServing: Float?      = null,
    @SerializedName("sugars_serving")        val sugarsServing: Float?     = null,
    // fallback (no suffix)
    @SerializedName("energy-kcal")        val energyKcal: Float?         = null,
    @SerializedName("proteins")           val proteins: Float?            = null,
    @SerializedName("carbohydrates")      val carbs: Float?               = null,
    @SerializedName("fat")                val fat: Float?                 = null,
    @SerializedName("fiber")              val fiber: Float?               = null,
    @SerializedName("sugars")             val sugars: Float?              = null
)
