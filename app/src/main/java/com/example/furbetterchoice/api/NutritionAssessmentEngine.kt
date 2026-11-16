package com.example.furbetterchoice.api

import java.util.Locale

enum class NutritionStatus {
    MEETS,
    BELOW,
    ABOVE_MAXIMUM,
    INFORMATIONAL,
    INSUFFICIENT_DATA
}

data class NutrientAssessment(
    val nutrientKey: String,
    val name: String,
    val asFedValue: Double?,
    val asFedUnit: String = "%",
    val dryMatterValue: Double?,
    val referenceThresholdText: String,
    val status: NutritionStatus,
    val resultText: String,
    val explanation: String,
    val minThreshold: Double? = null,
    val maxThreshold: Double? = null,
    val isNutritionalMaximum: Boolean = false,
    val energyBasisValue: Double? = null,
    val energyBasisUnit: String? = null
)

data class NutritionAssessment(
    val overallStatus: NutritionStatus,
    val overallHeadline: String,
    val overallMessage: String,
    val nutrientAssessments: List<NutrientAssessment>,
    val caRatio: Double?,
    val caRatioText: String?,
    val caRatioStatus: NutritionStatus,
    val caRatioMin: Double = 1.0,
    val caRatioMax: Double = 2.0,
    val dryMatterPercent: Double?,
    val isMoistureAvailable: Boolean,
    val lifeStageSpecified: Boolean,
    val targetPetType: String,
    val targetLifeStage: String,
    val merMode: FediafMerMode = FediafMerMode.STANDARD,
    val resolvedLifeStage: FediafLifeStage = FediafLifeStage.DOG_ADULT_110,
    val isSeniorMappedToAdult: Boolean = false,
    val seniorMappingExplanation: String? = null,
    val isTreatOrComplementary: Boolean = false,
    val treatExemptionMessage: String? = null,
    val isCannedOrWetFood: Boolean = false,
    val isLargeBreedPuppy: Boolean = false,
    val energyResult: EnergyExtractionResult = EnergyExtractionResult(EnergySource.UNAVAILABLE)
)

object NutritionAssessmentEngine {

    fun assess(
        product: Product,
        userPetType: String? = null,
        userPetBirthday: String? = null,
        userPetAgeYears: Long? = null,
        userPetBreed: String? = null,
        merMode: FediafMerMode = FediafMerMode.STANDARD,
        isLargeBreedPuppyOverride: Boolean? = null
    ): NutritionAssessment {
        val speciesRaw = (product.pet.ifEmpty { product.species.ifEmpty { userPetType ?: "Dog" } }).lowercase(Locale.ROOT)
        val isCat = speciesRaw.contains("cat")
        val petTypeDisplay = if (isCat) "Cat" else "Dog"

        val resolvedStage = FediafReferenceTables.resolveLifeStage(
            isCat = isCat,
            lifeStageRaw = product.lifeStage,
            merMode = merMode,
            petBirthday = userPetBirthday,
            petAgeYears = userPetAgeYears
        )
        val isLifeStageSpecified = resolvedStage.isLifeStageSpecified
        val targetLifeStageDisplay = resolvedStage.targetLifeStageDisplay

        // 1. Dry Matter Calculations
        val moistureAsFed = extractPercentNutrient(product, "moisture", "moisture_100g")
        val isMoistureAvailable = moistureAsFed != null && moistureAsFed > 0.0 && moistureAsFed < 100.0
        val dryMatterPercent = if (isMoistureAvailable) 100.0 - moistureAsFed!! else null

        val isTreat = NutritionEnergyHelper.isTreatOrComplementary(product)
        val isCannedOrWet = NutritionEnergyHelper.isCannedOrWetFood(product, moistureAsFed)
        val isLargeBreed = isLargeBreedPuppyOverride ?: (
            !isCat && resolvedStage.lifeStage.isGrowth &&
                NutritionEnergyHelper.isLargeBreedPuppy(product, userPetBreed)
            )

        // 2. Energy Extraction & FEDIAF Bidirectional 4,000 kcal ME/kg DM Scaling
        val energyResult = NutritionEnergyHelper.extractAndResolveEnergy(
            product = product,
            dryMatterPercent = dryMatterPercent,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )

        val refs = FediafReferenceTables.getReferences(resolvedStage.lifeStage, merMode)

        fun toDm(asFed: Double?): Double? {
            if (asFed == null || dryMatterPercent == null || dryMatterPercent <= 0.0) return null
            return (asFed / dryMatterPercent) * 100.0
        }

        val list = mutableListOf<NutrientAssessment>()

        // 3. Crude Protein Evaluation
        val proteinAsFed = extractPercentNutrient(product, "crudeProtein", "protein", "proteins", "proteins_100g", "crude_protein")
        val proteinDm = toDm(proteinAsFed)
        val minProteinDm = NutritionEnergyHelper.scaleThreshold(refs.protein.resolveMin(), energyResult)!!
        list.add(
            createRangeNutrientAssessment(
                key = "crudeProtein",
                displayName = "Crude Protein",
                asFed = proteinAsFed,
                dm = proteinDm,
                minDmRef = minProteinDm,
                maxDmRef = null,
                isNutritionalMax = false,
                isLifeStageSpecified = isLifeStageSpecified,
                isMoistureAvailable = isMoistureAvailable,
                isTreat = isTreat,
                meDmKcalKg = energyResult.dryMatterKcalPerKg,
                explanation = "Protein provides essential amino acids required for muscle maintenance, immune function, and organ repair."
            )
        )

        // 4. Crude Fat Evaluation
        val fatAsFed = extractPercentNutrient(product, "crudeFat", "fat", "fats", "fat_100g", "crude_fat")
        val fatDm = toDm(fatAsFed)
        val minFatDm = NutritionEnergyHelper.scaleThreshold(refs.fat.resolveMin(), energyResult)!!
        list.add(
            createRangeNutrientAssessment(
                key = "crudeFat",
                displayName = "Crude Fat",
                asFed = fatAsFed,
                dm = fatDm,
                minDmRef = minFatDm,
                maxDmRef = null,
                isNutritionalMax = false,
                isLifeStageSpecified = isLifeStageSpecified,
                isMoistureAvailable = isMoistureAvailable,
                isTreat = isTreat,
                meDmKcalKg = energyResult.dryMatterKcalPerKg,
                explanation = "Fat supplies concentrated energy, essential fatty acids, and supports healthy skin and coat."
            )
        )

        // 5. Calcium Evaluation
        val calciumAsFed = extractPercentNutrient(product, "calcium", "calcium_100g", "ca")
        val calciumDm = toDm(calciumAsFed)
        val minCalciumDm = NutritionEnergyHelper.scaleThreshold(
            refs.calcium.resolveMin(isLargeBreed = isLargeBreed),
            energyResult
        )!!
        val maxCalciumDm = NutritionEnergyHelper.scaleThreshold(
            refs.calcium.resolveMax(isLargeBreed = isLargeBreed),
            energyResult
        )
        list.add(
            createRangeNutrientAssessment(
                key = "calcium",
                displayName = "Calcium",
                asFed = calciumAsFed,
                dm = calciumDm,
                minDmRef = minCalciumDm,
                maxDmRef = maxCalciumDm,
                isNutritionalMax = refs.calcium.isNutritionalMax && maxCalciumDm != null,
                isLifeStageSpecified = isLifeStageSpecified,
                isMoistureAvailable = isMoistureAvailable,
                isTreat = isTreat,
                meDmKcalKg = energyResult.dryMatterKcalPerKg,
                explanation = "Calcium is vital for bone and teeth development, muscle contraction, and blood clotting."
            )
        )

        // 6. Phosphorus Evaluation
        val phosphorusAsFed = extractPercentNutrient(product, "phosphorus", "phosphorus_100g", "p")
        val phosphorusDm = toDm(phosphorusAsFed)
        val minPhosphorusDm = NutritionEnergyHelper.scaleThreshold(refs.phosphorus.resolveMin(), energyResult)!!
        val maxPhosphorusDm = NutritionEnergyHelper.scaleThreshold(refs.phosphorus.resolveMax(), energyResult)
        list.add(
            createRangeNutrientAssessment(
                key = "phosphorus",
                displayName = "Phosphorus",
                asFed = phosphorusAsFed,
                dm = phosphorusDm,
                minDmRef = minPhosphorusDm,
                maxDmRef = maxPhosphorusDm,
                isNutritionalMax = refs.phosphorus.isNutritionalMax && maxPhosphorusDm != null,
                isLifeStageSpecified = isLifeStageSpecified,
                isMoistureAvailable = isMoistureAvailable,
                isTreat = isTreat,
                meDmKcalKg = energyResult.dryMatterKcalPerKg,
                explanation = "Phosphorus works in tandem with calcium to maintain skeletal structure and cell energy."
            )
        )

        // 7. Calcium : Phosphorus Ratio Evaluation (dimensionless, never scaled by energy)
        val minCaP = refs.caPRatio.resolveMin(isLargeBreed = isLargeBreed) ?: 1.0
        val maxCaP = refs.caPRatio.resolveMax(isLargeBreed = isLargeBreed) ?: 2.0
        val (caRatio, caRatioText, caRatioStatus) = calculateCaRatio(
            caAsFed = calciumAsFed,
            pAsFed = phosphorusAsFed,
            minRatio = minCaP,
            maxRatio = maxCaP,
            isTreat = isTreat
        )

        // 8. Taurine (Cat only when present on product label)
        val taurineAsFed = extractPercentNutrient(product, "taurine", "taurine_100g")
        if (isCat && taurineAsFed != null && refs.taurine != null) {
            val taurineDm = toDm(taurineAsFed)
            val baseTaurineMin = refs.taurine.resolveMin(isCanned = isCannedOrWet) ?: 0.10
            val minTaurineDm = NutritionEnergyHelper.scaleThreshold(baseTaurineMin, energyResult)!!
            list.add(
                createRangeNutrientAssessment(
                    key = "taurine",
                    displayName = "Taurine",
                    asFed = taurineAsFed,
                    dm = taurineDm,
                    minDmRef = minTaurineDm,
                    maxDmRef = null,
                    isNutritionalMax = false,
                    isLifeStageSpecified = isLifeStageSpecified,
                    isMoistureAvailable = isMoistureAvailable,
                    isTreat = isTreat,
                    meDmKcalKg = energyResult.dryMatterKcalPerKg,
                    explanation = "Taurine is an essential amino acid for cats required for heart health, vision, and reproduction."
                )
            )
        }

        // 9. Crude Fibre (Informational)
        val fibreAsFed = extractPercentNutrient(product, "crudeFiber", "fiber", "fibers", "fiber_100g", "crude_fiber")
        val fibreDm = toDm(fibreAsFed)
        val fibreEnergy = NutritionEnergyHelper.calculatePer1000Kcal(fibreDm, "%", energyResult.dryMatterKcalPerKg)
        val fibreResultText = when {
            fibreDm != null -> "${formatOneDecimal(fibreDm)}% DM"
            fibreAsFed != null -> "${formatOneDecimal(fibreAsFed)}%"
            else -> "Not listed"
        }
        list.add(
            NutrientAssessment(
                nutrientKey = "crudeFiber",
                name = "Crude Fibre",
                asFedValue = fibreAsFed,
                asFedUnit = "%",
                dryMatterValue = fibreDm,
                referenceThresholdText = "Informational — No universal minimum",
                status = if (fibreAsFed != null) NutritionStatus.INFORMATIONAL else NutritionStatus.INSUFFICIENT_DATA,
                resultText = fibreResultText,
                explanation = "Supports healthy digestion, stool quality, and gut motility. FEDIAF does not set one universal minimum fibre percentage for all complete pet foods.",
                energyBasisValue = fibreEnergy?.first,
                energyBasisUnit = fibreEnergy?.second
            )
        )

        // 10. Crude Ash (Informational)
        val ashAsFed = extractPercentNutrient(product, "ash", "ash_100g", "crudeAsh", "crude_ash")
        val ashDm = toDm(ashAsFed)
        val ashEnergy = NutritionEnergyHelper.calculatePer1000Kcal(ashDm, "%", energyResult.dryMatterKcalPerKg)
        val ashResultText = when {
            ashDm != null -> "${formatOneDecimal(ashDm)}% DM"
            ashAsFed != null -> "${formatOneDecimal(ashAsFed)}%"
            else -> "Not listed"
        }
        list.add(
            NutrientAssessment(
                nutrientKey = "ash",
                name = "Crude Ash",
                asFedValue = ashAsFed,
                asFedUnit = "%",
                dryMatterValue = ashDm,
                referenceThresholdText = "Informational",
                status = if (ashAsFed != null) NutritionStatus.INFORMATIONAL else NutritionStatus.INSUFFICIENT_DATA,
                resultText = ashResultText,
                explanation = "Ash represents the total mineral content (such as calcium, phosphorus, and trace minerals). FEDIAF evaluates individual minerals rather than total ash.",
                energyBasisValue = ashEnergy?.first,
                energyBasisUnit = ashEnergy?.second
            )
        )

        // 11. Moisture Row (Informational)
        val moistureResultText = if (moistureAsFed != null) {
            "${formatOneDecimal(moistureAsFed)}%"
        } else {
            "Not listed"
        }
        list.add(
            NutrientAssessment(
                nutrientKey = "moisture",
                name = "Moisture",
                asFedValue = moistureAsFed,
                asFedUnit = "%",
                dryMatterValue = null,
                referenceThresholdText = "Product Moisture",
                status = if (isMoistureAvailable) NutritionStatus.INFORMATIONAL else NutritionStatus.INSUFFICIENT_DATA,
                resultText = moistureResultText,
                explanation = "Moisture is used to convert label nutrients into Dry Matter (DM) percentages for accurate comparison."
            )
        )

        // 12. Additional Nutrient Keys from raw map (Informational, excluding energy keys shown separately)
        product.nutrition.forEach { (rawKey, nv) ->
            val lowerKey = rawKey.lowercase(Locale.ROOT)
            val alreadyProcessed = listOf(
                "crudeprotein", "protein", "proteins",
                "crudefat", "fat", "fats",
                "calcium", "phosphorus", "taurine",
                "crudefiber", "fiber", "ash", "moisture",
                "metabolizable", "metabolized", "metabolised", "metabolisable",
                "energy", "calori", "kcal"
            ).any { lowerKey.contains(it) } || lowerKey == "me"

            if (!alreadyProcessed && nv.value > 0.0) {
                val nvAsFed = nv.value
                val nvUnit = nv.unit.ifEmpty { "%" }
                val nvDm = if (nvUnit == "%") toDm(nvAsFed) else null
                val nvEnergy = NutritionEnergyHelper.calculatePer1000Kcal(nvDm, nvUnit, energyResult.dryMatterKcalPerKg)
                val nvText = if (nvDm != null) "${formatOneDecimal(nvDm)}% DM" else "${formatOneDecimal(nvAsFed)} $nvUnit"
                list.add(
                    NutrientAssessment(
                        nutrientKey = rawKey,
                        name = nv.name.ifEmpty { formatNutrientLabel(rawKey) },
                        asFedValue = nvAsFed,
                        asFedUnit = nvUnit,
                        dryMatterValue = nvDm,
                        referenceThresholdText = "Label Declaration",
                        status = NutritionStatus.INFORMATIONAL,
                        resultText = nvText,
                        explanation = "Guaranteed or declared nutrient provided on product packaging.",
                        energyBasisValue = nvEnergy?.first,
                        energyBasisUnit = nvEnergy?.second
                    )
                )
            }
        }

        // 13. Overall Status Determination
        val treatExemptionMsg = if (isTreat) {
            "Complementary pet food / treat: Complete FEDIAF daily nutrient requirements do not apply because treats are intended to be fed alongside a complete balanced diet."
        } else {
            null
        }

        val evaluatedMandatory = list.filter { it.nutrientKey in listOf("crudeProtein", "crudeFat", "calcium", "phosphorus") }
        val evaluatedStatuses = evaluatedMandatory.map { it.status }

        val (overallStatus, headline, message) = when {
            isTreat -> Triple(
                NutritionStatus.INFORMATIONAL,
                "Complementary Food / Treat",
                treatExemptionMsg!!
            )
            !isLifeStageSpecified -> Triple(
                NutritionStatus.INSUFFICIENT_DATA,
                "Partial Nutrition Assessment",
                "The product's intended life stage is not specified, so life-stage-specific FEDIAF requirements cannot be fully evaluated."
            )
            !isMoistureAvailable -> Triple(
                NutritionStatus.INSUFFICIENT_DATA,
                "Partial Nutrition Assessment",
                "Moisture percentage is not provided on the label, so Dry Matter comparisons cannot be fully calculated."
            )
            evaluatedStatuses.contains(NutritionStatus.ABOVE_MAXIMUM) || caRatioStatus == NutritionStatus.ABOVE_MAXIMUM -> Triple(
                NutritionStatus.ABOVE_MAXIMUM,
                "Above Safe Limit",
                "One or more nutrient values exceed the FEDIAF nutritional maximum for $targetLifeStageDisplay $petTypeDisplay."
            )
            evaluatedStatuses.contains(NutritionStatus.BELOW) || caRatioStatus == NutritionStatus.BELOW -> Triple(
                NutritionStatus.BELOW,
                "Attention Required",
                "Some available nutrient values are below the applicable FEDIAF reference levels."
            )
            evaluatedStatuses.all { it == NutritionStatus.MEETS } &&
                (caRatioStatus == NutritionStatus.MEETS || caRatioStatus == NutritionStatus.INSUFFICIENT_DATA) -> Triple(
                NutritionStatus.MEETS,
                "Nutrition Check Passed",
                "Available nutrients meet the checked FEDIAF $targetLifeStageDisplay $petTypeDisplay reference levels."
            )
            else -> Triple(
                NutritionStatus.INSUFFICIENT_DATA,
                "Partial Nutrition Assessment",
                "Only a partial assessment is available because some required product information is missing."
            )
        }

        return NutritionAssessment(
            overallStatus = overallStatus,
            overallHeadline = headline,
            overallMessage = message,
            nutrientAssessments = list,
            caRatio = caRatio,
            caRatioText = caRatioText,
            caRatioStatus = caRatioStatus,
            caRatioMin = minCaP,
            caRatioMax = maxCaP,
            dryMatterPercent = dryMatterPercent,
            isMoistureAvailable = isMoistureAvailable,
            lifeStageSpecified = isLifeStageSpecified,
            targetPetType = petTypeDisplay,
            targetLifeStage = targetLifeStageDisplay,
            merMode = merMode,
            resolvedLifeStage = resolvedStage.lifeStage,
            isSeniorMappedToAdult = resolvedStage.isSeniorMappedToAdult,
            seniorMappingExplanation = resolvedStage.seniorMappingExplanation,
            isTreatOrComplementary = isTreat,
            treatExemptionMessage = treatExemptionMsg,
            isCannedOrWetFood = isCannedOrWet,
            isLargeBreedPuppy = isLargeBreed,
            energyResult = energyResult
        )
    }

    private fun createRangeNutrientAssessment(
        key: String,
        displayName: String,
        asFed: Double?,
        dm: Double?,
        minDmRef: Double,
        maxDmRef: Double?,
        isNutritionalMax: Boolean,
        isLifeStageSpecified: Boolean,
        isMoistureAvailable: Boolean,
        isTreat: Boolean,
        meDmKcalKg: Double?,
        explanation: String
    ): NutrientAssessment {
        val maxSuffix = if (isNutritionalMax) " (N)" else ""
        val refText = if (maxDmRef != null) {
            "${formatThreshold(minDmRef)}% – ${formatThreshold(maxDmRef)}% DM$maxSuffix"
        } else {
            "Minimum ${formatThreshold(minDmRef)}% DM"
        }
        val energyPair = NutritionEnergyHelper.calculatePer1000Kcal(dm, "%", meDmKcalKg)

        if (asFed == null) {
            return NutrientAssessment(
                nutrientKey = key,
                name = displayName,
                asFedValue = null,
                asFedUnit = "%",
                dryMatterValue = null,
                referenceThresholdText = refText,
                status = NutritionStatus.INSUFFICIENT_DATA,
                resultText = "Not listed",
                explanation = "$explanation (Label data not specified).",
                minThreshold = minDmRef,
                maxThreshold = maxDmRef,
                isNutritionalMaximum = isNutritionalMax
            )
        }

        if (!isMoistureAvailable || dm == null) {
            return NutrientAssessment(
                nutrientKey = key,
                name = displayName,
                asFedValue = asFed,
                asFedUnit = "%",
                dryMatterValue = null,
                referenceThresholdText = refText,
                status = NutritionStatus.INSUFFICIENT_DATA,
                resultText = "${formatOneDecimal(asFed)}%",
                explanation = "$explanation (Moisture value required to convert to Dry Matter).",
                minThreshold = minDmRef,
                maxThreshold = maxDmRef,
                isNutritionalMaximum = isNutritionalMax
            )
        }

        if (isTreat) {
            return NutrientAssessment(
                nutrientKey = key,
                name = displayName,
                asFedValue = asFed,
                asFedUnit = "%",
                dryMatterValue = dm,
                referenceThresholdText = "Complementary Food (N/A)",
                status = NutritionStatus.INFORMATIONAL,
                resultText = "${formatOneDecimal(dm)}% DM",
                explanation = "$explanation (Evaluated as informational for complementary treats).",
                minThreshold = null,
                maxThreshold = null,
                isNutritionalMaximum = false,
                energyBasisValue = energyPair?.first,
                energyBasisUnit = energyPair?.second
            )
        }

        if (!isLifeStageSpecified) {
            return NutrientAssessment(
                nutrientKey = key,
                name = displayName,
                asFedValue = asFed,
                asFedUnit = "%",
                dryMatterValue = dm,
                referenceThresholdText = refText,
                status = NutritionStatus.INSUFFICIENT_DATA,
                resultText = "${formatOneDecimal(dm)}% DM",
                explanation = "$explanation (Product life stage is not specified).",
                minThreshold = minDmRef,
                maxThreshold = maxDmRef,
                isNutritionalMaximum = isNutritionalMax,
                energyBasisValue = energyPair?.first,
                energyBasisUnit = energyPair?.second
            )
        }

        val belowMin = dm < minDmRef - 1e-6
        val aboveMax = maxDmRef != null && dm > maxDmRef + 1e-6
        val status = when {
            aboveMax -> NutritionStatus.ABOVE_MAXIMUM
            belowMin -> NutritionStatus.BELOW
            else -> NutritionStatus.MEETS
        }
        val resultText = when (status) {
            NutritionStatus.MEETS -> "${formatOneDecimal(dm)}% DM"
            NutritionStatus.BELOW -> "${formatOneDecimal(dm)}% DM — Below ${formatThreshold(minDmRef)}% DM min"
            NutritionStatus.ABOVE_MAXIMUM -> "${formatOneDecimal(dm)}% DM — Above ${formatThreshold(maxDmRef)}% DM max"
            else -> "${formatOneDecimal(dm)}% DM"
        }

        return NutrientAssessment(
            nutrientKey = key,
            name = displayName,
            asFedValue = asFed,
            asFedUnit = "%",
            dryMatterValue = dm,
            referenceThresholdText = refText,
            status = status,
            resultText = resultText,
            explanation = explanation,
            minThreshold = minDmRef,
            maxThreshold = maxDmRef,
            isNutritionalMaximum = isNutritionalMax,
            energyBasisValue = energyPair?.first,
            energyBasisUnit = energyPair?.second
        )
    }

    private fun calculateCaRatio(
        caAsFed: Double?,
        pAsFed: Double?,
        minRatio: Double,
        maxRatio: Double,
        isTreat: Boolean
    ): Triple<Double?, String, NutritionStatus> {
        if (caAsFed == null || pAsFed == null || pAsFed <= 0.0) {
            return Triple(
                null,
                "Not listed",
                NutritionStatus.INSUFFICIENT_DATA
            )
        }

        val ratio = caAsFed / pAsFed
        val formattedRatio = String.format(Locale.US, "%.2f", ratio)
        val text = "$formattedRatio : 1"

        if (isTreat) {
            return Triple(ratio, text, NutritionStatus.INFORMATIONAL)
        }

        val status = when {
            ratio > maxRatio + 1e-6 -> NutritionStatus.ABOVE_MAXIMUM
            ratio < minRatio - 1e-6 -> NutritionStatus.BELOW
            else -> NutritionStatus.MEETS
        }

        return Triple(ratio, text, status)
    }

    private fun extractPercentNutrient(product: Product, vararg keys: String): Double? {
        val nv = NutritionEnergyHelper.extractRawNutrientValue(product, keys.toList()) ?: return null
        val unitLower = nv.unit.trim().lowercase(Locale.ROOT)
        return when {
            unitLower.isEmpty() || unitLower == "%" || unitLower == "g/100g" || unitLower == "g/100 g" || unitLower == "percent" -> nv.value
            unitLower == "g/kg" || unitLower == "g / kg" -> nv.value / 10.0
            unitLower == "mg/kg" || unitLower == "ppm" -> nv.value / 10000.0
            unitLower == "mg/100g" -> nv.value / 1000.0
            else -> null
        }
    }

    private fun formatOneDecimal(value: Double?): String {
        if (value == null) return "-"
        return String.format(Locale.US, "%.1f", value)
    }

    private fun formatThreshold(value: Double?): String {
        if (value == null) return "-"
        return if (value < 1.0 && (value * 100.0) % 10.0 != 0.0) {
            String.format(Locale.US, "%.2f", value)
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun formatNutrientLabel(rawKey: String): String {
        return rawKey.trim()
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }
}
