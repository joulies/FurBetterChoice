package com.example.furbetterchoice.api

import java.util.Locale
import kotlin.math.roundToInt

enum class AafcoResultStatus(val displayName: String) {
    MEETS_REFERENCE("Meets Standard"),
    BELOW_REFERENCE("Below Minimum"),
    ABOVE_MAXIMUM("Above Safe Limit"),
    INSUFFICIENT_INFORMATION("Not Listed"),
    NOT_APPLICABLE("Not Applicable")
}

data class AafcoNutrientResult(
    val nutrientKey: String,
    val nutrientName: String,
    val category: AafcoNutrientCategory,
    val asFedValue: Double?,
    val asFedUnit: String,
    val dryMatterValue: Double?,
    val energyBasisValue: Double?,
    val energyBasisUnit: String?,
    val unit: String,
    val requiredMinimum: Double?,
    val maximumLimit: Double?,
    val percentageOfMinimum: Double?,
    val productValueDisplay: String,
    val referenceDisplay: String,
    val status: AafcoResultStatus,
    val resultSummaryText: String,
    val functionDescription: String,
    val referenceReason: String,
    val assessmentExplanation: String,
    val moreInfoNeededText: String?,
    val verificationStatus: AafcoVerificationStatus,
    val verificationNote: String?,
    val isListedInIngredientsOnly: Boolean = false
)

data class AafcoAssessmentResult(
    val standard: AssessmentStandard = AssessmentStandard.AAFCO,
    val species: AafcoSpecies,
    val lifeStageCategory: AafcoLifeStageCategory,
    val profileType: AafcoProfileType,
    val isLifeStageSpecified: Boolean,
    val isSeniorMappedToAdultMaintenance: Boolean,
    val seniorMappingExplanation: String?,
    val isLargeBreedPuppy: Boolean,
    val isCannedOrWetFood: Boolean,
    val isTreatOrComplementary: Boolean = false,
    val treatExemptionMessage: String? = null,
    val moistureAsFed: Double?,
    val isMoistureAvailable: Boolean,
    val dryMatterPercent: Double?,
    val metabolizableEnergyAsFedKcalKg: Double?,
    val metabolizableEnergyDmKcalKg: Double?,
    val isEnergyAvailable: Boolean,
    val energyResult: EnergyExtractionResult = EnergyExtractionResult(EnergySource.UNAVAILABLE),
    val caPRatio: Double?,
    val caPRatioText: String,
    val caPRatioStatus: AafcoResultStatus,
    val overallStatus: AafcoResultStatus,
    val overallHeadline: String,
    val overallSummary: String,
    val evaluatedNutrientsCount: Int,
    val totalApplicableNutrientsCount: Int,
    val completenessPercentage: Int,
    val meetsCount: Int,
    val belowCount: Int,
    val aboveMaxCount: Int,
    val insufficientInfoCount: Int,
    val notApplicableCount: Int,
    val nutrientResults: List<AafcoNutrientResult>,
    val edition: String = AafcoNutrientReference.EDITION_POST_2016,
    val sourceDocument: String = AafcoNutrientReference.SOURCE_AAFCO_OP,
    val methodologyAndScoringNote: String,
    val limitationsDisclaimer: String = DEFAULT_LIMITATIONS_DISCLAIMER
) {
    companion object {
        const val DEFAULT_LIMITATIONS_DISCLAIMER =
            "This assessment compares available product information against applicable AAFCO nutrient references. Product labels may not disclose all required nutrients. Meeting selected nutrient references does not independently prove that a product is nutritionally complete or suitable for every pet."
    }
}

object AafcoAssessmentEngine {

    /**
     * Evaluates a [Product] against the official AAFCO Dog or Cat Food Nutrient Profiles
     * for key label-disclosed nutrients that have an official AAFCO threshold.
     *
     * Completely independent of [NutritionAssessmentEngine] (FEDIAF).
     */
    fun assess(
        product: Product,
        userPetType: String? = null,
        userPetLifeStage: String? = null,
        userPetAgeYears: Long? = null,
        userPetBirthday: String? = null,
        userPetBreed: String? = null,
        isLargeBreedPuppyOverride: Boolean? = null
    ): AafcoAssessmentResult {
        val rawSpecies = product.pet.ifBlank { product.species.ifBlank { userPetType ?: "Dog" } }
        val rawLifeStage = product.lifeStage.ifBlank { userPetLifeStage ?: "" }

        val (lifeStageCategory, isLifeStageSpecified) = AafcoReferenceTables.resolveLifeStageCategory(
            speciesRaw = rawSpecies,
            lifeStageRaw = rawLifeStage,
            petAgeYears = userPetAgeYears,
            petBirthday = userPetBirthday
        )

        val species = lifeStageCategory.species
        val profileType = lifeStageCategory.mappedProfile
        val isSeniorMapped = lifeStageCategory.isSeniorStage

        val seniorMappingExplanation = if (isSeniorMapped) {
            "AAFCO does not have a separate Senior standard for ${species.displayName.lowercase()}s. " +
                "Senior ${species.displayName.lowercase()} foods are checked using the Adult Maintenance standard."
        } else {
            null
        }

        val moistureEntry = extractRawNutrient(product, listOf("moisture", "moisture_100g"))
        val moistureAsFed = if (moistureEntry != null && isPercentageUnit(moistureEntry.unit)) {
            moistureEntry.value
        } else {
            null
        }
        val isMoistureAvailable = moistureAsFed != null && moistureAsFed > 0.0 && moistureAsFed < 100.0
        val dryMatterPercent = if (isMoistureAvailable) 100.0 - moistureAsFed!! else null

        val isTreat = NutritionEnergyHelper.isTreatOrComplementary(product)
        val isCannedOrWetFood = NutritionEnergyHelper.isCannedOrWetFood(product, moistureAsFed)
        val isLargeBreedPuppy = isLargeBreedPuppyOverride ?: (
            species == AafcoSpecies.DOG &&
                profileType == AafcoProfileType.GROWTH_AND_REPRODUCTION &&
                NutritionEnergyHelper.isLargeBreedPuppy(product, userPetBreed)
            )

        // Energy extraction + AAFCO > 4,000 kcal ME/kg DM correction rule
        val energyResult = NutritionEnergyHelper.extractAndResolveEnergy(
            product = product,
            dryMatterPercent = dryMatterPercent,
            adjustmentMode = StandardEnergyAdjustmentMode.ABOVE_4000_ONLY
        )
        val meAsFedKcalKg = energyResult.asFedKcalPerKg
        val meDmKcalKg = energyResult.dryMatterKcalPerKg
        val isEnergyAvailable = energyResult.source == EnergySource.LABEL_DECLARED &&
            meDmKcalKg != null && meDmKcalKg > 0.0

        val references = AafcoReferenceTables.getReferencesFor(species, profileType)
        val ingredientsLower = product.ingredientsText.lowercase(Locale.ROOT)

        // Extract Calcium & Phosphorus first for Ca:P ratio calculation
        val caExtracted = extractAndConvertNutrient(
            product = product,
            aliases = listOf("calcium", "calcium_100g", "ca"),
            expectedUnit = "%",
            dryMatterPercent = dryMatterPercent,
            meDmKcalKg = meDmKcalKg
        )
        val pExtracted = extractAndConvertNutrient(
            product = product,
            aliases = listOf("phosphorus", "phosphorus_100g", "p"),
            expectedUnit = "%",
            dryMatterPercent = dryMatterPercent,
            meDmKcalKg = meDmKcalKg
        )

        val results = mutableListOf<AafcoNutrientResult>()

        for (ref in references) {
            if (ref.minimumValue == null && ref.maximumValue == null) continue

            if (ref.nutrientKey == "caPRatio") {
                results.add(
                    evaluateCaPRatio(
                        ref = ref,
                        caExtracted = caExtracted,
                        pExtracted = pExtracted,
                        isLifeStageSpecified = isLifeStageSpecified,
                        isTreat = isTreat
                    )
                )
            } else {
                results.add(
                    evaluateSingleNutrient(
                        product = product,
                        ref = ref,
                        dryMatterPercent = dryMatterPercent,
                        isMoistureAvailable = isMoistureAvailable,
                        isLifeStageSpecified = isLifeStageSpecified,
                        isLargeBreedPuppy = isLargeBreedPuppy,
                        isCannedOrWetFood = isCannedOrWetFood,
                        isTreat = isTreat,
                        energyResult = energyResult,
                        meDmKcalKg = meDmKcalKg,
                        ingredientsLower = ingredientsLower
                    )
                )
            }
        }

        val caPRatioResult = results.firstOrNull { it.nutrientKey == "caPRatio" }
        val (caPRatioVal, caPRatioText, caPRatioStatus) = if (caPRatioResult != null) {
            Triple(caPRatioResult.dryMatterValue, caPRatioResult.productValueDisplay, caPRatioResult.status)
        } else {
            val caSameBasis = caExtracted.dryMatterInRefUnit ?: caExtracted.normalizedAsFedInRefUnit
            val pSameBasis = pExtracted.dryMatterInRefUnit ?: pExtracted.normalizedAsFedInRefUnit
            val ratio = calculateCalciumToPhosphorusRatio(caSameBasis, pSameBasis)
            if (ratio != null) {
                Triple(ratio, String.format(Locale.US, "%.2f:1", ratio), AafcoResultStatus.NOT_APPLICABLE)
            } else {
                Triple(null, "Not Listed", AafcoResultStatus.INSUFFICIENT_INFORMATION)
            }
        }

        val totalApplicableCount = results.size
        val evaluatedCount = results.count {
            it.status == AafcoResultStatus.MEETS_REFERENCE ||
                it.status == AafcoResultStatus.BELOW_REFERENCE ||
                it.status == AafcoResultStatus.ABOVE_MAXIMUM
        }
        val completenessPercentage = if (totalApplicableCount > 0) {
            ((evaluatedCount.toDouble() / totalApplicableCount.toDouble()) * 100.0).roundToInt()
        } else {
            0
        }

        val meetsCount = results.count { it.status == AafcoResultStatus.MEETS_REFERENCE }
        val belowCount = results.count { it.status == AafcoResultStatus.BELOW_REFERENCE }
        val aboveMaxCount = results.count { it.status == AafcoResultStatus.ABOVE_MAXIMUM }
        val insufficientInfoCount = results.count { it.status == AafcoResultStatus.INSUFFICIENT_INFORMATION }
        val notApplicableCount = results.count { it.status == AafcoResultStatus.NOT_APPLICABLE }

        val treatExemptionMsg = if (isTreat) {
            "Complementary pet food / treat: Complete AAFCO Dog/Cat Food Nutrient Profiles do not apply to treats or intermittent/supplemental feeding products."
        } else {
            null
        }

        val (overallStatus, overallHeadline, overallSummary) = determineOverallOutcome(
            species = species,
            lifeStageCategory = lifeStageCategory,
            profileType = profileType,
            isLifeStageSpecified = isLifeStageSpecified,
            isMoistureAvailable = isMoistureAvailable,
            isTreat = isTreat,
            treatExemptionMessage = treatExemptionMsg,
            evaluatedCount = evaluatedCount,
            totalApplicableCount = totalApplicableCount,
            meetsCount = meetsCount,
            belowCount = belowCount,
            aboveMaxCount = aboveMaxCount
        )

        val methodologyAndScoringNote = buildString {
            append("Values are compared with water removed (Dry Matter) so dry kibble and wet food can be compared fairly against AAFCO standards.")
            if (energyResult.isScaledForThreshold && meDmKcalKg != null) {
                append(" Thresholds have been adjusted upward for this formula's energy density (${String.format(Locale.US, "%.0f", meDmKcalKg)} kcal/kg DM > 4,000 kcal/kg DM).")
            }
        }

        return AafcoAssessmentResult(
            species = species,
            lifeStageCategory = lifeStageCategory,
            profileType = profileType,
            isLifeStageSpecified = isLifeStageSpecified,
            isSeniorMappedToAdultMaintenance = isSeniorMapped,
            seniorMappingExplanation = seniorMappingExplanation,
            isLargeBreedPuppy = isLargeBreedPuppy,
            isCannedOrWetFood = isCannedOrWetFood,
            isTreatOrComplementary = isTreat,
            treatExemptionMessage = treatExemptionMsg,
            moistureAsFed = moistureAsFed,
            isMoistureAvailable = isMoistureAvailable,
            dryMatterPercent = dryMatterPercent,
            metabolizableEnergyAsFedKcalKg = meAsFedKcalKg,
            metabolizableEnergyDmKcalKg = meDmKcalKg,
            isEnergyAvailable = isEnergyAvailable,
            energyResult = energyResult,
            caPRatio = caPRatioVal,
            caPRatioText = caPRatioText,
            caPRatioStatus = caPRatioStatus,
            overallStatus = overallStatus,
            overallHeadline = overallHeadline,
            overallSummary = overallSummary,
            evaluatedNutrientsCount = evaluatedCount,
            totalApplicableNutrientsCount = totalApplicableCount,
            completenessPercentage = completenessPercentage,
            meetsCount = meetsCount,
            belowCount = belowCount,
            aboveMaxCount = aboveMaxCount,
            insufficientInfoCount = insufficientInfoCount,
            notApplicableCount = notApplicableCount,
            nutrientResults = results,
            methodologyAndScoringNote = methodologyAndScoringNote
        )
    }

    fun calculateDryMatterBasis(asFedValue: Double?, moisturePercentage: Double?): Double? {
        if (asFedValue == null || moisturePercentage == null) return null
        if (moisturePercentage <= 0.0 || moisturePercentage >= 100.0) return null
        val dryMatterPercent = 100.0 - moisturePercentage
        return (asFedValue / dryMatterPercent) * 100.0
    }

    fun calculateCalciumToPhosphorusRatio(calciumValue: Double?, phosphorusValue: Double?): Double? {
        if (calciumValue == null || phosphorusValue == null || calciumValue <= 0.0 || phosphorusValue <= 0.0) {
            return null
        }
        return calciumValue / phosphorusValue
    }

    fun calculatePercentageOfMinimum(productValue: Double?, requiredMinimum: Double?): Double? {
        if (productValue == null || requiredMinimum == null || requiredMinimum <= 0.0) return null
        return (productValue / requiredMinimum) * 100.0
    }

    fun calculateEnergyBasisPer1000Kcal(
        nutrientDmValue: Double?,
        unit: String,
        meKcalPerKgDm: Double?
    ): Pair<Double, String>? {
        return NutritionEnergyHelper.calculatePer1000Kcal(nutrientDmValue, unit, meKcalPerKgDm)
    }

    private data class ExtractedNutrient(
        val rawAsFedValue: Double?,
        val rawUnit: String,
        val normalizedAsFedInRefUnit: Double?,
        val dryMatterInRefUnit: Double?,
        val energyBasisValue: Double?,
        val energyBasisUnit: String?,
        val isUnitCompatible: Boolean,
        val incompatibilityReason: String?
    )

    private fun evaluateSingleNutrient(
        product: Product,
        ref: AafcoNutrientReference,
        dryMatterPercent: Double?,
        isMoistureAvailable: Boolean,
        isLifeStageSpecified: Boolean,
        isLargeBreedPuppy: Boolean,
        isCannedOrWetFood: Boolean,
        isTreat: Boolean,
        energyResult: EnergyExtractionResult,
        meDmKcalKg: Double?,
        ingredientsLower: String
    ): AafcoNutrientResult {
        val baseMin = if (isCannedOrWetFood && ref.cannedMinimumValue != null) {
            ref.cannedMinimumValue
        } else {
            ref.minimumValue
        }

        val baseMax = if (isLargeBreedPuppy && ref.largeBreedPuppyMax != null) {
            ref.largeBreedPuppyMax
        } else {
            ref.maximumValue
        }

        // Apply AAFCO > 4,000 kcal ME/kg DM energy correction when label ME exceeds 4,000 kcal/kg DM
        val effectiveMin = NutritionEnergyHelper.scaleThreshold(baseMin, energyResult)
        val effectiveMax = NutritionEnergyHelper.scaleThreshold(baseMax, energyResult)

        val referenceDisplay = formatReferenceText(effectiveMin, effectiveMax, ref.unit)
        val extracted = extractAndConvertNutrient(
            product = product,
            aliases = listOf(ref.nutrientKey) + ref.aliases,
            expectedUnit = ref.unit,
            dryMatterPercent = dryMatterPercent,
            meDmKcalKg = meDmKcalKg
        )

        if (isTreat) {
            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = extracted.normalizedAsFedInRefUnit,
                asFedUnit = ref.unit,
                dryMatterValue = extracted.dryMatterInRefUnit,
                energyBasisValue = extracted.energyBasisValue,
                energyBasisUnit = extracted.energyBasisUnit,
                unit = ref.unit,
                requiredMinimum = null,
                maximumLimit = null,
                percentageOfMinimum = null,
                productValueDisplay = when {
                    extracted.dryMatterInRefUnit != null -> "${formatNumber(extracted.dryMatterInRefUnit, ref.unit)} DM"
                    extracted.normalizedAsFedInRefUnit != null -> formatNumber(extracted.normalizedAsFedInRefUnit, ref.unit)
                    else -> "Not listed"
                },
                referenceDisplay = "Complementary Food (N/A)",
                status = AafcoResultStatus.NOT_APPLICABLE,
                resultSummaryText = AafcoResultStatus.NOT_APPLICABLE.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = "Complete AAFCO minimums do not apply to complementary treats or snacks.",
                moreInfoNeededText = null,
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote
            )
        }

        // Case 1: Nutrient not present in numerical product nutrition data
        if (extracted.rawAsFedValue == null) {
            val mentionedInIngredients = isNutrientMentionedInIngredients(ref, ingredientsLower)
            val ingredientNote = if (mentionedInIngredients) {
                "${ref.nutrientName} is listed in the ingredients, but the exact percentage is not printed on the nutrition label."
            } else {
                "${ref.nutrientName} is not listed on this product's nutrition label."
            }

            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = null,
                asFedUnit = ref.unit,
                dryMatterValue = null,
                energyBasisValue = null,
                energyBasisUnit = null,
                unit = ref.unit,
                requiredMinimum = effectiveMin,
                maximumLimit = effectiveMax,
                percentageOfMinimum = null,
                productValueDisplay = if (mentionedInIngredients) "In ingredients only" else "Not listed",
                referenceDisplay = referenceDisplay,
                status = AafcoResultStatus.INSUFFICIENT_INFORMATION,
                resultSummaryText = AafcoResultStatus.INSUFFICIENT_INFORMATION.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = ingredientNote,
                moreInfoNeededText = "Needs the exact ${ref.nutrientName} percentage from the package or brand.",
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote,
                isListedInIngredientsOnly = mentionedInIngredients
            )
        }

        // Case 2: Incompatible measurement unit
        if (!extracted.isUnitCompatible || extracted.normalizedAsFedInRefUnit == null) {
            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = extracted.rawAsFedValue,
                asFedUnit = extracted.rawUnit,
                dryMatterValue = null,
                energyBasisValue = null,
                energyBasisUnit = null,
                unit = ref.unit,
                requiredMinimum = effectiveMin,
                maximumLimit = effectiveMax,
                percentageOfMinimum = null,
                productValueDisplay = "${formatNumber(extracted.rawAsFedValue, extracted.rawUnit)} (Incompatible unit)",
                referenceDisplay = referenceDisplay,
                status = AafcoResultStatus.INSUFFICIENT_INFORMATION,
                resultSummaryText = AafcoResultStatus.INSUFFICIENT_INFORMATION.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = extracted.incompatibilityReason
                    ?: "The unit '${extracted.rawUnit}' cannot be directly compared to '${ref.unit}'.",
                moreInfoNeededText = "Needs ${ref.nutrientName} measured in ${ref.unit}.",
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote
            )
        }

        // Case 3: Moisture is missing -> cannot convert As-Fed to DMB
        if (!isMoistureAvailable || extracted.dryMatterInRefUnit == null) {
            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = extracted.normalizedAsFedInRefUnit,
                asFedUnit = ref.unit,
                dryMatterValue = null,
                energyBasisValue = null,
                energyBasisUnit = null,
                unit = ref.unit,
                requiredMinimum = effectiveMin,
                maximumLimit = effectiveMax,
                percentageOfMinimum = null,
                productValueDisplay = "${formatNumber(extracted.normalizedAsFedInRefUnit, ref.unit)} on label",
                referenceDisplay = referenceDisplay,
                status = AafcoResultStatus.INSUFFICIENT_INFORMATION,
                resultSummaryText = AafcoResultStatus.INSUFFICIENT_INFORMATION.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = "The label shows ${formatNumber(extracted.normalizedAsFedInRefUnit, ref.unit)}, but moisture (%) is missing so we cannot calculate the dry matter amount.",
                moreInfoNeededText = "Needs the food's moisture (%) from the label to remove water from the calculation.",
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote
            )
        }

        // Case 4: Product life stage is not specified
        if (!isLifeStageSpecified) {
            val isCatProduct = (product.pet.ifBlank { product.species }).contains("cat", ignoreCase = true)
            val youngPlural = if (isCatProduct) "kittens" else "puppies"
            val youngSingular = if (isCatProduct) "Kitten" else "Puppy"
            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = extracted.normalizedAsFedInRefUnit,
                asFedUnit = ref.unit,
                dryMatterValue = extracted.dryMatterInRefUnit,
                energyBasisValue = extracted.energyBasisValue,
                energyBasisUnit = extracted.energyBasisUnit,
                unit = ref.unit,
                requiredMinimum = effectiveMin,
                maximumLimit = effectiveMax,
                percentageOfMinimum = calculatePercentageOfMinimum(extracted.dryMatterInRefUnit, effectiveMin),
                productValueDisplay = "${formatNumber(extracted.dryMatterInRefUnit, ref.unit)} DM",
                referenceDisplay = referenceDisplay,
                status = AafcoResultStatus.INSUFFICIENT_INFORMATION,
                resultSummaryText = AafcoResultStatus.INSUFFICIENT_INFORMATION.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = "This food has ${formatNumber(extracted.dryMatterInRefUnit, ref.unit)} (dry matter basis), but the package does not state whether it is for $youngPlural or adults.",
                moreInfoNeededText = "Needs the target life stage ($youngSingular, Adult, or Senior).",
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote
            )
        }

        // Case 5: Full DMB comparison against Minimum and Maximum
        val dmVal = extracted.dryMatterInRefUnit
        val pctOfMin = calculatePercentageOfMinimum(dmVal, effectiveMin)
        val belowMin = effectiveMin != null && dmVal < effectiveMin - 1e-6
        val aboveMax = effectiveMax != null && dmVal > effectiveMax + 1e-6

        val status = when {
            aboveMax -> AafcoResultStatus.ABOVE_MAXIMUM
            belowMin -> AafcoResultStatus.BELOW_REFERENCE
            else -> AafcoResultStatus.MEETS_REFERENCE
        }

        val assessmentExplanation = when (status) {
            AafcoResultStatus.MEETS_REFERENCE -> buildString {
                append("Provides ${formatNumber(dmVal, ref.unit)} (water removed), which meets the AAFCO requirement of $referenceDisplay.")
                if (effectiveMax != null) append(" It also stays safely below the ${formatNumber(effectiveMax, ref.unit)} upper limit.")
            }
            AafcoResultStatus.BELOW_REFERENCE ->
                "Provides ${formatNumber(dmVal, ref.unit)} (water removed), which is below the AAFCO minimum of ${formatNumber(effectiveMin, ref.unit)}."
            AafcoResultStatus.ABOVE_MAXIMUM ->
                "Provides ${formatNumber(dmVal, ref.unit)} (water removed), which is above the AAFCO safe upper limit of ${formatNumber(effectiveMax, ref.unit)}."
            else -> ""
        }

        return AafcoNutrientResult(
            nutrientKey = ref.nutrientKey,
            nutrientName = ref.nutrientName,
            category = ref.category,
            asFedValue = extracted.normalizedAsFedInRefUnit,
            asFedUnit = ref.unit,
            dryMatterValue = dmVal,
            energyBasisValue = extracted.energyBasisValue,
            energyBasisUnit = extracted.energyBasisUnit,
            unit = ref.unit,
            requiredMinimum = effectiveMin,
            maximumLimit = effectiveMax,
            percentageOfMinimum = pctOfMin,
            productValueDisplay = "${formatNumber(dmVal, ref.unit)} DM",
            referenceDisplay = referenceDisplay,
            status = status,
            resultSummaryText = status.displayName,
            functionDescription = ref.functionDescription,
            referenceReason = ref.referenceReason,
            assessmentExplanation = assessmentExplanation,
            moreInfoNeededText = ref.verificationNote,
            verificationStatus = ref.verificationStatus,
            verificationNote = ref.verificationNote
        )
    }

    private fun evaluateCaPRatio(
        ref: AafcoNutrientReference,
        caExtracted: ExtractedNutrient,
        pExtracted: ExtractedNutrient,
        isLifeStageSpecified: Boolean,
        isTreat: Boolean
    ): AafcoNutrientResult {
        val minRatio = ref.minimumValue
        val maxRatio = ref.maximumValue
        val refText = when {
            minRatio != null && maxRatio != null -> "${formatRatioNumber(minRatio)}:1 to ${formatRatioNumber(maxRatio)}:1"
            minRatio != null -> "At least ${formatRatioNumber(minRatio)}:1"
            maxRatio != null -> "Up to ${formatRatioNumber(maxRatio)}:1"
            else -> "Not specified"
        }

        val caSameBasis = caExtracted.dryMatterInRefUnit ?: caExtracted.normalizedAsFedInRefUnit
        val pSameBasis = pExtracted.dryMatterInRefUnit ?: pExtracted.normalizedAsFedInRefUnit
        val bothOnSameBasis = (caExtracted.dryMatterInRefUnit != null && pExtracted.dryMatterInRefUnit != null) ||
            (caExtracted.normalizedAsFedInRefUnit != null && pExtracted.normalizedAsFedInRefUnit != null && caExtracted.isUnitCompatible && pExtracted.isUnitCompatible)

        if (isTreat) {
            val ratio = if (bothOnSameBasis && caSameBasis != null && pSameBasis != null && pSameBasis > 0.0) {
                calculateCalciumToPhosphorusRatio(caSameBasis, pSameBasis)
            } else null
            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = ratio,
                asFedUnit = ":1",
                dryMatterValue = ratio,
                energyBasisValue = null,
                energyBasisUnit = null,
                unit = ":1",
                requiredMinimum = null,
                maximumLimit = null,
                percentageOfMinimum = null,
                productValueDisplay = if (ratio != null) String.format(Locale.US, "%.2f : 1", ratio) else "Not listed",
                referenceDisplay = "Complementary Food (N/A)",
                status = AafcoResultStatus.NOT_APPLICABLE,
                resultSummaryText = AafcoResultStatus.NOT_APPLICABLE.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = "Complete AAFCO mineral ratio standards do not apply to treats or complementary foods.",
                moreInfoNeededText = null,
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote
            )
        }

        if (!bothOnSameBasis || caSameBasis == null || pSameBasis == null || pSameBasis <= 0.0) {
            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = null,
                asFedUnit = ":1",
                dryMatterValue = null,
                energyBasisValue = null,
                energyBasisUnit = null,
                unit = ":1",
                requiredMinimum = minRatio,
                maximumLimit = maxRatio,
                percentageOfMinimum = null,
                productValueDisplay = "Not listed",
                referenceDisplay = refText,
                status = AafcoResultStatus.INSUFFICIENT_INFORMATION,
                resultSummaryText = AafcoResultStatus.INSUFFICIENT_INFORMATION.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = "Both Calcium and Phosphorus percentages are needed to check their balance.",
                moreInfoNeededText = "Needs both Calcium (%) and Phosphorus (%) on the nutrition label.",
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote
            )
        }

        val ratio = calculateCalciumToPhosphorusRatio(caSameBasis, pSameBasis)!!
        val formattedRatio = String.format(Locale.US, "%.2f : 1", ratio)

        if (!isLifeStageSpecified) {
            return AafcoNutrientResult(
                nutrientKey = ref.nutrientKey,
                nutrientName = ref.nutrientName,
                category = ref.category,
                asFedValue = ratio,
                asFedUnit = ":1",
                dryMatterValue = ratio,
                energyBasisValue = null,
                energyBasisUnit = null,
                unit = ":1",
                requiredMinimum = minRatio,
                maximumLimit = maxRatio,
                percentageOfMinimum = calculatePercentageOfMinimum(ratio, minRatio),
                productValueDisplay = formattedRatio,
                referenceDisplay = refText,
                status = AafcoResultStatus.INSUFFICIENT_INFORMATION,
                resultSummaryText = AafcoResultStatus.INSUFFICIENT_INFORMATION.displayName,
                functionDescription = ref.functionDescription,
                referenceReason = ref.referenceReason,
                assessmentExplanation = "The calcium-to-phosphorus balance is $formattedRatio, but the product's life stage is not specified.",
                moreInfoNeededText = "Needs the product's intended life stage.",
                verificationStatus = ref.verificationStatus,
                verificationNote = ref.verificationNote
            )
        }

        val status = when {
            maxRatio != null && ratio > maxRatio + 1e-6 -> AafcoResultStatus.ABOVE_MAXIMUM
            minRatio != null && ratio < minRatio - 1e-6 -> AafcoResultStatus.BELOW_REFERENCE
            else -> AafcoResultStatus.MEETS_REFERENCE
        }

        val pctOfMin = calculatePercentageOfMinimum(ratio, minRatio)
        val explanation = when (status) {
            AafcoResultStatus.MEETS_REFERENCE ->
                "The balance of calcium to phosphorus is $formattedRatio, which meets the AAFCO requirement ($refText)."
            AafcoResultStatus.BELOW_REFERENCE ->
                "The balance is $formattedRatio, which is below the minimum $refText threshold (too little calcium compared to phosphorus)."
            AafcoResultStatus.ABOVE_MAXIMUM ->
                "The balance is $formattedRatio, which is above the maximum $refText range (too much calcium compared to phosphorus)."
            else -> ""
        }

        return AafcoNutrientResult(
            nutrientKey = ref.nutrientKey,
            nutrientName = ref.nutrientName,
            category = ref.category,
            asFedValue = ratio,
            asFedUnit = ":1",
            dryMatterValue = ratio,
            energyBasisValue = null,
            energyBasisUnit = null,
            unit = ":1",
            requiredMinimum = minRatio,
            maximumLimit = maxRatio,
            percentageOfMinimum = pctOfMin,
            productValueDisplay = formattedRatio,
            referenceDisplay = refText,
            status = status,
            resultSummaryText = status.displayName,
            functionDescription = ref.functionDescription,
            referenceReason = ref.referenceReason,
            assessmentExplanation = explanation,
            moreInfoNeededText = null,
            verificationStatus = ref.verificationStatus,
            verificationNote = ref.verificationNote
        )
    }

    private fun determineOverallOutcome(
        species: AafcoSpecies,
        lifeStageCategory: AafcoLifeStageCategory,
        profileType: AafcoProfileType,
        isLifeStageSpecified: Boolean,
        isMoistureAvailable: Boolean,
        isTreat: Boolean,
        treatExemptionMessage: String?,
        evaluatedCount: Int,
        totalApplicableCount: Int,
        meetsCount: Int,
        belowCount: Int,
        aboveMaxCount: Int
    ): Triple<AafcoResultStatus, String, String> {
        if (isTreat) {
            return Triple(
                AafcoResultStatus.NOT_APPLICABLE,
                "Complementary Food / Treat",
                treatExemptionMessage ?: "Complete AAFCO nutrient minimums do not apply to complementary treats."
            )
        }

        if (!isLifeStageSpecified) {
            val youngLabel = if (species == AafcoSpecies.CAT) "Kitten" else "Puppy"
            return Triple(
                AafcoResultStatus.INSUFFICIENT_INFORMATION,
                "Partial Nutrition Check",
                "The product's life stage ($youngLabel or Adult) is not listed, so we cannot finish checking AAFCO requirements."
            )
        }

        if (!isMoistureAvailable) {
            return Triple(
                AafcoResultStatus.INSUFFICIENT_INFORMATION,
                "Partial Nutrition Check",
                "Moisture (%) is not listed on the label, so Dry Matter values cannot be calculated."
            )
        }

        if (evaluatedCount == 0) {
            return Triple(
                AafcoResultStatus.INSUFFICIENT_INFORMATION,
                "Partial Nutrition Check",
                "Not enough nutrient values are listed on the label to compare against AAFCO standards."
            )
        }

        if (aboveMaxCount > 0 && belowCount > 0) {
            return Triple(
                AafcoResultStatus.ABOVE_MAXIMUM,
                "Attention Needed",
                "$belowCount nutrient(s) are below the minimum and $aboveMaxCount exceeded the safe upper limit."
            )
        }

        if (aboveMaxCount > 0) {
            return Triple(
                AafcoResultStatus.ABOVE_MAXIMUM,
                "Above Safe Upper Limit",
                "$aboveMaxCount listed nutrient(s) exceeded the AAFCO upper limit for ${lifeStageCategory.displayName}."
            )
        }

        if (belowCount > 0) {
            return Triple(
                AafcoResultStatus.BELOW_REFERENCE,
                "Below AAFCO Minimum",
                "$belowCount listed nutrient(s) fell below the AAFCO minimum for ${lifeStageCategory.displayName}."
            )
        }

        val missingCount = totalApplicableCount - evaluatedCount
        val missingNote = if (missingCount > 0) {
            " ($missingCount optional mineral/nutrient value(s) were not listed on the label.)"
        } else {
            ""
        }

        return Triple(
            AafcoResultStatus.MEETS_REFERENCE,
            "Nutrition Check Passed",
            "All $meetsCount listed nutrients meet the AAFCO ${profileType.displayName} standard for ${lifeStageCategory.displayName}.$missingNote"
        )
    }

    private fun extractAndConvertNutrient(
        product: Product,
        aliases: List<String>,
        expectedUnit: String,
        dryMatterPercent: Double?,
        meDmKcalKg: Double?
    ): ExtractedNutrient {
        val raw = extractRawNutrient(product, aliases)
            ?: return ExtractedNutrient(
                rawAsFedValue = null,
                rawUnit = expectedUnit,
                normalizedAsFedInRefUnit = null,
                dryMatterInRefUnit = null,
                energyBasisValue = null,
                energyBasisUnit = null,
                isUnitCompatible = true,
                incompatibilityReason = null
            )

        val rawUnitTrimmed = raw.unit.trim().ifEmpty { "%" }
        val normalizedAsFed = convertUnitIfCompatible(raw.value, rawUnitTrimmed, expectedUnit)

        if (normalizedAsFed == null) {
            return ExtractedNutrient(
                rawAsFedValue = raw.value,
                rawUnit = rawUnitTrimmed,
                normalizedAsFedInRefUnit = null,
                dryMatterInRefUnit = null,
                energyBasisValue = null,
                energyBasisUnit = null,
                isUnitCompatible = false,
                incompatibilityReason = "The unit '$rawUnitTrimmed' cannot be converted to '$expectedUnit'."
            )
        }

        val dmValue = if (dryMatterPercent != null && dryMatterPercent > 0.0 && dryMatterPercent < 100.0) {
            (normalizedAsFed / dryMatterPercent) * 100.0
        } else {
            null
        }

        val energyPair = calculateEnergyBasisPer1000Kcal(dmValue, expectedUnit, meDmKcalKg)

        return ExtractedNutrient(
            rawAsFedValue = raw.value,
            rawUnit = rawUnitTrimmed,
            normalizedAsFedInRefUnit = normalizedAsFed,
            dryMatterInRefUnit = dmValue,
            energyBasisValue = energyPair?.first,
            energyBasisUnit = energyPair?.second,
            isUnitCompatible = true,
            incompatibilityReason = null
        )
    }

    private fun convertUnitIfCompatible(value: Double, fromUnitRaw: String, targetUnit: String): Double? {
        val from = fromUnitRaw.trim().lowercase(Locale.ROOT)
        val target = targetUnit.trim().lowercase(Locale.ROOT)

        if (from == target) return value

        return when (target) {
            "%" -> when (from) {
                "%", "g/100g", "g/100 g", "percent" -> value
                "g/kg", "g / kg" -> value / 10.0
                "mg/kg", "ppm", "mg / kg" -> value / 10000.0
                "mg/100g", "mg/100 g" -> value / 1000.0
                else -> null
            }
            "mg/kg" -> when (from) {
                "mg/kg", "ppm", "mg / kg" -> value
                "mg/100g", "mg/100 g" -> value * 10.0
                "g/kg", "g / kg" -> value * 1000.0
                "%", "g/100g" -> value * 10000.0
                "ug/kg", "mcg/kg", "µg/kg" -> value / 1000.0
                else -> null
            }
            "iu/kg" -> when (from) {
                "iu/kg", "iu / kg", "ui/kg" -> value
                "iu/100g", "iu/100 g" -> value * 10.0
                else -> null
            }
            else -> null
        }
    }

    private fun isPercentageUnit(unitRaw: String): Boolean {
        val u = unitRaw.trim().lowercase(Locale.ROOT)
        return u.isEmpty() || u == "%" || u == "g/100g" || u == "g/100 g" || u == "percent"
    }

    private fun extractRawNutrient(product: Product, aliases: List<String>): NutrientValue? {
        return NutritionEnergyHelper.extractRawNutrientValue(product, aliases)
    }

    private fun isNutrientMentionedInIngredients(
        ref: AafcoNutrientReference,
        ingredientsLower: String
    ): Boolean {
        if (ingredientsLower.isBlank()) return false
        val searchTerms = when (ref.nutrientKey) {
            "calcium" -> listOf("calcium")
            "phosphorus" -> listOf("phosphorus", "phosphate", "dicalcium phosphate", "tricalcium phosphate")
            "taurine" -> listOf("taurine")
            else -> listOf(ref.nutrientName.lowercase(Locale.ROOT))
        }
        return searchTerms.any { term -> ingredientsLower.contains(term) }
    }

    private fun formatReferenceText(min: Double?, max: Double?, unit: String): String {
        return when {
            min != null && max != null -> "${formatNumber(min, unit)} to ${formatNumber(max, unit)}"
            min != null -> "At least ${formatNumber(min, unit)}"
            max != null -> "Up to ${formatNumber(max, unit)}"
            else -> "Not specified"
        }
    }

    private fun formatNumber(value: Double?, unit: String): String {
        if (value == null) return "Not listed"
        val formatted = when {
            value >= 100.0 && value % 1.0 == 0.0 -> String.format(Locale.US, "%.0f", value)
            value < 0.1 -> String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
            value < 1.0 && (value * 100.0) % 10.0 != 0.0 -> String.format(Locale.US, "%.2f", value)
            else -> String.format(Locale.US, "%.1f", value)
        }
        return if (unit == "%") "$formatted%" else "$formatted $unit"
    }

    private fun formatRatioNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }
}
