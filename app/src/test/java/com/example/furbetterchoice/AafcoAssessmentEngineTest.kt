package com.example.furbetterchoice

import com.example.furbetterchoice.api.AafcoAssessmentEngine
import com.example.furbetterchoice.api.AafcoLifeStageCategory
import com.example.furbetterchoice.api.AafcoProfileType
import com.example.furbetterchoice.api.AafcoResultStatus
import com.example.furbetterchoice.api.AafcoSpecies
import com.example.furbetterchoice.api.AssessmentStandard
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.NutritionAssessmentEngine
import com.example.furbetterchoice.api.NutritionStatus
import com.example.furbetterchoice.api.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AafcoAssessmentEngineTest {

    // 1. Adult dog food with available nutrient values (Plus Phase 5 formula check: 24% protein, 10% moisture -> 26.67% DMB)
    @Test
    fun test1_AdultDogFoodWithAvailableNutrientsAndFormulas() {
        val product = Product(
            productId = "AAFCO_01",
            productName = "Adult Dog Complete Formula",
            pet = "Dog",
            lifeStage = "Adult",
            foodType = "Dry Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.08, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.72, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertEquals(AssessmentStandard.AAFCO, result.standard)
        assertEquals(AafcoSpecies.DOG, result.species)
        assertEquals(AafcoLifeStageCategory.ADULT_DOG, result.lifeStageCategory)
        assertEquals(AafcoProfileType.ADULT_MAINTENANCE, result.profileType)
        assertFalse(result.isSeniorMappedToAdultMaintenance)
        assertTrue(result.isMoistureAvailable)
        assertEquals(90.0, result.dryMatterPercent!!, 0.01)

        // Only 5 threshold-backed key nutrients for Dog: Protein, Fat, Calcium, Phosphorus, Ca:P Ratio
        assertEquals(5, result.nutrientResults.size)
        assertTrue(result.nutrientResults.all { it.requiredMinimum != null || it.maximumLimit != null })

        // Protein DMB = (24 / 90) * 100 = 26.67%
        val protein = result.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertEquals(26.67, protein.dryMatterValue!!, 0.01)
        assertEquals(18.0, protein.requiredMinimum!!, 0.01)
        assertEquals(148.15, protein.percentageOfMinimum!!, 0.05)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, protein.status)

        // Formula 3 exact example: 24% DMB vs 18% DMB -> 133.33%
        val pctExample = AafcoAssessmentEngine.calculatePercentageOfMinimum(24.0, 18.0)
        assertEquals(133.33, pctExample!!, 0.01)

        // Ca:P = 1.2 / 0.8 = 1.5:1
        assertEquals(1.5, result.caPRatio!!, 0.01)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, result.caPRatioStatus)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, result.overallStatus)
        assertEquals(100, result.completenessPercentage)
    }

    // 2. Puppy food with growth and reproduction references
    @Test
    fun test2_PuppyFoodGrowthAndReproductionProfile() {
        val product = Product(
            productId = "AAFCO_02",
            productName = "Puppy Growth Formula",
            pet = "Dog",
            lifeStage = "Puppy",
            foodType = "Dry Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 25.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.26, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.99, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertEquals(AafcoSpecies.DOG, result.species)
        assertEquals(AafcoLifeStageCategory.PUPPY, result.lifeStageCategory)
        assertEquals(AafcoProfileType.GROWTH_AND_REPRODUCTION, result.profileType)

        val protein = result.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertEquals(22.5, protein.requiredMinimum!!, 0.01)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, protein.status)

        val ca = result.nutrientResults.first { it.nutrientKey == "calcium" }
        assertEquals(1.2, ca.requiredMinimum!!, 0.01)
        assertEquals(2.5, ca.maximumLimit!!, 0.01)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, ca.status)
    }

    // 3. Adult cat food
    @Test
    fun test3_AdultCatFood() {
        val product = Product(
            productId = "AAFCO_03",
            productName = "Adult Cat Maintenance Kibble",
            pet = "Cat",
            lifeStage = "Adult",
            foodType = "Dry Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 0.72, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.63, "%"),
                "taurine" to NutrientValue("Taurine", 0.12, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertEquals(AafcoSpecies.CAT, result.species)
        assertEquals(AafcoLifeStageCategory.ADULT_CAT, result.lifeStageCategory)
        assertEquals(AafcoProfileType.ADULT_MAINTENANCE, result.profileType)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, result.overallStatus)

        // 6 threshold-backed key nutrients for Cat: Protein, Fat, Calcium, Phosphorus, Ca:P Ratio, Taurine
        assertEquals(6, result.nutrientResults.size)
        assertTrue(result.nutrientResults.all { it.requiredMinimum != null || it.maximumLimit != null })

        val taurine = result.nutrientResults.first { it.nutrientKey == "taurine" }
        assertEquals(0.10, taurine.requiredMinimum!!, 0.001)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, taurine.status)
    }

    // 4. Kitten food (including canned taurine threshold check)
    @Test
    fun test4_KittenFoodGrowthAndReproduction() {
        val product = Product(
            productId = "AAFCO_04",
            productName = "Kitten Canned Pate",
            pet = "Cat",
            lifeStage = "Kitten",
            foodType = "Wet Canned Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 8.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 3.0, "%"),
                "moisture" to NutrientValue("Moisture", 75.0, "%"),
                "calcium" to NutrientValue("Calcium", 0.30, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.25, "%"),
                "taurine" to NutrientValue("Taurine", 0.06, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertEquals(AafcoSpecies.CAT, result.species)
        assertEquals(AafcoLifeStageCategory.KITTEN, result.lifeStageCategory)
        assertEquals(AafcoProfileType.GROWTH_AND_REPRODUCTION, result.profileType)
        assertTrue(result.isCannedOrWetFood)

        val taurine = result.nutrientResults.first { it.nutrientKey == "taurine" }
        assertEquals(0.20, taurine.requiredMinimum!!, 0.001)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, taurine.status)
    }

    // 5. Senior dog using adult maintenance references
    @Test
    fun test5_SeniorDogMappedToAdultMaintenance() {
        val product = Product(
            productId = "AAFCO_05",
            productName = "Senior Dog 7+ Formula",
            pet = "Dog",
            lifeStage = "Senior",
            foodType = "Dry Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 20.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 8.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertEquals(AafcoSpecies.DOG, result.species)
        assertEquals(AafcoLifeStageCategory.SENIOR_DOG, result.lifeStageCategory)
        assertEquals(AafcoProfileType.ADULT_MAINTENANCE, result.profileType)
        assertTrue(result.isSeniorMappedToAdultMaintenance)
        assertNotNull(result.seniorMappingExplanation)
        assertTrue(result.seniorMappingExplanation!!.contains("Adult Maintenance", ignoreCase = true))
    }

    // 6. Senior cat using adult maintenance references
    @Test
    fun test6_SeniorCatMappedToAdultMaintenance() {
        val product = Product(
            productId = "AAFCO_06",
            productName = "Senior Cat 11+ Recipe",
            pet = "Cat",
            lifeStage = "Senior",
            foodType = "Dry Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 28.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertEquals(AafcoSpecies.CAT, result.species)
        assertEquals(AafcoLifeStageCategory.SENIOR_CAT, result.lifeStageCategory)
        assertEquals(AafcoProfileType.ADULT_MAINTENANCE, result.profileType)
        assertTrue(result.isSeniorMappedToAdultMaintenance)
        assertNotNull(result.seniorMappingExplanation)
        assertTrue(result.seniorMappingExplanation!!.contains("Adult Maintenance", ignoreCase = true))
    }

    // 7. Product with missing moisture
    @Test
    fun test7_ProductWithMissingMoisture() {
        val product = Product(
            productId = "AAFCO_07",
            productName = "No Moisture Dog Food",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertFalse(result.isMoistureAvailable)
        assertNull(result.dryMatterPercent)
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, result.overallStatus)

        val protein = result.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertNull(protein.dryMatterValue)
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, protein.status)
    }

    // 8. Product with missing calcium
    @Test
    fun test8_ProductWithMissingCalcium() {
        val product = Product(
            productId = "AAFCO_08",
            productName = "Missing Calcium Dog Food",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.8, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        val ca = result.nutrientResults.first { it.nutrientKey == "calcium" }
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, ca.status)
        assertNull(result.caPRatio)
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, result.caPRatioStatus)
    }

    // 9. Product with missing phosphorus
    @Test
    fun test9_ProductWithMissingPhosphorus() {
        val product = Product(
            productId = "AAFCO_09",
            productName = "Missing Phosphorus Dog Food",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.0, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        val p = result.nutrientResults.first { it.nutrientKey == "phosphorus" }
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, p.status)
        assertNull(result.caPRatio)
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, result.caPRatioStatus)
    }

    // 10. Product with multiple missing nutrients (and ingredient-only mention check)
    @Test
    fun test10_ProductWithMultipleMissingNutrientsAndIngredientMention() {
        val product = Product(
            productId = "AAFCO_10",
            productName = "Typical Commercial Cat Label",
            pet = "Cat",
            lifeStage = "Adult",
            ingredients = "Chicken meal, brown rice, chicken fat, taurine, calcium carbonate",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        // Protein & Fat meet reference; missing Calcium, Phosphorus, Ca:P Ratio, Taurine are INSUFFICIENT_INFORMATION (not failures)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, result.overallStatus)
        assertEquals(2, result.meetsCount)
        assertEquals(0, result.belowCount)
        assertEquals(4, result.insufficientInfoCount)

        // Taurine is in ingredients list only -> must still be INSUFFICIENT_INFORMATION, never MEETS_REFERENCE
        val taurine = result.nutrientResults.first { it.nutrientKey == "taurine" }
        assertTrue(taurine.isListedInIngredientsOnly)
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, taurine.status)
    }

    // 11. Product whose nutrient value is below the minimum
    @Test
    fun test11_NutrientBelowMinimum() {
        val product = Product(
            productId = "AAFCO_11",
            productName = "Low Protein Puppy Food",
            pet = "Dog",
            lifeStage = "Puppy",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 16.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        val protein = result.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertEquals(AafcoResultStatus.BELOW_REFERENCE, protein.status)
        assertEquals(AafcoResultStatus.BELOW_REFERENCE, result.overallStatus)
    }

    // 12. Product whose nutrient value exceeds a maximum (including large-breed puppy calcium limit)
    @Test
    fun test12_NutrientExceedsMaximum() {
        val product = Product(
            productId = "AAFCO_12",
            productName = "Large Breed Puppy High Calcium Food",
            pet = "Dog",
            lifeStage = "Puppy",
            foodType = "Large Breed Dry Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 25.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.80, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 1.08, "%")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        assertTrue(result.isLargeBreedPuppy)
        val ca = result.nutrientResults.first { it.nutrientKey == "calcium" }
        assertEquals(1.8, ca.maximumLimit!!, 0.01)
        assertEquals(AafcoResultStatus.ABOVE_MAXIMUM, ca.status)
        assertEquals(AafcoResultStatus.ABOVE_MAXIMUM, result.overallStatus)
    }

    // 13. Product with unavailable energy information vs available energy information
    @Test
    fun test13_EnergyInformationHandlingAndConversionFormula() {
        val productWithoutEnergy = Product(
            productId = "AAFCO_13A",
            productName = "No ME Food",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )
        val resWithoutEnergy = AafcoAssessmentEngine.assess(productWithoutEnergy)
        assertFalse(resWithoutEnergy.isEnergyAvailable)
        assertNull(resWithoutEnergy.metabolizableEnergyDmKcalKg)
        val protNoEnergy = resWithoutEnergy.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertNull(protNoEnergy.energyBasisValue)

        val productWithEnergy = Product(
            productId = "AAFCO_13B",
            productName = "With ME Food",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "metabolizableEnergy" to NutrientValue("Metabolizable Energy", 3600.0, "kcal/kg")
            )
        )
        val resWithEnergy = AafcoAssessmentEngine.assess(productWithEnergy)
        assertTrue(resWithEnergy.isEnergyAvailable)
        assertEquals(4000.0, resWithEnergy.metabolizableEnergyDmKcalKg!!, 0.1)
        val protWithEnergy = resWithEnergy.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertEquals(66.67, protWithEnergy.energyBasisValue!!, 0.05)
        assertEquals("g/1000 kcal ME", protWithEnergy.energyBasisUnit)
    }

    // 14. Product with incompatible nutrient measurement units
    @Test
    fun test14_IncompatibleNutrientMeasurementUnits() {
        val product = Product(
            productId = "AAFCO_14",
            productName = "Incompatible Unit Food",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 120.0, "IU/L")
            )
        )

        val result = AafcoAssessmentEngine.assess(product)
        val ca = result.nutrientResults.first { it.nutrientKey == "calcium" }
        assertEquals(AafcoResultStatus.INSUFFICIENT_INFORMATION, ca.status)
        assertTrue(ca.productValueDisplay.contains("Incompatible unit", ignoreCase = true))
    }

    // 15. Product with incomplete guaranteed analysis + FEDIAF vs AAFCO independence check
    @Test
    fun test15_IncompleteGuaranteedAnalysisAndFediafIndependence() {
        val product = Product(
            productId = "AAFCO_15",
            productName = "Puppy Borderline Protein Food",
            pet = "Dog",
            lifeStage = "Puppy",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 18.9, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 9.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val fediafResult = NutritionAssessmentEngine.assess(product)
        val aafcoResult = AafcoAssessmentEngine.assess(product)

        val fediafProtein = fediafResult.nutrientAssessments.first { it.nutrientKey == "crudeProtein" }
        val aafcoProtein = aafcoResult.nutrientResults.first { it.nutrientKey == "crudeProtein" }

        assertEquals(NutritionStatus.MEETS, fediafProtein.status)
        assertEquals(AafcoResultStatus.BELOW_REFERENCE, aafcoProtein.status)
    }

    // 16. 2026 AAFCO feline Phosphorus max (2.0% DM) and feline Ca:P ratio minimum (1.0:1)
    @Test
    fun test16_Aafco2026FelinePhosphorusMaxAndCaPRatio() {
        val highPhosphorusCatFood = Product(
            productId = "AAFCO_CAT_P_MAX",
            productName = "High Mineral Cat Food",
            pet = "Cat",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.80, "%"), // 2.00% DM
                "phosphorus" to NutrientValue("Phosphorus", 1.98, "%"), // 2.20% DM (> 2.00% 2026 max!)
                "taurine" to NutrientValue("Taurine", 0.12, "%")
            )
        )

        val res = AafcoAssessmentEngine.assess(highPhosphorusCatFood)
        val p = res.nutrientResults.first { it.nutrientKey == "phosphorus" }
        val cap = res.nutrientResults.first { it.nutrientKey == "caPRatio" }

        assertEquals(2.0, p.maximumLimit!!, 0.01)
        assertEquals(AafcoResultStatus.ABOVE_MAXIMUM, p.status)
        assertEquals(AafcoResultStatus.BELOW_REFERENCE, cap.status) // 1.80 / 1.98 = 0.91 < 1.0
        assertEquals(AafcoResultStatus.ABOVE_MAXIMUM, res.overallStatus)
    }

    // 17. AAFCO > 4,000 kcal ME/kg DM energy density correction (scales upward only)
    @Test
    fun test17_AafcoEnergyDensityScalingAbove4000Only() {
        // 4,800 kcal/kg DM (4,320 as-fed at 90% DM) -> scales Dog Adult protein min from 18.0% to 21.6%
        val highEnergyDog = Product(
            productId = "AAFCO_HIGH_ME",
            pet = "Dog",
            lifeStage = "Adult",
            metabolizableEnergy = 4320.0,
            metabolizableEnergyUnit = "kcal/kg",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 18.0, "%"), // 20.0% DM (< 21.6% scaled min)
                "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )
        val highRes = AafcoAssessmentEngine.assess(highEnergyDog)
        assertTrue(highRes.energyResult.isScaledForThreshold)
        val highProtein = highRes.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertEquals(21.6, highProtein.requiredMinimum!!, 0.05)
        assertEquals(AafcoResultStatus.BELOW_REFERENCE, highProtein.status)

        // 3,500 kcal/kg DM (3,150 as-fed at 90% DM) -> AAFCO does NOT scale minimums down below 18.0%
        val lowEnergyDog = highEnergyDog.copy(
            productId = "AAFCO_LOW_ME",
            metabolizableEnergy = 3150.0
        )
        val lowRes = AafcoAssessmentEngine.assess(lowEnergyDog)
        assertFalse(lowRes.energyResult.isScaledForThreshold)
        val lowProtein = lowRes.nutrientResults.first { it.nutrientKey == "crudeProtein" }
        assertEquals(18.0, lowProtein.requiredMinimum!!, 0.05)
        assertEquals(AafcoResultStatus.MEETS_REFERENCE, lowProtein.status)
    }

    // 18. Treat / complementary food exemption
    @Test
    fun test18_TreatExemptionUnderAafco() {
        val treat = Product(
            productId = "AAFCO_TREAT",
            productName = "Freeze Dried Beef Bites",
            pet = "Dog",
            lifeStage = "Adult",
            foodType = "Treat",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 60.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 8.0, "%"),
                "moisture" to NutrientValue("Moisture", 5.0, "%")
            )
        )

        val res = AafcoAssessmentEngine.assess(treat)
        assertTrue(res.isTreatOrComplementary)
        assertEquals(AafcoResultStatus.NOT_APPLICABLE, res.overallStatus)
        assertNotNull(res.treatExemptionMessage)
    }
}
