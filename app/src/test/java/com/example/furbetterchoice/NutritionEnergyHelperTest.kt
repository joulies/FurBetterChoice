package com.example.furbetterchoice

import com.example.furbetterchoice.api.EnergySource
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.Nutriments
import com.example.furbetterchoice.api.NutritionEnergyHelper
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.api.ScoringSystem
import com.example.furbetterchoice.api.StandardEnergyAdjustmentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionEnergyHelperTest {

    @Test
    fun rootProductMetabolizableEnergyIsExtractedFirst() {
        val product = Product(
            productId = "E1",
            productName = "Root ME Dog Food",
            pet = "Dog",
            lifeStage = "Adult",
            metabolizableEnergy = 3600.0,
            metabolizableEnergyUnit = "kcal/kg",
            nutrition = mapOf(
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val result = NutritionEnergyHelper.extractAndResolveEnergy(
            product = product,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )

        assertEquals(EnergySource.LABEL_DECLARED, result.source)
        assertEquals(3600.0, result.asFedKcalPerKg!!, 0.01)
        assertEquals(4000.0, result.dryMatterKcalPerKg!!, 0.01)
        assertFalse(result.isScaledForThreshold)
        assertFalse(result.isEstimated)
    }

    @Test
    fun allMetabolizableEnergyAliasesAreRecognized() {
        val aliases = listOf(
            "metabolizableEnergy",
            "metabolized_energy",
            "metabolisedEnergy",
            "metabolisable",
            "me",
            "calories",
            "caloric_density"
        )

        for (alias in aliases) {
            val product = Product(
                productId = "ALIAS_$alias",
                productName = "Alias Test",
                nutrition = mapOf(
                    alias to NutrientValue(alias, 3600.0, "kcal/kg")
                )
            )
            val res = NutritionEnergyHelper.extractAndResolveEnergy(
                product = product,
                dryMatterPercent = 90.0,
                adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
            )
            assertEquals("Failed for alias $alias", EnergySource.LABEL_DECLARED, res.source)
            assertEquals("Failed kcal for alias $alias", 3600.0, res.asFedKcalPerKg!!, 0.01)
        }
    }

    @Test
    fun unitConversionsToKcalPerKgWorkAccurately() {
        val cases = listOf(
            Triple(360.0, "kcal/100g", 3600.0),
            Triple(3.6, "kcal/g", 3600.0),
            Triple(15062.4, "kJ/kg", 3600.0),
            Triple(1506.24, "kJ/100g", 3600.0),
            Triple(15.0624, "MJ/kg", 3600.0),
            Triple(1632.93, "kcal/lb", 3600.0)
        )

        for ((value, unit, expected) in cases) {
            val product = Product(
                productId = "UNIT_$unit",
                nutrition = mapOf("metabolizableEnergy" to NutrientValue("ME", value, unit))
            )
            val res = NutritionEnergyHelper.extractAndResolveEnergy(
                product = product,
                dryMatterPercent = 90.0,
                adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
            )
            assertEquals("Unit $unit failed", EnergySource.LABEL_DECLARED, res.source)
            assertEquals("Conversion for $unit failed", expected, res.asFedKcalPerKg!!, 1.5)
        }
    }

    @Test
    fun openPetFoodFactsEnergy100gInRawMapIsConvertedFromKj() {
        val product = Product(
            productId = "OPFF_1",
            nutriments = Nutriments(
                protein = 25.0,
                fat = 12.0,
                moisture = 10.0,
                rawNutritionMap = mapOf(
                    "energy_100g" to mapOf("value" to 1506.24, "unit" to "kJ")
                )
            )
        )

        val res = NutritionEnergyHelper.extractAndResolveEnergy(
            product = product,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )
        assertEquals(EnergySource.LABEL_DECLARED, res.source)
        assertEquals(3600.0, res.asFedKcalPerKg!!, 1.0)
    }

    @Test
    fun nonConvertibleServingUnitsAreDetectedSafely() {
        val product = Product(
            productId = "CUP_1",
            nutrition = mapOf(
                "metabolizableEnergy" to NutrientValue("Metabolizable Energy", 380.0, "kcal/cup")
            )
        )

        val res = NutritionEnergyHelper.extractAndResolveEnergy(
            product = product,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )
        assertEquals(EnergySource.NON_CONVERTIBLE_UNIT, res.source)
        assertNull(res.asFedKcalPerKg)
        assertFalse(res.isScaledForThreshold)
        assertNotNull(res.nonConvertibleReason)
    }

    @Test
    fun modifiedAtwaterFallbackIsDisplayOnlyAndNeverScalesThresholds() {
        // 30% protein, 15% fat, 10% moisture, 4% fiber, 7% ash -> NFE = 34%
        // kcal/100g = 3.5*30 + 8.5*15 + 3.5*34 = 105 + 127.5 + 119 = 351.5 -> 3515 kcal/kg as-fed
        val product = Product(
            productId = "MA_1",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 15.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "crudeFiber" to NutrientValue("Crude Fiber", 4.0, "%"),
                "ash" to NutrientValue("Ash", 7.0, "%")
            )
        )

        val res = NutritionEnergyHelper.extractAndResolveEnergy(
            product = product,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )

        assertEquals(EnergySource.ESTIMATED_MODIFIED_ATWATER, res.source)
        assertTrue(res.isEstimated)
        assertFalse(res.isScaledForThreshold)
        assertEquals(1.0, res.scaleFactorFrom4000, 0.0001)
        assertEquals(3515.0, res.asFedKcalPerKg!!, 1.0)
        assertEquals(3905.56, res.dryMatterKcalPerKg!!, 1.0)

        // Threshold scaling must be a no-op when energy is estimated
        assertEquals(18.0, NutritionEnergyHelper.scaleThreshold(18.0, res)!!, 0.001)
    }

    @Test
    fun bidirectionalVsAbove4000OnlyScalingModesBehaveDifferently() {
        val lowEnergyProduct = Product(
            productId = "LOW_ME",
            metabolizableEnergy = 3150.0, // 3500 kcal/kg DM at 90% DM
            metabolizableEnergyUnit = "kcal/kg"
        )
        val fediafLow = NutritionEnergyHelper.extractAndResolveEnergy(
            lowEnergyProduct,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )
        val aafcoLow = NutritionEnergyHelper.extractAndResolveEnergy(
            lowEnergyProduct,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.ABOVE_4000_ONLY
        )

        assertTrue(fediafLow.isScaledForThreshold)
        assertEquals(0.875, fediafLow.scaleFactorFrom4000, 0.001)
        assertFalse(aafcoLow.isScaledForThreshold)
        assertEquals(1.0, aafcoLow.scaleFactorFrom4000, 0.001)

        val highEnergyProduct = Product(
            productId = "HIGH_ME",
            metabolizableEnergy = 4320.0, // 4800 kcal/kg DM at 90% DM
            metabolizableEnergyUnit = "kcal/kg"
        )
        val fediafHigh = NutritionEnergyHelper.extractAndResolveEnergy(
            highEnergyProduct,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )
        val aafcoHigh = NutritionEnergyHelper.extractAndResolveEnergy(
            highEnergyProduct,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.ABOVE_4000_ONLY
        )

        assertTrue(fediafHigh.isScaledForThreshold)
        assertEquals(1.2, fediafHigh.scaleFactorFrom4000, 0.001)
        assertTrue(aafcoHigh.isScaledForThreshold)
        assertEquals(1.2, aafcoHigh.scaleFactorFrom4000, 0.001)
    }

    @Test
    fun calculatePer1000KcalConvertsDryMatterPercentCorrectly() {
        // 25% DM at 4000 kcal/kg DM = 250 g/kg / 4 = 62.5 g/1000 kcal ME
        val per1000 = NutritionEnergyHelper.calculatePer1000Kcal(25.0, "%", 4000.0)
        assertNotNull(per1000)
        assertEquals(62.5, per1000!!.first, 0.01)
        assertEquals("g/1000 kcal ME", per1000.second)
    }

    @Test
    fun crudeFiberAndCrudeAshDoNotAffectNutritionalScore() {
        val baseProduct = Product(
            productId = "SCORE_BASE",
            productName = "Balanced Kibble",
            pet = "Dog",
            lifeStage = "Adult",
            ingredients = "Chicken, Brown Rice, Chicken Fat, Minerals, Vitamins, Taurine, Natural Tocopherols, Salmon Oil, Chicory Root, Yucca Extract, Zinc Proteinate, Ferrous Sulfate, Copper Sulfate, Vitamin E Supplement, Vitamin A Supplement.",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 24.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.0, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.8, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )
        val withFiberAndAsh = baseProduct.copy(
            nutrition = baseProduct.nutrition + mapOf(
                "crudeFiber" to NutrientValue("Crude Fiber", 4.5, "%"),
                "ash" to NutrientValue("Ash", 8.0, "%")
            )
        )

        val baseScore = ScoringSystem.calculateNutritionalScore(baseProduct)
        val fiberAshScore = ScoringSystem.calculateNutritionalScore(withFiberAndAsh)

        assertEquals(baseScore.percentage, fiberAshScore.percentage)
        assertEquals(baseScore.criteriaScores, fiberAshScore.criteriaScores)
    }

    @Test
    fun realWorldLabelUnitsAndKeysAreDetectedAsLabelDeclared() {
        val cases = listOf(
            Triple("Metabolizable Energy (ME)", 3600.0, "kcal ME/kg"),
            Triple("ME (As Fed)", 3600.0, "kcal/kg as fed"),
            Triple("Calorie Content", 360.0, "kcal/100 g")
        )

        for ((key, value, unit) in cases) {
            val product = Product(
                productId = "REAL_$key",
                nutrition = mapOf(key to NutrientValue(key, value, unit))
            )
            val res = NutritionEnergyHelper.extractAndResolveEnergy(
                product = product,
                dryMatterPercent = 90.0,
                adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
            )
            assertEquals("Failed for $key / $unit", EnergySource.LABEL_DECLARED, res.source)
            assertEquals("Failed asFed kcal for $key", 3600.0, res.asFedKcalPerKg!!, 0.5)
            assertEquals("Failed DM kcal for $key", 4000.0, res.dryMatterKcalPerKg!!, 0.5)
        }

        val dmUnitProduct = Product(
            productId = "DM_UNIT",
            nutrition = mapOf("ME" to NutrientValue("ME", 4000.0, "kcal/kg DM"))
        )
        val dmRes = NutritionEnergyHelper.extractAndResolveEnergy(
            product = dmUnitProduct,
            dryMatterPercent = 90.0,
            adjustmentMode = StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000
        )
        assertEquals(EnergySource.LABEL_DECLARED, dmRes.source)
        assertEquals(3600.0, dmRes.asFedKcalPerKg!!, 0.5)
        assertEquals(4000.0, dmRes.dryMatterKcalPerKg!!, 0.5)
    }
}
