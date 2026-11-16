package com.example.furbetterchoice

import com.example.furbetterchoice.api.FirestoreRepository
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.Pet
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.api.Review
import com.example.furbetterchoice.api.SavedProduct
import com.example.furbetterchoice.api.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRepositoryTest {

    @Test
    fun testUserProfileModel() {
        val user = UserProfile(
            userId = "USER_123",
            displayName = "Jane Doe",
            email = "jane@example.com"
        )
        assertEquals("USER_123", user.userId)
        assertEquals("Jane Doe", user.displayName)
        assertEquals("jane@example.com", user.email)
    }

    @Test
    fun testPetModelSchema() {
        val pet = Pet(
            petId = "PET_0001",
            petName = "Buddy",
            breed = "Golden Retriever",
            age = 4,
            imageUrl = "https://example.com/buddy.jpg"
        )
        assertEquals("PET_0001", pet.petId)
        assertEquals("Buddy", pet.petName)
        assertEquals("Golden Retriever", pet.breed)
        assertEquals(4L, pet.age)
        assertEquals("https://example.com/buddy.jpg", pet.imageUrl)
        assertEquals("PET_0001", pet.id)
        assertEquals("Buddy", pet.name)
    }

    @Test
    fun testSavedProductModelSchema() {
        val saved = SavedProduct(
            barcode = "4806538750242",
            productId = "P0001"
        )
        assertEquals("4806538750242", saved.barcode)
        assertEquals("P0001", saved.productId)
    }

    @Test
    fun testProductAndNutritionModelSchema() {
        val protein = NutrientValue(name = "Crude Protein", value = 30.0, unit = "%")
        val fat = NutrientValue(name = "Crude Fat", value = 10.0, unit = "%")

        val product = Product(
            productId = "P0001",
            barcode = "4806538750242",
            brand = "Purina",
            productName = "Pro Plan Adult",
            pet = "Dog",
            lifeStage = "Adult",
            foodType = "Dry Food",
            ingredients = "Chicken, Rice, Corn",
            nutrition = mapOf("crudeProtein" to protein, "crudeFat" to fat)
        )

        assertEquals("P0001", product.productId)
        assertEquals("4806538750242", product.barcode)
        assertEquals("Purina", product.brand)
        assertEquals("Purina", product.brands)
        assertEquals("Pro Plan Adult", product.productName)
        assertEquals("Dog", product.pet)
        assertEquals("Dog", product.species)
        assertEquals("Adult", product.lifeStage)
        assertEquals("Dry Food", product.foodType)
        assertEquals("Chicken, Rice, Corn", product.ingredients)
        assertEquals(30.0, product.nutriments.protein)
        assertEquals(10.0, product.nutriments.fat)
    }

    @Test
    fun testReviewModelSchemaAndProductIdBinding() {
        val product1 = Product(
            productId = "P0001",
            barcode = "4806538750242",
            productName = "Product 1"
        )
        val product2 = Product(
            productId = "P0002",
            barcode = "9334214023564",
            productName = "Product 2"
        )

        val review = Review(
            userId = "USER_123",
            userName = "Jane",
            rating = 5,
            comment = "Great product!"
        )

        // Verify product ID is distinct from barcode
        assertNotEquals(product1.barcode, product1.productId)
        assertNotEquals(product2.barcode, product2.productId)

        // Review target paths use productId
        val reviewPath1 = "products/${product1.productId}/reviews/${review.userId}"
        val reviewPath2 = "products/${product2.productId}/reviews/${review.userId}"

        assertEquals("products/P0001/reviews/USER_123", reviewPath1)
        assertEquals("products/P0002/reviews/USER_123", reviewPath2)
    }

    @Test
    fun computeSimilarProductsMatchesSameBrandAndNameAndExcludesOwnBarcode() {
        val catalog = listOf(
            Product(productId = "P1", barcode = "1111111111111", brand = "Royal Canin", productName = "Indoor Adult Cat Food"),
            Product(productId = "P2", barcode = "2222222222222", brand = "Royal Canin", productName = "Urinary Care Adult Cat Food"),
            Product(productId = "P3", barcode = "3333333333333", brand = "Whiskas", productName = "Indoor Adult Cat Food")
        )

        val matches = FirestoreRepository.computeSimilarProducts(
            brand = "Royal Canin",
            productName = "Indoor Adult Cat Dry Food",
            catalog = catalog,
            excludeBarcode = "9999999999999"
        )

        assertEquals(1, matches.size)
        assertEquals("P1", matches.first().productId)

        // Excludes exact same barcode when updating an existing record
        val excluded = FirestoreRepository.computeSimilarProducts(
            brand = "Royal Canin",
            productName = "Indoor Adult Cat Food",
            catalog = catalog,
            excludeBarcode = "1111111111111"
        )
        assertTrue(excluded.none { it.productId == "P1" })
    }

    @Test
    fun matchesSpeciesFilterFiltersDogAndCatCorrectly() {
        val dogFood = Product(productId = "D1", productName = "Chicken Kibble", pet = "Dog")
        val catFood = Product(productId = "C1", productName = "Tuna Feast", pet = "Cat")

        assertTrue(FirestoreRepository.matchesSpeciesFilter(dogFood, "Dog"))
        assertFalse(FirestoreRepository.matchesSpeciesFilter(catFood, "Dog"))
        assertTrue(FirestoreRepository.matchesSpeciesFilter(catFood, "Cat"))
        assertFalse(FirestoreRepository.matchesSpeciesFilter(dogFood, "Cat"))
        assertTrue(FirestoreRepository.matchesSpeciesFilter(dogFood, null))
    }
}
