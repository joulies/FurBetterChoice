package com.example.furbetterchoice.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.furbetterchoice.api.CloudinaryUploadService
import com.example.furbetterchoice.api.ContentModerationFilter
import com.example.furbetterchoice.api.FirestoreRepository
import com.example.furbetterchoice.api.LabelAiCorrectionService
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.Pet
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.api.ProductRepository
import com.example.furbetterchoice.api.ProductTrustSummary
import com.example.furbetterchoice.api.RankedProduct
import com.example.furbetterchoice.api.Review
import com.example.furbetterchoice.api.ScannedFrontInfoResult
import com.example.furbetterchoice.api.ScannedIngredientsResult
import com.example.furbetterchoice.api.ScannedNutrientsResult
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ScannerEntryMode {
    SCAN_BARCODE,
    SCAN_NEW_PRODUCT
}

enum class SmartScanStep {
    BARCODE_SCANNING,
    FETCHING_PRODUCT,
    PROMPT_NEW_PRODUCT,
    CHOOSING_FIRST_TARGET,
    SCANNING_FRONT_OF_BAG,
    SCANNING_NUTRIENTS,
    SCANNING_INGREDIENTS,
    REVIEWING_RESULT
}

data class ScanSessionState(
    val entryMode: ScannerEntryMode = ScannerEntryMode.SCAN_BARCODE,
    val isNewProductFlow: Boolean = false,
    val preferredFirstStep: SmartScanStep? = null,
    val scannedBarcode: String = "",
    val baseDatabaseProduct: Product? = null,
    val workingProduct: Product? = null,
    val alternatives: List<Product> = emptyList(),
    val lowConfidenceNutrientKeys: Set<String> = emptySet(),
    val capturedNutrientKeys: Set<String> = emptySet(),
    val scanStep: SmartScanStep = SmartScanStep.BARCODE_SCANNING,
    val isAiProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val isSavedToDatabase: Boolean = false,
    val saveError: String? = null,
    val statusMessage: String? = null,
    val hasRestoredDraft: Boolean = false,
    val detectedLanguageTag: String = "en",
    val isEnglishPrioritized: Boolean = true,
    val usedAiCorrection: Boolean = false,
    val lastCompletedTarget: SmartScanStep? = null,
    val scanDoneNonce: Long = 0L,
    val pendingFrontPhotoUri: String? = null,
    val pendingNutritionPhotoUri: String? = null,
    val pendingIngredientsPhotoUri: String? = null,
    val pendingBarcodePhotoUri: String? = null
)

class AppViewModel : ViewModel() {
    private var auth: FirebaseAuth? = null
    val firestoreRepository by lazy { FirestoreRepository() }
    val productRepository by lazy { ProductRepository(firestoreRepository) }

    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    private val _pets = MutableStateFlow<List<Pet>>(emptyList())
    val pets: StateFlow<List<Pet>> = _pets.asStateFlow()

    private val _savedProducts = MutableStateFlow<List<Product>>(emptyList())
    val savedProducts: StateFlow<List<Product>> = _savedProducts.asStateFlow()

    private val _recentlyViewed = MutableStateFlow<List<Product>>(emptyList())
    val recentlyViewed: StateFlow<List<Product>> = _recentlyViewed.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _recommendationsCache = MutableStateFlow<Map<String, List<RankedProduct>>>(emptyMap())
    val recommendationsCache: StateFlow<Map<String, List<RankedProduct>>> = _recommendationsCache.asStateFlow()

    private val _productDetailsCache = MutableStateFlow<Map<String, Product>>(emptyMap())
    val productDetailsCache: StateFlow<Map<String, Product>> = _productDetailsCache.asStateFlow()

    private val _alternativesCache = MutableStateFlow<Map<String, List<Product>>>(emptyMap())
    val alternativesCache: StateFlow<Map<String, List<Product>>> = _alternativesCache.asStateFlow()

    private val _productReviews = MutableStateFlow<Map<String, List<Review>>>(emptyMap())
    val productReviews: StateFlow<Map<String, List<Review>>> = _productReviews.asStateFlow()

    private val _productTrustCache = MutableStateFlow<Map<String, ProductTrustSummary>>(emptyMap())
    val productTrustCache: StateFlow<Map<String, ProductTrustSummary>> = _productTrustCache.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<com.example.furbetterchoice.api.UserProfile?>(null)
    val currentUserProfile: StateFlow<com.example.furbetterchoice.api.UserProfile?> = _currentUserProfile.asStateFlow()

    private val _selectedAssessmentPetId = MutableStateFlow<String?>(null)
    val selectedAssessmentPetId: StateFlow<String?> = _selectedAssessmentPetId.asStateFlow()

    private val _selectedMerMode = MutableStateFlow(com.example.furbetterchoice.api.FediafMerMode.STANDARD)
    val selectedMerMode: StateFlow<com.example.furbetterchoice.api.FediafMerMode> = _selectedMerMode.asStateFlow()

    private val _scanSessionState = MutableStateFlow(ScanSessionState())
    val scanSessionState: StateFlow<ScanSessionState> = _scanSessionState.asStateFlow()

    private val draftGson by lazy { Gson() }
    private var appContext: Context? = null

    private var currentAuthUid: String? = null
    private var isAppInForeground: Boolean = true
    private var presenceHeartbeatJob: kotlinx.coroutines.Job? = null

    fun initOfflinePersistence(context: Context) {
        appContext = context.applicationContext
        val uid = currentAuthUid ?: auth?.currentUser?.uid
        hydrateOfflineCaches(uid)
    }

    private fun cacheDirFile(name: String): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "offline_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }

    private fun hydrateOfflineCaches(uid: String?) {
        viewModelScope.launch {
            val recentKey = if (uid.isNullOrBlank()) "recent_guest.json" else "recent_${uid}.json"
            val cachedRecent = withContext(Dispatchers.IO) {
                readJsonList<Product>(recentKey, object : TypeToken<List<Product>>() {}.type)
            }
            if (!cachedRecent.isNullOrEmpty() && _recentlyViewed.value.isEmpty()) {
                _recentlyViewed.value = cachedRecent
                cachedRecent.forEach { p ->
                    val code = p.barcode.ifBlank { p.productId }
                    if (code.isNotBlank()) cacheProductDetails(code, p)
                }
            }
            if (!uid.isNullOrBlank()) {
                val cachedPets = withContext(Dispatchers.IO) {
                    readJsonList<Pet>("pets_${uid}.json", object : TypeToken<List<Pet>>() {}.type)
                }
                if (!cachedPets.isNullOrEmpty() && _pets.value.isEmpty()) {
                    _pets.value = cachedPets
                }
                val cachedSaved = withContext(Dispatchers.IO) {
                    readJsonList<Product>("saved_${uid}.json", object : TypeToken<List<Product>>() {}.type)
                }
                if (!cachedSaved.isNullOrEmpty() && _savedProducts.value.isEmpty()) {
                    _savedProducts.value = cachedSaved
                    cachedSaved.forEach { p ->
                        val code = p.barcode.ifBlank { p.productId }
                        if (code.isNotBlank()) cacheProductDetails(code, p)
                    }
                }
            }
        }
    }

    private fun <T> readJsonList(fileName: String, type: java.lang.reflect.Type): List<T>? {
        return runCatching {
            val file = cacheDirFile(fileName) ?: return null
            if (!file.exists()) return null
            val text = file.readText()
            if (text.isBlank()) return null
            draftGson.fromJson<List<T>>(text, type)
        }.getOrNull()
    }

    private fun <T> persistJsonListAsync(fileName: String, items: List<T>) {
        if (appContext == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = cacheDirFile(fileName) ?: return@runCatching
                file.writeText(draftGson.toJson(items))
            }.onFailure {
                Log.w("AppViewModel", "Failed to persist offline cache $fileName", it)
            }
        }
    }

    fun onAppForegrounded() {
        isAppInForeground = true
        val uid = currentAuthUid ?: auth?.currentUser?.uid
        if (uid != null) {
            updateOnlinePresence(true)
            startPresenceHeartbeat()
        }
    }

    fun onAppBackgrounded() {
        isAppInForeground = false
        stopPresenceHeartbeat()
        val uid = currentAuthUid ?: auth?.currentUser?.uid
        if (uid != null) {
            updateOnlinePresence(false)
        }
    }

    private fun updateOnlinePresence(online: Boolean) {
        val uid = currentAuthUid ?: auth?.currentUser?.uid ?: return
        viewModelScope.launch {
            firestoreRepository.setUserOnlineStatus(uid, online)
        }
    }

    private fun startPresenceHeartbeat() {
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = viewModelScope.launch {
            while (isAppInForeground) {
                kotlinx.coroutines.delay(60_000L) // Refresh lastActive every 60s while app is open
                val uid = currentAuthUid ?: auth?.currentUser?.uid
                if (uid != null && isAppInForeground) {
                    firestoreRepository.setUserOnlineStatus(uid, true)
                }
            }
        }
    }

    private fun stopPresenceHeartbeat() {
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
    }

    fun initAuth() {
        try {
            auth = FirebaseAuth.getInstance()
            val user = auth?.currentUser

            if (user != null) {
                _isUserLoggedIn.value = true
                currentAuthUid = user.uid
                if (isAppInForeground) {
                    updateOnlinePresence(true)
                    startPresenceHeartbeat()
                }
                loadUserData()
            } else {
                _isUserLoggedIn.value = false
                currentAuthUid = null
                clearUserData()
            }

            auth?.addAuthStateListener { firebaseAuth ->
                val newUser = firebaseAuth.currentUser
                if (newUser != null) {
                    val isNewLogin = currentAuthUid != newUser.uid || !_isUserLoggedIn.value
                    currentAuthUid = newUser.uid
                    _isUserLoggedIn.value = true
                    if (isAppInForeground) {
                        updateOnlinePresence(true)
                        startPresenceHeartbeat()
                    }
                    if (isNewLogin) {
                        loadUserData()
                    }
                } else {
                    stopPresenceHeartbeat()
                    currentAuthUid = null
                    _isUserLoggedIn.value = false
                    clearUserData()
                }
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to initialize Firebase Auth listener", e)
        }
    }

    fun logout() {
        val uidToMarkOffline = currentAuthUid ?: auth?.currentUser?.uid
        stopPresenceHeartbeat()
        viewModelScope.launch {
            if (uidToMarkOffline != null) {
                runCatching { firestoreRepository.setUserOnlineStatus(uidToMarkOffline, false) }
            }
            try {
                auth?.signOut()
            } catch (e: Exception) {
                Log.e("AppViewModel", "Logout error", e)
            } finally {
                currentAuthUid = null
                _isUserLoggedIn.value = false
                clearUserData()
            }
        }
    }

    fun clearUserData() {
        stopPresenceHeartbeat()
        _pets.value = emptyList()
        _savedProducts.value = emptyList()
        _recentlyViewed.value = emptyList()
        _recommendationsCache.value = emptyMap()
        _currentUserProfile.value = null
        _selectedAssessmentPetId.value = null
        _selectedMerMode.value = com.example.furbetterchoice.api.FediafMerMode.STANDARD
    }

    fun loadUserData() {
        val uid = currentAuthUid ?: auth?.currentUser?.uid
        hydrateOfflineCaches(uid)
        viewModelScope.launch {
            loadPets()
            loadSavedProducts()
            loadCurrentUserProfile()
        }
    }

    fun loadCurrentUserProfile() {
        viewModelScope.launch {
            val uid = currentAuthUid ?: auth?.currentUser?.uid
            if (uid != null) {
                val userRes = firestoreRepository.getUserProfile(uid)
                val existingProfile = userRes.getOrNull()
                if (existingProfile != null) {
                    _currentUserProfile.value = existingProfile
                } else {
                    val user = auth?.currentUser
                    if (user != null) {
                        val fallbackName = user.displayName?.takeUnless { it.isBlank() }
                            ?: user.email?.substringBefore("@")
                            ?: "Pet Parent"
                        val fallbackEmail = user.email ?: ""
                        _currentUserProfile.value = com.example.furbetterchoice.api.UserProfile(
                            userId = user.uid,
                            displayName = fallbackName,
                            email = fallbackEmail,
                            role = "user"
                        )
                        firestoreRepository.createUserProfile(
                            userId = user.uid,
                            displayName = fallbackName,
                            email = fallbackEmail
                        )
                    }
                }
            }
        }
    }

    fun updateUserAvatar(avatarId: String) {
        viewModelScope.launch {
            val uid = currentAuthUid ?: auth?.currentUser?.uid ?: return@launch
            val res = firestoreRepository.updateUserProfileAvatar(uid, avatarId)
            if (res.isSuccess) {
                _currentUserProfile.update { current ->
                    current?.copy(avatarId = avatarId)
                        ?: com.example.furbetterchoice.api.UserProfile(userId = uid, avatarId = avatarId)
                }
            }
        }
    }

    fun canChangeDisplayName(): Pair<Boolean, Int> {
        val profile = _currentUserProfile.value ?: return Pair(true, 0)
        val lastChange = profile.lastNameChangeTimestamp
        if (lastChange == 0L) return Pair(true, 0)

        val fourteenDaysMs = 14 * 24 * 60 * 60 * 1000L
        val elapsed = System.currentTimeMillis() - lastChange
        if (elapsed >= fourteenDaysMs) {
            return Pair(true, 0)
        } else {
            val remainingMs = fourteenDaysMs - elapsed
            val remainingDays = (remainingMs / (24 * 60 * 60 * 1000L)).toInt() + 1
            return Pair(false, remainingDays)
        }
    }

    fun updateDisplayName(newDisplayName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val uid = currentAuthUid ?: auth?.currentUser?.uid ?: return@launch
            val (canChange, daysRemaining) = canChangeDisplayName()
            if (!canChange) {
                onResult(false, "You can change your name again in $daysRemaining day(s). Names can only be changed once every 2 weeks.")
                return@launch
            }

            val res = firestoreRepository.updateUserProfileNameWithTimestamp(uid, newDisplayName)
            if (res.isSuccess) {
                val now = System.currentTimeMillis()
                _currentUserProfile.update { current ->
                    current?.copy(displayName = newDisplayName, lastNameChangeTimestamp = now)
                        ?: com.example.furbetterchoice.api.UserProfile(userId = uid, displayName = newDisplayName, lastNameChangeTimestamp = now)
                }
                onResult(true, null)
            } else {
                onResult(false, "Failed to update profile name. Please try again.")
            }
        }
    }

    // ==================================================
    // PETS MANAGEMENT
    // ==================================================

    fun loadPets() {
        viewModelScope.launch {
            val uid = currentAuthUid ?: auth?.currentUser?.uid
            val result = firestoreRepository.getPets()
            if (result.isSuccess) {
                val loaded = result.getOrDefault(emptyList())
                _pets.value = loaded
                if (!uid.isNullOrBlank()) {
                    persistJsonListAsync("pets_${uid}.json", loaded)
                }
                val selectedId = _selectedAssessmentPetId.value
                if (selectedId != null && loaded.none { it.petId == selectedId || it.id == selectedId }) {
                    _selectedAssessmentPetId.value = null
                }
            }
        }
    }

    fun selectAssessmentPet(petId: String?) {
        _selectedAssessmentPetId.value = petId
    }

    fun setFediafMerMode(mode: com.example.furbetterchoice.api.FediafMerMode) {
        _selectedMerMode.value = mode
    }

    fun addPet(pet: Pet) {
        if (!_isUserLoggedIn.value) return
        viewModelScope.launch {
            val result = firestoreRepository.addPet(pet)
            if (result.isSuccess) {
                loadPets()
            } else {
                // Local optimistic update fallback if offline
                _pets.update { it + pet }
            }
        }
    }

    fun updatePet(updatedPet: Pet) {
        if (!_isUserLoggedIn.value) return
        viewModelScope.launch {
            val result = firestoreRepository.updatePet(updatedPet)
            if (result.isSuccess) {
                loadPets()
            } else {
                _pets.update { list -> list.map { if (it.petId == updatedPet.petId || it.id == updatedPet.id) updatedPet else it } }
            }
        }
    }

    fun deletePet(petId: String) {
        if (!_isUserLoggedIn.value) return
        viewModelScope.launch {
            val result = firestoreRepository.deletePet(petId)
            if (result.isSuccess) {
                loadPets()
            } else {
                _pets.update { list -> list.filter { it.petId != petId && it.id != petId } }
            }
        }
    }

    // ==================================================
    // SAVED PRODUCTS MANAGEMENT
    // ==================================================

    fun loadSavedProducts() {
        viewModelScope.launch {
            val savedRefsResult = withContext(Dispatchers.IO) {
                firestoreRepository.getSavedProducts()
            }
            if (savedRefsResult.isSuccess) {
                val savedRefs = savedRefsResult.getOrDefault(emptyList())
                val fetchedProducts = withContext(Dispatchers.IO) {
                    coroutineScope {
                        savedRefs.map { ref ->
                            async {
                                val cached = _productDetailsCache.value[ref.barcode]
                                    ?: _productDetailsCache.value[ref.productId]
                                if (cached != null) return@async cached

                                var product: Product? = null
                                if (ref.productId.isNotEmpty()) {
                                    product = productRepository.getProductById(ref.productId).getOrNull()
                                }
                                if (product == null && ref.barcode.isNotEmpty()) {
                                    product = productRepository.getProductByBarcode(ref.barcode).getOrNull()
                                }
                                product ?: Product(
                                    barcode = ref.barcode,
                                    productId = ref.productId,
                                    productName = "Saved Product (${ref.barcode})"
                                )
                            }
                        }.awaitAll()
                    }
                }
                _savedProducts.value = fetchedProducts
                val uid = currentAuthUid ?: auth?.currentUser?.uid
                if (!uid.isNullOrBlank()) {
                    persistJsonListAsync("saved_${uid}.json", fetchedProducts)
                }
            }
        }
    }

    fun toggleSaveProduct(product: Product) {
        if (!_isUserLoggedIn.value) return
        val barcode = product.barcode.ifEmpty { product.productId }
        if (barcode.isEmpty()) return

        val alreadySaved = isProductSaved(barcode)
        viewModelScope.launch {
            if (alreadySaved) {
                removeSavedProduct(barcode)
            } else {
                saveProduct(barcode, product.productId, product)
            }
        }
    }

    fun saveProduct(barcode: String, productId: String, product: Product? = null) {
        if (!_isUserLoggedIn.value) return
        viewModelScope.launch {
            firestoreRepository.saveProduct(barcode, productId)
            if (product != null && _savedProducts.value.none { it.barcode == barcode }) {
                _savedProducts.update { it + product }
            }
            loadSavedProducts()
        }
    }

    fun removeSavedProduct(barcode: String) {
        if (!_isUserLoggedIn.value) return
        viewModelScope.launch {
            firestoreRepository.unsaveProduct(barcode)
            _savedProducts.update { list -> list.filter { it.barcode != barcode && it.productId != barcode } }
        }
    }

    fun deleteSelectedProducts(barcodes: Set<String>) {
        if (!_isUserLoggedIn.value) return
        viewModelScope.launch {
            for (barcode in barcodes) {
                firestoreRepository.unsaveProduct(barcode)
            }
            _savedProducts.update { list -> list.filter { it.barcode !in barcodes && it.productId !in barcodes } }
        }
    }

    fun deleteAllSavedProducts() {
        if (!_isUserLoggedIn.value) return
        viewModelScope.launch {
            val currentBarcodes = _savedProducts.value.map { it.barcode.ifEmpty { it.productId } }
            for (barcode in currentBarcodes) {
                if (barcode.isNotEmpty()) {
                    firestoreRepository.unsaveProduct(barcode)
                }
            }
            _savedProducts.value = emptyList()
        }
    }

    fun isProductSaved(barcode: String?): Boolean {
        if (barcode.isNullOrEmpty()) return false
        return _savedProducts.value.any { it.barcode == barcode || it.productId == barcode }
    }

    // ==================================================
    // REVIEWS MANAGEMENT
    // ==================================================

    fun loadReviewsForProduct(productId: String, barcode: String = "") {
        if (productId.isBlank() && barcode.isBlank()) return
        viewModelScope.launch {
            val targetId = productId.ifBlank { barcode }
            val result = firestoreRepository.getReviews(targetId)
            if (result.isSuccess) {
                val reviews = result.getOrDefault(emptyList())
                _productReviews.update { currentMap ->
                    val newMap = currentMap.toMutableMap()
                    if (productId.isNotBlank()) newMap[productId] = reviews
                    if (barcode.isNotBlank()) newMap[barcode] = reviews
                    newMap
                }
            }
        }
    }

    fun addProductReview(productId: String, review: Review, barcode: String = "") {
        if (!_isUserLoggedIn.value || productId.isBlank()) return
        viewModelScope.launch {
            val result = firestoreRepository.submitReview(productId, review)
            if (result.isSuccess) {
                loadReviewsForProduct(productId, barcode)
            } else {
                // Local optimistic update
                _productReviews.update { currentMap ->
                    val existing = currentMap[productId] ?: emptyList()
                    val filtered = existing.filter { it.userId != review.userId }
                    val updated = filtered + review
                    val newMap = currentMap.toMutableMap()
                    newMap[productId] = updated
                    if (barcode.isNotBlank()) newMap[barcode] = updated
                    newMap
                }
            }
        }
    }

    fun deleteProductReview(productId: String, userId: String, barcode: String = "") {
        if (!_isUserLoggedIn.value || productId.isBlank()) return
        viewModelScope.launch {
            val result = firestoreRepository.deleteReview(productId)
            if (result.isSuccess) {
                loadReviewsForProduct(productId, barcode)
            } else {
                _productReviews.update { currentMap ->
                    val existing = currentMap[productId] ?: return@update currentMap
                    val updated = existing.filter { it.userId != userId }
                    val newMap = currentMap.toMutableMap()
                    newMap[productId] = updated
                    if (barcode.isNotBlank()) newMap[barcode] = updated
                    newMap
                }
            }
        }
    }

    // ==================================================
    // PRODUCT LOOKUP & CACHING
    // ==================================================

    suspend fun fetchProduct(barcodeOrId: String): Result<Product?> {
        val trimmed = barcodeOrId.trim()
        val result = productRepository.getProduct(trimmed)
        val product = result.getOrNull() ?: _productDetailsCache.value[trimmed]
        if (product != null) {
            cacheProductDetails(trimmed, product)
            if (product.barcode.isNotBlank()) {
                cacheProductDetails(product.barcode, product)
            }
            addRecentlyViewed(product)
            val targetProductId = product.productId.ifEmpty { trimmed }
            loadReviewsForProduct(targetProductId, product.barcode)
            loadProductTrust(targetProductId, product.barcode)
            return Result.success(product)
        }
        return result
    }

    suspend fun fetchAlternatives(species: String?, excludeBarcode: String?): List<Product> {
        return productRepository.getAlternatives(species, excludeBarcode)
    }

    suspend fun getBrandHistory(brandName: String): String? {
        return productRepository.getBrandHistory(brandName).getOrNull()
    }

    fun getNutritionAssessment(
        product: Product,
        userPetType: String? = null,
        userPetBirthday: String? = null,
        userPetAgeYears: Long? = null,
        userPetBreed: String? = null,
        merMode: com.example.furbetterchoice.api.FediafMerMode = _selectedMerMode.value
    ): com.example.furbetterchoice.api.NutritionAssessment {
        return com.example.furbetterchoice.api.NutritionAssessmentEngine.assess(
            product = product,
            userPetType = userPetType,
            userPetBirthday = userPetBirthday,
            userPetAgeYears = userPetAgeYears,
            userPetBreed = userPetBreed,
            merMode = merMode
        )
    }

    fun getAafcoAssessment(
        product: Product,
        userPetType: String? = null,
        userPetLifeStage: String? = null,
        userPetAgeYears: Long? = null,
        userPetBirthday: String? = null,
        userPetBreed: String? = null
    ): com.example.furbetterchoice.api.AafcoAssessmentResult {
        return com.example.furbetterchoice.api.AafcoAssessmentEngine.assess(
            product = product,
            userPetType = userPetType,
            userPetLifeStage = userPetLifeStage,
            userPetAgeYears = userPetAgeYears,
            userPetBirthday = userPetBirthday,
            userPetBreed = userPetBreed
        )
    }

    fun addRecentlyViewed(product: Product) {
        _recentlyViewed.update { currentList ->
            val filtered = currentList.filter { it.barcode != product.barcode || it.productId != product.productId }
            val updated = (listOf(product) + filtered).take(10)
            val uid = currentAuthUid ?: auth?.currentUser?.uid
            val recentKey = if (uid.isNullOrBlank()) "recent_guest.json" else "recent_${uid}.json"
            persistJsonListAsync(recentKey, updated)
            updated
        }
    }

    suspend fun searchCatalogProducts(
        query: String,
        speciesFilter: String? = null
    ): List<Product> {
        val trimmed = query.trim()
        val remote = firestoreRepository.searchProducts(
            query = trimmed,
            speciesFilter = speciesFilter,
            limit = 30
        ).getOrNull().orEmpty()

        val localPool = (_savedProducts.value + _recentlyViewed.value + _productDetailsCache.value.values)
            .distinctBy { it.barcode.ifBlank { it.productId } }
            .filter { p ->
                val matchesSpecies = FirestoreRepository.matchesSpeciesFilter(p, speciesFilter)
                val matchesQuery = trimmed.isEmpty() ||
                    p.productName.contains(trimmed, ignoreCase = true) ||
                    p.brands.contains(trimmed, ignoreCase = true) ||
                    p.barcode.contains(trimmed, ignoreCase = true)
                matchesSpecies && matchesQuery
            }

        return (remote + localPool)
            .distinctBy { it.barcode.ifBlank { it.productId } }
            .take(30)
    }

    suspend fun findPotentialDuplicateProducts(
        brand: String,
        productName: String,
        excludeBarcode: String = ""
    ): List<Product> {
        val remote = firestoreRepository.findSimilarProducts(
            brand = brand,
            productName = productName,
            excludeBarcode = excludeBarcode
        ).getOrNull()
        if (remote != null) return remote
        val localCatalog = (_savedProducts.value + _recentlyViewed.value + _productDetailsCache.value.values)
            .distinctBy { it.barcode.ifBlank { it.productId } }
        return FirestoreRepository.computeSimilarProducts(
            brand = brand,
            productName = productName,
            catalog = localCatalog,
            excludeBarcode = excludeBarcode
        )
    }

    fun loadProductTrust(productId: String, barcode: String = "") {
        val targetId = productId.ifBlank { barcode }
        if (targetId.isBlank()) return
        viewModelScope.launch {
            val res = firestoreRepository.getProductTrustSummary(targetId)
            val summary = res.getOrNull() ?: return@launch
            _productTrustCache.update { map ->
                val next = map.toMutableMap()
                if (productId.isNotBlank()) next[productId] = summary
                if (barcode.isNotBlank()) next[barcode] = summary
                next
            }
        }
    }

    fun confirmProductAccuracy(
        productId: String,
        barcode: String = "",
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        if (!_isUserLoggedIn.value || productId.isBlank()) {
            onResult(Result.failure(IllegalStateException("Please log in to confirm label accuracy.")))
            return
        }
        viewModelScope.launch {
            val res = firestoreRepository.confirmProductAccuracy(productId)
            if (res.isSuccess) {
                loadProductTrust(productId, barcode)
            }
            onResult(res)
        }
    }

    fun reportProductMistake(
        productId: String,
        reason: String,
        details: String,
        barcode: String = "",
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        if (!_isUserLoggedIn.value || productId.isBlank()) {
            onResult(Result.failure(IllegalStateException("Please log in to report label mistakes.")))
            return
        }
        viewModelScope.launch {
            val res = firestoreRepository.reportProductMistake(productId, reason, details)
            if (res.isSuccess) {
                loadProductTrust(productId, barcode)
            }
            onResult(res)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun setRecommendations(petId: String, recommendations: List<RankedProduct>) {
        _recommendationsCache.update { it + (petId to recommendations) }
    }

    fun cacheProductDetails(barcode: String, product: Product) {
        _productDetailsCache.update { it + (barcode to product) }
    }

    fun cacheAlternatives(barcode: String, alternatives: List<Product>) {
        _alternativesCache.update { it + (barcode to alternatives) }
    }

    suspend fun fetchComparisonProducts(rawBarcodes: List<String>): List<Product> {
        val uniqueBarcodes = rawBarcodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
        val resultList = mutableListOf<Product>()

        for (code in uniqueBarcodes) {
            val savedMatch = _savedProducts.value.firstOrNull { it.barcode == code || it.productId == code }
            if (savedMatch != null) {
                resultList.add(savedMatch)
                continue
            }

            val recentMatch = _recentlyViewed.value.firstOrNull { it.barcode == code || it.productId == code }
            if (recentMatch != null) {
                resultList.add(recentMatch)
                continue
            }

            val cachedMatch = _productDetailsCache.value[code]
            if (cachedMatch != null) {
                resultList.add(cachedMatch)
                continue
            }

            val fetched = fetchProduct(code).getOrNull()
            if (fetched != null) {
                resultList.add(fetched)
            }
        }
        return resultList
    }

    // ==================================================
    // USER SIDE PULL-TO-REFRESH FUNCTIONS
    // ==================================================

    suspend fun refreshProfileData() {
        loadPets()
        loadSavedProducts()
        loadCurrentUserProfile()
        kotlinx.coroutines.delay(450L)
    }

    suspend fun refreshPetsData() {
        val result = firestoreRepository.getPets()
        if (result.isSuccess) {
            _pets.value = result.getOrDefault(emptyList())
        }
        kotlinx.coroutines.delay(350L)
    }

    suspend fun refreshSavedProductsData() {
        val savedRefsResult = withContext(Dispatchers.IO) {
            firestoreRepository.getSavedProducts()
        }
        if (savedRefsResult.isSuccess) {
            val savedRefs = savedRefsResult.getOrDefault(emptyList())
            val fetchedProducts = withContext(Dispatchers.IO) {
                coroutineScope {
                    savedRefs.map { ref ->
                        async {
                            var product: Product? = null
                            if (ref.productId.isNotEmpty()) {
                                product = productRepository.getProductById(ref.productId).getOrNull()
                            }
                            if (product == null && ref.barcode.isNotEmpty()) {
                                product = productRepository.getProductByBarcode(ref.barcode).getOrNull()
                            }
                            product ?: Product(
                                barcode = ref.barcode,
                                productId = ref.productId,
                                productName = "Saved Product (${ref.barcode})"
                            )
                        }
                    }.awaitAll()
                }
            }
            _savedProducts.value = fetchedProducts
        }
        kotlinx.coroutines.delay(350L)
    }

    suspend fun refreshRecentHistoryData() {
        val currentList = _recentlyViewed.value
        if (currentList.isNotEmpty()) {
            val refreshed = currentList.map { existing ->
                val code = existing.barcode.ifEmpty { existing.productId }
                if (code.isNotEmpty()) {
                    productRepository.getProduct(code).getOrNull() ?: existing
                } else {
                    existing
                }
            }
            _recentlyViewed.value = refreshed
        }
        kotlinx.coroutines.delay(350L)
    }

    // ==================================================
    // COMMUNITY WORKSPACE PRODUCT CONTRIBUTION
    // ==================================================

    /**
     * Checks whether a barcode already exists in the product database.
     * Returns the existing [Product] if found, or `null` if the barcode does not exist yet.
     */
    suspend fun checkBarcodeExistsInDatabase(barcode: String): Product? {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return null
        _productDetailsCache.value[trimmed]?.let { return it }
        val found = runCatching { productRepository.getProduct(trimmed) }.getOrNull()?.getOrNull()
        if (found != null) {
            cacheProductDetails(trimmed, found)
        }
        return found
    }

    /**
     * Submits a verified new product from Community Workspace directly into the database
     * after verifying:
     * 1. Barcode is not already in the database.
     * 2. Product Name, Brand Name, Ingredients, and Nutrients pass strict moderation & sense checks.
     * 3. All 4 required photo attachments (Front Bag, Guaranteed Analysis/Nutrients, Ingredients, Barcode Image) are present.
     */
    suspend fun submitCommunityContribution(
        barcode: String,
        productName: String,
        brand: String,
        pet: String = "Dog",
        lifeStage: String = "Adult",
        foodType: String = "Dry Food",
        ingredients: String,
        nutrition: Map<String, NutrientValue>,
        metabolizableEnergy: Double? = null,
        metabolizableEnergyUnit: String? = null,
        productPhotoUrl: String,
        nutritionPhotoUrl: String,
        ingredientsPhotoUrl: String,
        barcodePhotoUrl: String
    ): Result<Unit> {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) {
            return Result.failure(IllegalArgumentException("Scan barcode first."))
        }

        // Verify barcode does not already exist in database
        val existing = checkBarcodeExistsInDatabase(cleanBarcode)
        if (existing != null) {
            return Result.failure(IllegalStateException("Product already exists"))
        }

        // Validate Product Name & Brand Name
        val nameErr = LabelAiCorrectionService.validateProductOrBrandName(productName, "Product Name")
        if (nameErr != null) return Result.failure(IllegalArgumentException(nameErr))

        val brandErr = LabelAiCorrectionService.validateProductOrBrandName(brand, "Brand Name")
        if (brandErr != null) return Result.failure(IllegalArgumentException(brandErr))

        // Validate Ingredients
        val cleanIngredients = LabelAiCorrectionService.filterIngredientInputText(ingredients.trim())
        if (cleanIngredients.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingredient list is required."))
        }
        val ingErr = LabelAiCorrectionService.validateManualIngredientInput(cleanIngredients)
        if (ingErr != null) return Result.failure(IllegalArgumentException(ingErr))

        if (ContentModerationFilter.containsAnyBadWords(productName, brand, foodType, cleanIngredients)) {
            return Result.failure(IllegalArgumentException("Inappropriate language detected."))
        }

        // Normalize and validate Nutrients
        val normalizedNutrition = LinkedHashMap<String, NutrientValue>()
        nutrition.forEach { (key, nv) ->
            if (nv.value.isFinite() && nv.value > 0.0) {
                val canonicalKey = when (key) {
                    "crude_protein" -> "crudeProtein"
                    "crude_fat" -> "crudeFat"
                    "crude_fiber" -> "crudeFiber"
                    else -> key
                }
                normalizedNutrition[canonicalKey] = nv
            }
        }
        val requiredCoreKeys = setOf("crudeProtein", "crudeFat", "crudeFiber", "moisture")
        if (!normalizedNutrition.keys.containsAll(requiredCoreKeys)) {
            return Result.failure(IllegalArgumentException("Please enter all required nutrients."))
        }

        // Validate all 4 required photo attachments
        if (productPhotoUrl.isBlank() || nutritionPhotoUrl.isBlank() || ingredientsPhotoUrl.isBlank() || barcodePhotoUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("All 4 photos are required."))
        }

        val newProduct = Product(
            productId = cleanBarcode,
            barcode = cleanBarcode,
            productName = productName.trim(),
            brand = brand.trim(),
            brands = brand.trim(),
            pet = pet.trim().ifBlank { "Dog" },
            species = pet.trim().ifBlank { "Dog" },
            lifeStage = lifeStage.trim().ifBlank { "Adult" },
            foodType = foodType.trim().ifBlank { "Dry Food" },
            ingredients = cleanIngredients,
            ingredientsText = cleanIngredients,
            nutrition = normalizedNutrition,
            imageUrl = productPhotoUrl.trim(),
            nutritionPhotoUrl = nutritionPhotoUrl.trim(),
            ingredientsPhotoUrl = ingredientsPhotoUrl.trim(),
            barcodePhotoUrl = barcodePhotoUrl.trim(),
            metabolizableEnergy = metabolizableEnergy?.takeIf { it.isFinite() && it > 0.0 },
            metabolizableEnergyUnit = if (metabolizableEnergy != null && metabolizableEnergy > 0.0) {
                metabolizableEnergyUnit?.ifBlank { "kcal/kg" } ?: "kcal/kg"
            } else null
        )

        val saveResult = firestoreRepository.saveOrUpdateProductSafely(
            product = newProduct,
            baseDatabaseProduct = null
        )
        if (saveResult.isSuccess) {
            cacheProductDetails(cleanBarcode, newProduct)
            _recommendationsCache.value = emptyMap()
            _alternativesCache.value = emptyMap()
            addRecentlyViewed(newProduct)
        }
        return saveResult
    }

    // ==================================================
    // SMART CAMERA LABEL SCANNER SESSION
    // ==================================================

    private var barcodeLookupJob: kotlinx.coroutines.Job? = null

    fun selectScannerEntryMode(mode: ScannerEntryMode) {
        _scanSessionState.update { current ->
            if (current.isAiProcessing || current.scanStep == SmartScanStep.FETCHING_PRODUCT) {
                return@update current
            }
            if (current.scannedBarcode.isBlank()) {
                current.copy(
                    entryMode = mode,
                    isNewProductFlow = mode == ScannerEntryMode.SCAN_NEW_PRODUCT,
                    scanStep = SmartScanStep.BARCODE_SCANNING,
                    statusMessage = "Align product barcode inside the frame"
                )
            } else if (current.baseDatabaseProduct != null) {
                // Existing database products cannot be edited or re-submitted as new products
                current.copy(
                    entryMode = ScannerEntryMode.SCAN_BARCODE,
                    isNewProductFlow = false,
                    scanStep = SmartScanStep.REVIEWING_RESULT,
                    statusMessage = "Product found!\nSwipe up the sheet below to view full FEDIAF/AAFCO analysis."
                )
            } else if (mode == ScannerEntryMode.SCAN_NEW_PRODUCT) {
                val nextStep = if (current.scanStep == SmartScanStep.REVIEWING_RESULT ||
                    current.scanStep == SmartScanStep.PROMPT_NEW_PRODUCT ||
                    current.scanStep == SmartScanStep.CHOOSING_FIRST_TARGET
                ) {
                    SmartScanStep.SCANNING_NUTRIENTS
                } else {
                    current.scanStep
                }
                current.copy(
                    entryMode = ScannerEntryMode.SCAN_NEW_PRODUCT,
                    isNewProductFlow = true,
                    scanStep = nextStep,
                    statusMessage = "Fit Guaranteed Analysis inside the box"
                )
            } else {
                current.copy(
                    entryMode = ScannerEntryMode.SCAN_NEW_PRODUCT,
                    isNewProductFlow = true,
                    statusMessage = "Fit Guaranteed Analysis inside the box"
                )
            }
        }
    }

    fun chooseFirstNewProductStep(step: SmartScanStep) {
        val msg = when (step) {
            SmartScanStep.SCANNING_NUTRIENTS ->
                "Fit Guaranteed Analysis inside the box"
            SmartScanStep.SCANNING_INGREDIENTS ->
                "Fit the Ingredients list inside the box"
            else -> null
        }
        _scanSessionState.update {
            if (it.isAiProcessing || it.scanStep == SmartScanStep.FETCHING_PRODUCT) return@update it
            it.copy(
                preferredFirstStep = step,
                scanStep = step,
                statusMessage = msg ?: it.statusMessage
            )
        }
    }

    fun proceedToScanNewProduct() {
        _scanSessionState.update { current ->
            current.copy(
                entryMode = ScannerEntryMode.SCAN_NEW_PRODUCT,
                isNewProductFlow = true,
                scanStep = SmartScanStep.SCANNING_NUTRIENTS,
                statusMessage = "Fit Guaranteed Analysis inside the box"
            )
        }
    }

    fun startBarcodeScanSession(
        context: Context,
        barcode: String,
        capturedBarcodeBitmap: Bitmap? = null
    ) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return
        val requestedMode = _scanSessionState.value.entryMode

        barcodeLookupJob?.cancel()

        _scanSessionState.value = ScanSessionState(
            entryMode = requestedMode,
            isNewProductFlow = requestedMode == ScannerEntryMode.SCAN_NEW_PRODUCT,
            scannedBarcode = trimmed,
            scanStep = SmartScanStep.FETCHING_PRODUCT,
            statusMessage = "Scanning..."
        )

        barcodeLookupJob = viewModelScope.launch {
            val lookupResult = runCatching { fetchProduct(trimmed) }
            val dbProduct = lookupResult.getOrNull()?.getOrNull()
            val isNotInDatabase = dbProduct == null
            val localDraft = if (isNotInDatabase) loadScanDraftFromLocal(context, trimmed) else null

            // If product exists in database, always use dbProduct directly (read-only)
            val working = dbProduct ?: localDraft ?: Product(
                productId = trimmed,
                barcode = trimmed,
                productName = "",
                brand = "",
                pet = "Dog",
                lifeStage = "Adult",
                foodType = "Dry Food",
                ingredients = ""
            )

            if (dbProduct != null) {
                addRecentlyViewed(dbProduct)
            }

            val capturedKeys = extractCapturedNutrientKeys(working)
            // Only allow new-product editing flow when the product is NOT in the database
            val useNewProductFlow = isNotInDatabase && requestedMode == ScannerEntryMode.SCAN_NEW_PRODUCT
            val effectiveEntryMode = if (useNewProductFlow) {
                ScannerEntryMode.SCAN_NEW_PRODUCT
            } else {
                ScannerEntryMode.SCAN_BARCODE
            }
            val initialStep = if (!isNotInDatabase) {
                SmartScanStep.REVIEWING_RESULT
            } else {
                SmartScanStep.PROMPT_NEW_PRODUCT
            }

            _scanSessionState.update {
                if (it.scannedBarcode != trimmed) return@update it
                it.copy(
                    entryMode = effectiveEntryMode,
                    isNewProductFlow = useNewProductFlow,
                    scannedBarcode = trimmed,
                    baseDatabaseProduct = dbProduct,
                    workingProduct = working,
                    capturedNutrientKeys = capturedKeys,
                    scanStep = initialStep,
                    hasRestoredDraft = localDraft != null,
                    statusMessage = when {
                        !isNotInDatabase ->
                            "Product found!\nSwipe up the sheet below to view full FEDIAF/AAFCO analysis."
                        else ->
                            "New Barcode Detected!"
                    }
                )
            }

            if (isNotInDatabase && capturedBarcodeBitmap != null) {
                attachAutoCapturedPhoto(context, SmartScanStep.BARCODE_SCANNING, capturedBarcodeBitmap)
            }

            // Load alternatives in background
            val speciesQuery = working.species.ifBlank { working.pet }.ifBlank { "Dog" }
            val alts = runCatching { fetchAlternatives(speciesQuery, trimmed) }.getOrDefault(emptyList())
            _scanSessionState.update {
                if (it.scannedBarcode != trimmed) it else it.copy(alternatives = alts)
            }
        }
    }

    fun setScanStep(step: SmartScanStep, statusMessage: String? = null) {
        _scanSessionState.update {
            if (it.isAiProcessing || it.scanStep == SmartScanStep.FETCHING_PRODUCT) {
                return@update it
            }
            if (it.baseDatabaseProduct != null) {
                it.copy(scanStep = SmartScanStep.REVIEWING_RESULT)
            } else {
                it.copy(
                    scanStep = step,
                    statusMessage = statusMessage ?: it.statusMessage
                )
            }
        }
    }

    fun setAiProcessing(processing: Boolean, statusMessage: String? = null) {
        _scanSessionState.update {
            it.copy(
                isAiProcessing = processing,
                statusMessage = statusMessage ?: it.statusMessage
            )
        }
    }

    fun attachAutoCapturedPhoto(
        context: Context,
        targetStep: SmartScanStep,
        bitmap: Bitmap?
    ) {
        if (bitmap == null) return
        val current = _scanSessionState.value
        if (current.baseDatabaseProduct != null) return
        val working = current.workingProduct ?: return
        val barcode = working.barcode.ifBlank { current.scannedBarcode }.ifBlank { "scan" }
        val slotKey = when (targetStep) {
            SmartScanStep.SCANNING_FRONT_OF_BAG -> "front"
            SmartScanStep.SCANNING_NUTRIENTS -> "nutrition"
            SmartScanStep.SCANNING_INGREDIENTS -> "ingredients"
            SmartScanStep.BARCODE_SCANNING -> "barcode"
            else -> return
        }

        viewModelScope.launch {
            val localUriStr = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.cacheDir, "auto_${slotKey}_${barcode}_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    Uri.fromFile(file).toString()
                }.getOrNull()
            }

            if (localUriStr != null) {
                _scanSessionState.update { state ->
                    when (targetStep) {
                        SmartScanStep.SCANNING_FRONT_OF_BAG -> state.copy(pendingFrontPhotoUri = localUriStr)
                        SmartScanStep.SCANNING_NUTRIENTS -> state.copy(pendingNutritionPhotoUri = localUriStr)
                        SmartScanStep.SCANNING_INGREDIENTS -> state.copy(pendingIngredientsPhotoUri = localUriStr)
                        SmartScanStep.BARCODE_SCANNING -> state.copy(pendingBarcodePhotoUri = localUriStr)
                        else -> state
                    }
                }
            }

            val uploadResult = CloudinaryUploadService.uploadBitmap(bitmap)
            val secureUrl = uploadResult.getOrNull()?.takeIf { it.isNotBlank() } ?: return@launch

            _scanSessionState.update { state ->
                val latestWorking = state.workingProduct ?: return@update state
                if (state.baseDatabaseProduct != null) return@update state
                val updatedWithPhoto = when (targetStep) {
                    SmartScanStep.SCANNING_FRONT_OF_BAG -> buildUpdatedProduct(
                        base = latestWorking,
                        imageUrl = secureUrl
                    )
                    SmartScanStep.SCANNING_NUTRIENTS -> buildUpdatedProduct(
                        base = latestWorking,
                        nutritionPhotoUrl = secureUrl
                    )
                    SmartScanStep.SCANNING_INGREDIENTS -> buildUpdatedProduct(
                        base = latestWorking,
                        ingredientsPhotoUrl = secureUrl
                    )
                    SmartScanStep.BARCODE_SCANNING -> buildUpdatedProduct(
                        base = latestWorking,
                        barcodePhotoUrl = secureUrl
                    )
                    else -> latestWorking
                }
                saveScanDraftToLocal(context, updatedWithPhoto)
                when (targetStep) {
                    SmartScanStep.SCANNING_FRONT_OF_BAG -> state.copy(
                        workingProduct = updatedWithPhoto,
                        pendingFrontPhotoUri = null
                    )
                    SmartScanStep.SCANNING_NUTRIENTS -> state.copy(
                        workingProduct = updatedWithPhoto,
                        pendingNutritionPhotoUri = null
                    )
                    SmartScanStep.SCANNING_INGREDIENTS -> state.copy(
                        workingProduct = updatedWithPhoto,
                        pendingIngredientsPhotoUri = null
                    )
                    SmartScanStep.BARCODE_SCANNING -> state.copy(
                        workingProduct = updatedWithPhoto,
                        pendingBarcodePhotoUri = null
                    )
                    else -> state
                }
            }
        }
    }

    fun applyFrontOfBagScanResult(
        context: Context,
        result: ScannedFrontInfoResult,
        detectedLanguage: String = "en",
        isEnglishPrioritized: Boolean = true,
        capturedBitmap: Bitmap? = null
    ) {
        val current = _scanSessionState.value
        if (current.baseDatabaseProduct != null) return
        val working = current.workingProduct ?: return

        val updatedProduct = buildUpdatedProduct(
            base = working,
            productName = result.productName.ifBlank { working.productName },
            brand = result.brand.ifBlank { working.brands },
            pet = result.species.ifBlank { working.species },
            lifeStage = result.lifeStage.ifBlank { working.lifeStage },
            foodType = result.foodType.ifBlank { working.foodType },
            packagingType = result.packagingType.ifBlank { working.packagingType },
            packagingMaterial = result.packagingMaterial.ifBlank { working.packagingMaterial },
            packagingRecyclability = result.packagingRecyclability.ifBlank { working.packagingRecyclability },
            packagingBiodegradability = result.packagingBiodegradability.ifBlank { working.packagingBiodegradability }
        )

        val nextStep = when {
            current.preferredFirstStep == SmartScanStep.SCANNING_INGREDIENTS && updatedProduct.ingredientsText.isBlank() ->
                SmartScanStep.SCANNING_INGREDIENTS
            current.capturedNutrientKeys.size < 4 ->
                SmartScanStep.SCANNING_NUTRIENTS
            updatedProduct.ingredientsText.isBlank() ->
                SmartScanStep.SCANNING_INGREDIENTS
            else ->
                SmartScanStep.REVIEWING_RESULT
        }

        val nextState = current.copy(
            workingProduct = updatedProduct,
            scanStep = nextStep,
            isAiProcessing = false,
            isSavedToDatabase = false,
            detectedLanguageTag = detectedLanguage,
            isEnglishPrioritized = isEnglishPrioritized,
            usedAiCorrection = result.usedAi,
            lastCompletedTarget = SmartScanStep.SCANNING_FRONT_OF_BAG,
            scanDoneNonce = current.scanDoneNonce + 1L,
            statusMessage = if (nextStep == SmartScanStep.REVIEWING_RESULT) {
                "Scanning complete!\nAI can make mistakes — please check all product details carefully."
            } else {
                "Front label captured!\nNow scan ${if (nextStep == SmartScanStep.SCANNING_INGREDIENTS) "Ingredients" else "Nutrients"}."
            }
        )
        _scanSessionState.value = nextState
        saveScanDraftToLocal(context, updatedProduct)
        attachAutoCapturedPhoto(context, SmartScanStep.SCANNING_FRONT_OF_BAG, capturedBitmap)

        if (updatedProduct.species.isNotBlank()) {
            viewModelScope.launch {
                val alts = runCatching {
                    fetchAlternatives(updatedProduct.species, updatedProduct.barcode)
                }.getOrDefault(emptyList())
                _scanSessionState.update { it.copy(alternatives = alts) }
            }
        }
    }

    fun applyScannedNutrientsResult(
        context: Context,
        result: ScannedNutrientsResult,
        detectedLanguage: String = "en",
        isEnglishPrioritized: Boolean = true,
        capturedBitmap: Bitmap? = null
    ) {
        val current = _scanSessionState.value
        if (current.baseDatabaseProduct != null) return
        val working = current.workingProduct ?: return

        if (result.wrongSectionDetected == "INGREDIENTS" && result.nutrition.isEmpty() && result.metabolizableEnergy == null) {
            _scanSessionState.update {
                it.copy(
                    isAiProcessing = false,
                    detectedLanguageTag = detectedLanguage,
                    isEnglishPrioritized = isEnglishPrioritized,
                    statusMessage = "That looks like the Ingredients list!\nAlign the frame over Guaranteed Analysis / Nutrients."
                )
            }
            return
        }

        val mergedNutrition = LinkedHashMap<String, NutrientValue>(working.nutrition)
        result.nutrition.forEach { (k, nv) ->
            if (nv.value.isFinite() && nv.value > 0.0) {
                mergedNutrition[k] = nv
            }
        }

        val updatedMe = result.metabolizableEnergy?.takeIf { it.isFinite() && it > 0.0 }
            ?: working.metabolizableEnergy
        val updatedMeUnit = if (result.metabolizableEnergy != null) {
            result.metabolizableEnergyUnit?.ifBlank { "kcal/kg" }
        } else {
            working.metabolizableEnergyUnit
        }

        val updatedProduct = buildUpdatedProduct(
            base = working,
            nutrition = mergedNutrition,
            metabolizableEnergy = updatedMe,
            metabolizableEnergyUnit = updatedMeUnit
        )

        val mergedLowConf = (current.lowConfidenceNutrientKeys + result.lowConfidenceKeys)
            .filter { mergedNutrition.containsKey(it) }
            .toSet()
        val capturedKeys = extractCapturedNutrientKeys(updatedProduct)
        val hasCoreFour = capturedKeys.containsAll(setOf("crudeProtein", "crudeFat", "crudeFiber", "moisture"))
        val hasValidCapture = result.nutrition.isNotEmpty() || result.metabolizableEnergy != null
        val shouldAutoAdvance = (hasCoreFour || capturedKeys.size >= 5) && hasValidCapture
        val hasIngredientsAlready = updatedProduct.ingredientsText.isNotBlank()

        val nextStep = when {
            !shouldAutoAdvance -> SmartScanStep.SCANNING_NUTRIENTS
            !hasIngredientsAlready -> SmartScanStep.SCANNING_INGREDIENTS
            else -> SmartScanStep.REVIEWING_RESULT
        }

        val nextMessage = when {
            !hasValidCapture ->
                "No nutrient percentages detected inside frame.\nMove closer over Guaranteed Analysis or tap Capture."
            shouldAutoAdvance && !hasIngredientsAlready ->
                "Nutrients Done! (${capturedKeys.size} captured)\nNow scan Ingredients — or redo Nutrients anytime."
            shouldAutoAdvance && hasIngredientsAlready ->
                "Scanning complete!\nAI can make mistakes — please check all product details carefully."
            else ->
                "Captured ${capturedKeys.size} nutrient(s) ✓\nPan across remaining rows or tap Ingredients →"
        }

        val nextState = current.copy(
            workingProduct = updatedProduct,
            lowConfidenceNutrientKeys = mergedLowConf,
            capturedNutrientKeys = capturedKeys,
            scanStep = nextStep,
            isAiProcessing = false,
            isSavedToDatabase = false,
            detectedLanguageTag = detectedLanguage,
            isEnglishPrioritized = isEnglishPrioritized,
            usedAiCorrection = result.usedAi,
            lastCompletedTarget = if (hasValidCapture) SmartScanStep.SCANNING_NUTRIENTS else current.lastCompletedTarget,
            scanDoneNonce = if (hasValidCapture) current.scanDoneNonce + 1L else current.scanDoneNonce,
            statusMessage = nextMessage
        )
        _scanSessionState.value = nextState
        saveScanDraftToLocal(context, updatedProduct)
        if (hasValidCapture) {
            attachAutoCapturedPhoto(context, SmartScanStep.SCANNING_NUTRIENTS, capturedBitmap)
        }
    }

    fun applyScannedIngredientsResult(
        context: Context,
        result: ScannedIngredientsResult,
        detectedLanguage: String = "en",
        isEnglishPrioritized: Boolean = true,
        capturedBitmap: Bitmap? = null
    ) {
        val current = _scanSessionState.value
        if (current.baseDatabaseProduct != null) return
        val working = current.workingProduct ?: return

        if (result.wrongSectionDetected == "NUTRIENTS") {
            val extraNutrients = result.detectedNutrientsInstead
            var updatedProduct = working
            if (extraNutrients != null && (extraNutrients.nutrition.isNotEmpty() || extraNutrients.metabolizableEnergy != null)) {
                val mergedNutrition = LinkedHashMap<String, NutrientValue>(working.nutrition)
                extraNutrients.nutrition.forEach { (k, nv) ->
                    if (!mergedNutrition.containsKey(k) && nv.value.isFinite() && nv.value > 0.0) {
                        mergedNutrition[k] = nv
                    }
                }
                val updatedMe = working.metabolizableEnergy ?: extraNutrients.metabolizableEnergy
                val updatedMeUnit = if (working.metabolizableEnergy != null) working.metabolizableEnergyUnit else extraNutrients.metabolizableEnergyUnit
                updatedProduct = buildUpdatedProduct(
                    base = working,
                    nutrition = mergedNutrition,
                    metabolizableEnergy = updatedMe,
                    metabolizableEnergyUnit = updatedMeUnit
                )
                saveScanDraftToLocal(context, updatedProduct)
            }
            _scanSessionState.update {
                it.copy(
                    workingProduct = updatedProduct,
                    capturedNutrientKeys = extractCapturedNutrientKeys(updatedProduct),
                    scanStep = SmartScanStep.SCANNING_INGREDIENTS,
                    isAiProcessing = false,
                    detectedLanguageTag = detectedLanguage,
                    isEnglishPrioritized = isEnglishPrioritized,
                    statusMessage = "That's Guaranteed Analysis (saved missing nutrients ✓)\nMove the frame over the Ingredients list."
                )
            }
            return
        }

        val sanitized = LabelAiCorrectionService.sanitizeIngredientsString(result.ingredientsText)
        if (sanitized.isBlank()) {
            _scanSessionState.update {
                it.copy(
                    scanStep = SmartScanStep.SCANNING_INGREDIENTS,
                    isAiProcessing = false,
                    detectedLanguageTag = detectedLanguage,
                    isEnglishPrioritized = isEnglishPrioritized,
                    statusMessage = "No ingredients list detected inside frame.\nCenter the Ingredients text inside the box."
                )
            }
            return
        }

        val updatedProduct = buildUpdatedProduct(
            base = working,
            ingredients = sanitized
        )

        val capturedKeys = extractCapturedNutrientKeys(updatedProduct)
        val hasCoreNutrients = capturedKeys.containsAll(setOf("crudeProtein", "crudeFat", "crudeFiber", "moisture")) ||
            capturedKeys.size >= 4
        val nextStep = if (!hasCoreNutrients) {
            SmartScanStep.SCANNING_NUTRIENTS
        } else {
            SmartScanStep.REVIEWING_RESULT
        }
        val nextMsg = if (!hasCoreNutrients) {
            "Ingredients Done!\nNow scan Nutrients — or redo Ingredients anytime."
        } else {
            "Scanning complete!\nAI can make mistakes — please check all product details carefully."
        }

        val nextState = current.copy(
            workingProduct = updatedProduct,
            capturedNutrientKeys = capturedKeys,
            scanStep = nextStep,
            isAiProcessing = false,
            isSavedToDatabase = false,
            detectedLanguageTag = detectedLanguage,
            isEnglishPrioritized = isEnglishPrioritized,
            usedAiCorrection = result.usedAi,
            lastCompletedTarget = SmartScanStep.SCANNING_INGREDIENTS,
            scanDoneNonce = current.scanDoneNonce + 1L,
            statusMessage = nextMsg
        )
        _scanSessionState.value = nextState
        saveScanDraftToLocal(context, updatedProduct)
        attachAutoCapturedPhoto(context, SmartScanStep.SCANNING_INGREDIENTS, capturedBitmap)
    }

    fun updateScannedProductInfo(
        context: Context,
        productName: String,
        brand: String,
        pet: String,
        lifeStage: String,
        foodType: String,
        imageUrl: String = "",
        nutritionPhotoUrl: String = "",
        ingredientsPhotoUrl: String = "",
        barcodePhotoUrl: String = "",
        packagingType: String = "",
        packagingMaterial: String = "",
        packagingRecyclability: String = "",
        packagingBiodegradability: String = ""
    ) {
        val current = _scanSessionState.value
        if (current.baseDatabaseProduct != null) return
        val working = current.workingProduct ?: return
        val updated = buildUpdatedProduct(
            base = working,
            productName = productName.trim(),
            brand = brand.trim(),
            pet = pet.trim(),
            lifeStage = lifeStage.trim(),
            foodType = foodType.trim(),
            imageUrl = imageUrl.trim().ifBlank { working.imageUrl.orEmpty() },
            nutritionPhotoUrl = nutritionPhotoUrl.trim(),
            ingredientsPhotoUrl = ingredientsPhotoUrl.trim(),
            barcodePhotoUrl = barcodePhotoUrl.trim().ifBlank { working.barcodePhotoUrl.orEmpty() },
            packagingType = packagingType.trim().ifBlank { working.packagingType },
            packagingMaterial = packagingMaterial.trim().ifBlank { working.packagingMaterial },
            packagingRecyclability = packagingRecyclability.trim().ifBlank { working.packagingRecyclability },
            packagingBiodegradability = packagingBiodegradability.trim().ifBlank { working.packagingBiodegradability }
        )
        _scanSessionState.update {
            it.copy(
                workingProduct = updated,
                isSavedToDatabase = false,
                saveError = null
            )
        }
        saveScanDraftToLocal(context, updated)

        if (!updated.species.equals(working.species, ignoreCase = true) && updated.species.isNotBlank()) {
            viewModelScope.launch {
                val alts = runCatching {
                    fetchAlternatives(updated.species, updated.barcode)
                }.getOrDefault(emptyList())
                _scanSessionState.update { it.copy(alternatives = alts) }
            }
        }
    }

    fun updateScannedNutrients(
        context: Context,
        nutrition: Map<String, NutrientValue>,
        metabolizableEnergy: Double?,
        metabolizableEnergyUnit: String?
    ) {
        val current = _scanSessionState.value
        if (current.baseDatabaseProduct != null) return
        val working = current.workingProduct ?: return

        // Strictly store only valid positive finite numbers
        val cleanNutrition = nutrition
            .filterValues { it.value.isFinite() && it.value > 0.0 }
            .toMap(LinkedHashMap())

        val cleanMe = metabolizableEnergy?.takeIf { it.isFinite() && it > 0.0 }
        val cleanMeUnit = if (cleanMe != null) {
            metabolizableEnergyUnit?.ifBlank { "kcal/kg" } ?: "kcal/kg"
        } else {
            null
        }

        val updated = buildUpdatedProduct(
            base = working,
            nutrition = cleanNutrition,
            metabolizableEnergy = cleanMe,
            metabolizableEnergyUnit = cleanMeUnit
        )

        _scanSessionState.update {
            it.copy(
                workingProduct = updated,
                lowConfidenceNutrientKeys = emptySet(), // User manually verified/edited
                capturedNutrientKeys = extractCapturedNutrientKeys(updated),
                isSavedToDatabase = false,
                saveError = null
            )
        }
        saveScanDraftToLocal(context, updated)
    }

    fun updateScannedIngredients(context: Context, ingredients: String) {
        val current = _scanSessionState.value
        if (current.baseDatabaseProduct != null) return
        val working = current.workingProduct ?: return
        val filtered = LabelAiCorrectionService.filterIngredientInputText(ingredients.trim())
        val updated = buildUpdatedProduct(
            base = working,
            ingredients = filtered
        )
        _scanSessionState.update {
            it.copy(
                workingProduct = updated,
                isSavedToDatabase = false,
                saveError = null
            )
        }
        saveScanDraftToLocal(context, updated)
    }

    fun saveScannedProductToDatabase(
        context: Context,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val current = _scanSessionState.value
        if (current.isSaving) return
        if (current.baseDatabaseProduct != null) {
            onComplete(Result.failure(IllegalStateException("This product is already in the database and cannot be modified.")))
            return
        }
        val working = current.workingProduct ?: run {
            val err = IllegalStateException("No scanned product to save.")
            onComplete(Result.failure(err))
            return
        }

        if (ContentModerationFilter.containsAnyBadWords(
                working.productName,
                working.brands,
                working.foodType,
                working.ingredientsText
            )
        ) {
            val err = IllegalArgumentException("Bad words detected. Please remove highlighted words before saving.")
            _scanSessionState.update { it.copy(saveError = err.message) }
            onComplete(Result.failure(err))
            return
        }

        val nameErr = LabelAiCorrectionService.validateProductOrBrandName(working.productName, "Product Name")
        if (nameErr != null) {
            val err = IllegalArgumentException(nameErr)
            _scanSessionState.update { it.copy(saveError = err.message) }
            onComplete(Result.failure(err))
            return
        }

        if (working.brands.isNotBlank()) {
            val brandErr = LabelAiCorrectionService.validateProductOrBrandName(working.brands, "Brand Name")
            if (brandErr != null) {
                val err = IllegalArgumentException(brandErr)
                _scanSessionState.update { it.copy(saveError = err.message) }
                onComplete(Result.failure(err))
                return
            }
        }

        if (working.imageUrl.isNullOrBlank() && current.pendingFrontPhotoUri.isNullOrBlank()) {
            val err = IllegalArgumentException("Product image is required. Scan the front of the bag or upload a photo to save.")
            _scanSessionState.update { it.copy(saveError = err.message) }
            onComplete(Result.failure(err))
            return
        }

        _scanSessionState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            var productToSave = _scanSessionState.value.workingProduct ?: working
            val latestSession = _scanSessionState.value

            // Automatically upload any still-pending local captured photos before saving to Firestore
            var resolvedFrontUrl = productToSave.imageUrl.orEmpty()
            if (resolvedFrontUrl.isBlank() && !latestSession.pendingFrontPhotoUri.isNullOrBlank()) {
                val res = CloudinaryUploadService.uploadImageUri(context, Uri.parse(latestSession.pendingFrontPhotoUri))
                resolvedFrontUrl = res.getOrNull().orEmpty()
            }
            var resolvedNutritionUrl = productToSave.nutritionPhotoUrl.orEmpty()
            if (resolvedNutritionUrl.isBlank() && !latestSession.pendingNutritionPhotoUri.isNullOrBlank()) {
                val res = CloudinaryUploadService.uploadImageUri(context, Uri.parse(latestSession.pendingNutritionPhotoUri))
                resolvedNutritionUrl = res.getOrNull().orEmpty()
            }
            var resolvedIngredientsUrl = productToSave.ingredientsPhotoUrl.orEmpty()
            if (resolvedIngredientsUrl.isBlank() && !latestSession.pendingIngredientsPhotoUri.isNullOrBlank()) {
                val res = CloudinaryUploadService.uploadImageUri(context, Uri.parse(latestSession.pendingIngredientsPhotoUri))
                resolvedIngredientsUrl = res.getOrNull().orEmpty()
            }
            var resolvedBarcodeUrl = productToSave.barcodePhotoUrl.orEmpty()
            if (resolvedBarcodeUrl.isBlank() && !latestSession.pendingBarcodePhotoUri.isNullOrBlank()) {
                val res = CloudinaryUploadService.uploadImageUri(context, Uri.parse(latestSession.pendingBarcodePhotoUri))
                resolvedBarcodeUrl = res.getOrNull().orEmpty()
            }

            productToSave = buildUpdatedProduct(
                base = productToSave,
                imageUrl = resolvedFrontUrl,
                nutritionPhotoUrl = resolvedNutritionUrl,
                ingredientsPhotoUrl = resolvedIngredientsUrl,
                barcodePhotoUrl = resolvedBarcodeUrl
            )

            if (productToSave.imageUrl.isNullOrBlank()) {
                val msg = "Product front photo upload failed. Check your internet or upload a photo manually."
                _scanSessionState.update { it.copy(isSaving = false, saveError = msg) }
                onComplete(Result.failure(IllegalStateException(msg)))
                return@launch
            }

            val result = firestoreRepository.saveOrUpdateProductSafely(
                product = productToSave,
                baseDatabaseProduct = current.baseDatabaseProduct
            )
            if (result.isSuccess) {
                val merged = firestoreRepository.mergeProductForSafeSave(productToSave, current.baseDatabaseProduct)
                val code = merged.barcode.ifBlank { merged.productId }
                if (code.isNotEmpty()) {
                    cacheProductDetails(code, merged)
                    clearScanDraftFromLocal(context, code)
                }
                if (merged.productId.isNotEmpty()) {
                    cacheProductDetails(merged.productId, merged)
                }
                _recommendationsCache.value = emptyMap()
                _alternativesCache.value = emptyMap()
                addRecentlyViewed(merged)
                _scanSessionState.update {
                    it.copy(
                        baseDatabaseProduct = merged,
                        workingProduct = merged,
                        pendingFrontPhotoUri = null,
                        pendingNutritionPhotoUri = null,
                        pendingIngredientsPhotoUri = null,
                        pendingBarcodePhotoUri = null,
                        isSaving = false,
                        isSavedToDatabase = true,
                        saveError = null,
                        hasRestoredDraft = false,
                        statusMessage = "Product saved permanently to the database!"
                    )
                }
                onComplete(Result.success(Unit))
            } else {
                saveScanDraftToLocal(context, productToSave)
                val msg = result.exceptionOrNull()?.message ?: "Could not save product. Your draft is saved locally."
                _scanSessionState.update {
                    it.copy(
                        workingProduct = productToSave,
                        isSaving = false,
                        isSavedToDatabase = false,
                        saveError = msg
                    )
                }
                onComplete(Result.failure(result.exceptionOrNull() ?: Exception(msg)))
            }
        }
    }

    fun resetScanSession(keepEntryMode: Boolean = false) {
        barcodeLookupJob?.cancel()
        barcodeLookupJob = null
        val modeToKeep = if (keepEntryMode) _scanSessionState.value.entryMode else ScannerEntryMode.SCAN_BARCODE
        _scanSessionState.value = ScanSessionState(
            entryMode = modeToKeep,
            isNewProductFlow = modeToKeep == ScannerEntryMode.SCAN_NEW_PRODUCT
        )
    }

    private fun saveScanDraftToLocal(context: Context, product: Product) {
        val code = product.barcode.ifBlank { product.productId }
        if (code.isBlank()) return
        runCatching {
            val prefs = context.getSharedPreferences("scan_drafts", Context.MODE_PRIVATE)
            prefs.edit().putString("draft_$code", draftGson.toJson(product)).apply()
        }.onFailure {
            Log.w("AppViewModel", "Failed to cache scan draft locally", it)
        }
    }

    private fun loadScanDraftFromLocal(context: Context, barcode: String): Product? {
        if (barcode.isBlank()) return null
        return runCatching {
            val prefs = context.getSharedPreferences("scan_drafts", Context.MODE_PRIVATE)
            val json = prefs.getString("draft_$barcode", null) ?: return null
            draftGson.fromJson(json, Product::class.java)
        }.getOrNull()
    }

    private fun clearScanDraftFromLocal(context: Context, barcode: String) {
        if (barcode.isBlank()) return
        runCatching {
            val prefs = context.getSharedPreferences("scan_drafts", Context.MODE_PRIVATE)
            prefs.edit().remove("draft_$barcode").apply()
        }
    }

    private fun extractCapturedNutrientKeys(product: Product): Set<String> {
        val keys = mutableSetOf<String>()
        product.nutrition.forEach { (k, nv) ->
            if (nv.value > 0.0) {
                when (k.lowercase(java.util.Locale.ROOT)) {
                    "crudeprotein", "protein", "proteins", "crude_protein" -> keys.add("crudeProtein")
                    "crudefat", "fat", "fats", "crude_fat" -> keys.add("crudeFat")
                    "crudefiber", "fiber", "fibers", "crude_fiber" -> keys.add("crudeFiber")
                    "moisture" -> keys.add("moisture")
                    "ash", "crudeash", "crude_ash" -> keys.add("ash")
                    "calcium" -> keys.add("calcium")
                    "phosphorus" -> keys.add("phosphorus")
                    "taurine" -> keys.add("taurine")
                    "omega3", "omega_3", "omega-3" -> keys.add("omega3")
                    "omega6", "omega_6", "omega-6" -> keys.add("omega6")
                    "sodium" -> keys.add("sodium")
                }
            }
        }
        return keys
    }

    private fun buildUpdatedProduct(
        base: Product,
        productName: String = base.productName,
        brand: String = base.brands,
        pet: String = base.species,
        lifeStage: String = base.lifeStage,
        foodType: String = base.foodType,
        ingredients: String = base.ingredientsText,
        nutrition: Map<String, NutrientValue> = base.nutrition,
        imageUrl: String = base.imageUrl.orEmpty(),
        nutritionPhotoUrl: String = base.nutritionPhotoUrl.orEmpty(),
        ingredientsPhotoUrl: String = base.ingredientsPhotoUrl.orEmpty(),
        barcodePhotoUrl: String = base.barcodePhotoUrl.orEmpty(),
        metabolizableEnergy: Double? = base.metabolizableEnergy,
        metabolizableEnergyUnit: String? = base.metabolizableEnergyUnit,
        packagingMaterial: String = base.packagingMaterial,
        packagingType: String = base.packagingType,
        packagingRecyclability: String = base.packagingRecyclability,
        packagingBiodegradability: String = base.packagingBiodegradability
    ): Product {
        val cleanIngredients = LabelAiCorrectionService.filterIngredientInputText(ingredients)
        return Product(
            productId = base.productId.ifBlank { base.barcode },
            barcode = base.barcode.ifBlank { base.productId },
            productName = productName,
            brand = brand,
            brands = brand,
            pet = pet,
            species = pet,
            lifeStage = lifeStage,
            foodType = foodType,
            imageUrl = imageUrl.ifBlank { base.imageUrl.orEmpty() },
            nutritionPhotoUrl = nutritionPhotoUrl,
            ingredientsPhotoUrl = ingredientsPhotoUrl,
            barcodePhotoUrl = barcodePhotoUrl,
            categoriesTags = base.categoriesTags,
            nutriscoreGrade = base.nutriscoreGrade,
            ecoscoreGrade = base.ecoscoreGrade,
            nutrition = nutrition,
            packagingText = base.packagingText,
            packagingTags = base.packagingTags,
            labelsTags = base.labelsTags,
            ingredients = cleanIngredients,
            ingredientsText = cleanIngredients,
            brandHistory = base.brandHistory,
            packagingMaterial = packagingMaterial,
            packagingType = packagingType,
            packagingRecyclability = packagingRecyclability,
            packagingBiodegradability = packagingBiodegradability,
            metabolizableEnergy = metabolizableEnergy,
            metabolizableEnergyUnit = metabolizableEnergyUnit
        )
    }
}
