package com.example.furbetterchoice.api

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

enum class EnergySource(val displayName: String) {
    LABEL_DECLARED("From Product Label"),
    ESTIMATED_MODIFIED_ATWATER("Estimated from Nutrients"),
    NON_CONVERTIBLE_UNIT("Serving Unit Only"),
    UNAVAILABLE("Not Listed")
}

enum class StandardEnergyAdjustmentMode {
    /**
     * FEDIAF Nutritional Guidelines: values are expressed per 1,000 kcal ME
     * (equivalently % DM at 4,000 kcal ME/kg DM), so thresholds scale proportionally
     * whenever label-declared ME DM differs from 4,000 kcal/kg DM.
     */
    BIDIRECTIONAL_4000,

    /**
     * AAFCO Official Dog & Cat Food Nutrient Profiles: % DM bases assume 4,000 kcal ME/kg DM
     * and must be corrected upward when energy density exceeds 4,000 kcal ME/kg DM.
     */
    ABOVE_4000_ONLY
}

data class EnergyExtractionResult(
    val source: EnergySource,
    val rawValue: Double? = null,
    val rawUnit: String? = null,
    val asFedKcalPerKg: Double? = null,
    val dryMatterKcalPerKg: Double? = null,
    val scaleFactorFrom4000: Double = 1.0,
    val isScaledForThreshold: Boolean = false,
    val isEstimated: Boolean = false,
    val nonConvertibleReason: String? = null,
    val matchedFieldName: String? = null
)

data class NfeCarbResult(
    val asFedCarbsPercent: Double,
    val dryMatterCarbsPercent: Double?,
    val isDeclaredOnLabel: Boolean,
    val usedDefaultAsh: Boolean,
    val usedDefaultFiber: Boolean
)

data class DailyFeedingEstimate(
    val petName: String,
    val petWeightKg: Double,
    val dailyKcalNeeded: Double,
    val dailyGrams: Double,
    val productKcalPerKgAsFed: Double,
    val isEnergyEstimated: Boolean,
    val formulaLabel: String
)

object NutritionEnergyHelper {

    const val REFERENCE_ENERGY_DENSITY_KCAL_KG_DM = 4000.0

    private val ENERGY_ALIAS_KEYS = listOf(
        "metabolizableEnergy",
        "metabolizable_energy",
        "metabolizableenergy",
        "metabolizedEnergy",
        "metabolized_energy",
        "metabolizedenergy",
        "metabolisedEnergy",
        "metabolised_energy",
        "metabolisableEnergy",
        "metabolisable_energy",
        "metabolizable",
        "metabolized",
        "metabolised",
        "metabolisable",
        "me",
        "me_kcal",
        "mekcal",
        "mekcalkg",
        "me_kcal_kg",
        "energyDensity",
        "energy_density",
        "caloricDensity",
        "caloric_density",
        "calorieContent",
        "calorie_content",
        "energy_kcal",
        "energy_kcal_100g",
        "energy_100g",
        "energy",
        "calories",
        "kcal_kg",
        "kcalPerKg",
        "kcal_100g",
        "kcal",
        "kj_kg",
        "kjPerKg",
        "mj_kg",
        "mjPerKg"
    )

    private val NON_CONVERTIBLE_UNIT_TOKENS = listOf(
        "cup", "can", "pouch", "treat", "piece", "biscuit",
        "chew", "stick", "serving", "tbsp", "tsp", "bag", "packet", "tin", "tub"
    )

    private val LARGE_BREED_KEYWORDS = listOf(
        "large breed", "giant breed", "maxi", "great dane", "mastiff",
        "st. bernard", "saint bernard", "bernese", "newfoundland",
        "german shepherd", "golden retriever", "labrador", "rottweiler",
        "doberman", "husky", "malamute", "cane corso", "irish wolfhound",
        "akita", "boxer", "weimaraner", "rhodesian"
    )

    fun extractAndResolveEnergy(
        product: Product,
        dryMatterPercent: Double?,
        adjustmentMode: StandardEnergyAdjustmentMode
    ): EnergyExtractionResult {
        val labelMatch = findRawLabelEnergy(product)

        if (labelMatch != null) {
            val (rawValue, rawUnitRaw, matchedKey) = labelMatch
            val rawUnit = rawUnitRaw.trim()
            val unitLower = rawUnit.lowercase(Locale.ROOT)
            val keyLower = matchedKey.lowercase(Locale.ROOT)

            val nonConvertibleToken = NON_CONVERTIBLE_UNIT_TOKENS.firstOrNull { token ->
                unitLower.contains(token)
            }
            if (nonConvertibleToken != null) {
                return EnergyExtractionResult(
                    source = EnergySource.NON_CONVERTIBLE_UNIT,
                    rawValue = rawValue,
                    rawUnit = rawUnit,
                    asFedKcalPerKg = null,
                    dryMatterKcalPerKg = null,
                    scaleFactorFrom4000 = 1.0,
                    isScaledForThreshold = false,
                    isEstimated = false,
                    nonConvertibleReason = "Calories are listed as ${formatEnergyNumber(rawValue)} $rawUnit, which can't be converted to kcal/kg without knowing the serving weight in grams.",
                    matchedFieldName = matchedKey
                )
            }

            val isDryMatterDeclaredUnit = unitLower.contains("dry matter") ||
                unitLower.contains("(dm)") ||
                Regex("""\bdm\b""").containsMatchIn(unitLower)

            val convertedKcalPerKg = convertEnergyToKcalPerKg(rawValue, unitLower, keyLower)
            val kcalPerKgAsFed = if (convertedKcalPerKg != null && isDryMatterDeclaredUnit && dryMatterPercent != null && dryMatterPercent > 0.0 && dryMatterPercent < 100.0) {
                (convertedKcalPerKg * dryMatterPercent) / 100.0
            } else {
                convertedKcalPerKg
            }
            if (kcalPerKgAsFed != null && kcalPerKgAsFed.isFinite() && kcalPerKgAsFed > 0.0) {
                val dmKcalKg = when {
                    isDryMatterDeclaredUnit && convertedKcalPerKg != null && convertedKcalPerKg.isFinite() -> convertedKcalPerKg
                    dryMatterPercent != null && dryMatterPercent > 0.0 && dryMatterPercent < 100.0 ->
                        ((kcalPerKgAsFed / dryMatterPercent) * 100.0).takeIf { it.isFinite() && it > 0.0 }
                    else -> null
                }

                val shouldScale = when {
                    dmKcalKg == null -> false
                    adjustmentMode == StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000 ->
                        abs(dmKcalKg - REFERENCE_ENERGY_DENSITY_KCAL_KG_DM) > 1.0
                    adjustmentMode == StandardEnergyAdjustmentMode.ABOVE_4000_ONLY ->
                        dmKcalKg > REFERENCE_ENERGY_DENSITY_KCAL_KG_DM + 1.0
                    else -> false
                }

                val scaleFactor = if (shouldScale && dmKcalKg != null) {
                    dmKcalKg / REFERENCE_ENERGY_DENSITY_KCAL_KG_DM
                } else {
                    1.0
                }

                return EnergyExtractionResult(
                    source = EnergySource.LABEL_DECLARED,
                    rawValue = rawValue,
                    rawUnit = rawUnit.ifEmpty { "kcal/kg" },
                    asFedKcalPerKg = kcalPerKgAsFed,
                    dryMatterKcalPerKg = dmKcalKg,
                    scaleFactorFrom4000 = scaleFactor,
                    isScaledForThreshold = shouldScale,
                    isEstimated = false,
                    nonConvertibleReason = null,
                    matchedFieldName = matchedKey
                )
            } else if (rawUnit.isNotEmpty()) {
                return EnergyExtractionResult(
                    source = EnergySource.NON_CONVERTIBLE_UNIT,
                    rawValue = rawValue,
                    rawUnit = rawUnit,
                    nonConvertibleReason = "Unsupported energy unit '$rawUnit'. Cannot convert to kcal/kg.",
                    matchedFieldName = matchedKey
                )
            }
        }

        // Fallback: Modified Atwater calculation (display-only, NEVER scales official compliance thresholds)
        val estimatedAsFed = estimateModifiedAtwaterKcalPerKg(product)
        if (estimatedAsFed != null && estimatedAsFed > 0.0) {
            val estimatedDm = if (dryMatterPercent != null && dryMatterPercent > 0.0 && dryMatterPercent < 100.0) {
                (estimatedAsFed / dryMatterPercent) * 100.0
            } else {
                null
            }
            return EnergyExtractionResult(
                source = EnergySource.ESTIMATED_MODIFIED_ATWATER,
                rawValue = estimatedAsFed,
                rawUnit = "kcal/kg (est.)",
                asFedKcalPerKg = estimatedAsFed,
                dryMatterKcalPerKg = estimatedDm,
                scaleFactorFrom4000 = 1.0,
                isScaledForThreshold = false,
                isEstimated = true,
                nonConvertibleReason = null,
                matchedFieldName = "Modified Atwater"
            )
        }

        return EnergyExtractionResult(source = EnergySource.UNAVAILABLE)
    }

    fun scaleThreshold(baseThreshold: Double?, energy: EnergyExtractionResult): Double? {
        if (baseThreshold == null) return null
        return if (energy.isScaledForThreshold) {
            baseThreshold * energy.scaleFactorFrom4000
        } else {
            baseThreshold
        }
    }

    fun calculatePer1000Kcal(
        dmValue: Double?,
        unit: String,
        meDmKcalKg: Double?
    ): Pair<Double, String>? {
        if (dmValue == null || meDmKcalKg == null || meDmKcalKg <= 0.0) return null
        return when (unit.trim()) {
            "%" -> {
                val gramsPer1000Kcal = (dmValue * 10.0 * 1000.0) / meDmKcalKg
                gramsPer1000Kcal to "g/1000 kcal ME"
            }
            "mg/kg" -> {
                val mgPer1000Kcal = (dmValue * 1000.0) / meDmKcalKg
                mgPer1000Kcal to "mg/1000 kcal ME"
            }
            "IU/kg" -> {
                val iuPer1000Kcal = (dmValue * 1000.0) / meDmKcalKg
                iuPer1000Kcal to "IU/1000 kcal ME"
            }
            else -> null
        }
    }

    fun estimateModifiedAtwaterKcalPerKg(product: Product): Double? {
        val protein = extractPercentNutrient(
            product,
            listOf("crudeProtein", "protein", "proteins", "proteins_100g", "crude_protein")
        ) ?: return null
        val fat = extractPercentNutrient(
            product,
            listOf("crudeFat", "fat", "fats", "fat_100g", "crude_fat")
        ) ?: return null
        val moisture = extractPercentNutrient(
            product,
            listOf("moisture", "moisture_100g")
        ) ?: return null

        if (moisture <= 0.0 || moisture >= 100.0) return null

        val fiber = extractPercentNutrient(
            product,
            listOf("crudeFiber", "fiber", "fibers", "fiber_100g", "crude_fiber")
        )
        val ash = extractPercentNutrient(
            product,
            listOf("ash", "ash_100g", "crudeAsh", "crude_ash")
        )
        val declaredCarbs = extractPercentNutrient(
            product,
            listOf("carbohydrates", "carbohydrates_100g", "carbs", "nfe")
        )

        // Require at least declared carbs or fiber/ash so NFE estimate is meaningful
        if (declaredCarbs == null && fiber == null && ash == null) return null

        val nfe = declaredCarbs ?: (100.0 - protein - fat - (fiber ?: 0.0) - (ash ?: 0.0) - moisture)
            .coerceAtLeast(0.0)

        val kcalPer100g = (3.5 * protein) + (8.5 * fat) + (3.5 * nfe)
        val kcalPerKg = kcalPer100g * 10.0
        return if (kcalPerKg > 100.0) kcalPerKg else null
    }

    fun convertEnergyToKcalPerKg(value: Double, unitLower: String, keyLower: String = ""): Double? {
        if (!value.isFinite() || value <= 0.0) return null
        val cleanedUnit = unitLower
            .replace("\\s+".toRegex(), "")
            .replace("(me)", "")
            .replace("(dm)", "")
            .replace("drymatter", "")
            .replace("as-fed", "")
            .replace("asfed", "")
            .replace("calculated", "")
            .replace("metabolizableenergy", "")
            .replace("metabolizable", "")
            .replace("metabolized", "")
            .replace("me/", "/")
            .replace("/me", "")
            .replace("dm", "")
        return when {
            cleanedUnit == "kcal/kg" || cleanedUnit == "kcalkg" || cleanedUnit == "kcalperkg" -> value
            cleanedUnit == "kcal/100g" || cleanedUnit == "kcal100g" || cleanedUnit == "kcalper100g" -> value * 10.0
            cleanedUnit == "kcal/g" || cleanedUnit == "kcalg" || cleanedUnit == "kcalperg" -> value * 1000.0
            cleanedUnit == "kj/kg" || cleanedUnit == "kjkg" || cleanedUnit == "kjperkg" -> value / 4.184
            cleanedUnit == "kj/100g" || cleanedUnit == "kj100g" || cleanedUnit == "kjper100g" -> (value * 10.0) / 4.184
            cleanedUnit == "mj/kg" || cleanedUnit == "mjkg" || cleanedUnit == "mjperkg" -> (value * 1000.0) / 4.184
            cleanedUnit == "kcal/lb" || cleanedUnit == "kcallb" || cleanedUnit == "kcalperlb" -> value * 2.20462262
            cleanedUnit == "kcal/oz" || cleanedUnit == "kcaloz" || cleanedUnit == "kcalperoz" -> value * 35.27396195
            cleanedUnit == "kcal" -> {
                if (keyLower.contains("100g")) value * 10.0 else value
            }
            cleanedUnit == "kj" -> {
                if (keyLower.contains("100g")) (value * 10.0) / 4.184 else value / 4.184
            }
            cleanedUnit == "mj" -> (value * 1000.0) / 4.184
            cleanedUnit.isEmpty() -> {
                when {
                    keyLower.contains("kcal") && keyLower.contains("100g") -> value * 10.0
                    keyLower.contains("kj") && keyLower.contains("100g") -> (value * 10.0) / 4.184
                    keyLower.contains("kj") -> value / 4.184
                    keyLower.contains("mj") -> (value * 1000.0) / 4.184
                    keyLower.contains("100g") -> (value * 10.0) / 4.184 // OpenPetFoodFacts energy_100g default is kJ
                    else -> value // Default Firestore ME unit is kcal/kg
                }
            }
            else -> null
        }
    }

    private fun findRawLabelEnergy(product: Product): Triple<Double, String, String>? {
        // 1. Check explicit root Product fields first
        val rootMe = product.metabolizableEnergy
        if (rootMe != null && rootMe > 0.0) {
            return Triple(rootMe, product.metabolizableEnergyUnit ?: "kcal/kg", "metabolizableEnergy")
        }

        // 2. Check Product.nutrition map across all alias keys & NutrientValue.name
        for (alias in ENERGY_ALIAS_KEYS) {
            val normAlias = normalizeKey(alias)
            val entry = product.nutrition[alias]
                ?.let { alias to it }
                ?: product.nutrition.entries.firstOrNull { (k, nv) ->
                    normalizeKey(k) == normAlias || normalizeKey(nv.name) == normAlias
                }?.let { it.key to it.value }

            if (entry != null && entry.second.value > 0.0) {
                return Triple(entry.second.value, entry.second.unit, entry.first)
            }
        }

        // Substring fallback on Product.nutrition keys/names (e.g., "Metabolized Energy (ME)", "Calorie Content")
        product.nutrition.entries.firstOrNull { (k, nv) ->
            val combined = "${k.lowercase(Locale.ROOT)} ${nv.name.lowercase(Locale.ROOT)}"
            nv.value > 0.0 && isEnergyLabelText(combined)
        }?.let { (k, nv) ->
            return Triple(nv.value, nv.unit, k)
        }

        // 3. Check Nutriments.rawNutritionMap (used by API / Compose previews)
        val rawMap = product.nutriments.rawNutritionMap
        if (rawMap.isNotEmpty()) {
            for (alias in ENERGY_ALIAS_KEYS) {
                val normAlias = normalizeKey(alias)
                val match = rawMap.entries.firstOrNull { (k, _) -> normalizeKey(k) == normAlias }
                if (match != null) {
                    val parsed = parseRawEnergyEntry(match.key, match.value, rawMap)
                    if (parsed != null) return parsed
                }
            }
            rawMap.entries.firstOrNull { (k, _) -> isEnergyLabelText(k.lowercase(Locale.ROOT)) }
                ?.let { (k, v) ->
                    val parsed = parseRawEnergyEntry(k, v, rawMap)
                    if (parsed != null) return parsed
                }
        }

        return null
    }

    private fun parseRawEnergyEntry(
        key: String,
        rawVal: Any,
        rawMap: Map<String, Any>
    ): Triple<Double, String, String>? {
        val numberRegex = Regex("""\d+(?:,\d{3})*(?:\.\d+)?|\.\d+""")
        return when (rawVal) {
            is NutrientValue -> if (rawVal.value > 0.0) Triple(rawVal.value, rawVal.unit, key) else null
            is Map<*, *> -> {
                val rawNumObj = rawVal["value"] ?: rawVal["val"] ?: rawVal["amount"] ?: rawVal["kcal"] ?: rawVal["energy"]
                var parsedUnitFromStr: String? = null
                val num = when (rawNumObj) {
                    is Number -> rawNumObj.toDouble()
                    is String -> {
                        val m = numberRegex.find(rawNumObj.trim())
                        parsedUnitFromStr = m?.let { rawNumObj.trim().substring(it.range.last + 1).trim().takeIf { u -> u.isNotEmpty() } }
                        m?.value?.replace(",", "")?.toDoubleOrNull()
                    }
                    else -> null
                }
                val unit = rawVal["unit"]?.toString()?.takeIf { it.isNotBlank() } ?: parsedUnitFromStr ?: ""
                if (num != null && num > 0.0) Triple(num, unit, key) else null
            }
            is Number -> {
                val d = rawVal.toDouble()
                val siblingUnit = rawMap["${key}_unit"]?.toString()
                    ?: rawMap["${key}Unit"]?.toString()
                    ?: rawMap["energy_unit"]?.toString()
                    ?: ""
                if (d > 0.0) Triple(d, siblingUnit, key) else null
            }
            is String -> {
                val trimmed = rawVal.trim()
                val m = numberRegex.find(trimmed)
                val num = m?.value?.replace(",", "")?.toDoubleOrNull()
                val unit = m?.let { trimmed.substring(it.range.last + 1).trim() } ?: ""
                if (num != null && num > 0.0) Triple(num, unit, key) else null
            }
            else -> null
        }
    }

    private fun isEnergyLabelText(textLower: String): Boolean {
        return textLower.contains("metaboliz") ||
            textLower.contains("metabolis") ||
            textLower.contains("calori") ||
            textLower.contains("kcal") ||
            textLower.contains("energy") ||
            Regex("""(^|\b)me(\b|_|$)""").containsMatchIn(textLower) ||
            textLower.contains("kj/kg") ||
            textLower.contains("mj/kg")
    }

    fun extractRawNutrientValue(product: Product, aliases: List<String>): NutrientValue? {
        for (alias in aliases) {
            val normAlias = normalizeKey(alias)
            val direct = product.nutrition[alias]
                ?: product.nutrition.entries.firstOrNull {
                    normalizeKey(it.key) == normAlias || normalizeKey(it.value.name) == normAlias
                }?.value
            if (direct != null && direct.value > 0.0) {
                return direct
            }
        }

        val n = product.nutriments
        for (alias in aliases) {
            val fallbackVal = when (normalizeKey(alias)) {
                "crudeprotein", "protein", "proteins", "proteins100g" -> n.protein
                "crudefat", "fat", "fats", "fat100g" -> n.fat
                "crudefiber", "fiber", "fibers", "fiber100g" -> n.fiber
                "ash", "ash100g", "crudeash" -> n.ash
                "carbohydrates", "carbohydrates100g", "carbs" -> n.carbohydrates
                "calcium", "calcium100g", "ca" -> n.calcium
                "moisture", "moisture100g" -> n.moisture
                else -> null
            }
            if (fallbackVal != null && fallbackVal > 0.0) {
                return NutrientValue(name = alias, value = fallbackVal, unit = "%")
            }
        }

        val rawMap = n.rawNutritionMap
        if (rawMap.isNotEmpty()) {
            for (alias in aliases) {
                val normAlias = normalizeKey(alias)
                val entry = rawMap.entries.firstOrNull { normalizeKey(it.key) == normAlias }
                if (entry != null) {
                    val v = entry.value
                    when (v) {
                        is NutrientValue -> if (v.value > 0.0) return v
                        is Number -> if (v.toDouble() > 0.0) return NutrientValue(entry.key, v.toDouble(), "%")
                        is Map<*, *> -> {
                            val num = (v["value"] as? Number)?.toDouble()
                            val unit = v["unit"]?.toString() ?: "%"
                            if (num != null && num > 0.0) return NutrientValue(entry.key, num, unit)
                        }
                    }
                }
            }
        }

        return null
    }

    private fun extractPercentNutrient(product: Product, aliases: List<String>): Double? {
        val nv = extractRawNutrientValue(product, aliases) ?: return null
        val u = nv.unit.trim().lowercase(Locale.ROOT)
        return when {
            u.isEmpty() || u == "%" || u == "g/100g" || u == "g/100 g" || u == "percent" -> nv.value
            u == "g/kg" || u == "g / kg" -> nv.value / 10.0
            else -> null
        }
    }

    fun isTreatOrComplementary(product: Product): Boolean {
        val combined = "${product.foodType} ${product.productName} ${product.categoriesTags?.joinToString(" ").orEmpty()}"
            .lowercase(Locale.ROOT)
        return listOf(
            "treat", "treats", "snack", "snacks", "complementary",
            "topper", "toppers", "biscuit", "biscuits", "chew", "chews", "dental stick"
        ).any { combined.contains(it) }
    }

    fun isCannedOrWetFood(product: Product, moistureAsFed: Double?): Boolean {
        val combined = "${product.foodType} ${product.productName}".lowercase(Locale.ROOT)
        return combined.contains("wet") ||
            combined.contains("canned") ||
            combined.contains("pouch") ||
            combined.contains("gravy") ||
            combined.contains("pate") ||
            combined.contains("paté") ||
            combined.contains("mousse") ||
            combined.contains("jelly") ||
            (moistureAsFed != null && moistureAsFed >= 60.0)
    }

    fun isLargeBreedPuppy(product: Product, petBreed: String? = null): Boolean {
        val combined = "${product.foodType} ${product.productName} ${product.lifeStage} ${petBreed.orEmpty()}"
            .lowercase(Locale.ROOT)
        return LARGE_BREED_KEYWORDS.any { combined.contains(it) }
    }

    fun calculateAgeInWeeks(birthday: String?): Long? {
        if (birthday.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val birthDate = sdf.parse(birthday.trim()) ?: return null
            val nowMs = Calendar.getInstance().timeInMillis
            val diffMs = (nowMs - birthDate.time).coerceAtLeast(0L)
            diffMs / (7L * 24L * 60L * 60L * 1000L)
        } catch (_: Exception) {
            null
        }
    }

    fun calculateAgeInYears(birthday: String?, fallbackAgeYears: Long?): Long? {
        if (!birthday.isNullOrBlank()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                val birthDate = sdf.parse(birthday.trim())
                if (birthDate != null) {
                    val now = Calendar.getInstance()
                    val birth = Calendar.getInstance().apply { time = birthDate }
                    var years = (now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)).toLong()
                    if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                        years--
                    }
                    return years.coerceAtLeast(0L)
                }
            } catch (_: Exception) {
            }
        }
        return fallbackAgeYears?.takeIf { it >= 0L }
    }

    fun normalizeKey(raw: String): String {
        return raw.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
    }

    private fun formatEnergyNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    /**
     * Calculates Estimated Carbohydrates (Nitrogen-Free Extract / NFE %) on As-Fed and Dry Matter bases:
     * NFE = 100 - (Protein + Fat + Fiber + Ash + Moisture).
     */
    fun calculateEstimatedCarbsNfe(product: Product): NfeCarbResult? {
        val moisture = extractPercentNutrient(product, listOf("moisture", "moisture_100g"))
        val dmPct = if (moisture != null && moisture > 0.0 && moisture < 100.0) 100.0 - moisture else null

        val declaredCarbs = extractPercentNutrient(
            product,
            listOf("carbohydrates", "carbohydrates_100g", "carbs", "nfe")
        )
        if (declaredCarbs != null && declaredCarbs >= 0.0) {
            val dmCarbs = if (dmPct != null && dmPct > 0.0) ((declaredCarbs / dmPct) * 100.0).coerceIn(0.0, 100.0) else null
            return NfeCarbResult(
                asFedCarbsPercent = declaredCarbs.coerceIn(0.0, 100.0),
                dryMatterCarbsPercent = dmCarbs,
                isDeclaredOnLabel = true,
                usedDefaultAsh = false,
                usedDefaultFiber = false
            )
        }

        val protein = extractPercentNutrient(
            product,
            listOf("crudeProtein", "protein", "proteins", "proteins_100g", "crude_protein")
        ) ?: return null
        val fat = extractPercentNutrient(
            product,
            listOf("crudeFat", "fat", "fats", "fat_100g", "crude_fat")
        ) ?: return null
        val actualMoisture = moisture ?: return null
        if (actualMoisture <= 0.0 || actualMoisture >= 100.0) return null

        val fiberRaw = extractPercentNutrient(
            product,
            listOf("crudeFiber", "fiber", "fibers", "fiber_100g", "crude_fiber")
        )
        val ashRaw = extractPercentNutrient(
            product,
            listOf("ash", "ash_100g", "crudeAsh", "crude_ash")
        )
        if (fiberRaw == null && ashRaw == null) return null

        val isWet = actualMoisture >= 60.0
        val effectiveFiber = fiberRaw ?: if (isWet) 1.0 else 3.5
        val effectiveAsh = ashRaw ?: if (isWet) 2.0 else 7.0

        val nfeAsFed = (100.0 - protein - fat - effectiveFiber - effectiveAsh - actualMoisture)
            .coerceIn(0.0, 100.0)
        val nfeDm = if (dmPct != null && dmPct > 0.0) ((nfeAsFed / dmPct) * 100.0).coerceIn(0.0, 100.0) else null

        return NfeCarbResult(
            asFedCarbsPercent = nfeAsFed,
            dryMatterCarbsPercent = nfeDm,
            isDeclaredOnLabel = false,
            usedDefaultAsh = ashRaw == null,
            usedDefaultFiber = fiberRaw == null
        )
    }

    /**
     * Parses a pet weight string like "12.5 kg" or "25 lbs" into kilograms.
     */
    fun parsePetWeightKg(weightRaw: String?): Double? {
        if (weightRaw.isNullOrBlank()) return null
        val lower = weightRaw.lowercase(Locale.ROOT)
        val match = Regex("""\d+(?:\.\d+)?""").find(lower) ?: return null
        val num = match.value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val kg = if (lower.contains("lb")) num / 2.20462262 else num
        return kg.takeIf { it in 0.2..120.0 }
    }

    /**
     * Estimates daily feeding grams for an active [Pet] based on FEDIAF MER equations
     * and the product's label-declared or Modified-Atwater-estimated kcal/kg.
     */
    fun calculateEstimatedDailyFeeding(
        product: Product,
        pet: Pet,
        merMode: FediafMerMode = FediafMerMode.STANDARD
    ): DailyFeedingEstimate? {
        val weightKg = parsePetWeightKg(pet.weight) ?: return null
        val moisture = extractPercentNutrient(product, listOf("moisture", "moisture_100g"))
        val dmPct = if (moisture != null && moisture > 0.0 && moisture < 100.0) 100.0 - moisture else null
        val energy = extractAndResolveEnergy(product, dmPct, StandardEnergyAdjustmentMode.BIDIRECTIONAL_4000)
        val kcalPerKg = energy.asFedKcalPerKg?.takeIf { it.isFinite() && it > 0.0 } ?: return null

        val isCat = pet.type.contains("cat", ignoreCase = true)
        val ageWeeks = calculateAgeInWeeks(pet.birthday)
        val ageYears = calculateAgeInYears(pet.birthday, pet.age)
        val isGrowth = (ageWeeks != null && ageWeeks < 52L) || (ageWeeks == null && ageYears != null && ageYears == 0L)

        val (dailyKcal, formulaLabel) = if (isCat) {
            val metabolicBase = Math.pow(weightKg, 0.67)
            when {
                isGrowth -> (180.0 * metabolicBase) to "Kitten Growth (180 × kg⁰·⁶⁷)"
                merMode == FediafMerMode.LOWER_ACTIVITY -> (75.0 * metabolicBase) to "Cat Indoor/Lower Activity (75 × kg⁰·⁶⁷)"
                else -> (100.0 * metabolicBase) to "Adult Cat Standard (100 × kg⁰·⁶⁷)"
            }
        } else {
            val metabolicBase = Math.pow(weightKg, 0.75)
            when {
                ageWeeks != null && ageWeeks < 14L -> (210.0 * metabolicBase) to "Puppy Early Growth (210 × kg⁰·⁷⁵)"
                isGrowth -> (175.0 * metabolicBase) to "Puppy Late Growth (175 × kg⁰·⁷⁵)"
                merMode == FediafMerMode.LOWER_ACTIVITY -> (95.0 * metabolicBase) to "Dog Lower Activity (95 × kg⁰·⁷⁵)"
                else -> (110.0 * metabolicBase) to "Adult Dog Standard (110 × kg⁰·⁷⁵)"
            }
        }

        val dailyGrams = (dailyKcal / kcalPerKg) * 1000.0
        if (!dailyGrams.isFinite() || dailyGrams <= 0.0) return null

        return DailyFeedingEstimate(
            petName = pet.petName.ifBlank { if (isCat) "Cat" else "Dog" },
            petWeightKg = weightKg,
            dailyKcalNeeded = dailyKcal,
            dailyGrams = dailyGrams,
            productKcalPerKgAsFed = kcalPerKg,
            isEnergyEstimated = energy.isEstimated,
            formulaLabel = formulaLabel
        )
    }
}
