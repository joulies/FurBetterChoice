package com.example.furbetterchoice.api

data class RecommendedNutrition(
    val protein: Double,
    val fat: Double,
    val fiber: Double,
    val ash: Double,
    val carbohydrates: Double? = null
)

object NutritionUtils {
    // Standard AAFCO / general veterinary recommendations (approximate percentages)
    val DOG_RECOMMENDATION = RecommendedNutrition(
        protein = 18.0,
        fat = 5.0,
        fiber = 4.0,
        ash = 7.0,
        carbohydrates = 45.0
    )

    val CAT_RECOMMENDATION = RecommendedNutrition(
        protein = 26.0,
        fat = 9.0,
        fiber = 4.0,
        ash = 7.0,
        carbohydrates = 35.0
    )

    fun getRecommendation(petType: String?): RecommendedNutrition {
        return when (petType?.lowercase()) {
            "cat" -> CAT_RECOMMENDATION
            else -> DOG_RECOMMENDATION // Default to dog
        }
    }
}
