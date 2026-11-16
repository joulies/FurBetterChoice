package com.example.furbetterchoice.api

import java.util.Calendar

data class RankedProduct(
    val product: Product,
    val suitabilityScore: Int,
    val matchReasons: List<String>
)

object FoodRecommender {

    fun rankProducts(pet: Pet, products: List<Product>): List<RankedProduct> {
        return products.map { product ->
            val (score, reasons) = calculateSuitability(pet, product)
            RankedProduct(product, score, reasons)
        }.sortedByDescending { it.suitabilityScore }
    }

    private fun calculateSuitability(pet: Pet, product: Product): Pair<Int, List<String>> {
        var score = 0
        val reasons = mutableListOf<String>()

        val petTypeLower = pet.type.lowercase().trim()
        val categoryTags = product.categoriesTags?.map { it.lowercase() } ?: emptyList()
        val productText = ((product.productName ?: "") + " " +
                (product.brand ?: "") + " " +
                (product.ingredientsText ?: "") + " " +
                categoryTags.joinToString(" ")).lowercase()

        // 1. Strict Pet Species Check (Dog vs. Cat)
        val isTargetCat = petTypeLower.contains("cat")
        val isTargetDog = petTypeLower.contains("dog") || !isTargetCat

        val productSpeciesLower = (product.pet.ifEmpty { product.species }).lowercase()
        val productIsExplicitCat = productSpeciesLower.contains("cat") || categoryTags.any { it.contains("cat") } || productText.contains("cat food") || productText.contains("kitten")
        val productIsExplicitDog = productSpeciesLower.contains("dog") || categoryTags.any { it.contains("dog") } || productText.contains("dog food") || productText.contains("puppy")

        if (isTargetCat && productIsExplicitDog && !productIsExplicitCat) {
            return Pair(0, listOf("Incompatible species (Cat food required)"))
        }
        if (isTargetDog && productIsExplicitCat && !productIsExplicitDog) {
            return Pair(0, listOf("Incompatible species (Dog food required)"))
        }

        if (isTargetCat && productIsExplicitCat) {
            score += 20
        } else if (isTargetDog && productIsExplicitDog) {
            score += 20
        }

        // 2. Allergen Safety Check (Strict Filter against pet.allergies & IngredientAnalyzer groups)
        if (pet.allergies.isNotBlank() && !pet.allergies.equals("None", ignoreCase = true)) {
            val allergenTokens = pet.allergies.lowercase()
                .split(",", ";", "\n", "/")
                .map { it.trim() }
                .filter { it.length >= 3 && it != "none" }

            val ingredientsRaw = product.ingredientsText
            val ingredientQuality = IngredientAnalyzer.evaluateIngredientQualitySummary(ingredientsRaw)
            val allergenGroupsLower = ingredientQuality.allergenGroups.map { it.lowercase() }

            val matchedAllergen = allergenTokens.firstOrNull { allergen ->
                val regex = Regex("""(?<![a-z0-9])${Regex.escape(allergen)}(?![a-z0-9])""", RegexOption.IGNORE_CASE)
                regex.containsMatchIn(ingredientsRaw) ||
                    regex.containsMatchIn(product.productName) ||
                    allergenGroupsLower.any { grp -> grp == allergen || grp.contains(allergen) }
            }

            if (matchedAllergen != null) {
                return Pair(0, listOf("⚠️ Contains allergen (${matchedAllergen.uppercase()})"))
            } else {
                reasons.add("Allergen-safe (${pet.allergies})")
            }
        }

        // 3. FEDIAF Nutritional Assessment Check (Using NutritionAssessmentEngine)
        val fediafAssessment = NutritionAssessmentEngine.assess(
            product = product,
            userPetType = pet.type,
            userPetBirthday = pet.birthday,
            userPetAgeYears = pet.age,
            userPetBreed = pet.breed
        )
        when (fediafAssessment.overallStatus) {
            NutritionStatus.MEETS -> {
                score += 25
                reasons.add("Meets FEDIAF nutritional minimums")
            }
            NutritionStatus.BELOW -> {
                score -= 15
                reasons.add("Below FEDIAF nutrient standards")
            }
            NutritionStatus.ABOVE_MAXIMUM -> {
                score -= 20
                reasons.add("⚠️ Exceeds FEDIAF nutritional maximum")
            }
            NutritionStatus.INFORMATIONAL -> {
                if (fediafAssessment.isTreatOrComplementary) {
                    score -= 25
                    reasons.add("Complementary food / treat (not a complete diet)")
                } else {
                    score += 10
                }
            }
            NutritionStatus.INSUFFICIENT_DATA -> {
                score += 5
            }
        }

        if (fediafAssessment.caRatioStatus == NutritionStatus.MEETS) {
            score += 10
            reasons.add("Optimal FEDIAF Ca:P balance (${fediafAssessment.caRatioText ?: "1:1 - 2:1"})")
        }

        // 4. Nutritional & Ingredient Quality Score (0-25 points; fair for both Firestore & OpenPetFoodFacts products)
        val computedQualityPct = ScoringSystem.calculateNutritionalScore(product, fediafAssessment).percentage
        val nutriGrade = product.nutriscoreGrade
        val nutriPoints = when (nutriGrade?.lowercase()) {
            "a" -> 25
            "b" -> 20
            "c" -> 15
            "d" -> 10
            else -> ((computedQualityPct / 100.0) * 25.0).toInt().coerceIn(5, 25)
        }
        score += nutriPoints
        if (nutriGrade != null && nutriPoints >= 20) {
            reasons.add("High Nutri-Score (${nutriGrade.uppercase()})")
        } else if (computedQualityPct >= 75) {
            reasons.add("Strong nutritional quality ($computedQualityPct%)")
        }

        // 5. Life Stage Match (0-20 points)
        val ageInfo = getPetAgeInfo(pet.birthday, pet.age)
        when (ageInfo) {
            AgeGroup.PUPPY_KITTEN -> {
                if (productText.contains("puppy") || productText.contains("junior") || productText.contains("kitten") || productText.contains("growth")) {
                    score += 20
                    reasons.add("Formulated for growth (FEDIAF Growth Target)")
                }
            }
            AgeGroup.ADULT -> {
                if (productText.contains("adult")) {
                    score += 15
                    reasons.add("FEDIAF Adult maintenance formula")
                }
            }
            AgeGroup.SENIOR -> {
                if (productText.contains("senior") || productText.contains("mature") || productText.contains("aging")) {
                    score += 20
                    reasons.add("Supports aging health & joints")
                }
            }
        }

        // 6. Breed & Size Keyword Match (0-20 points)
        val breedText = pet.breed.lowercase().trim()
        if (breedText.isNotEmpty() && productText.contains(breedText)) {
            score += 20
            reasons.add("Specially formulated for ${pet.breed}")
        } else {
            val sizeKeywords = listOf("small", "mini", "medium", "large", "maxi", "giant")
            val matchedSize = sizeKeywords.firstOrNull { keyword ->
                breedText.contains(keyword) && productText.contains(keyword)
            }
            if (matchedSize != null) {
                score += 15
                reasons.add("Suitable for ${matchedSize.lowercase()} breed size")
            }
        }

        return Pair(score.coerceIn(0, 100), reasons)
    }

    private fun getPetAgeInfo(birthday: String, ageYears: Long = 0L): AgeGroup {
        return try {
            if (birthday.isBlank()) {
                return when {
                    ageYears in 1..6 -> AgeGroup.ADULT
                    ageYears >= 7 -> AgeGroup.SENIOR
                    else -> AgeGroup.ADULT
                }
            }
            val parts = birthday.split("-")
            val birthYear = parts[0].toInt()
            val birthMonth = if (parts.size > 1) parts[1].toInt() else 1

            val now = Calendar.getInstance()
            val ageInMonths = (now.get(Calendar.YEAR) - birthYear) * 12 + (now.get(Calendar.MONTH) + 1 - birthMonth)

            when {
                ageInMonths < 12 -> AgeGroup.PUPPY_KITTEN
                ageInMonths > 84 -> AgeGroup.SENIOR // 7+ years
                else -> AgeGroup.ADULT
            }
        } catch (e: Exception) {
            AgeGroup.ADULT
        }
    }

    enum class AgeGroup { PUPPY_KITTEN, ADULT, SENIOR }
}

