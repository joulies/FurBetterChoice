package com.example.furbetterchoice.api

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProductRepository(
    val firestoreRepository: FirestoreRepository = FirestoreRepository(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val apiService: PetFoodApiService = PetFoodApiService.create()
) {

    /**
     * Retrieves a product by barcode string from Firestore using `firestoreRepository.getProductByBarcode`.
     * Query: `products.whereEqualTo("barcode", barcode).limit(1)`
     */
    suspend fun getProductByBarcode(barcode: String): Result<Product?> {
        return firestoreRepository.getProductByBarcode(barcode)
    }

    /**
     * Retrieves a product directly by Product ID from Firestore.
     * Path: `products/{productId}`
     */
    suspend fun getProductById(productId: String): Result<Product?> {
        return firestoreRepository.getProductById(productId)
    }

    /**
     * Combined lookup strategy:
     * 1. Try barcode query against Firestore (`barcode == queryStr`).
     * 2. Try direct Product ID document lookup against Firestore (`products/{queryStr}`).
     * 3. Fallback to OpenPetFoodFacts API if not found in Firestore.
     */
    suspend fun getProduct(queryStr: String): Result<Product?> = withContext(Dispatchers.IO) {
        val trimmedQuery = queryStr.trim()
        if (trimmedQuery.isEmpty()) return@withContext Result.success(null)

        // 1. Try Firestore Barcode Lookup
        val barcodeResult = getProductByBarcode(trimmedQuery)
        val productByBarcode = barcodeResult.getOrNull()
        if (productByBarcode != null) {
            return@withContext Result.success(productByBarcode)
        }

        // 2. Try Firestore Product ID Lookup
        val idResult = getProductById(trimmedQuery)
        val productById = idResult.getOrNull()
        if (productById != null) {
            return@withContext Result.success(productById)
        }

        // 3. Fallback to OpenPetFoodFacts API
        try {
            val apiResponse = apiService.getProduct(trimmedQuery)
            if (apiResponse.isSuccessful) {
                Result.success(apiResponse.body()?.product)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.w("ProductRepository", "API fallback failed for '$trimmedQuery'", e)
            Result.success(null)
        }
    }

    /**
     * Retrieves and ranks alternative products from Firestore matching the target species,
     * with case-insensitive species detection and nutritional quality sorting off the main thread.
     */
    suspend fun getAlternatives(species: String?, excludeBarcode: String?): List<Product> {
        val targetIsCat = species?.lowercase()?.contains("cat") == true ||
            species?.lowercase()?.contains("feline") == true ||
            species?.lowercase()?.contains("kitten") == true
        val targetIsDog = species?.lowercase()?.contains("dog") == true ||
            species?.lowercase()?.contains("canine") == true ||
            species?.lowercase()?.contains("puppy") == true

        fun matchesSpecies(product: Product): Boolean {
            if (!targetIsCat && !targetIsDog) return true
            val pSpec = (product.species.ifBlank { product.pet }).lowercase()
            val tags = product.categoriesTags?.joinToString(" ")?.lowercase().orEmpty()
            val nameText = "${product.productName} ${product.brands}".lowercase()
            val combined = "$pSpec $tags $nameText"

            val isCatProduct = pSpec.contains("cat") || pSpec.contains("feline") ||
                tags.contains("cat") || nameText.contains("cat ") || nameText.contains("kitten")
            val isDogProduct = pSpec.contains("dog") || pSpec.contains("canine") ||
                tags.contains("dog") || nameText.contains("dog ") || nameText.contains("puppy")

            return when {
                targetIsCat -> isCatProduct && !isDogProduct
                targetIsDog -> isDogProduct && !isCatProduct
                else -> combined.isNotBlank()
            }
        }

        fun isExcluded(product: Product): Boolean {
            if (excludeBarcode.isNullOrBlank()) return false
            return product.barcode.equals(excludeBarcode, ignoreCase = true) ||
                product.productId.equals(excludeBarcode, ignoreCase = true)
        }

        return try {
            val speciesFilter = when {
                targetIsCat -> "cat"
                targetIsDog -> "dog"
                else -> null
            }
            val allFirestoreProducts = withContext(Dispatchers.IO) {
                firestoreRepository.getAllProducts(speciesFilter = speciesFilter).getOrNull().orEmpty()
            }
            val matchedFirestore = withContext(Dispatchers.Default) {
                val targetSpeciesLabel = if (targetIsCat) "Cat" else "Dog"
                allFirestoreProducts
                    .filter { !isExcluded(it) && matchesSpecies(it) }
                    .distinctBy { it.barcode.ifBlank { it.productId.ifBlank { "${it.brands}_${it.productName}" } } }
                    .map { product ->
                        val fediaf = NutritionAssessmentEngine.assess(product, targetSpeciesLabel)
                        val baseScore = ScoringSystem.calculateNutritionalScore(product, fediaf).percentage
                        val fediafBonus = when (fediaf.overallStatus) {
                            NutritionStatus.MEETS -> 15
                            NutritionStatus.INFORMATIONAL -> if (fediaf.isTreatOrComplementary) 0 else 5
                            NutritionStatus.BELOW -> -10
                            NutritionStatus.ABOVE_MAXIMUM -> -20
                            NutritionStatus.INSUFFICIENT_DATA -> 0
                        }
                        val treatPenalty = if (fediaf.isTreatOrComplementary) -15 else 0
                        product to (baseScore + fediafBonus + treatPenalty)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
            }

            if (matchedFirestore.isNotEmpty()) {
                matchedFirestore
            } else {
                withContext(Dispatchers.IO) {
                    fetchApiAlternatives(if (targetIsCat) "cat" else "dog")
                        .filter { !isExcluded(it) && matchesSpecies(it) }
                }
            }
        } catch (e: Exception) {
            Log.w("ProductRepository", "Failed to fetch alternatives from Firestore", e)
            withContext(Dispatchers.IO) {
                fetchApiAlternatives(if (targetIsCat) "cat" else "dog")
                    .filter { !isExcluded(it) }
            }
        }
    }

    private suspend fun fetchApiAlternatives(species: String): List<Product> {
        return try {
            val category = if (species.lowercase().contains("cat")) "en:cat-food" else "en:dog-food"
            val response = apiService.searchProducts(category)
            if (response.isSuccessful) {
                response.body()?.products ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getBrandHistory(brandName: String): Result<String?> {
        return firestoreRepository.getBrandHistory(brandName)
    }
}
