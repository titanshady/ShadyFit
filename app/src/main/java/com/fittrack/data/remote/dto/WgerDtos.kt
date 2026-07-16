package com.fittrack.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.fittrack.domain.model.Exercise

// --- Wger API v3 DTOs ----------------------------------------------------------
// Wger's data model is split in two: a language-agnostic "exercise" (category,
// muscles, equipment, images) plus one "translation" per language (name,
// description). The combined "/exerciseinfo/" endpoint nests both together so we
// don't need a second round-trip per exercise.

data class WgerPageDto<T>(
    @SerializedName("count")    val count: Int = 0,
    @SerializedName("next")     val next: String? = null,
    @SerializedName("previous") val previous: String? = null,
    @SerializedName("results")  val results: List<T> = emptyList()
)

data class WgerLanguageDto(
    @SerializedName("id")         val id: Int = 0,
    @SerializedName("short_name") val shortName: String = "",
    @SerializedName("full_name")  val fullName: String = ""
)

data class WgerCategoryRefDto(
    @SerializedName("id")   val id: Int = 0,
    @SerializedName("name") val name: String = ""
)

data class WgerMuscleRefDto(
    @SerializedName("id")       val id: Int = 0,
    @SerializedName("name")     val name: String = "",     // Latin name, e.g. "Biceps brachii"
    @SerializedName("name_en")  val nameEn: String = "",    // English common name, e.g. "Biceps"
    @SerializedName("is_front") val isFront: Boolean = false
)

data class WgerEquipmentRefDto(
    @SerializedName("id")   val id: Int = 0,
    @SerializedName("name") val name: String = ""
)

data class WgerImageDto(
    @SerializedName("id")      val id: Int = 0,
    @SerializedName("image")   val image: String = "",
    @SerializedName("is_main") val isMain: Boolean = false
)

data class WgerTranslationDto(
    @SerializedName("id")          val id: Int = 0,
    @SerializedName("name")        val name: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("language")    val language: Int = 0
)

data class WgerExerciseInfoDto(
    @SerializedName("id")                val id: Int = 0,
    @SerializedName("category")          val category: WgerCategoryRefDto = WgerCategoryRefDto(),
    @SerializedName("muscles")           val muscles: List<WgerMuscleRefDto> = emptyList(),
    @SerializedName("muscles_secondary") val musclesSecondary: List<WgerMuscleRefDto> = emptyList(),
    @SerializedName("equipment")         val equipment: List<WgerEquipmentRefDto> = emptyList(),
    @SerializedName("images")            val images: List<WgerImageDto> = emptyList(),
    @SerializedName("translations")      val translations: List<WgerTranslationDto> = emptyList()
) {
    /** Picks the Portuguese translation and the main (or first) image.
     *  Returns null if there's no Portuguese translation at all — per the request to
     *  only bring exercises available in Portuguese, we skip those entirely rather
     *  than showing an English/German name the user didn't ask for. */
    fun toDomain(ptLanguageId: Int, localImagePath: String): Exercise? {
        val translation = translations.firstOrNull { it.language == ptLanguageId && it.name.isNotBlank() }
            ?: return null
        return Exercise(
            id               = id.toString(),
            name             = translation.name.replaceFirstChar { it.uppercase() },
            bodyPart         = category.name,
            equipment        = equipment.joinToString(", ") { it.name },
            target           = muscles.firstOrNull()?.nameEn?.ifBlank { muscles.firstOrNull()?.name } ?: category.name,
            secondaryMuscles = musclesSecondary.map { it.nameEn.ifBlank { it.name } },
            gifUrl           = localImagePath,
            instructions     = htmlDescriptionToSteps(translation.description)
        )
    }

    /** The main (or first) image's remote URL, to be downloaded once and cached locally. */
    val mainImageUrl: String
        get() = images.firstOrNull { it.isMain }?.image ?: images.firstOrNull()?.image ?: ""
}

/** Wger descriptions are one HTML blob (e.g. "<p>Step one.</p><p>Step two.</p>") rather
 *  than ExerciseDB's array of plain steps — split on block tags and strip markup so the
 *  rest of the app (which expects List<String>) doesn't need to know the difference. */
private fun htmlDescriptionToSteps(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    return html
        .replace(Regex("(?i)</p>|<br\\s*/?>|</li>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
