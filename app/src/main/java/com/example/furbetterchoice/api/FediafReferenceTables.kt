package com.example.furbetterchoice.api

import java.util.Locale

enum class FediafMerMode(
    val displayName: String,
    val dogMerLabel: String,
    val catMerLabel: String
) {
    STANDARD(
        displayName = "Standard Activity",
        dogMerLabel = "110 kcal/kg⁰·⁷⁵",
        catMerLabel = "100 kcal/kg⁰·⁶⁷"
    ),
    LOWER_ACTIVITY(
        displayName = "Lower Activity / Indoor",
        dogMerLabel = "95 kcal/kg⁰·⁷⁵",
        catMerLabel = "75 kcal/kg⁰·⁶⁷"
    )
}

enum class FediafLifeStage(
    val displayName: String,
    val isGrowth: Boolean,
    val isSeniorMappedToAdult: Boolean = false
) {
    DOG_ADULT_110("Adult Dog (MER 110)", isGrowth = false),
    DOG_ADULT_95("Adult Dog (MER 95)", isGrowth = false),
    DOG_EARLY_GROWTH("Early Growth (<14 wks) & Reproduction", isGrowth = true),
    DOG_LATE_GROWTH("Late Growth (≥14 wks)", isGrowth = true),
    DOG_SENIOR("Senior Dog (Adult Reference)", isGrowth = false, isSeniorMappedToAdult = true),

    CAT_ADULT_100("Adult Cat (MER 100)", isGrowth = false),
    CAT_ADULT_75("Adult Cat (MER 75)", isGrowth = false),
    CAT_GROWTH("Kitten / Growth & Reproduction", isGrowth = true),
    CAT_SENIOR("Senior Cat (Adult Reference)", isGrowth = false, isSeniorMappedToAdult = true)
}

data class FediafNutrientThreshold(
    val minDm: Double?,
    val maxDm: Double? = null,
    val isNutritionalMax: Boolean = false,
    val cannedMinDm: Double? = null,
    val largeBreedMinDm: Double? = null,
    val largeBreedMaxDm: Double? = null
) {
    fun resolveMin(isCanned: Boolean = false, isLargeBreed: Boolean = false): Double? {
        if (isCanned && cannedMinDm != null) return cannedMinDm
        if (isLargeBreed && largeBreedMinDm != null) return largeBreedMinDm
        return minDm
    }

    fun resolveMax(isLargeBreed: Boolean = false): Double? {
        if (isLargeBreed && largeBreedMaxDm != null) return largeBreedMaxDm
        return maxDm
    }
}

data class FediafProfileReferences(
    val lifeStage: FediafLifeStage,
    val protein: FediafNutrientThreshold,
    val fat: FediafNutrientThreshold,
    val calcium: FediafNutrientThreshold,
    val phosphorus: FediafNutrientThreshold,
    val caPRatio: FediafNutrientThreshold,
    val taurine: FediafNutrientThreshold? = null
)

data class FediafResolvedStage(
    val lifeStage: FediafLifeStage,
    val isLifeStageSpecified: Boolean,
    val targetLifeStageDisplay: String,
    val isSeniorMappedToAdult: Boolean,
    val seniorMappingExplanation: String?
)

object FediafReferenceTables {

    const val EDITION_2025 = "FEDIAF Nutritional Guidelines (2025)"

    fun resolveLifeStage(
        isCat: Boolean,
        lifeStageRaw: String?,
        merMode: FediafMerMode,
        petBirthday: String? = null,
        petAgeYears: Long? = null
    ): FediafResolvedStage {
        val trimmed = (lifeStageRaw ?: "").trim()
        val isSpecified = trimmed.isNotEmpty() &&
            !trimmed.equals("Not Specified", ignoreCase = true) &&
            !trimmed.equals("Unknown", ignoreCase = true)

        val lower = trimmed.lowercase(Locale.ROOT)
        val petAgeWeeks = NutritionEnergyHelper.calculateAgeInWeeks(petBirthday)
        val resolvedYears = NutritionEnergyHelper.calculateAgeInYears(petBirthday, petAgeYears)
        val isPetYoung = PetAssessmentFilter.isPetYoungGrowth(petBirthday, petAgeYears)
        val hasPetAgeContext = !petBirthday.isNullOrBlank() || petAgeYears != null

        if (!isSpecified) {
            val fallbackStage = when {
                isCat && isPetYoung -> FediafLifeStage.CAT_GROWTH
                !isCat && isPetYoung -> {
                    if (petAgeWeeks != null && petAgeWeeks < 14L) FediafLifeStage.DOG_EARLY_GROWTH
                    else FediafLifeStage.DOG_LATE_GROWTH
                }
                isCat && resolvedYears != null && resolvedYears >= 7L -> FediafLifeStage.CAT_SENIOR
                !isCat && resolvedYears != null && resolvedYears >= 7L -> FediafLifeStage.DOG_SENIOR
                isCat && merMode == FediafMerMode.LOWER_ACTIVITY -> FediafLifeStage.CAT_ADULT_75
                isCat -> FediafLifeStage.CAT_ADULT_100
                merMode == FediafMerMode.LOWER_ACTIVITY -> FediafLifeStage.DOG_ADULT_95
                else -> FediafLifeStage.DOG_ADULT_110
            }
            return FediafResolvedStage(
                lifeStage = fallbackStage,
                isLifeStageSpecified = false,
                targetLifeStageDisplay = "Not Specified",
                isSeniorMappedToAdult = fallbackStage.isSeniorMappedToAdult,
                seniorMappingExplanation = null
            )
        }

        val isAllLifeStages = lower.contains("all life stages") || lower.contains("all stages")
        if (isAllLifeStages && hasPetAgeContext) {
            val petIsSenior = resolvedYears != null && resolvedYears >= 7L
            if (isCat) {
                return when {
                    isPetYoung -> FediafResolvedStage(
                        lifeStage = FediafLifeStage.CAT_GROWTH,
                        isLifeStageSpecified = true,
                        targetLifeStageDisplay = "Kitten / Growth",
                        isSeniorMappedToAdult = false,
                        seniorMappingExplanation = null
                    )
                    petIsSenior -> FediafResolvedStage(
                        lifeStage = FediafLifeStage.CAT_SENIOR,
                        isLifeStageSpecified = true,
                        targetLifeStageDisplay = "Senior Cat",
                        isSeniorMappedToAdult = true,
                        seniorMappingExplanation = "FEDIAF does not establish a separate Senior nutrient table for cats. Senior cat diets are assessed against Adult Cat (${merMode.catMerLabel}) maintenance guidelines."
                    )
                    else -> {
                        val adultStage = if (merMode == FediafMerMode.LOWER_ACTIVITY) {
                            FediafLifeStage.CAT_ADULT_75
                        } else {
                            FediafLifeStage.CAT_ADULT_100
                        }
                        FediafResolvedStage(
                            lifeStage = adultStage,
                            isLifeStageSpecified = true,
                            targetLifeStageDisplay = "Adult",
                            isSeniorMappedToAdult = false,
                            seniorMappingExplanation = null
                        )
                    }
                }
            } else {
                return when {
                    isPetYoung -> {
                        val useEarly = petAgeWeeks != null && petAgeWeeks < 14L
                        val stage = if (useEarly) FediafLifeStage.DOG_EARLY_GROWTH else FediafLifeStage.DOG_LATE_GROWTH
                        val display = if (useEarly) "Puppy / Early Growth (<14 wks)" else "Puppy / Late Growth (≥14 wks)"
                        FediafResolvedStage(
                            lifeStage = stage,
                            isLifeStageSpecified = true,
                            targetLifeStageDisplay = display,
                            isSeniorMappedToAdult = false,
                            seniorMappingExplanation = null
                        )
                    }
                    petIsSenior -> FediafResolvedStage(
                        lifeStage = FediafLifeStage.DOG_SENIOR,
                        isLifeStageSpecified = true,
                        targetLifeStageDisplay = "Senior Dog",
                        isSeniorMappedToAdult = true,
                        seniorMappingExplanation = "FEDIAF does not establish a separate Senior nutrient table for dogs. Senior dog diets are assessed against Adult Dog (${merMode.dogMerLabel}) maintenance guidelines."
                    )
                    else -> {
                        val adultStage = if (merMode == FediafMerMode.LOWER_ACTIVITY) {
                            FediafLifeStage.DOG_ADULT_95
                        } else {
                            FediafLifeStage.DOG_ADULT_110
                        }
                        FediafResolvedStage(
                            lifeStage = adultStage,
                            isLifeStageSpecified = true,
                            targetLifeStageDisplay = "Adult",
                            isSeniorMappedToAdult = false,
                            seniorMappingExplanation = null
                        )
                    }
                }
            }
        }

        val isSenior = lower.contains("senior") ||
            lower.contains("mature") ||
            lower.contains("aging") ||
            lower.contains("7+") ||
            lower.contains("11+")

        val isEarlyExplicit = lower.contains("early") ||
            lower.contains("<14") ||
            lower.contains("< 14") ||
            lower.contains("under 14") ||
            lower.contains("starter") ||
            lower.contains("weaning") ||
            lower.contains("reproduction") ||
            lower.contains("lactat") ||
            lower.contains("gestat") ||
            lower.contains("mother")

        val isLateExplicit = lower.contains("late") ||
            lower.contains("14+") ||
            lower.contains(">=14") ||
            lower.contains("≥14") ||
            lower.contains("over 14")

        val isGrowth = isEarlyExplicit ||
            isLateExplicit ||
            lower.contains("puppy") ||
            lower.contains("kitten") ||
            lower.contains("growth") ||
            lower.contains("junior") ||
            isAllLifeStages

        if (isCat) {
            return when {
                isGrowth -> FediafResolvedStage(
                    lifeStage = FediafLifeStage.CAT_GROWTH,
                    isLifeStageSpecified = true,
                    targetLifeStageDisplay = "Kitten / Growth",
                    isSeniorMappedToAdult = false,
                    seniorMappingExplanation = null
                )
                isSenior -> FediafResolvedStage(
                    lifeStage = FediafLifeStage.CAT_SENIOR,
                    isLifeStageSpecified = true,
                    targetLifeStageDisplay = "Senior Cat",
                    isSeniorMappedToAdult = true,
                    seniorMappingExplanation = "FEDIAF does not establish a separate Senior nutrient table for cats. Senior cat diets are assessed against Adult Cat (${merMode.catMerLabel}) maintenance guidelines."
                )
                else -> {
                    val adultStage = if (merMode == FediafMerMode.LOWER_ACTIVITY) {
                        FediafLifeStage.CAT_ADULT_75
                    } else {
                        FediafLifeStage.CAT_ADULT_100
                    }
                    FediafResolvedStage(
                        lifeStage = adultStage,
                        isLifeStageSpecified = true,
                        targetLifeStageDisplay = "Adult",
                        isSeniorMappedToAdult = false,
                        seniorMappingExplanation = null
                    )
                }
            }
        } else {
            return when {
                isGrowth -> {
                    val useEarly = when {
                        isEarlyExplicit -> true
                        isLateExplicit -> false
                        petAgeWeeks != null && petAgeWeeks < 14L -> true
                        else -> false
                    }
                    val stage = if (useEarly) FediafLifeStage.DOG_EARLY_GROWTH else FediafLifeStage.DOG_LATE_GROWTH
                    val display = when {
                        isEarlyExplicit || (petAgeWeeks != null && petAgeWeeks < 14L) -> "Puppy / Early Growth (<14 wks)"
                        isLateExplicit -> "Puppy / Late Growth (≥14 wks)"
                        else -> "Puppy / Growth"
                    }
                    FediafResolvedStage(
                        lifeStage = stage,
                        isLifeStageSpecified = true,
                        targetLifeStageDisplay = display,
                        isSeniorMappedToAdult = false,
                        seniorMappingExplanation = null
                    )
                }
                isSenior -> FediafResolvedStage(
                    lifeStage = FediafLifeStage.DOG_SENIOR,
                    isLifeStageSpecified = true,
                    targetLifeStageDisplay = "Senior Dog",
                    isSeniorMappedToAdult = true,
                    seniorMappingExplanation = "FEDIAF does not establish a separate Senior nutrient table for dogs. Senior dog diets are assessed against Adult Dog (${merMode.dogMerLabel}) maintenance guidelines."
                )
                else -> {
                    val adultStage = if (merMode == FediafMerMode.LOWER_ACTIVITY) {
                        FediafLifeStage.DOG_ADULT_95
                    } else {
                        FediafLifeStage.DOG_ADULT_110
                    }
                    FediafResolvedStage(
                        lifeStage = adultStage,
                        isLifeStageSpecified = true,
                        targetLifeStageDisplay = "Adult",
                        isSeniorMappedToAdult = false,
                        seniorMappingExplanation = null
                    )
                }
            }
        }
    }

    /**
     * Returns FEDIAF (2025) Table III-3a (Dog) and Table III-4a (Cat) Dry Matter (%)
     * reference thresholds normalized to 4,000 kcal ME/kg DM.
     */
    fun getReferences(
        lifeStage: FediafLifeStage,
        merMode: FediafMerMode = FediafMerMode.STANDARD
    ): FediafProfileReferences {
        val effectiveStage = when (lifeStage) {
            FediafLifeStage.DOG_SENIOR ->
                if (merMode == FediafMerMode.LOWER_ACTIVITY) FediafLifeStage.DOG_ADULT_95 else FediafLifeStage.DOG_ADULT_110
            FediafLifeStage.CAT_SENIOR ->
                if (merMode == FediafMerMode.LOWER_ACTIVITY) FediafLifeStage.CAT_ADULT_75 else FediafLifeStage.CAT_ADULT_100
            else -> lifeStage
        }

        return when (effectiveStage) {
            FediafLifeStage.DOG_ADULT_110 -> FediafProfileReferences(
                lifeStage = lifeStage,
                protein = FediafNutrientThreshold(minDm = 18.0),
                fat = FediafNutrientThreshold(minDm = 5.5),
                calcium = FediafNutrientThreshold(minDm = 0.50, maxDm = 2.50, isNutritionalMax = true),
                phosphorus = FediafNutrientThreshold(minDm = 0.40, maxDm = 1.60, isNutritionalMax = true),
                caPRatio = FediafNutrientThreshold(minDm = 1.0, maxDm = 2.0, isNutritionalMax = true)
            )
            FediafLifeStage.DOG_ADULT_95 -> FediafProfileReferences(
                lifeStage = lifeStage,
                protein = FediafNutrientThreshold(minDm = 21.0),
                fat = FediafNutrientThreshold(minDm = 5.5),
                calcium = FediafNutrientThreshold(minDm = 0.58, maxDm = 2.50, isNutritionalMax = true),
                phosphorus = FediafNutrientThreshold(minDm = 0.46, maxDm = 1.60, isNutritionalMax = true),
                caPRatio = FediafNutrientThreshold(minDm = 1.0, maxDm = 2.0, isNutritionalMax = true)
            )
            FediafLifeStage.DOG_EARLY_GROWTH -> FediafProfileReferences(
                lifeStage = lifeStage,
                protein = FediafNutrientThreshold(minDm = 25.0),
                fat = FediafNutrientThreshold(minDm = 8.5),
                calcium = FediafNutrientThreshold(minDm = 1.00, maxDm = 1.60, isNutritionalMax = true),
                phosphorus = FediafNutrientThreshold(minDm = 0.90),
                caPRatio = FediafNutrientThreshold(minDm = 1.0, maxDm = 1.6, isNutritionalMax = true)
            )
            FediafLifeStage.DOG_LATE_GROWTH -> FediafProfileReferences(
                lifeStage = lifeStage,
                protein = FediafNutrientThreshold(minDm = 20.0),
                fat = FediafNutrientThreshold(minDm = 8.5),
                calcium = FediafNutrientThreshold(
                    minDm = 0.80,
                    maxDm = 1.80,
                    isNutritionalMax = true,
                    largeBreedMinDm = 1.00,
                    largeBreedMaxDm = 1.80
                ),
                phosphorus = FediafNutrientThreshold(minDm = 0.70),
                caPRatio = FediafNutrientThreshold(
                    minDm = 1.0,
                    maxDm = 1.8,
                    isNutritionalMax = true,
                    largeBreedMaxDm = 1.6
                )
            )
            FediafLifeStage.CAT_ADULT_100 -> FediafProfileReferences(
                lifeStage = lifeStage,
                protein = FediafNutrientThreshold(minDm = 25.0),
                fat = FediafNutrientThreshold(minDm = 9.0),
                calcium = FediafNutrientThreshold(minDm = 0.40),
                phosphorus = FediafNutrientThreshold(minDm = 0.26),
                caPRatio = FediafNutrientThreshold(minDm = 1.0, maxDm = 2.0, isNutritionalMax = true),
                taurine = FediafNutrientThreshold(minDm = 0.10, cannedMinDm = 0.20)
            )
            FediafLifeStage.CAT_ADULT_75 -> FediafProfileReferences(
                lifeStage = lifeStage,
                protein = FediafNutrientThreshold(minDm = 33.3),
                fat = FediafNutrientThreshold(minDm = 9.0),
                calcium = FediafNutrientThreshold(minDm = 0.53),
                phosphorus = FediafNutrientThreshold(minDm = 0.35),
                caPRatio = FediafNutrientThreshold(minDm = 1.0, maxDm = 2.0, isNutritionalMax = true),
                taurine = FediafNutrientThreshold(minDm = 0.13, cannedMinDm = 0.27)
            )
            FediafLifeStage.CAT_GROWTH -> FediafProfileReferences(
                lifeStage = lifeStage,
                protein = FediafNutrientThreshold(minDm = 28.0),
                fat = FediafNutrientThreshold(minDm = 9.0),
                calcium = FediafNutrientThreshold(minDm = 1.00),
                phosphorus = FediafNutrientThreshold(minDm = 0.84),
                caPRatio = FediafNutrientThreshold(minDm = 1.0, maxDm = 1.5, isNutritionalMax = true),
                taurine = FediafNutrientThreshold(minDm = 0.10, cannedMinDm = 0.25)
            )
            else -> getReferences(FediafLifeStage.DOG_ADULT_110, merMode)
        }
    }
}
