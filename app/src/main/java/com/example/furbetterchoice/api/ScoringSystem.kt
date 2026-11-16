package com.example.furbetterchoice.api

import kotlin.math.roundToInt

data class ScoreResult(
    val percentage: Int,
    val rating: String,
    val interpretation: String,
    val criteriaScores: Map<String, Int>
)

object ScoringSystem {

    fun calculateEcoScore(product: Product): ScoreResult {
        val criteria = mutableMapOf<String, Int>()
        
        // 1. Packaging Sustainability
        var sustainability = 1
        val pkgText = (product.packagingText ?: "").lowercase()
        val pkgTags = product.packagingTags ?: emptyList()
        
        if (pkgText.contains("biodegradable") || pkgText.contains("compostable")) {
            sustainability = 5
        } else if (pkgText.contains("recycled") || pkgTags.any { it.contains("recycled") }) {
            sustainability = 4
        } else if (pkgText.contains("paper") || pkgText.contains("cardboard")) {
            sustainability = 3
        } else if (pkgText.contains("plastic")) {
            sustainability = 2
        }
        criteria["Packaging Sustainability"] = sustainability

        // 2. Recyclability
        var recyclability = 1
        if (pkgTags.any { it.contains("recyclable") } && pkgText.contains("recycle")) {
            recyclability = 5
        } else if (pkgTags.any { it.contains("recyclable") }) {
            recyclability = 4
        } else if (pkgTags.isNotEmpty()) {
            recyclability = 3
        } else if (pkgText.isNotEmpty()) {
            recyclability = 2
        }
        criteria["Recyclability"] = recyclability

        // 3. Environmental Information
        var envInfo = 1
        val labels = product.labelsTags ?: emptyList()
        if (labels.size >= 2) {
            envInfo = 5
        } else if (labels.isNotEmpty()) {
            envInfo = 4
        } else if (pkgText.contains("eco") || pkgText.contains("environment")) {
            envInfo = 3
        } else if (product.ecoscoreGrade != null) {
            envInfo = 2
        }
        criteria["Environmental Information"] = envInfo

        val totalPoints = sustainability + recyclability + envInfo
        val percentage = ((totalPoints / 15.0) * 100).roundToInt()

        val (rating, interpretation) = when {
            percentage >= 90 -> "Excellent" to "Highly favorable eco-friendly characteristics"
            percentage >= 75 -> "Good" to "Generally favorable eco-friendly characteristics"
            percentage >= 50 -> "Fair" to "Indicates some favorable eco-friendly characteristics"
            percentage >= 25 -> "Poor" to "Indicates limited favorable eco-friendly characteristics"
            percentage >= 1 -> "Very Poor" to "Indicates very limited favorable eco-friendly characteristics"
            else -> "Insufficient Data" to "No sufficient information to determine eco-friendliness rating"
        }

        return ScoreResult(percentage, rating, interpretation, criteria)
    }

    fun calculateNutritionalScore(product: Product): ScoreResult {
        val assessment = NutritionAssessmentEngine.assess(product)
        return calculateNutritionalScore(product, assessment)
    }

    fun calculateNutritionalScore(product: Product, assessment: NutritionAssessment): ScoreResult {
        val criteria = mutableMapOf<String, Int>()
        val byKey = assessment.nutrientAssessments.associateBy { it.nutrientKey }

        val coreKeys = listOf("crudeProtein", "crudeFat", "calcium", "phosphorus")
        val coreAssessments = coreKeys.mapNotNull { byKey[it] }
        val disclosedCoreCount = coreAssessments.count { it.asFedValue != null }
        val belowCount = coreAssessments.count { it.status == NutritionStatus.BELOW } +
            if (assessment.caRatioStatus == NutritionStatus.BELOW) 1 else 0
        val aboveMaxCount = coreAssessments.count { it.status == NutritionStatus.ABOVE_MAXIMUM } +
            if (assessment.caRatioStatus == NutritionStatus.ABOVE_MAXIMUM) 1 else 0

        // 1. Nutritional Adequacy (Crude Fiber and Crude Ash intentionally excluded)
        val adequacy = when {
            assessment.isTreatOrComplementary -> {
                when {
                    disclosedCoreCount >= 2 -> 3
                    disclosedCoreCount == 1 -> 2
                    else -> 1
                }
            }
            assessment.overallStatus == NutritionStatus.MEETS -> 5
            aboveMaxCount > 0 && belowCount > 0 -> 1
            aboveMaxCount > 0 -> 2
            belowCount >= 2 -> 1
            belowCount == 1 -> if (disclosedCoreCount >= 3) 3 else 2
            disclosedCoreCount >= 3 -> 4
            disclosedCoreCount == 2 -> 3
            disclosedCoreCount == 1 -> 2
            else -> 1
        }
        criteria["Nutritional Adequacy"] = adequacy

        // 2. Nutritional Analysis (Essential disclosures only; Crude Fiber & Crude Ash excluded)
        var essentialDisclosureCount = disclosedCoreCount
        if (assessment.isMoistureAvailable) essentialDisclosureCount++
        if (assessment.energyResult.source == EnergySource.LABEL_DECLARED) essentialDisclosureCount++
        if (assessment.targetPetType == "Cat" && byKey["taurine"]?.asFedValue != null) essentialDisclosureCount++

        val analysis = when {
            essentialDisclosureCount >= 5 -> 5
            essentialDisclosureCount == 4 -> 4
            essentialDisclosureCount == 3 -> 3
            essentialDisclosureCount in 1..2 -> 2
            else -> 1
        }
        criteria["Nutritional Analysis"] = analysis

        // 3. Ingredient Quality & Transparency (quality-based rather than raw character length)
        val ingredients = product.ingredientsText
        val prodName = product.productName
        val quality = IngredientAnalyzer.evaluateIngredientQualitySummary(ingredients)
        val transparency = when {
            quality.totalCount == 0 -> if (prodName.isNotBlank()) 2 else 1
            else -> {
                var pts = 3
                if (quality.hasNamedAnimalProteinInTop3) pts += 1
                if (quality.totalCount >= 4 && quality.syntheticAdditiveCount == 0 && quality.unnamedByProductCount == 0) {
                    pts += 1
                }
                if (quality.syntheticAdditiveCount > 0) pts -= 1
                if (quality.unnamedByProductCount > 0 && !quality.hasNamedAnimalProteinInTop3) pts -= 1
                pts.coerceIn(1, 5)
            }
        }
        criteria["Ingredient Quality"] = transparency

        val totalPoints = adequacy + analysis + transparency
        val percentage = ((totalPoints / 15.0) * 100).roundToInt()

        val (rating, interpretation) = when {
            percentage >= 90 -> "Excellent" to "Highly favorable nutritional characteristics based on evaluated criteria"
            percentage >= 75 -> "Good" to "Generally favorable nutritional characteristics"
            percentage >= 50 -> "Fair" to "Indicates some favorable nutritional characteristics"
            percentage >= 25 -> "Poor" to "Indicates limited favorable nutritional characteristics"
            percentage >= 1 -> "Very Poor" to "Indicates very limited favorable nutritional characteristics"
            else -> "Insufficient Data" to "No sufficient information to determine nutritional rating"
        }

        return ScoreResult(percentage, rating, interpretation, criteria)
    }
}
