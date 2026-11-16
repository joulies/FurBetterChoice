package com.example.furbetterchoice

import com.example.furbetterchoice.api.IngredientAnalyzer
import org.junit.Assert.*
import org.junit.Test

class IngredientAnalyzerTest {

    @Test
    fun testAnalyzeIngredientsSuccess() {
        val ingredientsText = "Chicken, Rice, Taurine, Artificial Colors"
        val analysis = IngredientAnalyzer.analyzeIngredients(ingredientsText, "Dog")

        assertFalse(analysis.isEmpty())
        assertTrue(analysis.any { it.name.contains("Chicken", ignoreCase = true) && it.isBeneficial })
        assertTrue(analysis.any { it.name.contains("Taurine", ignoreCase = true) && it.isBeneficial })
        assertTrue(analysis.any { it.name.contains("Artificial Colors", ignoreCase = true) && !it.isBeneficial })
    }

    @Test
    fun testAnalyzeEmptyIngredients() {
        val analysis = IngredientAnalyzer.analyzeIngredients("", "Cat")
        assertEquals(1, analysis.size)
        assertEquals("Balanced Formulation", analysis[0].name)
    }

    @Test
    fun plantMealsAndOatmealAreNotMisclassifiedAsMarineProtein() {
        val ingredientsText = "Corn Gluten Meal, Soybean Meal, Oatmeal, Salmon Meal, Meat and Bone Meal"
        val analysis = IngredientAnalyzer.analyzeIngredients(ingredientsText, "Dog")

        val cornGluten = analysis.first { it.name.contains("Corn Gluten Meal", ignoreCase = true) }
        val soybeanMeal = analysis.first { it.name.contains("Soybean Meal", ignoreCase = true) }
        val oatmeal = analysis.first { it.name.contains("Oatmeal", ignoreCase = true) }
        val salmonMeal = analysis.first { it.name.contains("Salmon Meal", ignoreCase = true) }
        val meatAndBone = analysis.first { it.name.contains("Meat Bone Meal", ignoreCase = true) || it.name.contains("Meat", ignoreCase = true) }

        assertEquals("Plant Filler / Allergen", cornGluten.category)
        assertFalse(cornGluten.isBeneficial)

        assertEquals("Plant Filler / Allergen", soybeanMeal.category)
        assertEquals("Soy", soybeanMeal.allergenGroup)

        assertEquals("Digestible Carbohydrate", oatmeal.category)
        assertTrue(oatmeal.isBeneficial)

        assertEquals("Marine Protein", salmonMeal.category)
        assertEquals("Fish", salmonMeal.allergenGroup)

        assertEquals("By-Product / Unnamed", meatAndBone.category)
        assertFalse(meatAndBone.isBeneficial)
    }

    @Test
    fun syntheticPreservativesBhaBhtAndDyesAreDetected() {
        val ingredientsText = "Chicken, Brown Rice, BHA, BHT, Red 40"
        val analysis = IngredientAnalyzer.analyzeIngredients(ingredientsText, "Dog")

        val bha = analysis.first { it.name.equals("BHA", ignoreCase = true) }
        val bht = analysis.first { it.name.equals("BHT", ignoreCase = true) }
        val red40 = analysis.first { it.name.contains("Red 40", ignoreCase = true) }

        assertFalse(bha.isBeneficial)
        assertEquals("Synthetic Additive", bha.category)
        assertFalse(bht.isBeneficial)
        assertEquals("Synthetic Additive", bht.category)
        assertFalse(red40.isBeneficial)
        assertEquals("Synthetic Additive", red40.category)
    }

    @Test
    fun qualitySummaryIdentifiesTopProteinAndAllergens() {
        val summary = IngredientAnalyzer.evaluateIngredientQualitySummary(
            "Deboned Chicken, Brown Rice, Salmon Oil, Taurine, Vitamin E Supplement"
        )
        assertTrue(summary.hasNamedAnimalProteinInTop3)
        assertEquals(0, summary.syntheticAdditiveCount)
        assertEquals(0, summary.unnamedByProductCount)
        assertTrue(summary.allergenGroups.contains("Poultry"))
        assertTrue(summary.allergenGroups.contains("Fish"))
    }
}
