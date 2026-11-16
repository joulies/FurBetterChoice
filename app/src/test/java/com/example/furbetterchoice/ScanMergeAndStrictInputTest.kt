package com.example.furbetterchoice

import com.example.furbetterchoice.api.FirestoreRepository
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.NutritionAssessmentEngine
import com.example.furbetterchoice.api.NutritionStatus
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.ui.filterStrictDecimalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanMergeAndStrictInputTest {

    @Test
    fun strictDecimalInputFilterRejectsLettersSpacesAndMultipleDots() {
        assertEquals("26.5", filterStrictDecimalInput("26.5% protein"))
        assertEquals("1.25", filterStrictDecimalInput("1.2.5abc"))
        assertEquals("0.8", filterStrictDecimalInput(".8"))
        assertEquals("", filterStrictDecimalInput("abc% "))
    }

    @Test
    fun safeMergePreservesUntouchedDatabaseFieldsWhileUpdatingNutrientsAndIngredients() {
        val baseDb = Product(
            productId = "123456",
            barcode = "123456",
            productName = "Ocean Feast",
            brand = "PurePaws",
            pet = "Cat",
            lifeStage = "Adult",
            foodType = "Dry Food",
            imageUrl = "https://example.com/bag.jpg",
            brandHistory = "Founded in 2010",
            packagingMaterial = "Recyclable Pouch",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "ash" to NutrientValue("Ash", 7.0, "%")
            )
        )

        val scanned = Product(
            productId = "123456",
            barcode = "123456",
            productName = "Ocean Feast",
            brand = "PurePaws",
            pet = "Cat",
            lifeStage = "Adult",
            foodType = "Dry Food",
            ingredients = "Salmon, Chicken Meal, Taurine",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 34.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 16.0, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.1, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.9, "%"),
                "taurine" to NutrientValue("Taurine", 0.2, "%")
            )
        )

        val merged = FirestoreRepository.mergeProductForSafeSave(scanned, baseDb)

        assertEquals("https://example.com/bag.jpg", merged.imageUrl)
        assertEquals("Founded in 2010", merged.brandHistory)
        assertEquals("Recyclable Pouch", merged.packagingMaterial)
        assertEquals("Salmon, Chicken Meal, Taurine", merged.ingredientsText)
        assertEquals(34.0, merged.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(7.0, merged.nutrition["ash"]?.value ?: 0.0, 0.01)
        assertEquals(16.0, merged.nutrition["crudeFat"]?.value ?: 0.0, 0.01)

        // Verify live assessment evaluates cleanly on merged product
        val fediaf = NutritionAssessmentEngine.assess(merged)
        assertTrue(fediaf.overallStatus == NutritionStatus.MEETS)
    }
}
