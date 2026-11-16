package com.example.furbetterchoice

import com.example.furbetterchoice.api.FediafMerMode
import com.example.furbetterchoice.api.FoodRecommender
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.NutritionEnergyHelper
import com.example.furbetterchoice.api.Pet
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.api.ScoringSystem
import com.example.furbetterchoice.ui.detectSpeciesMatchedAllergens
import com.example.furbetterchoice.ui.resolveComparisonProductSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringSystemAndRecommenderTest {

    @Test
    fun scoringSystemRewardsQualityIngredientsOverRawCharacterLength() {
        val cleanProduct = Product(
            productId = "CLEAN",
            productName = "Simple Chicken Diet",
            pet = "Dog",
            lifeStage = "Adult",
            ingredients = "Deboned Chicken, Brown Rice, Salmon Oil, Taurine, Vitamin E",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 26.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 14.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.1, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.9, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )
        val longSyntheticProduct = cleanProduct.copy(
            productId = "LONG_SYNTH",
            ingredients = "Corn Gluten Meal, Soybean Meal, Meat and Bone Meal, Animal Fat, Wheat Flour, Brewers Rice, Cellulose, BHA, BHT, Red 40, Yellow 5, Blue 2, Propylene Glycol, Artificial Flavor, Calcium Carbonate, Zinc Oxide"
        )

        val cleanScore = ScoringSystem.calculateNutritionalScore(cleanProduct)
        val synthScore = ScoringSystem.calculateNutritionalScore(longSyntheticProduct)

        assertTrue(cleanScore.criteriaScores["Ingredient Quality"]!! > synthScore.criteriaScores["Ingredient Quality"]!!)
        assertTrue(cleanScore.percentage > synthScore.percentage)
    }

    @Test
    fun foodRecommenderRanksCompleteDietAboveTreatAndFiltersAllergens() {
        val dog = Pet(
            petId = "P1",
            petName = "Milo",
            type = "Dog",
            breed = "Beagle",
            age = 3L,
            weight = "12 kg",
            allergies = "Chicken"
        )

        val completeSalmonDiet = Product(
            productId = "COMPLETE_SALMON",
            productName = "Adult Salmon & Rice Dog Food",
            pet = "Dog",
            lifeStage = "Adult",
            foodType = "Dry Food",
            ingredients = "Salmon, Brown Rice, Salmon Oil, Taurine, Mixed Tocopherols",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 26.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 14.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.1, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.9, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val treatProduct = completeSalmonDiet.copy(
            productId = "SALMON_TREAT",
            productName = "Salmon Crunchy Dog Treats",
            foodType = "Treat"
        )

        val chickenDiet = completeSalmonDiet.copy(
            productId = "CHICKEN_DIET",
            productName = "Chicken Dog Food",
            ingredients = "Chicken Meal, Brown Rice, Salmon Oil"
        )

        val ranked = FoodRecommender.rankProducts(dog, listOf(treatProduct, chickenDiet, completeSalmonDiet))

        assertEquals("COMPLETE_SALMON", ranked[0].product.productId)
        assertTrue(ranked[0].suitabilityScore > ranked[1].suitabilityScore)
        val chickenRanked = ranked.first { it.product.productId == "CHICKEN_DIET" }
        assertEquals(0, chickenRanked.suitabilityScore)
    }

    @Test
    fun nfeCarbsAndDailyFeedingCalculatorProduceAccurateEstimates() {
        val kibble = Product(
            productId = "KIBBLE_ME",
            productName = "Adult Dog Kibble",
            pet = "Dog",
            lifeStage = "Adult",
            metabolizableEnergy = 3600.0,
            metabolizableEnergyUnit = "kcal/kg",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 26.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 14.0, "%"),
                "crudeFiber" to NutrientValue("Crude Fiber", 3.0, "%"),
                "ash" to NutrientValue("Ash", 7.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val nfe = NutritionEnergyHelper.calculateEstimatedCarbsNfe(kibble)
        assertNotNull(nfe)
        // 100 - (26 + 14 + 3 + 7 + 10) = 40% As-Fed; 40 / 90 * 100 = 44.44% DM
        assertEquals(40.0, nfe!!.asFedCarbsPercent, 0.01)
        assertEquals(44.44, nfe.dryMatterCarbsPercent!!, 0.05)

        val adultDog = Pet(
            petId = "DOG_1",
            petName = "Max",
            type = "Dog",
            age = 4L,
            weight = "10 kg"
        )
        val standardFeeding = NutritionEnergyHelper.calculateEstimatedDailyFeeding(kibble, adultDog, FediafMerMode.STANDARD)
        val lowerFeeding = NutritionEnergyHelper.calculateEstimatedDailyFeeding(kibble, adultDog, FediafMerMode.LOWER_ACTIVITY)

        assertNotNull(standardFeeding)
        assertNotNull(lowerFeeding)
        assertTrue(standardFeeding!!.dailyKcalNeeded > lowerFeeding!!.dailyKcalNeeded)
        assertTrue(standardFeeding.dailyGrams > 100.0 && standardFeeding.dailyGrams < 300.0)
    }

    @Test
    fun compareAllergenCheckIsolatesCatAndDogAllergiesByProductSpecies() {
        val allergicDog = Pet(
            petId = "DOG_A",
            petName = "Bantay",
            type = "Dog",
            allergies = "Chicken"
        )
        val allergicCat = Pet(
            petId = "CAT_A",
            petName = "Muning",
            type = "Cat",
            allergies = "Tuna"
        )

        val catChickenFood = Product(
            productId = "CAT_CHICKEN",
            productName = "Adult Cat Chicken Feast",
            pet = "Cat",
            ingredients = "Chicken, Brown Rice, Taurine"
        )
        val dogTunaFood = Product(
            productId = "DOG_TUNA",
            productName = "Adult Dog Tuna & Oat",
            pet = "Dog",
            ingredients = "Tuna, Oats, Salmon Oil"
        )
        val dogChickenFood = Product(
            productId = "DOG_CHICKEN",
            productName = "Adult Dog Chicken Kibble",
            pet = "Dog",
            ingredients = "Chicken Meal, Brown Rice"
        )

        assertEquals("Cat", resolveComparisonProductSpecies(catChickenFood))
        assertEquals("Dog", resolveComparisonProductSpecies(dogTunaFood))

        // Cat food must NOT show Dog's chicken allergy even when allergicDog is the active pet
        val catProductMatches = detectSpeciesMatchedAllergens(
            product = catChickenFood,
            pets = listOf(allergicDog, allergicCat),
            activePet = allergicDog
        )
        assertTrue(catProductMatches.isEmpty())

        // Dog food must NOT show Cat's tuna allergy even when allergicCat is the active pet
        val dogProductMatches = detectSpeciesMatchedAllergens(
            product = dogTunaFood,
            pets = listOf(allergicDog, allergicCat),
            activePet = allergicCat
        )
        assertTrue(dogProductMatches.isEmpty())

        // Dog food DOES flag Dog's chicken allergy
        val matchedDog = detectSpeciesMatchedAllergens(
            product = dogChickenFood,
            pets = listOf(allergicDog, allergicCat),
            activePet = allergicDog
        )
        assertEquals(1, matchedDog.size)
        assertEquals("Bantay", matchedDog.first().petName)
        assertEquals(listOf("Chicken"), matchedDog.first().allergens)
    }
}
