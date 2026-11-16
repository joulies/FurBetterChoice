package com.example.furbetterchoice.api

enum class AssessmentStandard(val displayName: String) {
    FEDIAF("FEDIAF"),
    AAFCO("AAFCO")
}

enum class AafcoSpecies(val displayName: String) {
    DOG("Dog"),
    CAT("Cat")
}

enum class AafcoLifeStageCategory(
    val displayName: String,
    val species: AafcoSpecies,
    val mappedProfile: AafcoProfileType,
    val isSeniorStage: Boolean = false
) {
    PUPPY("Puppy", AafcoSpecies.DOG, AafcoProfileType.GROWTH_AND_REPRODUCTION, false),
    ADULT_DOG("Adult Dog", AafcoSpecies.DOG, AafcoProfileType.ADULT_MAINTENANCE, false),
    SENIOR_DOG("Senior Dog", AafcoSpecies.DOG, AafcoProfileType.ADULT_MAINTENANCE, true),
    KITTEN("Kitten", AafcoSpecies.CAT, AafcoProfileType.GROWTH_AND_REPRODUCTION, false),
    ADULT_CAT("Adult Cat", AafcoSpecies.CAT, AafcoProfileType.ADULT_MAINTENANCE, false),
    SENIOR_CAT("Senior Cat", AafcoSpecies.CAT, AafcoProfileType.ADULT_MAINTENANCE, true)
}

enum class AafcoProfileType(val displayName: String) {
    GROWTH_AND_REPRODUCTION("Growth and Reproduction"),
    ADULT_MAINTENANCE("Adult Maintenance")
}

enum class AafcoNutrientCategory(val displayName: String) {
    MACRONUTRIENT("Macronutrients"),
    MINERAL("Minerals"),
    RATIO("Mineral & Fatty Acid Ratios"),
    AMINO_ACID("Amino Acids")
}

enum class AafcoReferenceBasis(val displayName: String) {
    DRY_MATTER("Dry Matter (Moisture Removed)"),
    RATIO_DRY_MATTER("Mineral Ratio")
}

enum class AafcoVerificationStatus(val displayName: String) {
    VERIFIED_OFFICIAL("Verified AAFCO Standard"),
    PARTIALLY_VERIFIED_2026_PENDING("Verified Minimum")
}

data class AafcoNutrientReference(
    val nutrientKey: String,
    val aliases: List<String>,
    val nutrientName: String,
    val category: AafcoNutrientCategory,
    val species: AafcoSpecies,
    val profile: AafcoProfileType,
    val minimumValue: Double?,
    val maximumValue: Double?,
    val largeBreedPuppyMax: Double? = null,
    val cannedMinimumValue: Double? = null,
    val unit: String,
    val referenceBasis: AafcoReferenceBasis = AafcoReferenceBasis.DRY_MATTER,
    val edition: String = EDITION_POST_2016,
    val sourceDocument: String = SOURCE_AAFCO_OP,
    val verificationStatus: AafcoVerificationStatus = AafcoVerificationStatus.VERIFIED_OFFICIAL,
    val verificationNote: String? = null,
    val functionDescription: String,
    val referenceReason: String
) {
    companion object {
        const val EDITION_POST_2016 = "AAFCO Official Nutrient Profiles"
        const val SOURCE_AAFCO_OP = "AAFCO Dog and Cat Food Nutrient Profiles (Dry Matter Basis)"
    }
}

object AafcoReferenceTables {

    fun resolveLifeStageCategory(
        speciesRaw: String?,
        lifeStageRaw: String?,
        petAgeYears: Long? = null,
        petBirthday: String? = null
    ): Pair<AafcoLifeStageCategory, Boolean> {
        val isCat = (speciesRaw ?: "").lowercase().contains("cat")
        val normalizedStage = (lifeStageRaw ?: "").trim()
        val lowerStage = normalizedStage.lowercase()
        val resolvedYears = NutritionEnergyHelper.calculateAgeInYears(petBirthday, petAgeYears)
        val isPetYoung = PetAssessmentFilter.isPetYoungGrowth(petBirthday, petAgeYears)
        val hasPetAgeContext = !petBirthday.isNullOrBlank() || petAgeYears != null

        val isSpecified = normalizedStage.isNotEmpty() &&
            !normalizedStage.equals("Not Specified", ignoreCase = true) &&
            !normalizedStage.equals("Unknown", ignoreCase = true)

        if (!isSpecified) {
            val fallbackCategory = when {
                isPetYoung -> if (isCat) AafcoLifeStageCategory.KITTEN else AafcoLifeStageCategory.PUPPY
                resolvedYears != null && resolvedYears >= 7L ->
                    if (isCat) AafcoLifeStageCategory.SENIOR_CAT else AafcoLifeStageCategory.SENIOR_DOG
                else ->
                    if (isCat) AafcoLifeStageCategory.ADULT_CAT else AafcoLifeStageCategory.ADULT_DOG
            }
            return fallbackCategory to false
        }

        val isAllLifeStages = lowerStage.contains("all life stages") || lowerStage.contains("all stages")
        if (isAllLifeStages && hasPetAgeContext) {
            val petCategory = when {
                isPetYoung -> if (isCat) AafcoLifeStageCategory.KITTEN else AafcoLifeStageCategory.PUPPY
                resolvedYears != null && resolvedYears >= 7L ->
                    if (isCat) AafcoLifeStageCategory.SENIOR_CAT else AafcoLifeStageCategory.SENIOR_DOG
                else ->
                    if (isCat) AafcoLifeStageCategory.ADULT_CAT else AafcoLifeStageCategory.ADULT_DOG
            }
            return petCategory to true
        }

        val isGrowth = lowerStage.contains("puppy") ||
            lowerStage.contains("kitten") ||
            lowerStage.contains("growth") ||
            lowerStage.contains("junior") ||
            isAllLifeStages

        val isSenior = lowerStage.contains("senior") ||
            lowerStage.contains("mature") ||
            lowerStage.contains("aging") ||
            lowerStage.contains("7+") ||
            lowerStage.contains("11+")

        val category = when {
            isCat && isGrowth -> AafcoLifeStageCategory.KITTEN
            isCat && isSenior -> AafcoLifeStageCategory.SENIOR_CAT
            isCat -> AafcoLifeStageCategory.ADULT_CAT
            !isCat && isGrowth -> AafcoLifeStageCategory.PUPPY
            !isCat && isSenior -> AafcoLifeStageCategory.SENIOR_DOG
            else -> AafcoLifeStageCategory.ADULT_DOG
        }

        return category to true
    }

    fun getReferencesFor(species: AafcoSpecies, profile: AafcoProfileType): List<AafcoNutrientReference> {
        return when (species) {
            AafcoSpecies.DOG -> buildDogReferences(profile)
            AafcoSpecies.CAT -> buildCatReferences(profile)
        }
    }

    private fun buildDogReferences(profile: AafcoProfileType): List<AafcoNutrientReference> {
        val isGrowth = profile == AafcoProfileType.GROWTH_AND_REPRODUCTION
        return listOf(
            AafcoNutrientReference(
                nutrientKey = "crudeProtein",
                aliases = listOf("crudeProtein", "protein", "proteins", "proteins_100g", "crude_protein"),
                nutrientName = "Crude Protein",
                category = AafcoNutrientCategory.MACRONUTRIENT,
                species = AafcoSpecies.DOG,
                profile = profile,
                minimumValue = if (isGrowth) 22.5 else 18.0,
                maximumValue = null,
                unit = "%",
                functionDescription = "Builds and repairs muscles, organs, skin, and coat, and supports a strong immune system.",
                referenceReason = if (isGrowth) {
                    "Growing puppies need at least 22.5% protein (with water removed) to build new muscle and tissue."
                } else {
                    "Adult dogs need at least 18.0% protein (with water removed) to maintain healthy muscles and organs."
                }
            ),
            AafcoNutrientReference(
                nutrientKey = "crudeFat",
                aliases = listOf("crudeFat", "fat", "fats", "fat_100g", "crude_fat"),
                nutrientName = "Crude Fat",
                category = AafcoNutrientCategory.MACRONUTRIENT,
                species = AafcoSpecies.DOG,
                profile = profile,
                minimumValue = if (isGrowth) 8.5 else 5.5,
                maximumValue = null,
                unit = "%",
                functionDescription = "Provides concentrated daily energy, keeps skin and coat healthy, and helps the body absorb vitamins.",
                referenceReason = if (isGrowth) {
                    "Puppies need at least 8.5% fat (with water removed) to fuel rapid growth and brain development."
                } else {
                    "Adult dogs need at least 5.5% fat (with water removed) for daily energy and coat health."
                }
            ),
            AafcoNutrientReference(
                nutrientKey = "calcium",
                aliases = listOf("calcium", "calcium_100g", "ca"),
                nutrientName = "Calcium",
                category = AafcoNutrientCategory.MINERAL,
                species = AafcoSpecies.DOG,
                profile = profile,
                minimumValue = if (isGrowth) 1.2 else 0.5,
                maximumValue = 2.5,
                largeBreedPuppyMax = if (isGrowth) 1.8 else null,
                unit = "%",
                verificationNote = if (isGrowth) "For large-breed puppies (expected adult weight over 70 lbs / 32 kg), the safe upper limit is 1.8%." else null,
                functionDescription = "Keeps bones and teeth strong and helps muscles and nerves work properly.",
                referenceReason = if (isGrowth) {
                    "Puppies need between 1.2% and 2.5% calcium (up to 1.8% for large-breed puppies) because too little or too much can harm growing bones."
                } else {
                    "Adult dogs need between 0.5% and 2.5% calcium to maintain strong bones without overloading the body."
                }
            ),
            AafcoNutrientReference(
                nutrientKey = "phosphorus",
                aliases = listOf("phosphorus", "phosphorus_100g", "p"),
                nutrientName = "Phosphorus",
                category = AafcoNutrientCategory.MINERAL,
                species = AafcoSpecies.DOG,
                profile = profile,
                minimumValue = if (isGrowth) 1.0 else 0.4,
                maximumValue = 1.6,
                unit = "%",
                functionDescription = "Works side-by-side with calcium to build strong bones and teeth and helps cells use energy.",
                referenceReason = "Dogs need at least ${if (isGrowth) "1.0%" else "0.4%"} and no more than 1.6% phosphorus to support bone strength while avoiding excess mineral strain."
            ),
            AafcoNutrientReference(
                nutrientKey = "caPRatio",
                aliases = emptyList(),
                nutrientName = "Ca : P Ratio",
                category = AafcoNutrientCategory.RATIO,
                species = AafcoSpecies.DOG,
                profile = profile,
                minimumValue = 1.0,
                maximumValue = 2.0,
                unit = ":1",
                referenceBasis = AafcoReferenceBasis.RATIO_DRY_MATTER,
                functionDescription = "Measures the balance between calcium and phosphorus in the food.",
                referenceReason = "Dogs need 1 to 2 parts calcium for every 1 part phosphorus (1:1 to 2:1) so their bodies can absorb both minerals properly."
            )
        )
    }

    private fun buildCatReferences(profile: AafcoProfileType): List<AafcoNutrientReference> {
        val isGrowth = profile == AafcoProfileType.GROWTH_AND_REPRODUCTION
        return listOf(
            AafcoNutrientReference(
                nutrientKey = "crudeProtein",
                aliases = listOf("crudeProtein", "protein", "proteins", "proteins_100g", "crude_protein"),
                nutrientName = "Crude Protein",
                category = AafcoNutrientCategory.MACRONUTRIENT,
                species = AafcoSpecies.CAT,
                profile = profile,
                minimumValue = if (isGrowth) 30.0 else 26.0,
                maximumValue = null,
                unit = "%",
                functionDescription = "Cats are natural meat-eaters and rely on high levels of protein for daily energy, lean muscle, and organ health.",
                referenceReason = if (isGrowth) {
                    "Growing kittens need at least 30.0% protein (with water removed) for healthy muscle and body growth."
                } else {
                    "Adult cats need at least 26.0% protein (with water removed) to maintain muscle mass and daily health."
                }
            ),
            AafcoNutrientReference(
                nutrientKey = "crudeFat",
                aliases = listOf("crudeFat", "fat", "fats", "fat_100g", "crude_fat"),
                nutrientName = "Crude Fat",
                category = AafcoNutrientCategory.MACRONUTRIENT,
                species = AafcoSpecies.CAT,
                profile = profile,
                minimumValue = 9.0,
                maximumValue = null,
                unit = "%",
                functionDescription = "Supplies concentrated energy, supports healthy skin and coat, and helps absorb essential vitamins.",
                referenceReason = "Cats and kittens need at least 9.0% fat (with water removed) to meet their energy and fatty acid needs."
            ),
            AafcoNutrientReference(
                nutrientKey = "calcium",
                aliases = listOf("calcium", "calcium_100g", "ca"),
                nutrientName = "Calcium",
                category = AafcoNutrientCategory.MINERAL,
                species = AafcoSpecies.CAT,
                profile = profile,
                minimumValue = if (isGrowth) 1.0 else 0.6,
                maximumValue = null,
                unit = "%",
                verificationStatus = AafcoVerificationStatus.VERIFIED_OFFICIAL,
                verificationNote = "AAFCO sets a minimum of ${if (isGrowth) "1.0%" else "0.6%"} calcium for cats.",
                functionDescription = "Essential mineral for strong bones, teeth, muscle movement, and nerve signaling.",
                referenceReason = "Cats need at least ${if (isGrowth) "1.0%" else "0.6%"} calcium (with water removed) to keep bones and teeth strong."
            ),
            AafcoNutrientReference(
                nutrientKey = "phosphorus",
                aliases = listOf("phosphorus", "phosphorus_100g", "p"),
                nutrientName = "Phosphorus",
                category = AafcoNutrientCategory.MINERAL,
                species = AafcoSpecies.CAT,
                profile = profile,
                minimumValue = if (isGrowth) 0.8 else 0.5,
                maximumValue = 2.0,
                unit = "%",
                verificationStatus = AafcoVerificationStatus.VERIFIED_OFFICIAL,
                verificationNote = "AAFCO 2026 feline profile sets a minimum of ${if (isGrowth) "0.8%" else "0.5%"} and a safe maximum of 2.0% DM phosphorus.",
                functionDescription = "Partners with calcium to build strong bones and teeth and supports cellular energy.",
                referenceReason = "Cats need between ${if (isGrowth) "0.8%" else "0.5%"} and 2.0% phosphorus (with water removed) for healthy bones while avoiding excess kidney mineral load."
            ),
            AafcoNutrientReference(
                nutrientKey = "caPRatio",
                aliases = emptyList(),
                nutrientName = "Ca : P Ratio",
                category = AafcoNutrientCategory.RATIO,
                species = AafcoSpecies.CAT,
                profile = profile,
                minimumValue = 1.0,
                maximumValue = null,
                unit = ":1",
                referenceBasis = AafcoReferenceBasis.RATIO_DRY_MATTER,
                verificationStatus = AafcoVerificationStatus.VERIFIED_OFFICIAL,
                verificationNote = "AAFCO 2026 feline profile specifies a minimum Ca:P ratio of 1:1 (1.0:1).",
                functionDescription = "Measures the balance between calcium and phosphorus in the food.",
                referenceReason = "Cats require at least 1 part calcium for every 1 part phosphorus (1:1 minimum) to support proper skeletal and kidney health."
            ),
            AafcoNutrientReference(
                nutrientKey = "taurine",
                aliases = listOf("taurine", "taurine_100g"),
                nutrientName = "Taurine",
                category = AafcoNutrientCategory.AMINO_ACID,
                species = AafcoSpecies.CAT,
                profile = profile,
                minimumValue = 0.10,
                maximumValue = null,
                cannedMinimumValue = 0.20,
                unit = "%",
                verificationNote = "Dry kibble requires at least 0.10% taurine; wet/canned food requires at least 0.20% taurine.",
                functionDescription = "An essential nutrient for cats that keeps their heart muscle strong and protects their eyesight.",
                referenceReason = "Cats cannot make enough taurine on their own, so their food must provide at least 0.10% (dry food) or 0.20% (wet food)."
            )
        )
    }
}
