package com.example.furbetterchoice.api

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class ProductPageResult(
    val products: List<Product>,
    val lastDocument: DocumentSnapshot?,
    val hasMore: Boolean
)

data class ProductTrustSummary(
    val verificationCount: Int = 0,
    val reportCount: Int = 0,
    val userHasVerified: Boolean = false,
    val userHasReported: Boolean = false
)

class FirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val catalogCacheMutex = Mutex()
    private var cachedCatalogProducts: List<Product> = emptyList()
    private var cachedCatalogAtMs: Long = 0L
    val currentUid: String?
        get() = auth.currentUser?.uid

    // ==================================================
    // USER PROFILE
    // Path: `users/{userId}`
    // ==================================================

    /**
     * Creates or updates user profile under `users/{userId}`.
     */
    suspend fun createUserProfile(userId: String, displayName: String, email: String): Result<Unit> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("User ID cannot be empty"))
        val data = hashMapOf(
            "userId" to userId,
            "displayName" to displayName,
            "email" to email,
            "createdAt" to Timestamp.now()
        )
        return try {
            firestore.collection("users")
                .document(userId)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error creating user profile for '$userId'", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves user profile from `users/{userId}`.
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        if (userId.isBlank()) return Result.success(null)
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                Result.success(mapDocToUserProfile(doc))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching user profile '$userId'", e)
            Result.failure(e)
        }
    }

    // ==================================================
    // PRODUCTS
    // Path: `products/{productId}`
    // ==================================================

    /**
     * Retrieves a product directly by Product ID from Firestore.
     * Path: `products/{productId}`
     */
    suspend fun getProductById(productId: String): Result<Product?> {
        if (productId.isBlank()) return Result.success(null)
        return try {
            val doc = firestore.collection("products")
                .document(productId)
                .get()
                .await()

            if (doc.exists()) {
                val product = mapDocToProduct(doc)
                Result.success(product)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching product by ID '$productId'", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves a product by barcode string from Firestore using query `barcode == barcode` with limit(1).
     * Does NOT download the entire collection.
     */
    suspend fun getProductByBarcode(barcode: String): Result<Product?> {
        if (barcode.isBlank()) return Result.success(null)
        return try {
            val snapshot = firestore.collection("products")
                .whereEqualTo("barcode", barcode)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents.first()
                val product = mapDocToProduct(doc)
                Result.success(product)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching product by barcode '$barcode'", e)
            Result.failure(e)
        }
    }

    // ==================================================
    // PETS
    // Path: `users/{currentUid}/pets/{petId}`
    // ==================================================

    /**
     * Fetches all pets for the currently authenticated user from `users/{currentUid}/pets`.
     */
    suspend fun getPets(): Result<List<Pet>> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("pets")
                .get()
                .await()

            val pets = snapshot.documents.mapNotNull { doc -> mapDocToPet(doc) }
            Result.success(pets)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching pets for user '$uid'", e)
            Result.failure(e)
        }
    }

    /**
     * Adds a pet profile under `users/{currentUid}/pets/{petId}`.
     * Fields: petId, petName, breed, age, imageUrl.
     */
    suspend fun addPet(pet: Pet): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        val petId = if (pet.petId.isBlank()) "PET_${System.currentTimeMillis()}" else pet.petId

        val petMap = hashMapOf<String, Any>(
            "petId" to petId,
            "petName" to pet.petName,
            "breed" to pet.breed,
            "age" to pet.age,
            "imageUrl" to pet.imageUrl
        )
        if (pet.type.isNotBlank()) petMap["type"] = pet.type
        if (pet.birthday.isNotBlank()) petMap["birthday"] = pet.birthday
        if (pet.sex.isNotBlank()) petMap["sex"] = pet.sex
        if (pet.weight.isNotBlank()) petMap["weight"] = pet.weight
        if (pet.allergies.isNotBlank()) petMap["allergies"] = pet.allergies

        return try {
            firestore.collection("users")
                .document(uid)
                .collection("pets")
                .document(petId)
                .set(petMap, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error adding pet for user '$uid'", e)
            Result.failure(e)
        }
    }

    /**
     * Updates an existing pet profile under `users/{currentUid}/pets/{petId}`.
     */
    suspend fun updatePet(pet: Pet): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (pet.petId.isBlank()) return Result.failure(IllegalArgumentException("Pet ID cannot be empty"))

        val petMap = hashMapOf<String, Any>(
            "petId" to pet.petId,
            "petName" to pet.petName,
            "breed" to pet.breed,
            "age" to pet.age,
            "imageUrl" to pet.imageUrl
        )
        if (pet.type.isNotBlank()) petMap["type"] = pet.type
        if (pet.birthday.isNotBlank()) petMap["birthday"] = pet.birthday
        if (pet.sex.isNotBlank()) petMap["sex"] = pet.sex
        if (pet.weight.isNotBlank()) petMap["weight"] = pet.weight
        if (pet.allergies.isNotBlank()) petMap["allergies"] = pet.allergies

        return try {
            firestore.collection("users")
                .document(uid)
                .collection("pets")
                .document(pet.petId)
                .set(petMap, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error updating pet '${pet.petId}'", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a pet profile under `users/{currentUid}/pets/{petId}`.
     */
    suspend fun deletePet(petId: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (petId.isBlank()) return Result.failure(IllegalArgumentException("Pet ID cannot be empty"))

        return try {
            firestore.collection("users")
                .document(uid)
                .collection("pets")
                .document(petId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error deleting pet '$petId'", e)
            Result.failure(e)
        }
    }

    // ==================================================
    // SAVED PRODUCTS
    // Path: `users/{currentUid}/saved_products/{barcode}`
    // ==================================================

    /**
     * Retrieves saved product references from `users/{currentUid}/saved_products`.
     */
    suspend fun getSavedProducts(): Result<List<SavedProduct>> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("saved_products")
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { doc ->
                val barcode = doc.getString("barcode") ?: doc.id
                val productId = doc.getString("productId") ?: ""
                val savedAt = doc.getDate("savedAt")
                SavedProduct(barcode = barcode, productId = productId, savedAt = savedAt)
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching saved products for user '$uid'", e)
            Result.failure(e)
        }
    }

    /**
     * Saves a product under `users/{currentUid}/saved_products/{barcode}` using the barcode as document ID.
     */
    suspend fun saveProduct(barcode: String, productId: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (barcode.isBlank()) return Result.failure(IllegalArgumentException("Barcode cannot be empty"))

        val data = hashMapOf(
            "barcode" to barcode,
            "productId" to productId,
            "savedAt" to Timestamp.now()
        )

        return try {
            firestore.collection("users")
                .document(uid)
                .collection("saved_products")
                .document(barcode)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error saving product '$barcode'", e)
            Result.failure(e)
        }
    }

    /**
     * Unsaves a product from `users/{currentUid}/saved_products/{barcode}`.
     */
    suspend fun unsaveProduct(barcode: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (barcode.isBlank()) return Result.failure(IllegalArgumentException("Barcode cannot be empty"))

        return try {
            firestore.collection("users")
                .document(uid)
                .collection("saved_products")
                .document(barcode)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error unsaving product '$barcode'", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a product is saved for the current user.
     */
    suspend fun isProductSaved(barcode: String): Result<Boolean> {
        val uid = currentUid ?: return Result.success(false)
        if (barcode.isBlank()) return Result.success(false)

        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .collection("saved_products")
                .document(barcode)
                .get()
                .await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error checking if product '$barcode' is saved", e)
            Result.failure(e)
        }
    }

    // ==================================================
    // REVIEWS
    // Path: `products/{productId}/reviews/{currentUid}`
    // ==================================================

    /**
     * Retrieves all reviews for a product from `products/{productId}/reviews`.
     */
    suspend fun getReviews(productId: String): Result<List<Review>> {
        if (productId.isBlank()) return Result.success(emptyList())
        return try {
            val snapshot = firestore.collection("products")
                .document(productId)
                .collection("reviews")
                .get()
                .await()

            val reviews = snapshot.documents.mapNotNull { doc -> mapDocToReview(doc) }
            Result.success(reviews)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching reviews for product '$productId'", e)
            Result.failure(e)
        }
    }

    /**
     * Gets current user's review for a product from `products/{productId}/reviews/{currentUid}`.
     */
    suspend fun getCurrentUserReview(productId: String): Result<Review?> {
        val uid = currentUid ?: return Result.success(null)
        if (productId.isBlank()) return Result.success(null)

        return try {
            val doc = firestore.collection("products")
                .document(productId)
                .collection("reviews")
                .document(uid)
                .get()
                .await()

            if (doc.exists()) {
                Result.success(mapDocToReview(doc))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching user review for product '$productId'", e)
            Result.failure(e)
        }
    }

    /**
     * Submits or updates a review under `products/{productId}/reviews/{currentUid}`.
     * Document ID is the Firebase Authentication UID to enforce one review per user per product.
     * Does NOT duplicate productId or barcode inside the review document.
     * Validates rating between 1 and 5.
     */
    suspend fun submitReview(productId: String, review: Review): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (productId.isBlank()) return Result.failure(IllegalArgumentException("Product ID cannot be empty"))
        if (review.rating !in 1..5) return Result.failure(IllegalArgumentException("Rating must be between 1 and 5"))

        val userName = review.userName.ifBlank { auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore("@") ?: "Pet Parent" }

        val reviewData = hashMapOf(
            "userId" to uid,
            "userName" to userName,
            "rating" to review.rating,
            "comment" to review.comment,
            "timestamp" to Timestamp.now()
        )

        return try {
            firestore.collection("products")
                .document(productId)
                .collection("reviews")
                .document(uid)
                .set(reviewData, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error submitting review for product '$productId'", e)
            Result.failure(e)
        }
    }

    /**
     * Updates a review for a product under `products/{productId}/reviews/{currentUid}`.
     */
    suspend fun updateReview(productId: String, review: Review): Result<Unit> {
        return submitReview(productId, review)
    }

    /**
     * Deletes current user's review under `products/{productId}/reviews/{currentUid}`.
     */
    suspend fun deleteReview(productId: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (productId.isBlank()) return Result.failure(IllegalArgumentException("Product ID cannot be empty"))

        return try {
            firestore.collection("products")
                .document(productId)
                .collection("reviews")
                .document(uid)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error deleting review for product '$productId'", e)
            Result.failure(e)
        }
    }

    // ==================================================
    // MAPPING UTILITIES
    // ==================================================

    fun mapDocToProduct(doc: DocumentSnapshot): Product? {
        if (!doc.exists()) return null

        val productId = doc.getString("productId") ?: doc.getString("product_id") ?: doc.id
        val barcode = doc.getString("barcode") ?: doc.getString("code") ?: doc.id
        val brand = doc.getString("brand") ?: doc.getString("brands") ?: ""
        val productName = doc.getString("productName") ?: doc.getString("product_name") ?: doc.getString("name") ?: "Unknown Product"
        val pet = doc.getString("pet") ?: doc.getString("species") ?: ""
        val lifeStage = doc.getString("lifeStage") ?: doc.getString("life_stage") ?: ""
        val foodType = doc.getString("foodType") ?: doc.getString("food_type") ?: ""

        val imageUrl = (doc.get("imageUrl")
            ?: doc.get("image_url")
            ?: doc.get("image")
            ?: doc.get("imageUri")
            ?: doc.get("image_uri")
            ?: doc.get("photoUrl")
            ?: doc.get("photo_url")
            ?: doc.get("url"))?.toString()?.trim() ?: ""

        val ingredients = when (val ing = doc.get("ingredients") ?: doc.get("ingredientsText") ?: doc.get("ingredients_text")) {
            is String -> ing
            is List<*> -> ing.mapNotNull { it?.toString() }.joinToString(", ")
            else -> ""
        }

        val nutritionMap = mutableMapOf<String, NutrientValue>()

        fun isEnergyKeyName(key: String): Boolean {
            val l = key.lowercase(Locale.ROOT)
            val norm = l.replace("[^a-z0-9]".toRegex(), "")
            return l.contains("metaboliz") || l.contains("metabolis") ||
                l.contains("calori") || l.contains("kcal") ||
                l.contains("energy") || norm == "me" ||
                norm.startsWith("mekcal") || norm.startsWith("mekj") ||
                norm.endsWith("kjkg") || norm.endsWith("mjkg")
        }

        fun isEnergyUnitField(key: String): Boolean {
            val norm = key.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
            return norm.endsWith("unit") && (
                norm.contains("energy") || norm.contains("metabol") ||
                    norm.contains("calori") || norm == "meunit"
                )
        }

        val firstNumberRegex = Regex("""\d+(?:,\d{3})*(?:\.\d+)?|\.\d+""")

        fun parseNumberAndUnitFromText(rawText: String, fallbackUnit: String): Pair<Double, String>? {
            val trimmed = rawText.trim()
            val match = firstNumberRegex.find(trimmed) ?: return null
            val num = match.value.replace(",", "").toDoubleOrNull() ?: return null
            if (num <= 0.0) return null
            val afterNum = trimmed.substring(match.range.last + 1).trim()
            val beforeNum = trimmed.substring(0, match.range.first).trim()
            val unit = afterNum.ifBlank { beforeNum }.ifBlank { fallbackUnit }
            return num to unit
        }

        fun mergeRawNutritionMap(rawMap: Map<*, *>?, overwriteExisting: Boolean) {
            rawMap?.forEach { (k, v) ->
                if (k == null) return@forEach
                val keyStr = k.toString()
                if (!overwriteExisting && nutritionMap.containsKey(keyStr)) return@forEach
                val isEnergyKey = isEnergyKeyName(keyStr)
                when (v) {
                    is Map<*, *> -> {
                        val nName = v["name"]?.toString()?.takeIf { it.isNotBlank() } ?: keyStr
                        val rawNumObj = v["value"] ?: v["val"] ?: v["amount"]
                            ?: v["kcal"] ?: v["energy"] ?: v["quantity"] ?: v["number"] ?: v["min"]
                        var parsedUnitFromStr: String? = null
                        val nVal = when (rawNumObj) {
                            is Number -> rawNumObj.toDouble()
                            is String -> {
                                val parsed = parseNumberAndUnitFromText(rawNumObj, "")
                                parsedUnitFromStr = parsed?.second?.takeIf { it.isNotBlank() }
                                parsed?.first ?: 0.0
                            }
                            else -> 0.0
                        }
                        val nUnit = v["unit"]?.toString()?.takeIf { it.isNotBlank() }
                            ?: parsedUnitFromStr
                            ?: if (isEnergyKey) "kcal/kg" else "%"
                        nutritionMap[keyStr] = NutrientValue(nName, nVal, nUnit)
                    }
                    is Number -> {
                        val defaultUnit = if (isEnergyKey) "" else "%"
                        nutritionMap[keyStr] = NutrientValue(keyStr, v.toDouble(), defaultUnit)
                    }
                    is String -> {
                        val parsed = parseNumberAndUnitFromText(v, if (isEnergyKey) "" else "%")
                        if (parsed != null) {
                            nutritionMap[keyStr] = NutrientValue(keyStr, parsed.first, parsed.second)
                        }
                    }
                }
            }
        }

        // Merge legacy/secondary nutrient maps first, then primary "nutrition" map
        listOf("nutriments", "nutrients", "guaranteedAnalysis", "guaranteed_analysis").forEach { mapKey ->
            mergeRawNutritionMap(doc.get(mapKey) as? Map<*, *>, overwriteExisting = false)
        }
        mergeRawNutritionMap(doc.get("nutrition") as? Map<*, *>, overwriteExisting = true)

        // Optional root-level Metabolizable Energy (backward-compatible with legacy records without ME)
        val rootEnergyKeys = listOf(
            "metabolizableEnergy", "metabolizable_energy",
            "metabolizedEnergy", "metabolized_energy",
            "metabolisedEnergy", "metabolisableEnergy",
            "metabolizableEnergyKcal", "metabolizable_energy_kcal",
            "calorieContent", "calorie_content",
            "energyDensity", "energy_density",
            "caloricDensity", "caloric_density",
            "energy_kcal", "energyKcal", "energy_kcal_100g", "energy_100g",
            "me", "energy", "calories",
            "kcal_kg", "kcalPerKg", "kcal_100g", "kcal",
            "kj_kg", "kjPerKg", "mj_kg", "mjPerKg"
        )
        var rootMeValue: Double? = null
        var rootMeUnit: String? = (doc.getString("metabolizableEnergyUnit")
            ?: doc.getString("metabolizable_energy_unit")
            ?: doc.getString("energyUnit")
            ?: doc.getString("energy_unit")
            ?: doc.getString("meUnit"))?.trim()?.takeIf { it.isNotEmpty() }

        val docData = doc.data
        if (rootMeUnit == null && docData != null) {
            rootMeUnit = docData.entries
                .firstOrNull { (k, v) -> isEnergyUnitField(k) && v is String && v.isNotBlank() }
                ?.value?.toString()?.trim()
        }

        fun tryExtractRootEnergy(keyName: String, rawField: Any?): Boolean {
            if (rawField == null) return false
            val keyLower = keyName.lowercase(java.util.Locale.ROOT)
            val inferredKeyUnit = when {
                keyLower.contains("kcal") && keyLower.contains("100g") -> "kcal/100g"
                keyLower.contains("kj") && keyLower.contains("100g") -> "kj/100g"
                keyLower.contains("kj") -> "kj/kg"
                keyLower.contains("mj") -> "mj/kg"
                keyLower.contains("100g") -> "kj/100g"
                else -> null
            }
            when (rawField) {
                is Number -> {
                    val d = rawField.toDouble()
                    if (d > 0.0) {
                        rootMeValue = d
                        if (rootMeUnit == null && inferredKeyUnit != null) {
                            rootMeUnit = inferredKeyUnit
                        }
                        return true
                    }
                }
                is String -> {
                    val parsed = parseNumberAndUnitFromText(rawField, "")
                    if (parsed != null) {
                        rootMeValue = parsed.first
                        if (rootMeUnit == null) {
                            rootMeUnit = parsed.second.takeIf { it.isNotEmpty() } ?: inferredKeyUnit
                        }
                        return true
                    }
                }
                is Map<*, *> -> {
                    val rawNumObj = rawField["value"] ?: rawField["val"] ?: rawField["amount"]
                        ?: rawField["kcal"] ?: rawField["energy"] ?: rawField["quantity"] ?: rawField["number"]
                    val parsedUnitFromStr: String?
                    val d = when (rawNumObj) {
                        is Number -> {
                            parsedUnitFromStr = null
                            rawNumObj.toDouble()
                        }
                        is String -> {
                            val parsed = parseNumberAndUnitFromText(rawNumObj, "")
                            parsedUnitFromStr = parsed?.second?.takeIf { it.isNotEmpty() }
                            parsed?.first
                        }
                        else -> {
                            parsedUnitFromStr = null
                            null
                        }
                    }
                    if (d != null && d > 0.0) {
                        rootMeValue = d
                        if (rootMeUnit == null) {
                            rootMeUnit = rawField["unit"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                                ?: parsedUnitFromStr
                                ?: inferredKeyUnit
                        }
                        return true
                    }
                }
            }
            return false
        }

        for (ek in rootEnergyKeys) {
            if (tryExtractRootEnergy(ek, doc.get(ek))) break
        }
        // Fallback: scan all root fields case/space/underscore-insensitively (e.g., "Metabolizable Energy", "ME")
        if (rootMeValue == null && docData != null) {
            for ((k, v) in docData) {
                if (isEnergyKeyName(k) && !isEnergyUnitField(k) &&
                    !k.equals("nutrition", ignoreCase = true) &&
                    !k.equals("nutriments", ignoreCase = true)
                ) {
                    if (tryExtractRootEnergy(k, v)) break
                }
            }
        }

        val brandHistory = doc.getString("brandHistory") 
            ?: doc.getString("brand_history") 
            ?: doc.getString("history") 
            ?: ""

        val rawPackaging = doc.get("packaging") as? Map<*, *>

        val packagingMaterial = (doc.getString("packagingMaterial")
            ?: doc.getString("packaging_material")
            ?: rawPackaging?.get("packagingMaterial")?.toString()
            ?: rawPackaging?.get("material")?.toString()
            ?: "").trim()

        val packagingType = (doc.getString("packagingType")
            ?: doc.getString("packaging_type")
            ?: rawPackaging?.get("packagingType")?.toString()
            ?: rawPackaging?.get("type")?.toString()
            ?: "").trim()

        val packagingRecyclability = (doc.getString("packagingRecyclability")
            ?: doc.getString("packaging_recyclability")
            ?: doc.getString("recyclability")
            ?: rawPackaging?.get("packagingRecyclability")?.toString()
            ?: rawPackaging?.get("recyclability")?.toString()
            ?: "").trim()

        val packagingBiodegradability = (doc.getString("packagingBiodegradability")
            ?: doc.getString("packaging_biodegradability")
            ?: doc.getString("biodegradability")
            ?: rawPackaging?.get("packagingBiodegradability")?.toString()
            ?: rawPackaging?.get("biodegradability")?.toString()
            ?: "").trim()

        val nutritionPhotoUrl = (doc.getString("nutritionPhotoUrl")
            ?: doc.getString("nutrition_photo_url")
            ?: "").trim()
        val ingredientsPhotoUrl = (doc.getString("ingredientsPhotoUrl")
            ?: doc.getString("ingredients_photo_url")
            ?: "").trim()
        val barcodePhotoUrl = (doc.getString("barcodePhotoUrl")
            ?: doc.getString("barcode_photo_url")
            ?: "").trim()

        return Product(
            productId = productId,
            barcode = barcode,
            brand = brand,
            productName = productName,
            pet = pet,
            lifeStage = lifeStage,
            foodType = foodType,
            ingredients = ingredients,
            nutrition = nutritionMap,
            imageUrl = imageUrl,
            nutritionPhotoUrl = nutritionPhotoUrl,
            ingredientsPhotoUrl = ingredientsPhotoUrl,
            barcodePhotoUrl = barcodePhotoUrl,
            brandHistory = brandHistory,
            packagingMaterial = packagingMaterial,
            packagingType = packagingType,
            packagingRecyclability = packagingRecyclability,
            packagingBiodegradability = packagingBiodegradability,
            metabolizableEnergy = rootMeValue,
            metabolizableEnergyUnit = rootMeUnit
        )
    }

    fun mapDocToPet(doc: DocumentSnapshot): Pet? {
        if (!doc.exists()) return null
        val petId = doc.getString("petId") ?: doc.id
        val petName = doc.getString("petName") ?: doc.getString("name") ?: "Pet"
        val breed = doc.getString("breed") ?: ""
        val age = doc.getLong("age") ?: 0L
        val imageUrl = doc.getString("imageUrl") ?: doc.getString("imageUri") ?: ""
        val type = doc.getString("type") ?: "Dog"
        val birthday = doc.getString("birthday") ?: ""
        val sex = doc.getString("sex") ?: "Male"
        val weight = doc.getString("weight") ?: doc.getDouble("weight")?.toString() ?: ""
        val allergies = doc.getString("allergies") ?: (doc.get("allergies") as? List<*>)?.joinToString(", ") ?: ""

        return Pet(
            petId = petId,
            petName = petName,
            breed = breed,
            age = age,
            imageUrl = imageUrl,
            type = type,
            birthday = birthday,
            sex = sex,
            weight = weight,
            allergies = allergies
        )
    }

    fun mapDocToUserProfile(doc: DocumentSnapshot): UserProfile? {
        if (!doc.exists()) return null
        return try {
            val userId = (doc.get("userId") ?: doc.get("UserId") ?: doc.get("uid") ?: doc.id).toString().trim()
            val rawEmail = (doc.get("email") ?: doc.get("Email") ?: doc.get("userEmail") ?: "").toString().trim()
            val rawName = (doc.get("displayName") ?: doc.get("DisplayName") ?: doc.get("name") ?: doc.get("userName") ?: "").toString().trim()
            val displayName = rawName.ifEmpty {
                if (rawEmail.contains("@")) rawEmail.substringBefore("@") else "User"
            }
            val avatarId = (doc.get("avatarId") ?: doc.get("AvatarId") ?: "dog_1").toString()
            val lastNameChangeTimestamp = runCatching {
                when (val rawTs = doc.get("lastNameChangeTimestamp")) {
                    is Number -> rawTs.toLong()
                    is String -> rawTs.toLongOrNull() ?: 0L
                    else -> 0L
                }
            }.getOrDefault(0L)
            val createdAt = runCatching {
                when (val rawDate = doc.get("createdAt")) {
                    is Timestamp -> rawDate.toDate()
                    is java.util.Date -> rawDate
                    is Number -> java.util.Date(rawDate.toLong())
                    else -> null
                }
            }.getOrNull()

            val isDisabled = runCatching {
                doc.getBoolean("isDisabled")
                    ?: doc.getBoolean("disabled")
                    ?: (doc.getString("status")?.equals("disabled", ignoreCase = true) == true)
            }.getOrDefault(false)

            val isOnline = runCatching {
                doc.getBoolean("isOnline") ?: false
            }.getOrDefault(false)

            val lastActive = runCatching {
                when (val rawLa = doc.get("lastActive")) {
                    is Number -> rawLa.toLong()
                    is Timestamp -> rawLa.toDate().time
                    is java.util.Date -> rawLa.time
                    is String -> rawLa.toLongOrNull() ?: 0L
                    else -> 0L
                }
            }.getOrDefault(0L)

            UserProfile(
                userId = userId.ifEmpty { doc.id },
                displayName = displayName,
                email = rawEmail,
                role = "user",
                avatarId = avatarId,
                isDisabled = isDisabled,
                isOnline = isOnline,
                lastActive = lastActive,
                lastNameChangeTimestamp = lastNameChangeTimestamp,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            Log.w("FirestoreRepository", "Failed to map user doc ${doc.id}", e)
            null
        }
    }

    suspend fun updateUserProfileAvatar(userId: String, avatarId: String): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .update("avatarId", avatarId)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to update avatarId for $userId", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfileNameWithTimestamp(userId: String, newDisplayName: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val updates = mapOf(
                "displayName" to newDisplayName.trim(),
                "lastNameChangeTimestamp" to now
            )
            firestore.collection("users")
                .document(userId)
                .update(updates)
                .await()

            val user = auth.currentUser
            if (user != null) {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(newDisplayName.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to update displayName for $userId", e)
            Result.failure(e)
        }
    }

    fun mergeProductForSafeSave(
        scannedProduct: Product,
        baseDatabaseProduct: Product?
    ): Product = Companion.mergeProductForSafeSave(scannedProduct, baseDatabaseProduct)

    companion object {
        /**
         * Merges newly scanned/edited product details onto an existing database product
         * without wiping untouched fields (image, brandHistory, packaging, existing nutrients).
         */
        fun mergeProductForSafeSave(
            scannedProduct: Product,
            baseDatabaseProduct: Product?
        ): Product {
            if (baseDatabaseProduct == null) return scannedProduct
            val mergedNutrition = LinkedHashMap<String, NutrientValue>(baseDatabaseProduct.nutrition)
            scannedProduct.nutrition.forEach { (k, nv) ->
                if (nv.value.isFinite() && nv.value > 0.0) {
                    mergedNutrition[k] = nv
                }
            }
            return baseDatabaseProduct.copy(
                productId = scannedProduct.productId.ifBlank { baseDatabaseProduct.productId },
                barcode = scannedProduct.barcode.ifBlank { baseDatabaseProduct.barcode },
                brand = scannedProduct.brand.ifBlank { baseDatabaseProduct.brand },
                productName = scannedProduct.productName.ifBlank { baseDatabaseProduct.productName },
                pet = scannedProduct.pet.ifBlank { baseDatabaseProduct.pet },
                lifeStage = scannedProduct.lifeStage.ifBlank { baseDatabaseProduct.lifeStage },
                foodType = scannedProduct.foodType.ifBlank { baseDatabaseProduct.foodType },
                ingredients = scannedProduct.ingredientsText.ifBlank { baseDatabaseProduct.ingredientsText },
                nutrition = mergedNutrition,
                imageUrl = scannedProduct.imageUrl?.takeIf { it.isNotBlank() } ?: baseDatabaseProduct.imageUrl,
                nutritionPhotoUrl = scannedProduct.nutritionPhotoUrl?.takeIf { it.isNotBlank() } ?: baseDatabaseProduct.nutritionPhotoUrl,
                ingredientsPhotoUrl = scannedProduct.ingredientsPhotoUrl?.takeIf { it.isNotBlank() } ?: baseDatabaseProduct.ingredientsPhotoUrl,
                barcodePhotoUrl = scannedProduct.barcodePhotoUrl?.takeIf { it.isNotBlank() } ?: baseDatabaseProduct.barcodePhotoUrl,
                brandHistory = scannedProduct.brandHistory.ifBlank { baseDatabaseProduct.brandHistory },
                packagingMaterial = scannedProduct.packagingMaterial.ifBlank { baseDatabaseProduct.packagingMaterial },
                packagingType = scannedProduct.packagingType.ifBlank { baseDatabaseProduct.packagingType },
                packagingRecyclability = scannedProduct.packagingRecyclability.ifBlank { baseDatabaseProduct.packagingRecyclability },
                packagingBiodegradability = scannedProduct.packagingBiodegradability.ifBlank { baseDatabaseProduct.packagingBiodegradability },
                metabolizableEnergy = scannedProduct.metabolizableEnergy ?: baseDatabaseProduct.metabolizableEnergy,
                metabolizableEnergyUnit = scannedProduct.metabolizableEnergyUnit?.takeIf { it.isNotBlank() }
                    ?: baseDatabaseProduct.metabolizableEnergyUnit
            )
        }

        private const val CATALOG_TTL_MS = 5 * 60 * 1000L

        fun matchesSpeciesFilter(product: Product, speciesFilter: String?): Boolean {
            if (speciesFilter.isNullOrBlank()) return true
            val filterLower = speciesFilter.trim().lowercase(Locale.ROOT)
            val wantCat = filterLower.contains("cat") || filterLower.contains("feline") || filterLower.contains("kitten")
            val wantDog = filterLower.contains("dog") || filterLower.contains("canine") || filterLower.contains("puppy")
            if (!wantCat && !wantDog) return true

            val pSpec = (product.species.ifBlank { product.pet }).lowercase(Locale.ROOT)
            val tags = product.categoriesTags?.joinToString(" ")?.lowercase(Locale.ROOT).orEmpty()
            val nameText = "${product.productName} ${product.brands}".lowercase(Locale.ROOT)
            val isCat = pSpec.contains("cat") || pSpec.contains("feline") || tags.contains("cat") || nameText.contains("kitten")
            val isDog = pSpec.contains("dog") || pSpec.contains("canine") || tags.contains("dog") || nameText.contains("puppy")

            return when {
                wantCat -> isCat && !isDog
                else -> isDog && !isCat
            }
        }

        fun computeSimilarProducts(
            brand: String,
            productName: String,
            catalog: List<Product>,
            excludeBarcode: String = ""
        ): List<Product> {
            val cleanName = productName.trim().lowercase(Locale.ROOT)
            if (cleanName.length < 3) return emptyList()
            val cleanBrand = brand.trim().lowercase(Locale.ROOT)
            val stopTokens = setOf("dog", "cat", "food", "dry", "wet", "formula", "recipe", "for", "with", "and", "the")

            fun tokenize(text: String): Set<String> = text
                .lowercase(Locale.ROOT)
                .replace("[^a-z0-9\\s]".toRegex(), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 2 && it !in stopTokens }
                .toSet()

            val targetTokens = tokenize(cleanName)
            if (targetTokens.isEmpty()) return emptyList()

            return catalog.mapNotNull { existing ->
                val exBarcode = existing.barcode.ifBlank { existing.productId }
                if (excludeBarcode.isNotBlank() && exBarcode.equals(excludeBarcode.trim(), ignoreCase = true)) {
                    return@mapNotNull null
                }
                val exName = existing.productName.trim().lowercase(Locale.ROOT)
                if (exName.isBlank()) return@mapNotNull null
                val exBrand = existing.brands.trim().lowercase(Locale.ROOT)
                val brandMatches = cleanBrand.isBlank() || exBrand.isBlank() ||
                    exBrand == cleanBrand || exBrand.contains(cleanBrand) || cleanBrand.contains(exBrand)
                if (!brandMatches) return@mapNotNull null

                val exTokens = tokenize(exName)
                if (exTokens.isEmpty()) return@mapNotNull null
                val intersection = targetTokens.intersect(exTokens).size
                val union = targetTokens.union(exTokens).size
                val jaccard = if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
                val containsMatch = exName.contains(cleanName) || cleanName.contains(exName)
                if (jaccard >= 0.55 || (containsMatch && intersection >= 2)) {
                    existing to jaccard
                } else {
                    null
                }
            }.sortedByDescending { it.second }.map { it.first }.take(5)
        }
    }

    /**
     * Safely saves a scanned/contributed product directly to `products/{productId}`:
     * 1. Rejects any text containing flagged profanity via [ContentModerationFilter] or gibberish via [LabelAiCorrectionService].
     * 2. Validates numeric nutrition entries.
     * 3. Writes an automatic backup snapshot to `products/{productId}/edit_history`
     *    before overwriting an existing product.
     * 4. Performs a field-by-field merge on `products/{productId}`.
     */
    suspend fun saveOrUpdateProductSafely(
        product: Product,
        baseDatabaseProduct: Product?
    ): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        val merged = mergeProductForSafeSave(product, baseDatabaseProduct)

        if (ContentModerationFilter.containsAnyBadWords(
                merged.productName,
                merged.brands,
                merged.foodType,
                merged.ingredientsText
            )
        ) {
            return Result.failure(IllegalArgumentException("Inappropriate language detected. Please remove flagged words before saving."))
        }

        val nameErr = LabelAiCorrectionService.validateProductOrBrandName(merged.productName, "Product Name")
        if (nameErr != null) {
            return Result.failure(IllegalArgumentException(nameErr))
        }
        if (merged.brands.isNotBlank()) {
            val brandErr = LabelAiCorrectionService.validateProductOrBrandName(merged.brands, "Brand Name")
            if (brandErr != null) {
                return Result.failure(IllegalArgumentException(brandErr))
            }
        }

        val targetId = merged.barcode.ifBlank { merged.productId }.ifBlank {
            return Result.failure(IllegalArgumentException("Barcode or Product ID is required"))
        }

        return try {
            val docRef = firestore.collection("products").document(targetId)

            // Record previous snapshot in edit_history before updating an existing product
            if (baseDatabaseProduct != null) {
                runCatching {
                    val prevNutrition = baseDatabaseProduct.nutrition.mapValues { (_, nv) ->
                        mapOf("name" to nv.name, "value" to nv.value, "unit" to nv.unit)
                    }
                    val historySnapshot = hashMapOf<String, Any>(
                        "productName" to baseDatabaseProduct.productName,
                        "brand" to baseDatabaseProduct.brands,
                        "pet" to baseDatabaseProduct.species,
                        "lifeStage" to baseDatabaseProduct.lifeStage,
                        "foodType" to baseDatabaseProduct.foodType,
                        "ingredients" to baseDatabaseProduct.ingredientsText,
                        "nutrition" to prevNutrition,
                        "savedByUid" to uid,
                        "savedAt" to Timestamp.now()
                    )
                    baseDatabaseProduct.metabolizableEnergy?.let {
                        historySnapshot["metabolizableEnergy"] = it
                    }
                    docRef.collection("edit_history").add(historySnapshot).await()
                }.onFailure { err ->
                    Log.w("FirestoreRepository", "Could not write edit_history snapshot for $targetId", err)
                }
            }

            val cleanNutritionMap = merged.nutrition
                .filterValues { it.value.isFinite() && it.value > 0.0 }
                .mapValues { (_, nv) ->
                    mapOf(
                        "name" to nv.name,
                        "value" to nv.value,
                        "unit" to nv.unit.ifBlank { "%" }
                    )
                }

            val map = mutableMapOf<String, Any>(
                "productId" to targetId,
                "barcode" to targetId,
                "productName" to merged.productName.trim(),
                "brand" to merged.brands.trim(),
                "pet" to merged.species.trim(),
                "lifeStage" to merged.lifeStage.trim(),
                "foodType" to merged.foodType.trim(),
                "ingredients" to merged.ingredientsText.trim(),
                "nutrition" to cleanNutritionMap,
                "updatedByUid" to uid,
                "updatedAt" to Timestamp.now()
            )
            val img = merged.imageUrl
            if (!img.isNullOrBlank()) map["imageUrl"] = img
            val nutPhoto = merged.nutritionPhotoUrl
            if (!nutPhoto.isNullOrBlank()) map["nutritionPhotoUrl"] = nutPhoto
            val ingPhoto = merged.ingredientsPhotoUrl
            if (!ingPhoto.isNullOrBlank()) map["ingredientsPhotoUrl"] = ingPhoto
            val barPhoto = merged.barcodePhotoUrl
            if (!barPhoto.isNullOrBlank()) map["barcodePhotoUrl"] = barPhoto
            if (merged.brandHistory.isNotBlank()) map["brandHistory"] = merged.brandHistory
            if (merged.packagingMaterial.isNotBlank()) map["packagingMaterial"] = merged.packagingMaterial
            if (merged.packagingType.isNotBlank()) map["packagingType"] = merged.packagingType
            if (merged.packagingRecyclability.isNotBlank()) map["packagingRecyclability"] = merged.packagingRecyclability
            if (merged.packagingBiodegradability.isNotBlank()) map["packagingBiodegradability"] = merged.packagingBiodegradability
            val meVal = merged.metabolizableEnergy
            if (meVal != null && meVal.isFinite() && meVal > 0.0) {
                map["metabolizableEnergy"] = meVal
                map["metabolizableEnergyUnit"] = merged.metabolizableEnergyUnit?.takeIf { it.isNotBlank() } ?: "kcal/kg"
            }

            docRef.set(map, SetOptions.merge()).await()
            invalidateProductCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to safely save product $targetId", e)
            Result.failure(e)
        }
    }

    suspend fun invalidateProductCache() {
        catalogCacheMutex.withLock {
            cachedCatalogProducts = emptyList()
            cachedCatalogAtMs = 0L
        }
    }

    /**
     * Fetches a deterministic cursor-paginated slice of `products` ordered by document ID.
     */
    suspend fun getProductsPage(
        speciesFilter: String? = null,
        pageSize: Int = 50,
        lastDocument: DocumentSnapshot? = null
    ): Result<ProductPageResult> {
        return try {
            var query = firestore.collection("products")
                .orderBy(FieldPath.documentId())
                .limit(pageSize.coerceIn(1, 100).toLong())
            if (lastDocument != null) {
                query = query.startAfter(lastDocument)
            }
            val snapshot = query.get().await()
            val mapped = snapshot.documents.mapNotNull { mapDocToProduct(it) }
            val filtered = if (speciesFilter.isNullOrBlank()) {
                mapped
            } else {
                mapped.filter { matchesSpeciesFilter(it, speciesFilter) }
            }
            Result.success(
                ProductPageResult(
                    products = filtered,
                    lastDocument = snapshot.documents.lastOrNull(),
                    hasMore = snapshot.size() >= pageSize
                )
            )
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to fetch paginated products", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches catalog products using a 5-minute TTL cache and cursor pagination instead of an
     * unindexed `.limit(100)` dump.
     */
    suspend fun getAllProducts(
        speciesFilter: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<Product>> {
        val now = System.currentTimeMillis()
        val cachedSnapshot = catalogCacheMutex.withLock {
            if (!forceRefresh && cachedCatalogProducts.isNotEmpty() && (now - cachedCatalogAtMs) < CATALOG_TTL_MS) {
                cachedCatalogProducts
            } else {
                null
            }
        }
        if (cachedSnapshot != null) {
            val filtered = if (speciesFilter.isNullOrBlank()) {
                cachedSnapshot
            } else {
                cachedSnapshot.filter { matchesSpeciesFilter(it, speciesFilter) }
            }
            return Result.success(filtered)
        }

        return try {
            val accumulated = mutableListOf<Product>()
            var cursor: DocumentSnapshot? = null
            var pages = 0
            while (pages < 4) {
                val pageRes = getProductsPage(speciesFilter = null, pageSize = 60, lastDocument = cursor)
                val page = pageRes.getOrElse { err ->
                    return if (accumulated.isNotEmpty()) {
                        Result.success(accumulated.filter { matchesSpeciesFilter(it, speciesFilter) })
                    } else {
                        Result.failure(err)
                    }
                }
                accumulated.addAll(page.products)
                cursor = page.lastDocument
                pages++
                if (!page.hasMore || cursor == null) break
            }

            val deduped = accumulated.distinctBy { it.barcode.ifBlank { it.productId } }
            catalogCacheMutex.withLock {
                cachedCatalogProducts = deduped
                cachedCatalogAtMs = System.currentTimeMillis()
            }
            val filtered = if (speciesFilter.isNullOrBlank()) {
                deduped
            } else {
                deduped.filter { matchesSpeciesFilter(it, speciesFilter) }
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to fetch products", e)
            Result.failure(e)
        }
    }

    suspend fun searchProducts(
        query: String,
        speciesFilter: String? = null,
        limit: Int = 25
    ): Result<List<Product>> {
        val allRes = getAllProducts(speciesFilter = speciesFilter)
        if (allRes.isFailure) return allRes
        val list = allRes.getOrDefault(emptyList())
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return Result.success(list.take(limit))

        val scored = list.mapNotNull { product ->
            val name = product.productName.lowercase(Locale.ROOT)
            val brand = product.brands.lowercase(Locale.ROOT)
            val barcode = product.barcode.lowercase(Locale.ROOT)
            val species = product.species.lowercase(Locale.ROOT)
            val score = when {
                barcode == trimmed -> 100
                barcode.startsWith(trimmed) -> 90
                name.startsWith(trimmed) -> 80
                brand.startsWith(trimmed) -> 70
                name.contains(trimmed) -> 60
                brand.contains(trimmed) -> 50
                species.contains(trimmed) -> 30
                else -> 0
            }
            if (score > 0) product to score else null
        }.sortedByDescending { it.second }.map { it.first }.take(limit)

        return Result.success(scored)
    }

    suspend fun findSimilarProducts(
        brand: String,
        productName: String,
        excludeBarcode: String = ""
    ): Result<List<Product>> {
        val catalog = getAllProducts().getOrElse { return Result.failure(it) }
        return Result.success(computeSimilarProducts(brand, productName, catalog, excludeBarcode))
    }

    // ==================================================
    // COMMUNITY LABEL TRUST (VERIFICATIONS & REPORTS)
    // ==================================================

    suspend fun getProductTrustSummary(productId: String): Result<ProductTrustSummary> {
        if (productId.isBlank()) return Result.success(ProductTrustSummary())
        val uid = currentUid
        return try {
            val productRef = firestore.collection("products").document(productId)
            val verificationsSnap = productRef.collection("verifications").get().await()
            val userReportExists = if (uid != null) {
                runCatching {
                    productRef.collection("reports").document(uid).get().await().exists()
                }.getOrDefault(false)
            } else {
                false
            }
            Result.success(
                ProductTrustSummary(
                    verificationCount = verificationsSnap.size(),
                    reportCount = if (userReportExists) 1 else 0,
                    userHasVerified = uid != null && verificationsSnap.documents.any { it.id == uid },
                    userHasReported = userReportExists
                )
            )
        } catch (e: Exception) {
            Log.w("FirestoreRepository", "Failed to load trust summary for '$productId'", e)
            Result.failure(e)
        }
    }

    suspend fun confirmProductAccuracy(productId: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (productId.isBlank()) return Result.failure(IllegalArgumentException("Product ID cannot be empty"))
        val data = hashMapOf(
            "userId" to uid,
            "timestamp" to Timestamp.now()
        )
        return try {
            firestore.collection("products")
                .document(productId)
                .collection("verifications")
                .document(uid)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to confirm label accuracy for '$productId'", e)
            Result.failure(e)
        }
    }

    suspend fun reportProductMistake(
        productId: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("User not authenticated"))
        if (productId.isBlank()) return Result.failure(IllegalArgumentException("Product ID cannot be empty"))
        val cleanReason = reason.trim().ifBlank { "Incorrect Label Data" }.take(120)
        val cleanDetails = details.trim().take(1000)
        val data = hashMapOf(
            "userId" to uid,
            "reason" to cleanReason,
            "details" to cleanDetails,
            "timestamp" to Timestamp.now()
        )
        return try {
            firestore.collection("products")
                .document(productId)
                .collection("reports")
                .document(uid)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to report mistake for '$productId'", e)
            Result.failure(e)
        }
    }

    suspend fun getBrandHistory(brandName: String): Result<String?> {
        return try {
            val trimmed = brandName.trim()
            Log.d("FirestoreRepository", "getBrandHistory called for brand: '$trimmed'")

            // 1. Direct document lookup in 'brands' collection
            if (trimmed.isNotEmpty()) {
                try {
                    val directDoc = firestore.collection("brands").document(trimmed).get().await()
                    if (directDoc.exists()) {
                        val history = directDoc.getString("brandHistory")
                            ?: directDoc.getString("brand_history")
                            ?: directDoc.getString("history")
                        if (!history.isNullOrBlank()) {
                            Log.d("FirestoreRepository", "Found direct brand doc history for '$trimmed': $history")
                            return Result.success(history)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreRepository", "Direct document lookup failed for '$trimmed'", e)
                }
            }

            // 2. Scan all documents in 'brands' collection
            try {
                val snapshot = firestore.collection("brands").get().await()
                for (doc in snapshot.documents) {
                    val docId = doc.id.trim()
                    val brandField = (doc.getString("brandName") ?: doc.getString("brand") ?: doc.getString("name") ?: "").trim()
                    val history = doc.getString("brandHistory")
                        ?: doc.getString("brand_history")
                        ?: doc.getString("history")

                    if (!history.isNullOrBlank()) {
                        if (trimmed.isEmpty() || docId.equals(trimmed, ignoreCase = true) || brandField.equals(trimmed, ignoreCase = true)) {
                            Log.d("FirestoreRepository", "Found scan brand history in '${doc.id}': $history")
                            return Result.success(history)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Brands collection scan failed for '$trimmed'", e)
            }

            // 3. Look up in 'products' collection as fallback
            try {
                val query = if (trimmed.isNotEmpty()) {
                    firestore.collection("products").whereEqualTo("brand", trimmed).limit(5)
                } else {
                    firestore.collection("products").limit(10)
                }
                val prodSnapshot = query.get().await()
                for (doc in prodSnapshot.documents) {
                    val history = doc.getString("brandHistory") ?: doc.getString("brand_history") ?: doc.getString("history")
                    if (!history.isNullOrBlank()) {
                        Log.d("FirestoreRepository", "Found history in 'products' collection doc '${doc.id}' for '$trimmed': $history")
                        return Result.success(history)
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Products collection history lookup failed for '$trimmed'", e)
            }

            Log.d("FirestoreRepository", "No brand history found in Firestore for '$trimmed'")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Failed to fetch brand history for '$brandName'", e)
            Result.failure(e)
        }
    }

    suspend fun setUserOnlineStatus(uid: String, isOnline: Boolean): Result<Unit> {
        if (uid.isBlank()) return Result.success(Unit)
        return try {
            val updates = hashMapOf<String, Any>(
                "userId" to uid,
                "isOnline" to isOnline,
                "lastActive" to System.currentTimeMillis()
            )
            auth.currentUser?.email?.takeIf { it.isNotBlank() }?.let { updates["email"] = it }
            auth.currentUser?.displayName?.takeIf { it.isNotBlank() }?.let { updates["displayName"] = it }

            firestore.collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirestoreRepository", "Failed to update online status for $uid", e)
            Result.failure(e)
        }
    }

    fun mapDocToReview(doc: DocumentSnapshot): Review? {
        if (!doc.exists()) return null
        val userId = doc.getString("userId") ?: doc.id
        val userName = doc.getString("userName") ?: "Pet Parent"
        val rating = doc.getLong("rating")?.toInt() ?: 0
        val comment = doc.getString("comment") ?: ""
        val timestamp = doc.getDate("timestamp")

        return Review(
            userId = userId,
            userName = userName,
            rating = rating,
            comment = comment,
            timestamp = timestamp
        )
    }
}
