package com.example.furbetterchoice

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

class NutritionAssessmentEngineTest {

    @Test
    fun testAdultCatAcceptanceCaseFromSpec() {
        // Spec Section 23 Example Case:
        // Pet = Cat, LifeStage = Adult
        // Crude Protein = 30%, Fat = 10%, Fibre = 4%, Moisture = 10%, Calcium = 1%, Phosphorus = 0.8%
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
            "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
            "crudeFiber" to NutrientValue("Crude Fibre", 4.0, "%"),
            "moisture" to NutrientValue("Moisture", 10.0, "%"),
            "calcium" to NutrientValue("Calcium", 1.0, "%"),
            "phosphorus" to NutrientValue("Phosphorus", 0.8, "%")
        )

        val product = Product(
            productId = "P0001",
            barcode = "4806538750242",
            productName = "Adult Cat Feast",
            pet = "Cat",
            lifeStage = "Adult",
            foodType = "Dry Food",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)

        // 1. Dry Matter = 90%
        assertNotNull(assessment.dryMatterPercent)
        assertEquals(90.0, assessment.dryMatterPercent!!, 0.01)
        assertTrue(assessment.isMoistureAvailable)
        assertTrue(assessment.lifeStageSpecified)

        // 2. Protein DM = 33.3%
        val protein = assessment.nutrientAssessments.find { it.nutrientKey == "crudeProtein" }
        assertNotNull(protein)
        assertEquals(30.0, protein!!.asFedValue!!, 0.01)
        assertEquals(33.33, protein.dryMatterValue!!, 0.05)
        assertEquals(NutritionStatus.MEETS, protein.status)

        // 3. Fat DM = 11.1%
        val fat = assessment.nutrientAssessments.find { it.nutrientKey == "crudeFat" }
        assertNotNull(fat)
        assertEquals(11.11, fat!!.dryMatterValue!!, 0.05)
        assertEquals(NutritionStatus.MEETS, fat.status)

        // 4. Fibre DM = 4.4% (Informational)
        val fibre = assessment.nutrientAssessments.find { it.nutrientKey == "crudeFiber" }
        assertNotNull(fibre)
        assertEquals(NutritionStatus.INFORMATIONAL, fibre!!.status)

        // 5. Calcium & Phosphorus DM
        val ca = assessment.nutrientAssessments.find { it.nutrientKey == "calcium" }
        val p = assessment.nutrientAssessments.find { it.nutrientKey == "phosphorus" }
        assertEquals(NutritionStatus.MEETS, ca!!.status)
        assertEquals(NutritionStatus.MEETS, p!!.status)

        // 6. Ca:P Ratio = 1.25 : 1
        assertNotNull(assessment.caRatio)
        assertEquals(1.25, assessment.caRatio!!, 0.01)
        assertEquals(NutritionStatus.MEETS, assessment.caRatioStatus)

        // 7. Overall Status = MEETS
        assertEquals(NutritionStatus.MEETS, assessment.overallStatus)
        assertTrue(assessment.overallMessage.contains("meet", ignoreCase = true))
    }

    @Test
    fun testAdultDogCase() {
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 20.0, "%"),
            "crudeFat" to NutrientValue("Crude Fat", 6.0, "%"),
            "moisture" to NutrientValue("Moisture", 10.0, "%"),
            "calcium" to NutrientValue("Calcium", 0.6, "%"),
            "phosphorus" to NutrientValue("Phosphorus", 0.5, "%")
        )

        val product = Product(
            productId = "P0002",
            barcode = "9334214023564",
            productName = "Adult Dog Formula",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)
        assertEquals(NutritionStatus.MEETS, assessment.overallStatus)
        assertEquals("Dog", assessment.targetPetType)
        assertEquals("Adult", assessment.targetLifeStage)
    }

    @Test
    fun testPuppyGrowthCase() {
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 26.0, "%"),
            "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
            "moisture" to NutrientValue("Moisture", 10.0, "%"),
            "calcium" to NutrientValue("Calcium", 1.2, "%"),
            "phosphorus" to NutrientValue("Phosphorus", 1.0, "%")
        )

        val product = Product(
            productId = "P0003",
            productName = "Puppy Growth Formula",
            pet = "Dog",
            lifeStage = "Puppy",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)
        assertEquals(NutritionStatus.MEETS, assessment.overallStatus)
        assertEquals("Puppy / Growth", assessment.targetLifeStage)
    }

    @Test
    fun testKittenGrowthCase() {
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 32.0, "%"),
            "crudeFat" to NutrientValue("Crude Fat", 14.0, "%"),
            "moisture" to NutrientValue("Moisture", 10.0, "%"),
            "calcium" to NutrientValue("Calcium", 1.1, "%"),
            "phosphorus" to NutrientValue("Phosphorus", 0.9, "%")
        )

        val product = Product(
            productId = "P0004",
            productName = "Kitten Formula",
            pet = "Cat",
            lifeStage = "Kitten",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)
        assertEquals(NutritionStatus.MEETS, assessment.overallStatus)
        assertEquals("Kitten / Growth", assessment.targetLifeStage)
    }

    @Test
    fun testLifeStageNotSpecifiedPartialAssessment() {
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
            "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
            "moisture" to NutrientValue("Moisture", 10.0, "%")
        )

        val product = Product(
            productId = "P0005",
            productName = "Generic Pet Food",
            pet = "Dog",
            lifeStage = "Not Specified",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)
        assertFalse(assessment.lifeStageSpecified)
        assertEquals(NutritionStatus.INSUFFICIENT_DATA, assessment.overallStatus)
        assertTrue(assessment.overallMessage.contains("not specified", ignoreCase = true))
    }

    @Test
    fun testMissingMoistureHandling() {
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
            "crudeFat" to NutrientValue("Crude Fat", 10.0, "%")
        )

        val product = Product(
            productId = "P0006",
            productName = "No Moisture Food",
            pet = "Cat",
            lifeStage = "Adult",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)
        assertFalse(assessment.isMoistureAvailable)
        assertNull(assessment.dryMatterPercent)
        assertEquals(NutritionStatus.INSUFFICIENT_DATA, assessment.overallStatus)

        val protein = assessment.nutrientAssessments.find { it.nutrientKey == "crudeProtein" }
        assertNotNull(protein)
        assertEquals(30.0, protein!!.asFedValue!!, 0.01)
        assertNull(protein.dryMatterValue)
        assertEquals(NutritionStatus.INSUFFICIENT_DATA, protein.status)
    }

    @Test
    fun testMissingCalciumAndPhosphorusHandling() {
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
            "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
            "moisture" to NutrientValue("Moisture", 10.0, "%")
        )

        val product = Product(
            productId = "P0007",
            productName = "No Minerals Food",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)
        assertNull(assessment.caRatio)
        assertEquals(NutritionStatus.INSUFFICIENT_DATA, assessment.caRatioStatus)
        assertTrue(assessment.caRatioText!!.contains("Not listed", ignoreCase = true))
    }

    @Test
    fun testTaurineEvaluatedOnlyWhenPresent() {
        // Product 1: Cat food without taurine explicitly declared -> taurine row not in assessment, non-taurine nutrients evaluated normally
        val noTaurineProduct = Product(
            productId = "P0008",
            pet = "Cat",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )
        val assessment1 = NutritionAssessmentEngine.assess(noTaurineProduct)
        assertNull(assessment1.nutrientAssessments.find { it.nutrientKey == "taurine" })

        // Product 2: Cat food WITH taurine declared -> taurine evaluated against reference
        val taurineProduct = Product(
            productId = "P0009",
            pet = "Cat",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 10.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "taurine" to NutrientValue("Taurine", 0.15, "%")
            )
        )
        val assessment2 = NutritionAssessmentEngine.assess(taurineProduct)
        val taurineAssessment = assessment2.nutrientAssessments.find { it.nutrientKey == "taurine" }
        assertNotNull(taurineAssessment)
        assertEquals(NutritionStatus.MEETS, taurineAssessment!!.status)
    }

    @Test
    fun testMissingNutrientNeverTreatedAsZero() {
        val nutritionMap = mapOf(
            "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
            "moisture" to NutrientValue("Moisture", 10.0, "%")
            // Crude Fat is missing
        )

        val product = Product(
            productId = "P0010",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = nutritionMap
        )

        val assessment = NutritionAssessmentEngine.assess(product)
        val fatAssessment = assessment.nutrientAssessments.find { it.nutrientKey == "crudeFat" }

        assertNotNull(fatAssessment)
        assertNull(fatAssessment!!.asFedValue)
        assertNull(fatAssessment.dryMatterValue)
        assertEquals(NutritionStatus.INSUFFICIENT_DATA, fatAssessment.status)
        assertEquals("Not listed", fatAssessment.resultText)
    }

    @Test
    fun testFediafAdultDogMer95IncreasesRequiredProteinMinimum() {
        // At 18.0% DM protein (16.2% as-fed at 10% moisture), Dog meets MER 110 (18.0%) but is BELOW MER 95 (21.0%)
        val product = Product(
            productId = "FEDIAF_MER95",
            pet = "Dog",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 16.2, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 6.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 0.6, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.5, "%")
            )
        )

        val mer110 = NutritionAssessmentEngine.assess(product, merMode = com.example.furbetterchoice.api.FediafMerMode.STANDARD)
        val mer95 = NutritionAssessmentEngine.assess(product, merMode = com.example.furbetterchoice.api.FediafMerMode.LOWER_ACTIVITY)

        assertEquals(NutritionStatus.MEETS, mer110.nutrientAssessments.first { it.nutrientKey == "crudeProtein" }.status)
        assertEquals(NutritionStatus.BELOW, mer95.nutrientAssessments.first { it.nutrientKey == "crudeProtein" }.status)
    }

    @Test
    fun testFediafAdultCatMer75Thresholds() {
        // Cat Adult MER 75 requires 33.3% DM protein; 27.0% as-fed at 90% DM = 30.0% DM (meets MER 100 25.0%, below MER 75 33.3%)
        val product = Product(
            productId = "FEDIAF_CAT_MER75",
            pet = "Cat",
            lifeStage = "Adult",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 27.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 9.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 0.72, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.63, "%")
            )
        )

        val mer100 = NutritionAssessmentEngine.assess(product, merMode = com.example.furbetterchoice.api.FediafMerMode.STANDARD)
        val mer75 = NutritionAssessmentEngine.assess(product, merMode = com.example.furbetterchoice.api.FediafMerMode.LOWER_ACTIVITY)

        assertEquals(NutritionStatus.MEETS, mer100.overallStatus)
        assertEquals(NutritionStatus.BELOW, mer75.overallStatus)
    }

    @Test
    fun testFediafEarlyVsLateGrowthAndLargeBreedCaPRatioMax() {
        // Late Growth (>14 wks) Large Breed Puppy: Ca min 1.00%, max 1.60% (N), Ca:P max 1.6:1 (N)
        // Calcium 1.44% as-fed (1.60% DM), Phosphorus 0.81% as-fed (0.90% DM) -> Ca:P = 1.78:1 (> 1.60:1 large-breed max!)
        val product = Product(
            productId = "FEDIAF_LB_PUPPY",
            pet = "Dog",
            lifeStage = "Late Growth",
            foodType = "Large Breed Dry Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 22.5, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 9.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.44, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.81, "%")
            )
        )

        val lbLate = NutritionAssessmentEngine.assess(product)
        assertEquals(com.example.furbetterchoice.api.FediafLifeStage.DOG_LATE_GROWTH, lbLate.resolvedLifeStage)
        assertTrue(lbLate.isLargeBreedPuppy)
        assertEquals(1.6, lbLate.caRatioMax, 0.01)
        assertEquals(NutritionStatus.ABOVE_MAXIMUM, lbLate.caRatioStatus)
        assertEquals(NutritionStatus.ABOVE_MAXIMUM, lbLate.overallStatus)

        // Small/medium breed late growth allows up to 1.8:1 (N) Ca:P
        val smallLate = NutritionAssessmentEngine.assess(product, isLargeBreedPuppyOverride = false)
        assertEquals(1.8, smallLate.caRatioMax, 0.01)
        assertEquals(NutritionStatus.MEETS, smallLate.caRatioStatus)
    }

    @Test
    fun testFediafCannedCatTaurineThresholds() {
        // Canned cat adult MER 100 requires 0.20% DM taurine; MER 75 requires 0.27% DM taurine
        val cannedCatFood = Product(
            productId = "FEDIAF_CANNED_CAT",
            pet = "Cat",
            lifeStage = "Adult",
            foodType = "Wet Canned Food",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 10.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 4.0, "%"),
                "moisture" to NutrientValue("Moisture", 75.0, "%"),
                "calcium" to NutrientValue("Calcium", 0.25, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.20, "%"),
                "taurine" to NutrientValue("Taurine", 0.055, "%") // 0.22% DM
            )
        )

        val mer100 = NutritionAssessmentEngine.assess(cannedCatFood, merMode = com.example.furbetterchoice.api.FediafMerMode.STANDARD)
        val mer75 = NutritionAssessmentEngine.assess(cannedCatFood, merMode = com.example.furbetterchoice.api.FediafMerMode.LOWER_ACTIVITY)

        assertTrue(mer100.isCannedOrWetFood)
        assertEquals(NutritionStatus.MEETS, mer100.nutrientAssessments.first { it.nutrientKey == "taurine" }.status)
        assertEquals(NutritionStatus.BELOW, mer75.nutrientAssessments.first { it.nutrientKey == "taurine" }.status)
    }

    @Test
    fun testFediafBidirectional4000KcalEnergyScaling() {
        // Dog Adult MER 110 base protein min = 18.0% DM at 4000 kcal/kg DM.
        // At 4500 kcal/kg DM (4050 as-fed at 90% DM), scaled protein min = 18.0 * (4500/4000) = 20.25% DM.
        // With 18.0% as-fed (20.0% DM), it meets unscaled 18.0% but is BELOW 20.25% at 4500 kcal/kg DM!
        val highMeProduct = Product(
            productId = "FEDIAF_HIGH_ME",
            pet = "Dog",
            lifeStage = "Adult",
            metabolizableEnergy = 4050.0,
            metabolizableEnergyUnit = "kcal/kg",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 18.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 9.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 0.9, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.72, "%")
            )
        )

        val highRes = NutritionAssessmentEngine.assess(highMeProduct)
        assertTrue(highRes.energyResult.isScaledForThreshold)
        val highProtein = highRes.nutrientAssessments.first { it.nutrientKey == "crudeProtein" }
        assertEquals(20.25, highProtein.minThreshold!!, 0.05)
        assertEquals(NutritionStatus.BELOW, highProtein.status)

        // At 3200 kcal/kg DM (2880 as-fed at 90% DM), scaled protein min = 18.0 * 0.8 = 14.4% DM.
        // With 14.4% as-fed (16.0% DM), it MEETS energy-scaled 14.4% DM!
        val lowMeProduct = highMeProduct.copy(
            productId = "FEDIAF_LOW_ME",
            metabolizableEnergy = 2880.0,
            nutrition = highMeProduct.nutrition + ("crudeProtein" to NutrientValue("Crude Protein", 14.4, "%"))
        )
        val lowRes = NutritionAssessmentEngine.assess(lowMeProduct)
        assertTrue(lowRes.energyResult.isScaledForThreshold)
        val lowProtein = lowRes.nutrientAssessments.first { it.nutrientKey == "crudeProtein" }
        assertEquals(14.4, lowProtein.minThreshold!!, 0.05)
        assertEquals(NutritionStatus.MEETS, lowProtein.status)
    }

    @Test
    fun testFediafSeniorMappingAndTreatExemption() {
        val seniorDogFood = Product(
            productId = "FEDIAF_SENIOR",
            pet = "Dog",
            lifeStage = "Senior",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 20.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 8.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 0.8, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.6, "%")
            )
        )
        val seniorRes = NutritionAssessmentEngine.assess(seniorDogFood)
        assertTrue(seniorRes.isSeniorMappedToAdult)
        assertNotNull(seniorRes.seniorMappingExplanation)

        val dogTreat = Product(
            productId = "FEDIAF_TREAT",
            productName = "Chicken Jerky Dog Treats",
            pet = "Dog",
            lifeStage = "Adult",
            foodType = "Treat / Complementary",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 50.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 3.0, "%"),
                "moisture" to NutrientValue("Moisture", 14.0, "%")
            )
        )
        val treatRes = NutritionAssessmentEngine.assess(dogTreat)
        assertTrue(treatRes.isTreatOrComplementary)
        assertEquals(NutritionStatus.INFORMATIONAL, treatRes.overallStatus)
    }
}
