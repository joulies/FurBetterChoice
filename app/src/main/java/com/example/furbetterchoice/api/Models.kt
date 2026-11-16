package com.example.furbetterchoice.api

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class UserProfile(
    @DocumentId val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "user",
    val avatarId: String = "dog_1",
    val isDisabled: Boolean = false,
    val isOnline: Boolean = false,
    val lastActive: Long = 0L,
    val lastNameChangeTimestamp: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null
) {
    val uid: String get() = userId
    val id: String get() = userId
}

data class Pet(
    @DocumentId val petId: String = "",
    val petName: String = "",
    val breed: String = "",
    val age: Long = 0,
    val imageUrl: String = "",
    val type: String = "Dog",
    val birthday: String = "",
    val sex: String = "Male",
    val weight: String = "",
    val allergies: String = ""
) {
    val id: String get() = petId.ifEmpty { UUID.randomUUID().toString() }
    val name: String get() = petName
    val imageUri: String? get() = imageUrl.ifEmpty { null }

    constructor(
        id: String,
        name: String,
        type: String,
        breed: String,
        birthday: String,
        imageUri: String?,
        sex: String = "Male",
        weight: String = "",
        allergies: String = ""
    ) : this(
        petId = id,
        petName = name,
        breed = breed,
        age = calculateAgeFromBirthday(birthday),
        imageUrl = imageUri ?: "",
        type = type,
        birthday = birthday,
        sex = sex,
        weight = weight,
        allergies = allergies
    )
}

private fun calculateAgeFromBirthday(birthday: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birthDate = sdf.parse(birthday) ?: return 0L
        val now = Calendar.getInstance()
        val birth = Calendar.getInstance().apply { time = birthDate }
        var ageYears = (now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)).toLong()
        if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            ageYears--
        }
        ageYears.coerceAtLeast(0L)
    } catch (e: Exception) {
        0L
    }
}

data class SavedProduct(
    @DocumentId val barcode: String = "",
    val productId: String = "",
    @ServerTimestamp val savedAt: Date? = null
)

data class NutrientValue(
    val name: String = "",
    val value: Double = 0.0,
    val unit: String = ""
)

data class Product(
    @DocumentId
    @SerializedName("productId", alternate = ["product_id", "id", "_id"])
    val productId: String = "",

    @SerializedName("barcode", alternate = ["code", "barcode_number"])
    val barcode: String = "",

    @SerializedName("brand", alternate = ["brands", "brand_owner", "brand_name"])
    val brand: String = "",

    @SerializedName("productName", alternate = ["product_name", "product_name_en", "name", "title"])
    val productName: String = "",

    @SerializedName("pet", alternate = ["species", "animal_type", "target_pet"])
    val pet: String = "",

    @SerializedName("lifeStage", alternate = ["life_stage", "target_lifestage"])
    val lifeStage: String = "",

    @SerializedName("foodType", alternate = ["food_type", "type"])
    val foodType: String = "",

    @SerializedName("ingredients", alternate = ["ingredientsText", "ingredients_text", "ingredients_text_en"])
    val ingredients: String = "",

    val nutrition: Map<String, NutrientValue> = emptyMap(),

    @SerializedName("imageUrl", alternate = ["image_url", "image_front_url", "image_front_small_url", "image", "photoUrl", "photo_url"])
    val imageUrl: String? = "",

    @SerializedName("nutritionPhotoUrl", alternate = ["nutrition_photo_url", "nutrition_url"])
    val nutritionPhotoUrl: String? = "",

    @SerializedName("ingredientsPhotoUrl", alternate = ["ingredients_photo_url", "ingredients_url"])
    val ingredientsPhotoUrl: String? = "",

    @SerializedName("barcodePhotoUrl", alternate = ["barcode_photo_url", "barcode_url"])
    val barcodePhotoUrl: String? = "",

    @SerializedName("brandHistory", alternate = ["brand_history", "history", "eco_history"])
    val brandHistory: String = "",

    @SerializedName("packagingMaterial", alternate = ["packaging_material", "material"])
    val packagingMaterial: String = "",

    @SerializedName("packagingType", alternate = ["packaging_type"])
    val packagingType: String = "",

    @SerializedName("packagingRecyclability", alternate = ["packaging_recyclability", "recyclability"])
    val packagingRecyclability: String = "",

    @SerializedName("packagingBiodegradability", alternate = ["packaging_biodegradability", "biodegradability"])
    val packagingBiodegradability: String = "",

    @SerializedName(
        "metabolizableEnergy",
        alternate = ["metabolizable_energy", "metabolizedEnergy", "metabolized_energy", "metabolisedEnergy", "metabolisableEnergy", "me", "energy", "calories", "caloricDensity"]
    )
    val metabolizableEnergy: Double? = null,

    @SerializedName(
        "metabolizableEnergyUnit",
        alternate = ["metabolizable_energy_unit", "energyUnit", "energy_unit", "meUnit"]
    )
    val metabolizableEnergyUnit: String? = null
) {
    @SerializedName("brands", alternate = ["brand"])
    private var _brands: String = ""

    @SerializedName("species", alternate = ["pet"])
    private var _species: String = ""

    @SerializedName("ingredients_text", alternate = ["ingredientsText", "ingredients_text_en", "ingredients"])
    private var _ingredientsText: String = ""

    @SerializedName("categories_tags", alternate = ["categoriesTags", "categories_hierarchy", "categories"])
    private var _categoriesTags: List<String>? = null

    @SerializedName("nutriscore_grade", alternate = ["nutriscoreGrade", "nutriscore_score"])
    private var _nutriscoreGrade: String? = "A"

    @SerializedName("ecoscore_grade", alternate = ["ecoscoreGrade"])
    private var _ecoscoreGrade: String? = "A"

    @SerializedName("nutriments", alternate = ["nutrition"])
    private var _nutriments: Nutriments? = null

    @SerializedName("packaging_text", alternate = ["packagingText"])
    private var _packagingText: String? = null

    @SerializedName("packaging_tags", alternate = ["packagingTags"])
    private var _packagingTags: List<String>? = null

    @SerializedName("labels_tags", alternate = ["labelsTags"])
    private var _labelsTags: List<String>? = null

    val brands: String get() = (brand ?: "").ifEmpty { _brands ?: "" }
    val species: String get() = (pet ?: "").ifEmpty { _species ?: "" }
    val ingredientsText: String get() = (ingredients ?: "").ifEmpty { _ingredientsText ?: "" }

    val categoriesTags: List<String>?
        get() = _categoriesTags ?: listOfNotNull(
            if (species.isNotEmpty()) "en:${species.lowercase()}" else null,
            if (foodType.isNotEmpty()) "en:${foodType.lowercase()}" else null
        ).ifEmpty { null }

    val nutriscoreGrade: String? get() = _nutriscoreGrade ?: "A"
    val ecoscoreGrade: String? get() = _ecoscoreGrade ?: "A"
    val packagingText: String? get() = _packagingText
    val packagingTags: List<String>? get() = _packagingTags
    val labelsTags: List<String>? get() = _labelsTags

    constructor(
        productId: String = "",
        barcode: String = "",
        productName: String = "",
        brand: String = "",
        brands: String = brand,
        pet: String = "",
        species: String = pet,
        lifeStage: String = "",
        foodType: String = "",
        imageUrl: String? = "",
        nutritionPhotoUrl: String? = "",
        ingredientsPhotoUrl: String? = "",
        barcodePhotoUrl: String? = "",
        categoriesTags: List<String>? = null,
        nutriscoreGrade: String? = "A",
        ecoscoreGrade: String? = "A",
        nutrition: Map<String, NutrientValue> = emptyMap(),
        nutriments: Nutriments? = null,
        packagingText: String? = null,
        packagingTags: List<String>? = null,
        labelsTags: List<String>? = null,
        ingredients: String = "",
        ingredientsText: String = ingredients,
        brandHistory: String = "",
        packagingMaterial: String = "",
        packagingType: String = "",
        packagingRecyclability: String = "",
        packagingBiodegradability: String = "",
        metabolizableEnergy: Double? = null,
        metabolizableEnergyUnit: String? = null
    ) : this(
        productId = productId,
        barcode = barcode,
        brand = if (brands.isNotEmpty()) brands else brand,
        productName = productName,
        pet = if (species.isNotEmpty()) species else pet,
        lifeStage = lifeStage,
        foodType = foodType,
        ingredients = if (ingredientsText.isNotEmpty()) ingredientsText else ingredients,
        nutrition = nutrition,
        imageUrl = imageUrl ?: "",
        nutritionPhotoUrl = nutritionPhotoUrl ?: "",
        ingredientsPhotoUrl = ingredientsPhotoUrl ?: "",
        barcodePhotoUrl = barcodePhotoUrl ?: "",
        brandHistory = brandHistory,
        packagingMaterial = packagingMaterial,
        packagingType = packagingType,
        packagingRecyclability = packagingRecyclability,
        packagingBiodegradability = packagingBiodegradability,
        metabolizableEnergy = metabolizableEnergy,
        metabolizableEnergyUnit = metabolizableEnergyUnit
    ) {
        this._brands = if (brands.isNotEmpty()) brands else brand
        this._species = if (species.isNotEmpty()) species else pet
        this._ingredientsText = if (ingredientsText.isNotEmpty()) ingredientsText else ingredients
        this._categoriesTags = categoriesTags
        this._nutriscoreGrade = nutriscoreGrade
        this._ecoscoreGrade = ecoscoreGrade
        this._nutriments = nutriments
        this._packagingText = packagingText
        this._packagingTags = packagingTags
        this._labelsTags = labelsTags
    }

    val nutriments: Nutriments
        get() {
            if (_nutriments != null) return _nutriments!!
            fun findValue(vararg keys: String): Double? {
                for (k in keys) {
                    val nv = nutrition[k]
                        ?: nutrition.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value
                        ?: continue
                    return nv.value
                }
                return null
            }
            return Nutriments(
                protein = findValue("crudeProtein", "protein", "proteins", "proteins_100g", "crude_protein"),
                fat = findValue("crudeFat", "fat", "fats", "fat_100g", "crude_fat"),
                fiber = findValue("crudeFiber", "fiber", "fibers", "fiber_100g", "crude_fiber"),
                ash = findValue("ash", "ash_100g", "crudeAsh", "crude_ash"),
                carbohydrates = findValue("carbohydrates", "carbohydrates_100g", "carbs"),
                calcium = findValue("calcium", "calcium_100g"),
                moisture = findValue("moisture", "moisture_100g")
            )
        }
}

data class Review(
    @DocumentId val userId: String = "",
    val userName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    @ServerTimestamp val timestamp: Date? = null
)

typealias ProductReview = Review

data class Nutriments(
    @SerializedName("proteins_100g") val protein: Double? = null,
    @SerializedName("fat_100g") val fat: Double? = null,
    @SerializedName("fiber_100g") val fiber: Double? = null,
    @SerializedName("ash_100g") val ash: Double? = null,
    @SerializedName("carbohydrates_100g") val carbohydrates: Double? = null,
    @SerializedName("calcium_100g") val calcium: Double? = null,
    val moisture: Double? = null,
    val rawNutritionMap: Map<String, Any> = emptyMap()
)

data class ProductResponse(
    @SerializedName("product") val product: Product?,
    @SerializedName("status") val status: Int,
    @SerializedName("status_verbose") val statusVerbose: String?
)

data class SearchResponse(
    @SerializedName("products") val products: List<Product>,
    @SerializedName("count") val count: Int
)

