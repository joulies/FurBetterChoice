package com.example.furbetterchoice.api

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class ScannedFrontInfoResult(
    val productName: String = "",
    val brand: String = "",
    val species: String = "",
    val lifeStage: String = "",
    val foodType: String = "",
    val packagingType: String = "",
    val packagingMaterial: String = "",
    val packagingRecyclability: String = "",
    val packagingBiodegradability: String = "",
    val usedAi: Boolean = false
)

data class ScannedNutrientsResult(
    val nutrition: Map<String, NutrientValue> = emptyMap(),
    val metabolizableEnergy: Double? = null,
    val metabolizableEnergyUnit: String? = null,
    val lowConfidenceKeys: Set<String> = emptySet(),
    val correctedSummaryText: String = "",
    val usedAi: Boolean = false,
    val wrongSectionDetected: String? = null
)

data class ScannedIngredientsResult(
    val ingredientsText: String = "",
    val correctionsAppliedCount: Int = 0,
    val usedAi: Boolean = false,
    val detectedNutrientsInstead: ScannedNutrientsResult? = null,
    val wrongSectionDetected: String? = null
)

data class LabelSectionSplit(
    val nutrientSectionText: String,
    val ingredientSectionText: String,
    val feedingGuideOrNoiseCount: Int,
    val detectedSections: Set<String>
)

/**
 * Uses Firebase AI Logic (Gemini Developer API free tier) with a resilient
 * offline OCR-autocorrect & pet-food sanity-checking fallback so messy camera
 * reads become accurate numbers (for nutrients) and clean text (for ingredients).
 */
object LabelAiCorrectionService {

    private const val TAG = "LabelAiCorrection"
    private const val GEMINI_TIMEOUT_MS = 10_000L
    private const val MAX_VISION_BITMAP_DIM = 1280

    private val aiClient by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private val frontSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "productName" to Schema.string("Product name from front label"),
                "brand" to Schema.string("Brand name"),
                "species" to Schema.enumeration(listOf("Dog", "Cat", ""), "Target species"),
                "lifeStage" to Schema.enumeration(listOf("Adult", "Puppy", "Kitten", "Senior", "All Life Stages", ""), "Target life stage"),
                "foodType" to Schema.enumeration(listOf("Dry Food", "Wet Food", "Treat", ""), "Food type"),
                "packagingType" to Schema.enumeration(listOf("Can", "Pouch", "Bag", "Tub", "Tetra Pak", ""), "Packaging type (Can, Pouch, Bag, Tub, Tetra Pak)"),
                "packagingMaterial" to Schema.string("Packaging material (e.g. Aluminum / Tin, Flexible Plastic / Foil, Paper / Cardboard)"),
                "packagingRecyclability" to Schema.enumeration(listOf("Recyclable", "Difficult to Recycle", "Not Recyclable", ""), "Recyclability status"),
                "packagingBiodegradability" to Schema.enumeration(listOf("Biodegradable", "Non-Biodegradable", ""), "Biodegradability status")
            )
        )
    }

    private val nutrientsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "crudeProtein" to Schema.double(nullable = true),
                "crudeFat" to Schema.double(nullable = true),
                "crudeFiber" to Schema.double(nullable = true),
                "moisture" to Schema.double(nullable = true),
                "ash" to Schema.double(nullable = true),
                "calcium" to Schema.double(nullable = true),
                "phosphorus" to Schema.double(nullable = true),
                "taurine" to Schema.double(nullable = true),
                "omega3" to Schema.double(nullable = true),
                "omega6" to Schema.double(nullable = true),
                "sodium" to Schema.double(nullable = true),
                "metabolizableEnergy" to Schema.double(nullable = true),
                "metabolizableEnergyUnit" to Schema.enumeration(listOf("kcal/kg", "kcal/100g", "kcal/cup"), nullable = true),
                "lowConfidenceKeys" to Schema.array(Schema.string())
            ),
            optionalProperties = listOf(
                "crudeProtein", "crudeFat", "crudeFiber", "moisture", "ash",
                "calcium", "phosphorus", "taurine", "omega3", "omega6", "sodium",
                "metabolizableEnergy", "metabolizableEnergyUnit"
            )
        )
    }

    private val ingredientsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "ingredients" to Schema.string("Comma-separated English ingredients list"),
                "correctionsCount" to Schema.long("Number of OCR corrections applied")
            )
        )
    }

    private val frontGeminiModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = frontSchema
            }
        )
    }

    private val nutrientsGeminiModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = nutrientsSchema
            }
        )
    }

    private val ingredientsGeminiModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = ingredientsSchema
            }
        )
    }

    /**
     * Multi-frame consensus: combines 3-5 consecutive OCR frames by retaining
     * the cleanest version of each line across all frames so multi-part tables
     * do not lose top or bottom rows when hand position shifts slightly.
     */
    fun mergeConsensusFrames(frames: List<String>): String {
        val nonEmpty = frames.map { it.trim() }.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return ""
        if (nonEmpty.size == 1) return nonEmpty.first()

        val splitFrames = nonEmpty.map { frame ->
            frame.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }

        // Count how often each normalized line appears across frames
        val lineFreq = mutableMapOf<String, Int>()
        splitFrames.forEach { lines ->
            lines.distinctBy { it.lowercase(Locale.ROOT) }.forEach { line ->
                val norm = line.lowercase(Locale.ROOT)
                lineFreq[norm] = (lineFreq[norm] ?: 0) + 1
            }
        }

        // Anchor on the richest frame, then append any unique lines seen in other frames
        // (or upgrade lines with cleaner digit/percentage formatting).
        val bestFrame = splitFrames.maxByOrNull { lines ->
            val consensusScore = lines.sumOf { lineFreq[it.lowercase(Locale.ROOT)] ?: 1 }
            val digitScore = lines.sumOf { line -> line.count { it.isDigit() || it == '%' || it == '.' } }
            consensusScore * 3 + digitScore
        } ?: emptyList()

        val merged = LinkedHashMap<String, String>()
        fun addOrUpgradeLine(line: String) {
            val key = line.lowercase(Locale.ROOT)
                .replace(Regex("""[^a-z0-9]"""), "")
                .take(18)
                .ifBlank { line.lowercase(Locale.ROOT) }
            val existing = merged[key]
            if (existing == null) {
                merged[key] = line
            } else {
                val existingScore = (lineFreq[existing.lowercase(Locale.ROOT)] ?: 1) * 5 +
                    existing.count { it.isDigit() || it == '%' || it == '.' }
                val candidateScore = (lineFreq[line.lowercase(Locale.ROOT)] ?: 1) * 5 +
                    line.count { it.isDigit() || it == '%' || it == '.' }
                if (candidateScore > existingScore) {
                    merged[key] = line
                }
            }
        }

        bestFrame.forEach(::addOrUpgradeLine)
        splitFrames.forEach { lines -> lines.forEach(::addOrUpgradeLine) }
        return merged.values.joinToString("\n")
    }

    suspend fun parseAndCorrectFrontOfBag(
        rawEnglishText: String,
        croppedBitmap: Bitmap? = null
    ): ScannedFrontInfoResult =
        withContext(Dispatchers.Default) {
            val cleaned = rawEnglishText.trim()
            if (cleaned.isEmpty() && croppedBitmap == null) return@withContext ScannedFrontInfoResult()

            val aiResult = tryCallGeminiJson(
                model = frontGeminiModel,
                prompt = """
                    You are parsing a dog or cat food package front label from a phone camera.
                    Visually identify if the product is in a Can, Pouch, Bag, Tub, or Tetra Pak.
                    Fix any OCR spelling mistakes and return ONLY a JSON object with these keys:
                    {
                      "productName": string,
                      "brand": string,
                      "species": "Dog" | "Cat" | "",
                      "lifeStage": "Adult" | "Puppy" | "Kitten" | "Senior" | "All Life Stages" | "",
                      "foodType": "Dry Food" | "Wet Food" | "Treat" | "",
                      "packagingType": "Can" | "Pouch" | "Bag" | "Tub" | "Tetra Pak" | "",
                      "packagingMaterial": string,
                      "packagingRecyclability": "Recyclable" | "Difficult to Recycle" | "Not Recyclable" | "",
                      "packagingBiodegradability": "Biodegradable" | "Non-Biodegradable" | ""
                    }
                    Use empty string "" if unknown.
                    OCR text:
                    $cleaned
                """.trimIndent(),
                bitmap = croppedBitmap
            )

            if (aiResult != null) {
                return@withContext ScannedFrontInfoResult(
                    productName = aiResult.optString("productName"),
                    brand = aiResult.optString("brand"),
                    species = aiResult.optString("species"),
                    lifeStage = aiResult.optString("lifeStage"),
                    foodType = aiResult.optString("foodType"),
                    packagingType = aiResult.optString("packagingType"),
                    packagingMaterial = aiResult.optString("packagingMaterial"),
                    packagingRecyclability = aiResult.optString("packagingRecyclability"),
                    packagingBiodegradability = aiResult.optString("packagingBiodegradability"),
                    usedAi = true
                )
            }

            parseFrontOfBagLocally(cleaned)
        }

    suspend fun parseAndCorrectNutrients(
        rawEnglishText: String,
        croppedBitmap: Bitmap? = null
    ): ScannedNutrientsResult =
        withContext(Dispatchers.Default) {
            val sectionSplit = classifyAndSplitSections(rawEnglishText)
            if (croppedBitmap == null &&
                sectionSplit.nutrientSectionText.isBlank() &&
                sectionSplit.ingredientSectionText.isNotBlank()
            ) {
                return@withContext ScannedNutrientsResult(wrongSectionDetected = "INGREDIENTS")
            }

            val targetText = sectionSplit.nutrientSectionText.ifBlank { rawEnglishText }
            val preCleaned = preCleanNumericOcr(targetText)
            if (preCleaned.isBlank() && croppedBitmap == null) return@withContext ScannedNutrientsResult()

            // Always run the fast local multi-format & multilingual parser for cross-validation / fallback
            val localParsed = parseNutrientsLocally(preCleaned)

            val aiResult = tryCallGeminiJson(
                model = nutrientsGeminiModel,
                prompt = """
                    You are an expert multilingual pet-food nutrition label parser.
                    Read the Guaranteed Analysis / Analytical Constituents from the cropped label (image and/or OCR text).
                    The label may be in ANY format or language:
                    - Paragraph form (e.g. "Analytical constituents: protein 28%, fat content 15%, crude ash 7.5%, crude fibres 2.5%, moisture 10%.")
                    - Value-before-name form (e.g. "28% Crude Protein, 16% Crude Fat")
                    - 2-column or Listed form (bulleted, dashed, or colon-separated)
                    - Multi-language table form (e.g. "Crude Protein / Protéines brutes / Rohprotein / Proteína bruta ... 28.0%")
                    - Foreign-language-only form (French, German, Spanish, Italian, Portuguese, Dutch, Polish, etc. — translate to the canonical keys below)
                    - Multi-column "As Fed" vs "Dry Matter" table: ALWAYS extract the "As Fed" / "Guaranteed" column (the first column), NEVER the Dry Matter column.

                    Unit Normalization Rules:
                    1. Return all nutrient values as PERCENTAGE numbers (e.g. 26.0 for 26.0%, no % sign).
                    2. Convert g/100g -> % (1:1, e.g. 28 g/100g = 28.0).
                    3. Convert g/kg -> % (divide by 10, e.g. 12 g/kg Calcium = 1.2).
                    4. Convert mg/kg or ppm -> % (divide by 10000, e.g. 1500 mg/kg Taurine = 0.15).
                    5. Convert kJ/100g or MJ/kg energy into kcal/kg if kcal is not directly stated (1 kcal = 4.184 kJ).
                    6. Fix OCR typos (e.g. "Crvde Pr0tein" -> crudeProtein, "M01sture" -> moisture, "26.O%" -> 26.0, "O.8%" -> 0.8).
                    7. STRICTLY IGNORE Ingredients lists, feeding guide tables ("cups/day", "g/day"), and marketing slogans.
                    8. Biological check: crudeProtein + crudeFat + crudeFiber + moisture + ash must not exceed 105%. If any value looks ambiguous or smudged, include its key in "lowConfidenceKeys".

                    Return ONLY a JSON object:
                    {
                      "crudeProtein": number | null,
                      "crudeFat": number | null,
                      "crudeFiber": number | null,
                      "moisture": number | null,
                      "ash": number | null,
                      "calcium": number | null,
                      "phosphorus": number | null,
                      "taurine": number | null,
                      "omega3": number | null,
                      "omega6": number | null,
                      "sodium": number | null,
                      "metabolizableEnergy": number | null,
                      "metabolizableEnergyUnit": "kcal/kg" | "kcal/100g" | "kcal/cup" | null,
                      "lowConfidenceKeys": [string]
                    }
                    OCR text:
                    $preCleaned
                """.trimIndent(),
                bitmap = croppedBitmap
            )

            if (aiResult != null) {
                val aiParsed = buildNutrientsResultFromJson(aiResult, usedAi = true)
                if (aiParsed.nutrition.isNotEmpty() || aiParsed.metabolizableEnergy != null) {
                    return@withContext crossValidateAndMergeNutrients(aiParsed, localParsed)
                }
            }

            localParsed
        }

    /**
     * Cross-validates Gemini AI nutrient output with the deterministic local multi-format parser:
     * 1. Fills in any nutrient keys that local parsing found but AI missed.
     * 2. Flags keys in lowConfidenceKeys if AI and local OCR disagree by > 1.5%.
     * 3. Runs the Biological Proximate Sum Sanity Check (Protein + Fat + Fiber + Moisture + Ash <= 105%).
     */
    fun crossValidateAndMergeNutrients(
        aiResult: ScannedNutrientsResult,
        localResult: ScannedNutrientsResult
    ): ScannedNutrientsResult {
        val mergedMap = LinkedHashMap<String, NutrientValue>()
        val mergedLowConf = LinkedHashSet<String>(aiResult.lowConfidenceKeys)
        mergedLowConf.addAll(localResult.lowConfidenceKeys)

        val allKeys = (aiResult.nutrition.keys + localResult.nutrition.keys).distinct()
        for (key in allKeys) {
            val aiVal = aiResult.nutrition[key]
            val localVal = localResult.nutrition[key]
            when {
                aiVal != null && localVal != null -> {
                    mergedMap[key] = aiVal
                    val diff = kotlin.math.abs(aiVal.value - localVal.value)
                    val threshold = if (key in setOf("calcium", "phosphorus", "taurine", "sodium", "omega3")) 0.5 else 1.5
                    if (diff > threshold) {
                        mergedLowConf.add(key)
                    }
                }
                aiVal != null -> mergedMap[key] = aiVal
                localVal != null -> mergedMap[key] = localVal
            }
        }

        val (sanitizedMap, bioLowConf) = applyBiologicalProximateSanityCheck(mergedMap, localResult.nutrition)
        mergedLowConf.addAll(bioLowConf)

        val finalMe = aiResult.metabolizableEnergy ?: localResult.metabolizableEnergy
        val finalMeUnit = aiResult.metabolizableEnergyUnit ?: localResult.metabolizableEnergyUnit
        val summary = sanitizedMap.values.joinToString(" · ") { "${it.name}: ${it.value}${it.unit}" }

        return ScannedNutrientsResult(
            nutrition = sanitizedMap,
            metabolizableEnergy = finalMe,
            metabolizableEnergyUnit = finalMeUnit,
            lowConfidenceKeys = mergedLowConf,
            correctedSummaryText = summary,
            usedAi = aiResult.usedAi
        )
    }

    /**
     * Enforces the biological rule that the 5 proximate macros
     * (Crude Protein + Crude Fat + Crude Fiber + Moisture + Ash) cannot exceed ~105%.
     * If an OCR digit error (e.g., reading "Moisture 10.0%" as "70.0%" on dry kibble)
     * pushes the sum > 105%, repairs or flags the anomalous value in lowConfidenceKeys.
     */
    fun applyBiologicalProximateSanityCheck(
        nutrition: Map<String, NutrientValue>,
        fallbackReference: Map<String, NutrientValue> = emptyMap()
    ): Pair<LinkedHashMap<String, NutrientValue>, Set<String>> {
        val result = LinkedHashMap(nutrition)
        val flagged = mutableSetOf<String>()
        val proximateKeys = listOf("crudeProtein", "crudeFat", "crudeFiber", "moisture", "ash")

        fun currentProximateSum(): Double =
            proximateKeys.sumOf { result[it]?.value ?: 0.0 }

        if (currentProximateSum() <= 105.0) {
            return result to flagged
        }

        // First check if substituting any single key from fallbackReference fixes the sum
        for (key in proximateKeys) {
            val cur = result[key] ?: continue
            val ref = fallbackReference[key]
            if (ref != null && ref.value < cur.value && (currentProximateSum() - cur.value + ref.value) <= 105.0) {
                result[key] = ref
                flagged.add(key)
                return result to flagged
            }
        }

        // Second check: common OCR digit/decimal errors on dry food:
        // e.g. Moisture "70%" or "70.0%" when "10%" was misread ('1' -> '7'), or missing decimal ("75%" Ash -> "7.5%")
        val moisture = result["moisture"]
        val otherSumWithoutMoisture = proximateKeys.filter { it != "moisture" }.sumOf { result[it]?.value ?: 0.0 }
        if (moisture != null && moisture.value in 65.0..79.0 && otherSumWithoutMoisture >= 38.0) {
            // Dry food where "10%" or "12%" moisture was OCR'd as "70%" or "72%"
            val repairedMoisture = kotlin.math.round((moisture.value - 60.0) * 100.0) / 100.0
            if (repairedMoisture in 5.0..19.0 && (otherSumWithoutMoisture + repairedMoisture) <= 105.0) {
                result["moisture"] = moisture.copy(value = repairedMoisture)
                flagged.add("moisture")
                return result to flagged
            }
        }

        // Check if dividing a single inflated macro by 10 (missing decimal, e.g. Ash 75% -> 7.5%, Fiber 35% -> 3.5%) fixes the sum
        for (key in listOf("ash", "crudeFiber", "moisture", "crudeFat", "crudeProtein")) {
            val cur = result[key] ?: continue
            val tenth = kotlin.math.round((cur.value / 10.0) * 100.0) / 100.0
            if (cur.value >= 25.0 && tenth >= 1.0 && (currentProximateSum() - cur.value + tenth) <= 105.0) {
                result[key] = cur.copy(value = tenth)
                flagged.add(key)
                return result to flagged
            }
        }

        // Otherwise flag the largest proximate contributor(s) as low confidence for user review
        val largestKey = proximateKeys.maxByOrNull { result[it]?.value ?: 0.0 }
        if (largestKey != null) {
            flagged.add(largestKey)
        }
        return result to flagged
    }

    suspend fun autocorrectIngredients(
        rawEnglishText: String,
        croppedBitmap: Bitmap? = null
    ): ScannedIngredientsResult =
        withContext(Dispatchers.Default) {
            val sectionSplit = classifyAndSplitSections(rawEnglishText)

            if (sectionSplit.ingredientSectionText.isBlank() && croppedBitmap == null) {
                if (sectionSplit.nutrientSectionText.isNotBlank()) {
                    val fallbackNutrients = parseNutrientsLocally(sectionSplit.nutrientSectionText)
                    if (fallbackNutrients.nutrition.isNotEmpty() || fallbackNutrients.metabolizableEnergy != null) {
                        return@withContext ScannedIngredientsResult(
                            ingredientsText = "",
                            detectedNutrientsInstead = fallbackNutrients,
                            wrongSectionDetected = "NUTRIENTS"
                        )
                    }
                }
                return@withContext ScannedIngredientsResult()
            }

            val cleaned = sectionSplit.ingredientSectionText.ifBlank { rawEnglishText }.trim()
            val aiResult = tryCallGeminiJson(
                model = ingredientsGeminiModel,
                prompt = """
                    You are autocorrecting OCR camera text from a dog or cat food Ingredients list.
                    1. Read and use commas (",") as the primary separator to identify and distinguish separate ingredients listed on the package label.
                    2. Fix camera OCR misspellings (e.g., "Chlcken MeaI" -> "Chicken Meal", "Br0wn Rlce" -> "Brown Rice", "Taur1ne" -> "Taurine").
                    3. Rejoin multi-word compound ingredients that were wrapped across lines or split by stray OCR line breaks (e.g., "Chicken, Meal" -> "Chicken Meal", "Brown, Rice" -> "Brown Rice", "Dried, Beet, Pulp" -> "Dried Beet Pulp", "Vitamin E, Supplement" -> "Vitamin E Supplement", "Calcium, Carbonate" -> "Calcium Carbonate", "Salmon, Oil" -> "Salmon Oil").
                    4. Preserve all commas (",") separating distinct ingredient items on the label (e.g., "Chicken Meal, Brown Rice, Salmon Oil, Taurine").
                    5. Insert missing spaces if OCR glued two words together (e.g., "ChickenMeal" -> "Chicken Meal").
                    6. Remove leading headers like "Ingredients:" or "Composition:" and output distinct English ingredients cleanly separated by commas and spaces (", ").
                    7. STRICTLY EXCLUDE any Guaranteed Analysis, Analytical Constituents, Nutrition Facts, nutrient percentages (Crude Protein, Crude Fat, Crude Fiber, Moisture, Ash, Calcium, Phosphorus, % min, % max), kcal/kg calorie numbers, or Feeding Guidelines.
                    8. Do NOT invent ingredients that are not in the label. If there are NO real ingredients, return "".
                    Return ONLY a JSON object:
                    {
                      "ingredients": string,
                      "correctionsCount": number
                    }
                    OCR text:
                    $cleaned
                """.trimIndent(),
                bitmap = croppedBitmap
            )

            if (aiResult != null) {
                val fixed = sanitizeIngredientsString(aiResult.optString("ingredients"))
                if (fixed.isNotBlank()) {
                    return@withContext ScannedIngredientsResult(
                        ingredientsText = fixed,
                        correctionsAppliedCount = aiResult.optInt("correctionsCount"),
                        usedAi = true
                    )
                }
            }

            autocorrectIngredientsLocally(rawEnglishText)
        }

    // -------------------------------------------------------------------------
    // Smart Section Classifier (Nutrients vs Ingredients vs Feeding Guide)
    // -------------------------------------------------------------------------

    private enum class LabelSectionKind { NONE, NUTRIENTS, INGREDIENTS, FEEDING_GUIDE }

    // Supports English, French, German, Spanish, Italian, Portuguese, Dutch, and Polish headers
    private val NUTRIENT_HEADER_REGEX = Regex(
        """\b(guaranteed\s+analysis|analytical\s+constituents|nutrition(?:al)?\s+(?:facts|analysis|information|declaration)|typical\s+analysis|calorie\s+content|constituants\s+analytiques|analyse\s+(?:garantie|moyenne)|valeurs\s+nutritionnelles|analytische\s+bestandteile|inhaltsstoffe|n[aä]hrwertangaben|componentes\s+anal[ií]ticos|an[aá]lisis\s+garantizado|informaci[oó]n\s+nutricional|componenti\s+analitici|analisi\s+garantita|tenori\s+analitici|constituintes\s+anal[ií]ticos|n[ií]veis\s+de\s+garantia|analytische\s+bestanddelen|gegarandeerde\s+analyse|sk[lł]adniki\s+analityczne|analiza\s+gwarantowana)\b""",
        RegexOption.IGNORE_CASE
    )
    private val INGREDIENT_HEADER_REGEX = Regex(
        """^\s*(?:ingredients(?:\s+list)?|composition|ingr[eé]dients|zutaten|zusammensetzung|ingredientes|ingredienti|samenstelling|sk[lł]ad)\s*:?""",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_INGREDIENT_SPLIT_REGEX = Regex(
        """\b(ingredients(?:\s+list)?|composition|ingr[eé]dients|zutaten|zusammensetzung|ingredientes|ingredienti|samenstelling|sk[lł]ad)\s*:""",
        RegexOption.IGNORE_CASE
    )
    private val FEEDING_GUIDE_HEADER_REGEX = Regex(
        """\b(feeding\s+(?:guide|guidelines?|instructions?|directions?|chart)|daily\s+feeding|recommended\s+daily|how\s+to\s+feed|mode\s+d'emploi|ration\s+journali[eè]re|f[uü]tterungsempfehlung|gu[ií]a\s+de\s+alimentaci[oó]n|dosi\s+consigliate)\b""",
        RegexOption.IGNORE_CASE
    )
    private val INGREDIENT_STOP_SECTION_REGEX = Regex(
        """\b(formulated\s+to\s+meet|nutritional\s+levels\s+established|animal\s+feeding\s+tests|aafco\s+(?:dog|cat)\s+food|complete\s+and\s+balanced\s+nutrition|calorie\s+content|metabolizable\s+energy|calculated\s+me\b|feeding\s+(?:guide|guidelines?|instructions?|directions?)|store\s+in\s+a\s+cool|keep\s+in\s+a\s+(?:cool|dry)|refrigerate\s+after\s+opening|manufactured\s+(?:by|for)|distributed\s+by|imported\s+by|produced\s+(?:by|for)|questions?\s+or\s+comments?|visit\s+us\s+at|best\s+before|best\s+by|use\s+by|guaranteed\s+analysis|analytical\s+constituents|nutrition(?:al)?\s+facts|constituants\s+analytiques|analytische\s+bestandteile|componentes\s+anal[ií]ticos|componenti\s+analitici)\b""",
        RegexOption.IGNORE_CASE
    )
    private val FEEDING_OR_PACKAGING_NOISE_REGEX = Regex(
        """\b(cups?\s*(?:/|per)\s*day|g\s*/\s*day|grams?\s*(?:/|per)\s*day|body\s*weight|lbs?\b|best\s+before|best\s+by|net\s+wt|net\s+weight|keep\s+fresh\s+water|store\s+in\s+a\s+cool|keep\s+in\s+a\s+dry|manufactured\s+(?:by|for)|distributed\s+by|imported\s+by|formulated\s+to\s+meet|nutritional\s+levels\s+established|animal\s+feeding\s+tests|aafco|satisfaction\s+guaranteed|not\s+for\s+human\s+consumption|made\s+in\s+(?:the\s+)?usa|product\s+of\s+\w+|www\.|\.com\b)\b""",
        RegexOption.IGNORE_CASE
    )

    // Multilingual nutrient line keywords (EN, FR, DE, ES, IT, PT, NL, PL)
    private val NUTRIENT_LINE_KEYWORD_REGEX = Regex(
        """(?<![\p{L}\p{N}])(?:crude\s+protein|protein\s+content|prot[eé]ines?(?:\s+brutes?)?|rohprotein|prote[ií]nas?(?:\s+(?:brutas?|crudas?|grezza))?|ruw\s+eiwit|bia[lł]ko(?:\s+surowe)?|protein|crude\s+fat|fat\s+content|oils?\s*(?:&|and)\s*fats?|mati[eè]res?\s+grasses?(?:\s+brutes?)?|rohfett|fettgehalt|roh[oö]le|grasas?(?:\s+(?:brutas?|crudas?))?|aceites?\s+y\s+grasas?|grassi(?:\s+grezzi)?|oli\s+e\s+grassi|mat[eé]ria\s+gorda|extrato\s+et[eé]reo|ruw\s+vet|t[lł]uszcz(?:\s+surowy)?|fat|crude\s+fib(?:er|re)s?|cellulose\s+brute|fibres?(?:\s+(?:alimentaires?|brutes?))?|rohfaser|fibras?(?:\s+(?:brutas?|crudas?|grezza))?|ruwe\s+celstof|w[lł][oó]kno(?:\s+surowe)?|fib(?:er|re)s?|moisture|water\s+content|humidit[eé]|teneur\s+en\s+eau|feuchtigkeit|feuchte|humedad|umidit[aà]|humidade|umidade|vocht|wilgotno[sś][cć]|water|crude\s+ash|inorganic\s+matter|cendres?(?:\s+brutes?)?|mati[eè]res?\s+inorganiques?|rohasche|anorganische\s+stoffe|cenizas?(?:\s+brutas?)?|materia\s+inorg[aá]nica|ceneri(?:\s+grezze)?|cinzas?(?:\s+brutas?)?|mat[eé]ria\s+mineral|ruwe\s+as|popi[oó][lł](?:\s+surowy)?|ash|calcium|kalzium|calcio|c[aá]lcio|wap[nń]|phosphorus|phosphore|phosphor|f[oó]sforo|fosforo|fosfor|taurine|taurin|taurina|tauryna|sodium|natrium|sodio|s[oó]dio|s[oó]d|om[eé]ga[\s-]*[36]|epa\s*\+?\s*dha|metabolizable\s+energy|calorie\s+content|[eé]nergie\s+m[eé]tabolisable|umsetzbare\s+energie|energ[ií]a\s+metabolizable)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    private val NUTRIENT_VALUE_SIGNAL_REGEX = Regex(
        """\d+(?:[.,]\d+)?\s*%|\(\s*(?:min|max)\.?\s*\)|\b(?:min|max)\.?\s*\d|\d{2,5}\s*(?:kcal|kj)\s*/|\d+(?:[.,]\d+)?\s*mj\s*/\s*kg|\b\d+(?:[.,]\d+)?\s*(?:mg|g|iu)\s*/\s*(?:kg|100\s*g)|\b\d+(?:[.,]\d+)?\s*ppm\b|\b\d+(?:[.,]\d+)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val STANDALONE_NUTRIENT_VALUE_LINE_REGEX = Regex(
        """^\s*(?:\(?\s*(?:min|max)\.?\s*\)?\s*)?[:.\-]*\s*\d+(?:[.,]\d+)?\s*(?:%|g\s*/\s*100\s*g|g\s*/\s*kg|mg\s*/\s*kg|ppm|kcal\s*/\s*(?:kg|100\s*g|cup)|kj\s*/\s*(?:kg|100\s*g)|mj\s*/\s*kg)(?:\s*\(?\s*(?:min|max)\.?\s*\)?)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val NON_INGREDIENT_ITEM_REGEX = Regex(
        """^(?:guaranteed\s+analysis|analytical\s+constituents|nutrition(?:al)?\s+facts|calorie\s+content|metabolizable\s+energy|feeding\s+guide.*|.*(?:crude\s+protein|crude\s+fat|oils?\s*(?:&|and)\s*fats?|crude\s+fib(?:er|re)|moisture|crude\s+ash|inorganic\s+matter|ash|calcium|phosphorus|sodium|omega[\s-]*[36])\s*(?:\(?\s*(?:min|max)\.?\s*\)?)?\s*[:.\-]*\s*\d+(?:[.,]\d+)?\s*%?.*|.*(?:kcal\s*/\s*(?:kg|100\s*g|cup)|\(\s*(?:min|max)\.?\s*\)|\b(?:min|max)\.?\s*\d+(?:[.,]\d+)?\s*%).*|[\d.,%\s:/()<>-]+)$""",
        RegexOption.IGNORE_CASE
    )

    fun classifyAndSplitSections(rawText: String): LabelSectionSplit {
        if (rawText.isBlank()) {
            return LabelSectionSplit("", "", 0, emptySet())
        }

        // First pre-split any single line where Guaranteed Analysis and "Ingredients:" were merged together
        val expandedLines = rawText.lines().flatMap { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) emptyList()
            else {
                val inlineSplit = INLINE_INGREDIENT_SPLIT_REGEX.find(trimmed)
                if (inlineSplit != null && inlineSplit.range.first > 0) {
                    listOf(
                        trimmed.substring(0, inlineSplit.range.first).trim(),
                        trimmed.substring(inlineSplit.range.first).trim()
                    ).filter { it.isNotEmpty() }
                } else {
                    listOf(trimmed)
                }
            }
        }

        val nutrientLines = mutableListOf<String>()
        val ingredientLines = mutableListOf<String>()
        var noiseCount = 0
        var activeSection = LabelSectionKind.NONE

        for (idx in expandedLines.indices) {
            val line = expandedLines[idx].trim()
            if (line.isEmpty()) continue

            val cleanedNumeric = preCleanNumericOcr(line)
            val isNutrientHeader = NUTRIENT_HEADER_REGEX.containsMatchIn(cleanedNumeric)
            val isIngredientHeader = INGREDIENT_HEADER_REGEX.containsMatchIn(line)
            val isFeedingHeader = FEEDING_GUIDE_HEADER_REGEX.containsMatchIn(line)

            if (isNutrientHeader) {
                activeSection = LabelSectionKind.NUTRIENTS
                nutrientLines.add(cleanedNumeric)
                continue
            }
            if (isIngredientHeader) {
                activeSection = LabelSectionKind.INGREDIENTS
                val afterHeader = line.replace(INGREDIENT_HEADER_REGEX, "").trim()
                if (afterHeader.isNotEmpty()) {
                    val stopMatch = INGREDIENT_STOP_SECTION_REGEX.find(afterHeader)
                    if (stopMatch != null) {
                        val beforeStop = afterHeader.substring(0, stopMatch.range.first).trim().trimEnd('.', ',', ';')
                        if (beforeStop.isNotEmpty()) ingredientLines.add(beforeStop)
                        activeSection = LabelSectionKind.FEEDING_GUIDE
                    } else {
                        ingredientLines.add(afterHeader)
                    }
                }
                continue
            }
            if (isFeedingHeader) {
                activeSection = LabelSectionKind.FEEDING_GUIDE
                noiseCount++
                continue
            }

            // If we are inside INGREDIENTS and hit a stop-section boundary (e.g. AAFCO statement,
            // storage instructions, distributor info), truncate before the boundary and exit INGREDIENTS.
            if (activeSection == LabelSectionKind.INGREDIENTS) {
                val stopMatch = INGREDIENT_STOP_SECTION_REGEX.find(line)
                if (stopMatch != null) {
                    val beforeStop = line.substring(0, stopMatch.range.first).trim().trimEnd('.', ',', ';')
                    if (beforeStop.isNotEmpty() && containsKnownIngredientSignal(beforeStop)) {
                        ingredientLines.add(beforeStop)
                    }
                    activeSection = LabelSectionKind.FEEDING_GUIDE
                    noiseCount++
                    continue
                }
            }

            // Explicit line-level signals override section state so adjacent columns
            // or misplaced lines never leak across Nutrients <-> Ingredients.
            val hasNutrientKeyword = NUTRIENT_LINE_KEYWORD_REGEX.containsMatchIn(cleanedNumeric)
            val hasValueSignal = NUTRIENT_VALUE_SIGNAL_REGEX.containsMatchIn(cleanedNumeric)
            val isStandaloneValueLine = STANDALONE_NUTRIENT_VALUE_LINE_REGEX.matches(cleanedNumeric)

            // Check if this is a value-less nutrient name line in a split-column or wrapped table
            val isSplitColumnNutrientName = hasNutrientKeyword &&
                !hasValueSignal &&
                !isCompositionPercentageIngredient(line) &&
                !line.contains(',') &&
                (activeSection == LabelSectionKind.NUTRIENTS ||
                    expandedLines.drop(idx + 1).take(6).any { nextLine ->
                        STANDALONE_NUTRIENT_VALUE_LINE_REGEX.matches(preCleanNumericOcr(nextLine))
                    })

            val looksLikeNutrientRow = ((hasNutrientKeyword && hasValueSignal) &&
                !isCompositionPercentageIngredient(line)) ||
                isSplitColumnNutrientName ||
                Regex("""^\s*(?:min|max)\.?\s*\d+(?:[.,]\d+)?\s*%""", RegexOption.IGNORE_CASE).containsMatchIn(cleanedNumeric) ||
                Regex("""\d{2,5}(?:[.,]\d+)?\s*(?:kcal|kj)\s*/\s*(?:kg|100\s*g|cup)""", RegexOption.IGNORE_CASE).containsMatchIn(cleanedNumeric) ||
                Regex("""\d+(?:[.,]\d+)?\s*mj\s*/\s*kg""", RegexOption.IGNORE_CASE).containsMatchIn(cleanedNumeric)

            val looksLikeFeedingNoise = FEEDING_OR_PACKAGING_NOISE_REGEX.containsMatchIn(line) ||
                INGREDIENT_STOP_SECTION_REGEX.containsMatchIn(line)

            val commaCount = line.count { it == ',' || it == ';' }
            val hasIngredientSignal = containsKnownIngredientSignal(line)
            val looksLikeIngredientParagraph = !looksLikeNutrientRow &&
                !looksLikeFeedingNoise &&
                hasIngredientSignal &&
                (commaCount >= 1 || activeSection == LabelSectionKind.INGREDIENTS)

            when {
                looksLikeNutrientRow -> {
                    activeSection = LabelSectionKind.NUTRIENTS
                    nutrientLines.add(cleanedNumeric)
                }
                looksLikeFeedingNoise -> {
                    if (activeSection == LabelSectionKind.INGREDIENTS) {
                        activeSection = LabelSectionKind.FEEDING_GUIDE
                    }
                    noiseCount++
                }
                activeSection == LabelSectionKind.NUTRIENTS && (isStandaloneValueLine || Regex("""^[\d.,%\s:/()<>-]+$""").matches(cleanedNumeric)) -> {
                    // Pure percentage/unit fragment from a 2-column table stays with nutrients
                    nutrientLines.add(cleanedNumeric)
                }
                looksLikeIngredientParagraph -> {
                    activeSection = LabelSectionKind.INGREDIENTS
                    ingredientLines.add(line)
                }
                activeSection == LabelSectionKind.INGREDIENTS -> {
                    if (hasIngredientSignal) {
                        ingredientLines.add(line)
                    } else {
                        noiseCount++
                    }
                }
                activeSection == LabelSectionKind.FEEDING_GUIDE -> {
                    noiseCount++
                }
                else -> {
                    // Strict gate when no "Ingredients:" header was seen:
                    // Only accept headerless lines if they contain genuine pet-food ingredient vocabulary
                    // and no standalone nutrient percentages.
                    if (hasIngredientSignal && isStrictHeaderlessIngredientCandidate(line)) {
                        ingredientLines.add(line)
                    } else {
                        noiseCount++
                    }
                }
            }
        }

        val detected = buildSet {
            if (nutrientLines.isNotEmpty()) add("NUTRIENTS")
            if (ingredientLines.isNotEmpty()) add("INGREDIENTS")
        }

        return LabelSectionSplit(
            nutrientSectionText = nutrientLines.joinToString("\n"),
            ingredientSectionText = ingredientLines.joinToString("\n"),
            feedingGuideOrNoiseCount = noiseCount,
            detectedSections = detected
        )
    }

    // -------------------------------------------------------------------------
    // Offline / Unit-testable Parsers & Sanity Checks
    // -------------------------------------------------------------------------

    fun preCleanNumericOcr(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            // Fix O/o inside numbers: "26.O%" -> "26.0%", "O.8%" -> "0.8%", "1O.0%" -> "10.0%"
            .replace(Regex("""(?<=\d)[Oo](?=\s*%|\b|\.|\s*(?:g|mg|kcal|kj|mj))""", RegexOption.IGNORE_CASE), "0")
            .replace(Regex("""\b[Oo](?=[.,]\d)"""), "0")
            .replace(Regex("""(?<=[.,])[Oo](?=\d|\s*%|\b)"""), "0")
            // Convert European decimal commas inside numbers with units/percent (e.g. "28,5%" -> "28.5%", "1,2 g/kg" -> "1.2 g/kg")
            .replace(Regex("""(?<=\d),(?=\d+\s*(?:%|g\b|mg\b|ppm\b|iu\b|kcal\b|kj\b|mj\b))""", RegexOption.IGNORE_CASE), ".")
            // Common English label OCR typos
            .replace(Regex("""crvde|crude\s*pr[0o]te[1il]n""", RegexOption.IGNORE_CASE), "Crude Protein")
            .replace(Regex("""pr[0o]te[1il]n""", RegexOption.IGNORE_CASE), "Protein")
            .replace(Regex("""oils?\s*(?:&|and)\s*fats?""", RegexOption.IGNORE_CASE), "Crude Fat")
            .replace(Regex("""f[a@]t\b""", RegexOption.IGNORE_CASE), "Fat")
            .replace(Regex("""f[1il]b[e3]r|f[1il]br[e3]""", RegexOption.IGNORE_CASE), "Fiber")
            .replace(Regex("""m[0o][1il]sture|mo1sture""", RegexOption.IGNORE_CASE), "Moisture")
            .replace(Regex("""ca[1il]c[1il]um""", RegexOption.IGNORE_CASE), "Calcium")
            .replace(Regex("""ph[0o]sph[0o]r[ou]s""", RegexOption.IGNORE_CASE), "Phosphorus")
            .replace(Regex("""taur[1il]ne""", RegexOption.IGNORE_CASE), "Taurine")
            .replace(Regex("""inorganic\s+matter""", RegexOption.IGNORE_CASE), "Ash")
            .replace(Regex("""\ba[s$]h\b""", RegexOption.IGNORE_CASE), "Ash")
            .trim()
    }

    fun parseFrontOfBagLocally(raw: String): ScannedFrontInfoResult {
        val lower = raw.lowercase(Locale.ROOT)
        val species = when {
            lower.contains("cat") || lower.contains("kitten") || lower.contains("feline") -> "Cat"
            lower.contains("dog") || lower.contains("puppy") || lower.contains("canine") -> "Dog"
            else -> ""
        }
        val lifeStage = when {
            lower.contains("all life stages") || lower.contains("all stages") -> "All Life Stages"
            lower.contains("kitten") -> "Kitten"
            lower.contains("puppy") -> "Puppy"
            lower.contains("senior") || lower.contains("mature") || lower.contains("7+") -> "Senior"
            lower.contains("adult") -> "Adult"
            else -> ""
        }
        val foodType = when {
            lower.contains("treat") || lower.contains("snack") || lower.contains("biscuit") -> "Treat"
            lower.contains("wet") || lower.contains("pouch") || lower.contains("canned") || lower.contains("gravy") -> "Wet Food"
            lower.contains("dry") || lower.contains("kibble") -> "Dry Food"
            else -> ""
        }
        val packagingType = when {
            lower.contains("can") || lower.contains("canned") || lower.contains("tin") -> "Can"
            lower.contains("pouch") || lower.contains("gravy") -> "Pouch"
            lower.contains("bag") || lower.contains("kibble") || lower.contains("dry") -> "Bag"
            lower.contains("tub") || lower.contains("cup") -> "Tub"
            lower.contains("tetra") || lower.contains("carton") -> "Tetra Pak"
            else -> if (foodType == "Wet Food") "Pouch" else if (foodType == "Dry Food") "Bag" else ""
        }
        val packagingMaterial = when (packagingType) {
            "Can" -> "Aluminum / Tin"
            "Pouch" -> "Flexible Plastic / Foil"
            "Bag" -> "Plastic Film / Paper"
            "Tub" -> "Plastic"
            "Tetra Pak" -> "Cardboard / Composite"
            else -> ""
        }
        val packagingRecyclability = when (packagingType) {
            "Can" -> "Recyclable"
            "Pouch", "Bag" -> "Difficult to Recycle"
            "Tub", "Tetra Pak" -> "Recyclable"
            else -> ""
        }
        val packagingBiodegradability = if (packagingType.isNotBlank()) "Non-Biodegradable" else ""

        val lines = raw.lines().map { it.trim() }.filter { it.length >= 3 }
        val brand = lines.firstOrNull().orEmpty().take(40)
        val productName = lines.drop(1).firstOrNull().orEmpty().take(60)
        return ScannedFrontInfoResult(
            productName = productName,
            brand = brand,
            species = species,
            lifeStage = lifeStage,
            foodType = foodType,
            packagingType = packagingType,
            packagingMaterial = packagingMaterial,
            packagingRecyclability = packagingRecyclability,
            packagingBiodegradability = packagingBiodegradability,
            usedAi = false
        )
    }

    private data class MultilingualNutrientSpec(
        val key: String,
        val displayName: String,
        val keywordPattern: String,
        val maxAllowed: Double = 100.0
    )

    private val MULTILINGUAL_NUTRIENT_SPECS = listOf(
        MultilingualNutrientSpec(
            key = "crudeProtein",
            displayName = "Crude Protein",
            keywordPattern = """(?:crude\s*protein|protein\s*content|prot[eé]ines?(?:\s*brutes?)?|rohprotein|prote[ií]nas?(?:\s*(?:brutas?|crudas?))?|proteina\s*grezza|ruw\s*eiwit|eiwit|bia[lł]ko(?:\s*surowe)?|protein)""",
            maxAllowed = 100.0
        ),
        MultilingualNutrientSpec(
            key = "crudeFat",
            displayName = "Crude Fat",
            keywordPattern = """(?:crude\s*fat|fat\s*content|crude\s*oils?\s*(?:&|and)\s*fats?|oils?\s*(?:&|and)\s*fats?|mati[eè]res?\s*grasses?(?:\s*brutes?)?|teneur\s*en\s*mati[eè]res?\s*grasses?|rohfett|fettgehalt|roh[oö]le(?:\s*und\s*-?fette)?|grasas?(?:\s*(?:brutas?|crudas?))?|aceites?\s*y\s*grasas?(?:\s*brutos?)?|contenido\s*de\s*grasa|grassi(?:\s*grezzi)?|oli\s*e\s*grassi(?:\s*grezzi)?|mat[eé]ria\s*gorda(?:\s*bruta)?|extrato\s*et[eé]reo|ruw\s*vet|vetgehalte|t[lł]uszcz(?:\s*surowy)?|fat)""",
            maxAllowed = 100.0
        ),
        MultilingualNutrientSpec(
            key = "crudeFiber",
            displayName = "Crude Fiber",
            keywordPattern = """(?:crude\s*(?:fibers?|fibres?)|dietary\s*(?:fibers?|fibres?)|cellulose\s*brute|fibres?\s*(?:alimentaires?|brutes?)|rohfaser|fibras?\s*(?:brutas?|crudas?|alimentarias?)|fibra\s*(?:bruta|cruda|grezza)|ruwe\s*celstof|w[lł][oó]kno(?:\s*surowe)?|fibers?|fibres?)""",
            maxAllowed = 100.0
        ),
        MultilingualNutrientSpec(
            key = "moisture",
            displayName = "Moisture",
            keywordPattern = """(?:moisture|water\s*content|humidit[eé]|teneur\s*en\s*eau|feuchtigkeit|feuchte|humedad|umidit[aà]|humidade|umidade|vocht|vochtgehalte|wilgotno[sś][cć]|water)""",
            maxAllowed = 100.0
        ),
        MultilingualNutrientSpec(
            key = "ash",
            displayName = "Ash",
            keywordPattern = """(?:crude\s*ash|inorganic\s*matter|incinerated\s*residue|mineral\s*matter|cendres?(?:\s*brutes?)?|mati[eè]res?\s*inorganiques?|rohasche|anorganische\s*stoffe|cenizas?(?:\s*brutas?)?|materia\s*inorg[aá]nica|ceneri(?:\s*grezze)?|materia\s*inorganica|cinzas?(?:\s*brutas?)?|mat[eé]ria\s*mineral|ruwe\s*as|popi[oó][lł](?:\s*surowy)?|ash)""",
            maxAllowed = 100.0
        ),
        MultilingualNutrientSpec(
            key = "calcium",
            displayName = "Calcium",
            keywordPattern = """(?:calcium|kalzium|calcio|c[aá]lcio|wap[nń])""",
            maxAllowed = 15.0
        ),
        MultilingualNutrientSpec(
            key = "phosphorus",
            displayName = "Phosphorus",
            keywordPattern = """(?:phosphorus|phosphore|phosphor|f[oó]sforo|fosforo|fosfor)""",
            maxAllowed = 15.0
        ),
        MultilingualNutrientSpec(
            key = "taurine",
            displayName = "Taurine",
            keywordPattern = """(?:taurine|taurin|taurina|tauryna)""",
            maxAllowed = 10.0
        ),
        MultilingualNutrientSpec(
            key = "omega3",
            displayName = "Omega-3",
            keywordPattern = """(?:(?:acides?\s*gras\s*|[aá]cidos?\s*grasos?\s*|acidi\s*grassi\s*)?om[eé]ga[\s\-]*3(?:\s*fatty\s*acids?|\s*-?fetts[aä]uren)?|epa\s*\+?\s*dha)""",
            maxAllowed = 15.0
        ),
        MultilingualNutrientSpec(
            key = "omega6",
            displayName = "Omega-6",
            keywordPattern = """(?:(?:acides?\s*gras\s*|[aá]cidos?\s*grasos?\s*|acidi\s*grassi\s*)?om[eé]ga[\s\-]*6(?:\s*fatty\s*acids?|\s*-?fetts[aä]uren)?)""",
            maxAllowed = 20.0
        ),
        MultilingualNutrientSpec(
            key = "sodium",
            displayName = "Sodium",
            keywordPattern = """(?:sodium|natrium|sodio|s[oó]dio|s[oó]d)""",
            maxAllowed = 5.0
        )
    )

    /**
     * Reconstructs 2-column nutrition tables where ML Kit read either:
     * 1. A vertical block of N value-less nutrient names followed by a vertical block of N standalone values, or
     * 2. Wrapped two-line pairs ("Crude Protein (min)\n28.0%").
     */
    private fun reconstructSplitColumnAndWrappedNutrients(rawCleaned: String): String {
        val lines = rawCleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return rawCleaned

        val hasDigitWithUnitRegex = Regex(
            """(?<!om[eé]ga[\s\-])\b\d+(?:[.,]\d+)?\s*(?:%|g\s*/\s*100\s*g|g\s*/\s*kg|mg\s*/\s*kg|ppm)\b""",
            RegexOption.IGNORE_CASE
        )

        // Pass 1: Zip contiguous blocks of N nutrient-name-only lines followed by M standalone-value lines
        val afterColumnZip = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val nameBlock = mutableListOf<String>()
            var j = i
            while (j < lines.size) {
                val l = lines[j]
                val isHeaderOnly = NUTRIENT_HEADER_REGEX.matches(l)
                val hasKey = NUTRIENT_LINE_KEYWORD_REGEX.containsMatchIn(l) && !isHeaderOnly
                val hasVal = hasDigitWithUnitRegex.containsMatchIn(l) || STANDALONE_NUTRIENT_VALUE_LINE_REGEX.matches(l)
                if (hasKey && !hasVal) {
                    nameBlock.add(l)
                    j++
                } else {
                    break
                }
            }

            if (nameBlock.size >= 2) {
                val valBlock = mutableListOf<String>()
                var k = j
                while (k < lines.size && valBlock.size < nameBlock.size) {
                    val vl = lines[k]
                    if (STANDALONE_NUTRIENT_VALUE_LINE_REGEX.matches(vl)) {
                        valBlock.add(vl)
                        k++
                    } else {
                        break
                    }
                }
                if (valBlock.size >= 2) {
                    val pairCount = minOf(nameBlock.size, valBlock.size)
                    for (idx in 0 until pairCount) {
                        afterColumnZip.add("${nameBlock[idx]}  ${valBlock[idx]}")
                    }
                    for (idx in pairCount until nameBlock.size) {
                        afterColumnZip.add(nameBlock[idx])
                    }
                    i = j + pairCount
                    continue
                }
            }

            afterColumnZip.add(lines[i])
            i++
        }

        // Pass 2: Pair any remaining single nutrient-name-only line with an immediately following standalone value line
        val finalLines = mutableListOf<String>()
        var idx = 0
        while (idx < afterColumnZip.size) {
            val cur = afterColumnZip[idx]
            val next = afterColumnZip.getOrNull(idx + 1)
            val curHasKey = NUTRIENT_LINE_KEYWORD_REGEX.containsMatchIn(cur) && !NUTRIENT_HEADER_REGEX.matches(cur)
            val curHasVal = hasDigitWithUnitRegex.containsMatchIn(cur)
            if (curHasKey && !curHasVal && next != null && STANDALONE_NUTRIENT_VALUE_LINE_REGEX.matches(next)) {
                finalLines.add("$cur  $next")
                idx += 2
            } else {
                finalLines.add(cur)
                idx++
            }
        }

        return finalLines.joinToString("\n")
    }

    /**
     * Splits paragraph-form or listed nutrient text into individual clauses
     * while preserving decimal numbers (e.g., "28.5%") and multilingual slash-separated rows.
     */
    private fun expandNutrientClauses(reconstructedText: String): List<String> {
        val clauses = mutableListOf<String>()
        for (line in reconstructedText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            // Split on ',', ';', '•', '·', or sentence period '.' (not inside decimals or "min."/"max.")
            val splitParts = trimmed.split(
                Regex("""[,;•·]+|(?<!\bmin|\bmax|\d)\.(?!\d)""", RegexOption.IGNORE_CASE)
            )
            for (part in splitParts) {
                val p = part.trim()
                if (p.isNotEmpty()) clauses.add(p)
            }
            // Also keep the full line at the end so multilingual slash-rows that use commas in translations still match
            if (splitParts.size > 1) {
                clauses.add(trimmed)
            }
        }
        return clauses
    }

    fun parseNutrientsLocally(raw: String): ScannedNutrientsResult {
        val sectionSplit = classifyAndSplitSections(raw)
        if (sectionSplit.nutrientSectionText.isBlank() && sectionSplit.ingredientSectionText.isNotBlank()) {
            return ScannedNutrientsResult(wrongSectionDetected = "INGREDIENTS")
        }
        val cleaned = preCleanNumericOcr(sectionSplit.nutrientSectionText.ifBlank { raw })
        val reconstructed = reconstructSplitColumnAndWrappedNutrients(cleaned)
        val clauses = expandNutrientClauses(reconstructed)

        val nutritionMap = linkedMapOf<String, NutrientValue>()
        val lowConfidence = mutableSetOf<String>()

        fun convertUnitToPercent(rawVal: Double, rawUnit: String): Double {
            val normUnit = rawUnit.lowercase(Locale.ROOT).replace(Regex("""\s+"""), "")
            return when {
                normUnit == "g/kg" -> kotlin.math.round((rawVal / 10.0) * 1000.0) / 1000.0
                normUnit == "mg/kg" || normUnit == "ppm" -> kotlin.math.round((rawVal / 10000.0) * 10000.0) / 10000.0
                else -> rawVal // "%" or "g/100g" or unitless
            }
        }

        for (spec in MULTILINGUAL_NUTRIENT_SPECS) {
            // Pattern 1a (Name -> Value with explicit unit %, g/100g, g/kg, mg/kg, ppm):
            // Allows up to 140 chars of multilingual translations (including "Oméga-3"/"Omega-6") before the unit-tagged value.
            val nameToValueWithUnitRegex = Regex(
                """(?<![\p{L}\p{N}])${spec.keywordPattern}(?![\p{L}\p{N}])[^\n]{0,140}?(?<!om[eé]ga[\s\-])(?<!om[eé]ga)(\d+(?:[.,]\d+)?)\s*(%|g\s*/\s*100\s*g|g\s*/\s*kg|mg\s*/\s*kg|ppm)""",
                RegexOption.IGNORE_CASE
            )
            // Pattern 1b (Name -> Unitless Value fallback for tables that omit '%' on individual rows):
            val nameToUnitlessValueRegex = Regex(
                """(?<![\p{L}\p{N}])${spec.keywordPattern}(?![\p{L}\p{N}])[^\n]{0,140}?(?<!om[eé]ga[\s\-])(?<!om[eé]ga)(\d+(?:[.,]\d+)?)\s*$""",
                RegexOption.IGNORE_CASE
            )
            // Pattern 2 (Value -> Name): e.g. "28% Crude Protein" or "15.5% Rohfett"
            val valueToNameRegex = Regex(
                """(\d+(?:[.,]\d+)?)\s*(%|g\s*/\s*100\s*g|g\s*/\s*kg|mg\s*/\s*kg|ppm)\s*(?:\(?\s*(?:min|max)\.?\s*\)?\s*)?(?<![\p{L}\p{N}])${spec.keywordPattern}(?![\p{L}\p{N}])""",
                RegexOption.IGNORE_CASE
            )

            var matchedNumStr: String? = null
            var matchedUnitStr = "%"

            // Pass 1: Search for explicit unit-tagged values (Name -> Value% or Value% -> Name)
            for (clause in clauses) {
                val m1 = nameToValueWithUnitRegex.find(clause)
                if (m1 != null) {
                    matchedNumStr = m1.groupValues[1]
                    matchedUnitStr = m1.groupValues[2].ifBlank { "%" }
                    break
                }
                val m2 = valueToNameRegex.find(clause)
                if (m2 != null) {
                    matchedNumStr = m2.groupValues[1]
                    matchedUnitStr = m2.groupValues[2].ifBlank { "%" }
                    break
                }
            }

            // Pass 2: Fallback to unitless numbers at end of clause if no unit-tagged value was found
            if (matchedNumStr == null) {
                for (clause in clauses) {
                    val mUnitless = nameToUnitlessValueRegex.find(clause)
                    if (mUnitless != null) {
                        matchedNumStr = mUnitless.groupValues[1]
                        matchedUnitStr = "%"
                        break
                    }
                }
            }

            if (matchedNumStr != null) {
                val parsedRaw = matchedNumStr.replace(",", ".").toDoubleOrNull()
                if (parsedRaw != null) {
                    val convertedPct = convertUnitToPercent(parsedRaw, matchedUnitStr)
                    val (sanitized, isLowConf) = applyDecimalSanityCheck(spec.key, convertedPct, spec.maxAllowed)
                    if (sanitized != null && sanitized > 0.0 && sanitized <= spec.maxAllowed) {
                        nutritionMap[spec.key] = NutrientValue(name = spec.displayName, value = sanitized, unit = "%")
                        if (isLowConf) lowConfidence.add(spec.key)
                    }
                }
            }
        }

        // Apply Biological Proximate Sum Sanity Check (Protein + Fat + Fiber + Moisture + Ash <= 105%)
        val (bioSanitizedMap, bioFlagged) = applyBiologicalProximateSanityCheck(nutritionMap)
        lowConfidence.addAll(bioFlagged)

        // Metabolizable Energy (kcal/kg, kcal/100g, kcal/cup, kJ/100g, kJ/kg, MJ/kg)
        var meVal: Double? = null
        var meUnit: String? = null
        val meKgMatch = Regex("""(\d{3,5}(?:[.,]\d+)?)\s*kcal\s*/\s*kg""", RegexOption.IGNORE_CASE).find(reconstructed)
        val me100gMatch = Regex("""(\d{2,4}(?:[.,]\d+)?)\s*kcal\s*/\s*100\s*g""", RegexOption.IGNORE_CASE).find(reconstructed)
        val meCupMatch = Regex("""(\d{2,4}(?:[.,]\d+)?)\s*kcal\s*/\s*cup""", RegexOption.IGNORE_CASE).find(reconstructed)
        val kj100gMatch = Regex("""(\d{3,5}(?:[.,]\d+)?)\s*kj\s*/\s*100\s*g""", RegexOption.IGNORE_CASE).find(reconstructed)
        val kjKgMatch = Regex("""(\d{4,6}(?:[.,]\d+)?)\s*kj\s*/\s*kg""", RegexOption.IGNORE_CASE).find(reconstructed)
        val mjKgMatch = Regex("""(\d{1,2}(?:[.,]\d+)?)\s*mj\s*/\s*kg""", RegexOption.IGNORE_CASE).find(reconstructed)

        when {
            meKgMatch != null -> {
                val v = meKgMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (v != null && v in 200.0..8000.0) {
                    meVal = v
                    meUnit = "kcal/kg"
                }
            }
            me100gMatch != null -> {
                val v = me100gMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (v != null && v in 20.0..800.0) {
                    meVal = v * 10.0
                    meUnit = "kcal/kg"
                }
            }
            meCupMatch != null -> {
                val v = meCupMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (v != null && v in 100.0..1200.0) {
                    meVal = v
                    meUnit = "kcal/cup"
                }
            }
            kj100gMatch != null -> {
                val kj = kj100gMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (kj != null && kj in 150.0..3200.0) {
                    meVal = kotlin.math.round(((kj * 10.0) / 4.184) * 10.0) / 10.0
                    meUnit = "kcal/kg"
                }
            }
            kjKgMatch != null -> {
                val kj = kjKgMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (kj != null && kj in 1500.0..32000.0) {
                    meVal = kotlin.math.round((kj / 4.184) * 10.0) / 10.0
                    meUnit = "kcal/kg"
                }
            }
            mjKgMatch != null -> {
                val mj = mjKgMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (mj != null && mj in 1.5..32.0) {
                    meVal = kotlin.math.round((mj * 239.006) * 10.0) / 10.0
                    meUnit = "kcal/kg"
                }
            }
        }

        val summary = bioSanitizedMap.values.joinToString(" · ") { "${it.name}: ${it.value}${it.unit}" }
        return ScannedNutrientsResult(
            nutrition = bioSanitizedMap,
            metabolizableEnergy = meVal,
            metabolizableEnergyUnit = meUnit,
            lowConfidenceKeys = lowConfidence,
            correctedSummaryText = summary,
            usedAi = false
        )
    }

    /**
     * Catches common missing-decimal OCR issues on pet-food labels:
     * e.g., Calcium "125%" -> 1.25% (low confidence flagged for user review),
     * Moisture "120%" -> 12.0%.
     */
    fun applyDecimalSanityCheck(key: String, rawValue: Double, maxAllowed: Double): Pair<Double?, Boolean> {
        if (!rawValue.isFinite() || rawValue <= 0.0) return null to false
        return when (key) {
            "calcium", "phosphorus", "omega3", "omega6" -> when {
                rawValue <= maxAllowed -> rawValue to false
                rawValue in (maxAllowed + 0.1)..150.0 -> (rawValue / 100.0) to true
                else -> null to false
            }
            "taurine", "sodium" -> when {
                rawValue <= maxAllowed -> rawValue to false
                rawValue in (maxAllowed + 0.1)..100.0 -> (rawValue / 100.0) to true
                else -> null to false
            }
            else -> when {
                rawValue <= maxAllowed -> rawValue to false
                rawValue in 100.1..999.0 -> (rawValue / 10.0) to true
                else -> null to false
            }
        }
    }

    private val INGREDIENT_OCR_REPLACEMENTS = listOf(
        Regex("""\bchlcken\b""", RegexOption.IGNORE_CASE) to "Chicken",
        Regex("""\bch1cken\b""", RegexOption.IGNORE_CASE) to "Chicken",
        Regex("""\bde-?b[0o]ned\b""", RegexOption.IGNORE_CASE) to "Deboned",
        Regex("""\bmea[1i]\b""", RegexOption.IGNORE_CASE) to "Meal",
        Regex("""\bbr0wn\b""", RegexOption.IGNORE_CASE) to "Brown",
        Regex("""\brlce\b""", RegexOption.IGNORE_CASE) to "Rice",
        Regex("""\btaur[1il]ne\b""", RegexOption.IGNORE_CASE) to "Taurine",
        Regex("""\bsalm[0o]n\b""", RegexOption.IGNORE_CASE) to "Salmon",
        Regex("""\bwh[e3]at\b""", RegexOption.IGNORE_CASE) to "Wheat",
        Regex("""\bc[0o]rn\b""", RegexOption.IGNORE_CASE) to "Corn",
        Regex("""\bv[1il]tam[1il]n(s)?\b""", RegexOption.IGNORE_CASE) to "Vitamin$1",
        Regex("""\bm[1il]neral(s)?\b""", RegexOption.IGNORE_CASE) to "Mineral$1",
        Regex("""\bp[0o]ultry\b""", RegexOption.IGNORE_CASE) to "Poultry",
        Regex("""\blam[b6]\b""", RegexOption.IGNORE_CASE) to "Lamb",
        Regex("""\bb[e3]{2}f\b""", RegexOption.IGNORE_CASE) to "Beef",
        Regex("""\bo[1il]l\b""", RegexOption.IGNORE_CASE) to "Oil",
        Regex("""\bpr[0o]te[1il]n\b""", RegexOption.IGNORE_CASE) to "Protein",
        Regex("""\bsupp[1il]ement\b""", RegexOption.IGNORE_CASE) to "Supplement",
        Regex("""\bcarb[0o]nate\b""", RegexOption.IGNORE_CASE) to "Carbonate",
        Regex("""\bchl[0o]r[1il]de\b""", RegexOption.IGNORE_CASE) to "Chloride",
        Regex("""\bt[0o]c[0o]pher[0o]ls?\b""", RegexOption.IGNORE_CASE) to "Tocopherols",
        Regex("""\bpyr[1il]d[0o]x[1il]ne\b""", RegexOption.IGNORE_CASE) to "Pyridoxine",
        Regex("""\br[1il]b[0o]flav[1il]n\b""", RegexOption.IGNORE_CASE) to "Riboflavin",
        Regex("""\bth[1il]am[1il]ne\b""", RegexOption.IGNORE_CASE) to "Thiamine",
        Regex("""\bmeth[1il][0o]n[1il]ne\b""", RegexOption.IGNORE_CASE) to "Methionine",
        Regex("""\bch[0o]l[1il]ne\b""", RegexOption.IGNORE_CASE) to "Choline",
        Regex("""\bflax\s*s[e3]{2}d\b""", RegexOption.IGNORE_CASE) to "Flaxseed"
    )

    // Curated Pet-Food Ingredient Lexicon for strict positive validation & fuzzy OCR typo recovery
    private val KNOWN_INGREDIENT_WORDS = setOf(
        // Meats, Poultry, Fish, Dairy, Eggs & Animal Derivatives
        "chicken", "turkey", "duck", "quail", "goose", "pheasant", "poultry", "fowl",
        "beef", "lamb", "mutton", "goat", "pork", "boar", "venison", "bison", "buffalo",
        "kangaroo", "rabbit", "hare", "salmon", "trout", "tuna", "whitefish", "herring",
        "menhaden", "mackerel", "sardine", "sardines", "anchovy", "anchovies", "cod",
        "haddock", "pollock", "halibut", "catfish", "tilapia", "flounder", "sole", "whiting",
        "hake", "fish", "krill", "shrimp", "prawn", "crab", "lobster", "mussel", "mussels",
        "clam", "oyster", "scallop", "squid", "calamari", "meat", "animal", "liver", "heart",
        "kidney", "lung", "spleen", "tripe", "giblets", "gizzard", "tongue", "trachea",
        "cartilage", "marrow", "bone", "blood", "plasma", "egg", "eggs", "whey", "casein",
        "milk", "cheese", "yogurt", "colostrum", "gelatin", "collagen", "lard", "tallow",
        "suet", "greaves", "cracklings", "broth", "stock", "gravy", "digest", "hydrolysate",
        "by-product", "by-products", "meal", "derivative", "derivatives",
        // Grains, Starches & Fibers
        "rice", "barley", "oat", "oats", "oatmeal", "groats", "corn", "maize", "wheat",
        "sorghum", "milo", "millet", "rye", "spelt", "kamut", "triticale", "amaranth",
        "quinoa", "buckwheat", "farro", "bulgur", "hominy", "grits", "semolina", "bran",
        "germ", "middlings", "shorts", "flour", "starch", "malt", "malted", "brewers",
        "distillers", "grain", "grains", "cereal", "cereals", "potato", "potatoes", "yam",
        "yams", "tapioca", "cassava", "manioc", "arrowroot", "sago", "cellulose", "pectin",
        "inulin", "psyllium", "chicory", "fructooligosaccharide", "fructooligosaccharides",
        "mannanoligosaccharide", "mannanoligosaccharides", "oligosaccharide", "oligosaccharides",
        "beet", "pulp", "pomace", "hull", "hulls", "husks", "fiber", "fibre", "miscanthus",
        // Legumes, Pulses, Seeds & Plant Proteins
        "pea", "peas", "lentil", "lentils", "chickpea", "chickpeas", "garbanzo", "bean",
        "beans", "pinto", "navy", "fava", "fababean", "lupin", "lupine", "soy", "soya",
        "soybean", "soybeans", "alfalfa", "lucerne", "clover", "peanut", "peanuts", "flax",
        "flaxseed", "linseed", "chia", "hemp", "sesame", "sunflower", "safflower", "rapeseed",
        "canola", "camelina", "cottonseed", "guar", "carob", "cassia", "fenugreek", "seed", "seeds",
        // Oils, Fats & Lipids
        "oil", "fat", "fats", "lipid", "lipids", "triglycerides", "lecithin", "glycerin",
        "glycerine", "glycerol", "coconut", "palm", "olive", "algal", "borage", "primrose",
        "epa", "dha", "arachidonic", "linoleic", "omega", "omega-3", "omega-6",
        // Fruits, Vegetables, Herbs, Botanicals & Fungi
        "carrot", "carrots", "blueberry", "blueberries", "cranberry", "cranberries",
        "raspberry", "raspberries", "blackberry", "blackberries", "strawberry", "strawberries",
        "mulberry", "acai", "goji", "elderberry", "apple", "apples", "pear", "pears",
        "banana", "bananas", "mango", "papaya", "pineapple", "orange", "citrus", "lemon",
        "lime", "grapefruit", "pomegranate", "apricot", "peach", "plum", "prune", "fig",
        "date", "melon", "watermelon", "cantaloupe", "pumpkin", "squash", "butternut",
        "zucchini", "cucumber", "tomato", "tomatoes", "spinach", "kale", "broccoli",
        "cauliflower", "cabbage", "lettuce", "celery", "asparagus", "artichoke", "parsnip",
        "turnip", "rutabaga", "radish", "beets", "chard", "collards", "watercress",
        "dandelion", "parsley", "cilantro", "coriander", "basil", "oregano", "thyme",
        "rosemary", "sage", "mint", "peppermint", "spearmint", "chamomile", "fennel",
        "anise", "dill", "marjoram", "tarragon", "turmeric", "curcumin", "ginger",
        "cinnamon", "clove", "nutmeg", "cardamom", "garlic", "aloe", "yucca", "schidigera",
        "marigold", "calendula", "hibiscus", "rosehips", "echinacea", "ginseng", "valerian",
        "nettle", "kelp", "seaweed", "algae", "spirulina", "chlorella", "ascophyllum",
        "wakame", "nori", "dulse", "carrageenan", "agar", "mushroom", "shiitake", "reishi",
        "maitake", "yeast", "saccharomyces", "cerevisiae", "torula", "root",
        // Vitamins, Amino Acids, Minerals & Supplements
        "vitamin", "vitamins", "provitamin", "supplement", "retinyl", "retinol", "carotene",
        "beta-carotene", "thiamine", "thiamin", "mononitrate", "hydrochloride", "riboflavin",
        "niacin", "niacinamide", "nicotinic", "nicotinamide", "pantothenic", "pantothenate",
        "pyridoxine", "biotin", "folic", "folate", "cobalamin", "cyanocobalamin", "choline",
        "inositol", "ascorbic", "ascorbate", "polyphosphate", "cholecalciferol",
        "ergocalciferol", "tocopherol", "tocopherols", "alpha-tocopherol", "menadione",
        "bisulfite", "taurine", "carnitine", "l-carnitine", "methionine", "dl-methionine",
        "l-methionine", "lysine", "l-lysine", "threonine", "l-threonine", "tryptophan",
        "l-tryptophan", "arginine", "l-arginine", "cysteine", "l-cysteine", "cystine",
        "glycine", "glutamine", "histidine", "isoleucine", "leucine", "phenylalanine",
        "tyrosine", "valine", "betaine", "creatine", "glucosamine", "chondroitin",
        "methylsulfonylmethane", "hyaluronic", "lutein", "zeaxanthin", "astaxanthin",
        "lycopene", "mineral", "minerals", "calcium", "phosphorus", "phosphate", "dicalcium",
        "tricalcium", "monocalcium", "carbonate", "potassium", "sodium", "chloride", "salt",
        "magnesium", "zinc", "iron", "ferrous", "ferric", "copper", "cupric", "manganese",
        "manganous", "iodine", "iodate", "iodide", "dihydriodide", "ethylenediamine",
        "selenium", "selenite", "selenomethionine", "cobalt", "chromium", "picolinate",
        "sulfate", "sulphate", "oxide", "proteinate", "chelate", "chelated", "complex",
        "polysaccharide", "gluconate", "citrate", "lactate", "fumarate", "malate", "acetate",
        "propionate", "sorbate", "benzoate", "palmitate", "stearate", "bicarbonate",
        "tripolyphosphate", "hexametaphosphate", "pyrophosphate", "montmorillonite",
        "bentonite", "kaolin", "zeolite", "clinoptilolite", "attapulgite",
        // Probiotics, Enzymes & Fermentation
        "lactobacillus", "acidophilus", "casei", "plantarum", "fermentum", "reuteri",
        "rhamnosus", "enterococcus", "faecium", "bifidobacterium", "animalis", "longum",
        "bifidum", "thermophilum", "lactis", "bacillus", "subtilis", "licheniformis",
        "coagulans", "pediococcus", "acidilactici", "aspergillus", "oryzae", "niger",
        "trichoderma", "papain", "bromelain", "amylase", "protease", "lipase", "cellulase",
        "xylanase", "phytase", "beta-glucan", "beta-glucans", "fermentation", "probiotic",
        "prebiotic", "solubles", "extract", "concentrate", "isolate", "protein", "gluten",
        // Binders, Gums, Flavors, Colors & Preservatives
        "water", "flavor", "flavour", "flavors", "flavoring", "flavouring", "smoke",
        "caramel", "titanium", "dioxide", "annatto", "paprika", "chlorophyll", "gum",
        "xanthan", "acacia", "alginate", "dextrin", "maltodextrin", "dextrose", "glucose",
        "fructose", "sucrose", "molasses", "honey", "syrup", "sorbitol", "citric", "lactic",
        "phosphoric", "malic", "tartaric", "acetic", "sorbic", "propionic", "benzoic",
        "fumaric", "acid", "acids", "bha", "bht", "ethoxyquin", "gallate", "preservative",
        "preserved", "antioxidant", "emulsifier", "stabilizer", "thickener", "palatant",
        "flakes", "powder"
    )

    // Words that can be fuzzy-healed when OCR introduces 1-2 character typos
    private val FUZZY_HEALABLE_INGREDIENT_DICTIONARY = listOf(
        "Chicken", "Turkey", "Salmon", "Herring", "Menhaden", "Whitefish", "Mackerel",
        "Sardine", "Anchovy", "Venison", "Buffalo", "Poultry", "Deboned", "Dehydrated",
        "Hydrolyzed", "Oatmeal", "Sorghum", "Barley", "Tapioca", "Cassava", "Potatoes",
        "Cellulose", "Chicory", "Inulin", "Psyllium", "Lentils", "Chickpeas", "Garbanzo",
        "Soybean", "Alfalfa", "Flaxseed", "Linseed", "Sunflower", "Safflower", "Canola",
        "Coconut", "Lecithin", "Glycerin", "Carrots", "Blueberries", "Cranberries",
        "Raspberries", "Apples", "Pumpkin", "Spinach", "Broccoli", "Parsley", "Rosemary",
        "Turmeric", "Chamomile", "Marigold", "Seaweed", "Spirulina", "Carrageenan",
        "Vitamin", "Vitamins", "Supplement", "Thiamine", "Mononitrate", "Hydrochloride",
        "Riboflavin", "Niacin", "Pantothenate", "Pyridoxine", "Biotin", "Folic",
        "Cobalamin", "Choline", "Ascorbic", "Cholecalciferol", "Tocopherol", "Tocopherols",
        "Menadione", "Taurine", "Carnitine", "Methionine", "Lysine", "Threonine",
        "Tryptophan", "Arginine", "Glucosamine", "Chondroitin", "Minerals", "Calcium",
        "Phosphorus", "Phosphate", "Dicalcium", "Tricalcium", "Carbonate", "Potassium",
        "Sodium", "Chloride", "Magnesium", "Ferrous", "Copper", "Manganese", "Manganous",
        "Iodate", "Iodide", "Dihydriodide", "Selenium", "Selenite", "Sulfate", "Sulphate",
        "Proteinate", "Chelate", "Gluconate", "Citrate", "Lactobacillus", "Acidophilus",
        "Enterococcus", "Faecium", "Bifidobacterium", "Bacillus", "Subtilis", "Aspergillus",
        "Fermentation", "Extract", "Concentrate", "Isolate", "Protein", "Natural", "Flavor"
    )

    // Chemical/nutritional suffixes that positively identify vitamin/mineral/additive compounds
    private val INGREDIENT_MORPHOLOGY_REGEX = Regex(
        """\b(?:[a-z]{4,}(?:ate|ine|ide|ose|ol|in|ic|ous|ase|an|en|ite)|l-[a-z]{4,}|dl-[a-z]{4,}|d-[a-z]{4,}|vitamin\s+[a-z0-9]+|omega[\s-]*[36])\b""",
        RegexOption.IGNORE_CASE
    )

    // Words that are incomplete prefixes when standing alone and should join with the following word
    private val STANDALONE_INGREDIENT_PREFIXES = setOf(
        "deboned", "boneless", "dried", "dehydrated", "hydrolyzed", "ground", "whole",
        "wholegrain", "whole-grain", "cracked", "pearled", "split", "malted", "sun-cured",
        "suncured", "fresh", "raw", "natural", "organic", "brewers", "brewer's", "distillers",
        "menhaden", "sweet", "brown", "white", "yellow", "green", "red", "black", "navy",
        "garbanzo", "pinto", "plain", "dried plain", "dried beet", "mixed", "preserved",
        "preserved with", "vitamin", "provitamin", "vitamin a", "vitamin b1", "vitamin b2",
        "vitamin b3", "vitamin b5", "vitamin b6", "vitamin b7", "vitamin b9", "vitamin b12",
        "vitamin c", "vitamin d", "vitamin d3", "vitamin e", "vitamin k", "d-calcium",
        "dl-alpha", "l-ascorbyl-2-polyphosphate",
        "choline", "folic", "pantothenic", "ascorbic", "citric", "lactic", "pyridoxine",
        "thiamine", "riboflavin"
    )

    // Modifiers that are NOT valid ingredients if stranded alone without a noun (e.g., cut off at frame edge)
    private val STRANDED_MODIFIER_ONLY_WORDS = setOf(
        "deboned", "boneless", "dried", "dehydrated", "hydrolyzed", "ground", "whole",
        "wholegrain", "whole-grain", "cracked", "pearled", "split", "malted", "sun-cured",
        "suncured", "fresh", "raw", "natural", "organic", "sweet", "brown", "white",
        "yellow", "green", "red", "black", "plain", "mixed", "preserved", "preserved with",
        "sufficient", "for", "processing", "and", "with", "from", "source", "of", "added",
        "color", "colour", "artificial"
    )

    // Words that are incomplete suffixes/modifiers when standing alone and belong to the previous word
    private val STANDALONE_INGREDIENT_SUFFIXES = setOf(
        "meal", "by-product", "by-products", "by-product meal", "by-products meal",
        "oil", "fat", "broth", "stock", "gravy", "liver", "heart", "kidney", "giblets",
        "tripe", "bone", "cartilage", "marrow", "digest", "flavor", "flavour",
        "natural flavor", "extract", "concentrate", "isolate", "protein", "protein isolate",
        "protein concentrate", "gluten", "gluten meal", "pulp", "beet pulp", "pomace",
        "bran", "germ", "hull", "hulls", "flour", "starch", "grits", "flakes", "powder",
        "fiber", "fibre", "root", "seed", "seeds", "yeast", "dried yeast", "solubles",
        "fermentation product", "fermentation extract", "supplement", "acetate", "palmitate",
        "mononitrate", "hydrochloride", "pantothenate", "nicotinamide", "cobalamin", "acid",
        "chloride", "carbonate", "sulfate", "sulphate", "oxide", "proteinate", "chelate",
        "amino acid chelate", "amino acid complex", "complex", "selenite", "iodate",
        "iodide", "dihydriodide", "gluconate", "citrate", "lactate", "propionate",
        "sorbate", "tocopherols", "preservative", "rice", "potato", "potatoes", "peas",
        "lentils", "beans", "chickpeas", "barley", "oats", "oatmeal", "sorghum", "millet"
    )

    // Nouns that only join with a following noun like "rice"/"peas"/"potato" when the first is a grain/color modifier
    private val NOUN_PAIR_FIRST_FOR_RICE_PEAS_POTATO = setOf(
        "brown", "white", "brewers", "brewer's", "ground", "whole", "wild", "jasmine",
        "basmati", "sweet", "dried", "mashed", "russet", "green", "yellow", "split",
        "field", "red", "black", "chick", "garbanzo", "pinto", "navy", "kidney",
        "pearled", "cracked", "rolled", "steel-cut", "wholegrain", "whole-grain"
    )

    /**
     * Distinguishes valid EU/UK composition percentages (e.g., "Chicken (26%)", "Fresh Salmon 18%")
     * from Guaranteed Analysis rows (e.g., "Crude Protein (min) 26%", "Moisture 10%").
     */
    private fun isCompositionPercentageIngredient(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        if (!lower.contains('%')) return false
        // Reject if it contains Guaranteed Analysis / multilingual nutrient row markers or key:value colons
        if (lower.contains(':') ||
            Regex("""\b(?:crude|guaranteed|analytical|constituents|moisture|inorganic\s+matter|ash|metabolizable|calorie|kcal|min\.?|max\.?|brute|brutes|bruta|bruto|rohprotein|rohfett|rohfaser|rohasche|feuchtigkeit|umidit[aà]|humedad|humidit[eé]|umidade|ceniza|cendres|ceneri|cinza|ruwe|vocht|bia[lł]ko|t[lł]uszcz|w[lł][oó]kno|popi[oó][lł]|wilgotno[sś][cć])\b""").containsMatchIn(lower)
        ) {
            return false
        }
        // Strip the percentage annotation e.g. "(26%)" or "26.5%" and check if the base name is a known food ingredient
        val withoutPct = lower
            .replace(Regex("""\(\s*\d+(?:[.,]\d+)?\s*%\s*\)"""), "")
            .replace(Regex("""\b\d+(?:[.,]\d+)?\s*%"""), "")
            .trim(' ', ':', '-', '.', ',', ';')
        if (withoutPct.length < 3) return false
        // Must not be a standalone nutrient name like "protein 26%" or "fat 14%" or "fiber 4%" or "calcium 1.2%"
        if (withoutPct in setOf("protein", "proteins", "fat", "fats", "oils & fats", "oils and fats", "fiber", "fibre", "fibres", "fibers", "cellulose", "calcium", "phosphorus", "sodium", "taurine", "ash", "moisture", "water")) {
            return false
        }
        if (NUTRIENT_LINE_KEYWORD_REGEX.containsMatchIn(withoutPct) &&
            !Regex("""\b(?:chicken|poultry|turkey|duck|beef|lamb|pork|venison|salmon|tuna|fish|whitefish|herring|trout|cod|sardine|mackerel|shrimp|egg|rice|corn|maize|wheat|barley|oat|oats|pea|peas|lentil|lentils|chickpea|potato|carrot|pumpkin|apple|berry|beet|chicory|flaxseed|linseed|sunflower|animal|vegetable|plant)\b""").containsMatchIn(withoutPct)
        ) {
            return false
        }
        return containsKnownIngredientSignal(withoutPct)
    }

    /**
     * Checks whether a text line or phrase contains at least one recognized pet-food ingredient
     * word, prefix, suffix, or fuzzy-matched ingredient token.
     */
    private fun containsKnownIngredientSignal(text: String): Boolean {
        val normalized = applyOcrReplacementsToText(text).lowercase(Locale.ROOT)
        if (normalized in STANDALONE_INGREDIENT_PREFIXES || normalized in STANDALONE_INGREDIENT_SUFFIXES) {
            return true
        }
        val words = Regex("""[a-z][a-z0-9'-]{1,}""")
            .findAll(normalized)
            .map { it.value.trim('\'', '-') }
            .filter { it.length >= 2 }
            .toList()
        if (words.isEmpty()) return false
        return words.any { w ->
            w in KNOWN_INGREDIENT_WORDS ||
                w in STANDALONE_INGREDIENT_PREFIXES ||
                w in STANDALONE_INGREDIENT_SUFFIXES ||
                findFuzzyIngredientMatch(w) != null
        }
    }

    /**
     * Strict gate for lines when NO "Ingredients:" or "Composition:" header was seen in the frame.
     * Prevents marketing claims, addresses, storage notes, or OCR noise from being classified as ingredients.
     */
    private fun isStrictHeaderlessIngredientCandidate(line: String): Boolean {
        if (FEEDING_OR_PACKAGING_NOISE_REGEX.containsMatchIn(line) ||
            INGREDIENT_STOP_SECTION_REGEX.containsMatchIn(line)
        ) {
            return false
        }
        if (line.contains('%') && !isCompositionPercentageIngredient(line)) {
            return false
        }
        val parts = splitIngredientsPreservingParentheses(line)
            .map { it.trim().trim(',', '.', ':', ';') }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false

        // If comma-separated, require at least half of the clauses to carry known ingredient signals
        if (parts.size >= 2) {
            val validCount = parts.count { part ->
                !NON_INGREDIENT_ITEM_REGEX.matches(part) && containsKnownIngredientSignal(part)
            }
            return validCount >= 1 && validCount * 2 >= parts.size
        }

        // Single clause without commas: only accept if it's short (<= 5 words) and almost every content word is an ingredient word
        val words = Regex("""[a-zA-Z][a-zA-Z0-9'-]*""").findAll(line).map { it.value.lowercase(Locale.ROOT) }.toList()
        if (words.isEmpty() || words.size > 5) return false
        val stopWords = setOf("with", "and", "of", "in", "for", "from", "by", "to", "a", "an", "the")
        val contentWords = words.filter { it !in stopWords }
        if (contentWords.isEmpty()) return false
        val matchedCount = contentWords.count { w ->
            w in KNOWN_INGREDIENT_WORDS ||
                w in STANDALONE_INGREDIENT_PREFIXES ||
                w in STANDALONE_INGREDIENT_SUFFIXES ||
                INGREDIENT_MORPHOLOGY_REGEX.matches(w) ||
                findFuzzyIngredientMatch(w) != null
        }
        return matchedCount == contentWords.size
    }

    private fun applyOcrReplacementsToText(raw: String): String {
        var text = raw
        for ((pattern, replacement) in INGREDIENT_OCR_REPLACEMENTS) {
            if (pattern.containsMatchIn(text)) {
                text = pattern.replace(text, replacement)
            }
        }
        return text
    }

    fun autocorrectIngredientsLocally(raw: String): ScannedIngredientsResult {
        val sectionSplit = classifyAndSplitSections(raw)
        if (sectionSplit.ingredientSectionText.isBlank()) {
            if (sectionSplit.nutrientSectionText.isNotBlank()) {
                val fallbackNutrients = parseNutrientsLocally(sectionSplit.nutrientSectionText)
                if (fallbackNutrients.nutrition.isNotEmpty() || fallbackNutrients.metabolizableEnergy != null) {
                    return ScannedIngredientsResult(
                        ingredientsText = "",
                        detectedNutrientsInstead = fallbackNutrients,
                        wrongSectionDetected = "NUTRIENTS"
                    )
                }
            }
            return ScannedIngredientsResult()
        }

        var text = normalizeIngredientLineWraps(sectionSplit.ingredientSectionText)
        var corrections = 0
        for ((pattern, replacement) in INGREDIENT_OCR_REPLACEMENTS) {
            if (pattern.containsMatchIn(text)) {
                text = pattern.replace(text, replacement)
                corrections++
            }
        }

        val (cleaned, fuzzyFixes) = sanitizeIngredientsStringWithCount(text)
        corrections += fuzzyFixes
        return ScannedIngredientsResult(
            ingredientsText = cleaned,
            correctionsAppliedCount = corrections,
            usedAi = false
        )
    }

    /**
     * Joins wrapped lines with spaces (instead of turning line breaks into commas)
     * and un-glues camelCase / stuck words from OCR.
     */
    private fun normalizeIngredientLineWraps(raw: String): String {
        val strippedHeader = raw.trim()
            .replace(Regex("""^\s*(?:ingredients(?:\s+list)?|composition|ingr[eé]dients|zutaten|ingredientes)\s*:?\s*""", RegexOption.IGNORE_CASE), "")
            // Join hyphenated line breaks seamlessly: "Sup-\nplement" -> "Supplement"
            .replace(Regex("""-\s*\r?\n\s*"""), "")

        val hasExplicitDelimiters = strippedHeader.any { it == ',' || it == ';' || it == '•' || it == '·' }
        val lineJoined = if (hasExplicitDelimiters) {
            // Standard paragraph: commas separate ingredients, newlines are just word wraps!
            strippedHeader.replace(Regex("""\s*\r?\n\s*"""), " ")
        } else {
            // No commas at all: treat newlines as candidate separators, which rejoinSplitCompoundIngredients
            // will stitch back together if a multi-word ingredient was wrapped across lines.
            strippedHeader.replace(Regex("""\s*\r?\n\s*"""), ", ")
        }

        return lineJoined
            .replace(Regex("""[|•·]+"""), ", ")
            // Un-glue camelCase words like "ChickenMeal" -> "Chicken Meal" (only after >=2 lowercase letters so OCR typos like "MeaI" aren't split)
            .replace(Regex("""([a-z]{2,})([A-Z][a-z])"""), "$1 $2")
    }

    /**
     * Splits ingredient text on commas and semicolons ONLY at top-level (parenthesis depth 0)
     * so parenthetical sub-ingredients like "Chicken Fat (Preserved with Mixed Tocopherols, Citric Acid)"
     * or "Vitamins (Vitamin E Supplement, Niacin Supplement)" stay intact instead of breaking into
     * unbalanced fragments.
     */
    fun splitIngredientsPreservingParentheses(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0

        for (ch in text) {
            when (ch) {
                '(', '[' -> {
                    parenDepth++
                    current.append(ch)
                }
                ')', ']' -> {
                    if (parenDepth > 0) {
                        parenDepth--
                        current.append(ch)
                    }
                }
                ',', ';' -> {
                    if (parenDepth == 0) {
                        val token = current.toString().trim()
                        if (token.isNotEmpty()) result.add(token)
                        current.setLength(0)
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) {
            // If an opening parenthesis was never closed due to frame edge, balance or clean it
            val balanced = balanceParentheses(last)
            if (balanced.isNotEmpty()) result.add(balanced)
        }
        return result
    }

    private fun balanceParentheses(token: String): String {
        var openCount = 0
        val sb = StringBuilder(token.length + 2)
        for (ch in token) {
            when (ch) {
                '(' -> {
                    openCount++
                    sb.append(ch)
                }
                ')' -> {
                    if (openCount > 0) {
                        openCount--
                        sb.append(ch)
                    }
                }
                else -> sb.append(ch)
            }
        }
        while (openCount > 0) {
            // If nothing meaningful is after '(', strip the trailing '('; otherwise close it
            val lastOpenIdx = sb.lastIndexOf("(")
            if (lastOpenIdx >= 0 && sb.substring(lastOpenIdx + 1).isBlank()) {
                sb.delete(lastOpenIdx, sb.length)
            } else {
                sb.append(')')
            }
            openCount--
        }
        return sb.toString().trim()
    }

    /**
     * Filters user ingredient text input in real-time by stripping emojis, special letters,
     * and non-standard symbols. Only allows standard English letters, digits, spaces,
     * newlines, and essential ingredient punctuation (commas, periods, hyphens, colons,
     * semicolons, percent, ampersand, slash, parentheses).
     */
    fun filterIngredientInputText(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' -> sb.append(ch)
                ch == ',' || ch == '.' || ch == '-' || ch == ':' || ch == ';' ||
                    ch == '%' || ch == '&' || ch == '/' || ch == '(' || ch == ')' ||
                    ch == ' ' || ch == '\n' || ch == '\r' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun sanitizeIngredientsString(raw: String): String {
        return sanitizeIngredientsStringWithCount(raw).first
    }

    private fun sanitizeIngredientsStringWithCount(raw: String): Pair<String, Int> {
        if (raw.isBlank()) return "" to 0
        val filteredInput = filterIngredientInputText(raw)
        val normalized = normalizeIngredientLineWraps(filteredInput)

        // Truncate at any stop-section boundary (AAFCO, storage, feeding guide, etc.)
        val stopMatch = INGREDIENT_STOP_SECTION_REGEX.find(normalized)
        val truncated = if (stopMatch != null) {
            normalized.substring(0, stopMatch.range.first).trim()
        } else {
            normalized
        }
        if (truncated.isBlank()) return "" to 0

        val initialParts = splitIngredientsPreservingParentheses(truncated)
            .map { part ->
                balanceParentheses(
                    part.trim()
                        .replace(Regex("""\s+"""), " ")
                        .trim(',', '.', ':', ';', ' ')
                )
            }
            .filter { part ->
                part.length >= 2 &&
                    part.any { it.isLetter() } &&
                    (!NON_INGREDIENT_ITEM_REGEX.matches(part) || isCompositionPercentageIngredient(part)) &&
                    !FEEDING_OR_PACKAGING_NOISE_REGEX.containsMatchIn(part) &&
                    !INGREDIENT_STOP_SECTION_REGEX.containsMatchIn(part)
            }

        var fuzzyFixCount = 0
        val healedInitial = initialParts.map { part ->
            val (healed, fixes) = healIngredientWordsFuzzy(part)
            fuzzyFixCount += fixes
            healed
        }

        val rejoined = rejoinSplitCompoundIngredients(healedInitial)
        val strictlyValidated = rejoined.filter { item ->
            isStrictValidIngredientToken(item)
        }

        val finalJoined = strictlyValidated
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(", ")
        return finalJoined to fuzzyFixCount
    }

    /**
     * Extracts the list of strictly validated ingredients from a raw or comma-separated string.
     */
    fun extractValidatedIngredientsList(raw: String): List<String> {
        val sanitized = sanitizeIngredientsString(raw)
        if (sanitized.isBlank()) return emptyList()
        return splitIngredientsPreservingParentheses(sanitized)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Validates manual user input in EditScannedIngredientsDialog.
     * Returns null if valid, or a clear user-friendly error message if invalid.
     */
    fun validateManualIngredientInput(raw: String): String? {
        val filtered = filterIngredientInputText(raw).trim()
        if (filtered.isBlank()) return "Please enter an ingredient name."
        val parts = splitIngredientsPreservingParentheses(filtered)
            .map { it.trim().trim(',', '.', ':', ';') }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "Please enter a valid ingredient name."

        for (part in parts) {
            if (NON_INGREDIENT_ITEM_REGEX.matches(part) && !isCompositionPercentageIngredient(part)) {
                return "\"$part\" looks like a nutrient value, not an ingredient."
            }
            if (FEEDING_OR_PACKAGING_NOISE_REGEX.containsMatchIn(part) ||
                INGREDIENT_STOP_SECTION_REGEX.containsMatchIn(part)
            ) {
                return "\"$part\" looks like packaging or feeding instructions, not an ingredient."
            }
            val (healed, _) = healIngredientWordsFuzzy(applyOcrReplacementsToText(part))
            if (!isStrictValidIngredientToken(healed)) {
                return "\"$part\" is not recognized as a valid pet-food ingredient."
            }
        }
        return null
    }

    /**
     * Validates a Product Name or Brand Name in both Scan New Product and Community Workspace.
     * Rejects profanity, gibberish/keyboard mash, pure numbers/symbols, nutrient rows, and packaging noise.
     * Returns null if valid, or a user-friendly error message if invalid.
     */
    fun validateProductOrBrandName(raw: String, fieldLabel: String = "Name"): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "$fieldLabel is required."

        val mod = ContentModerationFilter.analyze(trimmed)
        if (mod.containsBadWords) {
            return "Inappropriate language detected in $fieldLabel (${mod.matchedWords.joinToString(", ")})."
        }

        val letterCount = trimmed.count { it.isLetter() }
        val digitCount = trimmed.count { it.isDigit() }
        if (letterCount < 2 || digitCount > letterCount) {
            return "$fieldLabel must contain valid words, not just numbers or symbols."
        }
        if (trimmed.length < 2 || trimmed.length > 100) {
            return "$fieldLabel must be between 2 and 100 characters."
        }

        if ((NON_INGREDIENT_ITEM_REGEX.matches(trimmed) && trimmed.contains('%')) ||
            (NUTRIENT_LINE_KEYWORD_REGEX.containsMatchIn(trimmed) && Regex("""\d+\s*%""").containsMatchIn(trimmed))
        ) {
            return "$fieldLabel looks like a nutrient value rather than a valid ${fieldLabel.lowercase(Locale.ROOT)}."
        }
        if (FEEDING_OR_PACKAGING_NOISE_REGEX.containsMatchIn(trimmed) ||
            INGREDIENT_STOP_SECTION_REGEX.containsMatchIn(trimmed)
        ) {
            return "$fieldLabel looks like packaging or feeding instructions."
        }

        val words = Regex("""[\p{L}]+""")
            .findAll(trimmed.lowercase(Locale.ROOT))
            .map { it.value }
            .filter { it.isNotEmpty() }
            .toList()
        if (words.isEmpty()) {
            return "Please enter a valid $fieldLabel."
        }

        val allowedNoVowelShortWords = setOf("dr", "st", "mr", "ms", "uk", "us", "rx", "xl", "xs", "sm", "md", "lg")
        val keyboardMashRegex = Regex("""(?:qwer|wert|erty|rtyu|asdf|sdfg|dfgh|fghj|ghjk|hjkl|zxcv|xcvb|cvbn|vbnm|xkqz|qzpt|bcdfgh|mnpqrst)""")
        val tripleRepeatRegex = Regex("""([a-z])\1\1""")
        val heavyConsonantClusterRegex = Regex("""[bcdfghjklmnpqrstvwxz]{5,}""")
        val fourConsonantClusterRegex = Regex("""[bcdfghjklmnpqrstvwxz]{4,}""")

        for (word in words) {
            if (tripleRepeatRegex.containsMatchIn(word)) {
                return "\"$word\" in $fieldLabel does not look like a real word."
            }
            if (keyboardMashRegex.containsMatchIn(word)) {
                return "\"$word\" in $fieldLabel looks like random characters."
            }
            val asciiLetters = word.filter { it in 'a'..'z' }
            if (asciiLetters.length >= 3 && asciiLetters !in allowedNoVowelShortWords) {
                val vowelCount = asciiLetters.count { it in "aeiouy" }
                if (vowelCount == 0) {
                    return "\"$word\" in $fieldLabel does not look like a valid word."
                }
                if (heavyConsonantClusterRegex.containsMatchIn(asciiLetters)) {
                    return "\"$word\" in $fieldLabel does not make sense."
                }
                if (asciiLetters !in KNOWN_INGREDIENT_WORDS &&
                    fourConsonantClusterRegex.containsMatchIn(asciiLetters) &&
                    vowelCount.toFloat() / asciiLetters.length < 0.22f
                ) {
                    return "\"$word\" in $fieldLabel does not look like a valid word."
                }
                if (asciiLetters.length >= 6 && vowelCount.toFloat() / asciiLetters.length < 0.16f) {
                    return "\"$word\" in $fieldLabel does not look like a valid word."
                }
            }
        }

        return null
    }

    /**
     * Strict positive validator for a single (rejoined) ingredient item.
     * Rejects:
     * - Stranded modifiers cut off at frame borders (e.g. standalone "Dried", "Deboned", "Preserved with")
     * - Non-ingredient packaging phrases, AAFCO statements, or nutrient rows
     * - OCR gibberish (abnormal vowel/consonant ratios or unknown random tokens)
     * Accepts:
     * - Items containing at least one known pet-food ingredient word, compound suffix, or valid chemical morphology.
     */
    fun isStrictValidIngredientToken(item: String): Boolean {
        val trimmed = item.trim().trim(',', '.', ':', ';', '-', ' ')
        if (trimmed.length < 3) return false

        val lower = trimmed.lowercase(Locale.ROOT)
        // Reject stranded prefix-only modifiers with no noun (e.g. cut off at frame edge)
        if (lower in STRANDED_MODIFIER_ONLY_WORDS) return false

        // Reject nutrient rows unless it's a valid composition percentage ingredient like "Chicken (26%)"
        if (NON_INGREDIENT_ITEM_REGEX.matches(trimmed) && !isCompositionPercentageIngredient(trimmed)) {
            return false
        }
        if (FEEDING_OR_PACKAGING_NOISE_REGEX.containsMatchIn(trimmed) ||
            INGREDIENT_STOP_SECTION_REGEX.containsMatchIn(trimmed)
        ) {
            return false
        }

        // Strip valid percentage suffix e.g. "(26%)" for letter/word checks
        val baseWithoutPct = lower
            .replace(Regex("""\(\s*\d+(?:[.,]\d+)?\s*%\s*\)"""), "")
            .replace(Regex("""\b\d+(?:[.,]\d+)?\s*%"""), "")
            .trim()
        if (baseWithoutPct.length < 3 || baseWithoutPct in STRANDED_MODIFIER_ONLY_WORDS) return false

        val letterCount = baseWithoutPct.count { it in 'a'..'z' }
        val digitCount = baseWithoutPct.count { it in '0'..'9' }
        if (letterCount < 3 || digitCount > letterCount) return false

        // Must have at least one vowel ('a', 'e', 'i', 'o', 'u', 'y') unless it's a known acronym like BHA, BHT, MSM, FOS, MOS, EPA, DHA
        val knownAcronyms = setOf("bha", "bht", "msm", "fos", "mos", "epa", "dha")
        val hasVowel = baseWithoutPct.any { it in "aeiouy" } || baseWithoutPct in knownAcronyms
        if (!hasVowel) return false

        val words = Regex("""[a-z][a-z0-9'-]*""")
            .findAll(baseWithoutPct)
            .map { it.value.trim('\'', '-') }
            .filter { it.isNotEmpty() }
            .toList()
        if (words.isEmpty() || words.size > 14) return false

        // Reject if any word looks like pure OCR consonant smash (>= 5 consecutive consonants and not in lexicon)
        val consonantSmashRegex = Regex("""[bcdfghjklmnpqrstvwxz]{5,}""")
        for (w in words) {
            if (w !in KNOWN_INGREDIENT_WORDS && consonantSmashRegex.containsMatchIn(w)) {
                return false
            }
        }

        // Positive check: at least one non-modifier word in the item must be a known pet-food ingredient noun,
        // chemical/nutritional compound suffix, or morphology match.
        val hasPositiveIngredientNoun = words.any { w ->
            (w in KNOWN_INGREDIENT_WORDS && w !in STRANDED_MODIFIER_ONLY_WORDS) ||
                (w in STANDALONE_INGREDIENT_SUFFIXES && w !in STRANDED_MODIFIER_ONLY_WORDS) ||
                INGREDIENT_MORPHOLOGY_REGEX.matches(w)
        } || INGREDIENT_MORPHOLOGY_REGEX.containsMatchIn(baseWithoutPct)

        return hasPositiveIngredientNoun
    }

    private fun healIngredientWordsFuzzy(token: String): Pair<String, Int> {
        var fixes = 0
        val healed = Regex("""\b[A-Za-z]{5,15}\b""").replace(token) { matchResult ->
            val word = matchResult.value
            val lower = word.lowercase(Locale.ROOT)
            if (lower in KNOWN_INGREDIENT_WORDS ||
                lower in STANDALONE_INGREDIENT_PREFIXES ||
                lower in STANDALONE_INGREDIENT_SUFFIXES
            ) {
                word
            } else {
                val fuzzy = findFuzzyIngredientMatch(lower)
                if (fuzzy != null) {
                    fixes++
                    fuzzy
                } else {
                    word
                }
            }
        }
        return healed to fixes
    }

    private fun findFuzzyIngredientMatch(lowerWord: String): String? {
        if (lowerWord.length < 5) return null
        val maxDist = if (lowerWord.length >= 9) 2 else 1
        var bestMatch: String? = null
        var bestDist = maxDist + 1

        for (candidate in FUZZY_HEALABLE_INGREDIENT_DICTIONARY) {
            val candLower = candidate.lowercase(Locale.ROOT)
            if (kotlin.math.abs(candLower.length - lowerWord.length) > maxDist) continue
            // Require first or last character to match to avoid false-positive word swaps
            if (candLower.first() != lowerWord.first() && candLower.last() != lowerWord.last()) continue
            val dist = boundedLevenshtein(lowerWord, candLower, maxDist)
            if (dist <= maxDist && dist < bestDist) {
                bestDist = dist
                bestMatch = candidate
                if (dist == 1) break
            }
        }
        return bestMatch
    }

    private fun boundedLevenshtein(a: String, b: String, maxDist: Int): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > maxDist) return maxDist + 1
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            var rowMin = curr[0]
            val ca = a[i - 1]
            for (j in 1..m) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > maxDist) return maxDist + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }

    /**
     * Rejoins multi-word pet-food ingredients that were accidentally separated by OCR
     * commas or line wraps (e.g., ["Chicken", "Meal"] -> ["Chicken Meal"],
     * ["Brown", "Rice"] -> ["Brown Rice"], ["Dried", "Beet", "Pulp"] -> ["Dried Beet Pulp"],
     * ["Vitamin E", "Supplement"] -> ["Vitamin E Supplement"]).
     */
    fun rejoinSplitCompoundIngredients(items: List<String>): List<String> {
        if (items.size <= 1) return items
        val result = mutableListOf<String>()
        var i = 0
        while (i < items.size) {
            var current = items[i].trim()
            while (i + 1 < items.size) {
                val next = items[i + 1].trim()
                if (shouldMergeIngredientPair(current, next)) {
                    current = "$current $next".replace(Regex("""\s+"""), " ")
                    i++
                } else {
                    break
                }
            }
            result.add(current)
            i++
        }
        return result
    }

    private fun shouldMergeIngredientPair(prev: String, next: String): Boolean {
        val prevLower = prev.lowercase(Locale.ROOT).trim()
        val nextLower = next.lowercase(Locale.ROOT).trim()
        if (prevLower.isEmpty() || nextLower.isEmpty()) return false
        if (prevLower.endsWith(")")) return false

        val prevLastWord = prevLower.substringAfterLast(' ')
        val nextFirstWord = nextLower.substringBefore(' ')

        // 1. If prev is a standalone modifier/prefix (e.g. "Deboned", "Dried", "Brown", "Vitamin E", "Calcium")
        if (prevLower in STANDALONE_INGREDIENT_PREFIXES || prevLastWord in STANDALONE_INGREDIENT_PREFIXES) {
            return true
        }

        // 2. For grain/legume nouns ("rice", "peas", "potato", "lentils", etc.), only merge if prev is a grain/color modifier
        val grainNouns = setOf(
            "rice", "potato", "potatoes", "peas", "lentils", "beans",
            "chickpeas", "barley", "oats", "oatmeal", "sorghum", "millet"
        )
        if (nextLower in grainNouns || nextFirstWord in grainNouns) {
            return prevLower in NOUN_PAIR_FIRST_FOR_RICE_PEAS_POTATO ||
                prevLastWord in NOUN_PAIR_FIRST_FOR_RICE_PEAS_POTATO
        }

        // 3. If next is a standalone ingredient suffix/head noun (e.g. "Meal", "Oil", "Fat", "Pulp", "Supplement", "Carbonate", "Sulfate")
        if (nextLower in STANDALONE_INGREDIENT_SUFFIXES) {
            return true
        }

        // 4. Multi-word suffix starts with a connector (e.g. "By-Product Meal", "Gluten Meal", "Amino Acid Chelate")
        if (nextLower.startsWith("by-product") ||
            nextLower.startsWith("gluten ") ||
            nextLower.startsWith("protein ") ||
            nextLower.startsWith("amino acid") ||
            nextLower.startsWith("fermentation ") ||
            nextLower.endsWith(" supplement") && prevLower.startsWith("vitamin")
        ) {
            return true
        }

        return false
    }

    // -------------------------------------------------------------------------
    // Gemini JSON Helpers
    // -------------------------------------------------------------------------

    private fun downscaleBitmapForVision(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_VISION_BITMAP_DIM || maxSide <= 0) return bitmap
        val scale = MAX_VISION_BITMAP_DIM.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private suspend fun tryCallGeminiJson(
        model: GenerativeModel,
        prompt: String,
        bitmap: Bitmap? = null
    ): JsonObject? {
        val preparedBitmap = bitmap?.let(::downscaleBitmapForVision)
        repeat(2) { attempt ->
            try {
                val response = withTimeoutOrNull(GEMINI_TIMEOUT_MS) {
                    if (preparedBitmap != null) {
                        model.generateContent(
                            content {
                                image(preparedBitmap)
                                text(prompt)
                            }
                        )
                    } else {
                        model.generateContent(prompt)
                    }
                } ?: run {
                    Log.w(TAG, "Gemini AI call timed out after ${GEMINI_TIMEOUT_MS}ms (attempt ${attempt + 1})")
                    return@repeat
                }

                val rawText = response.text?.trim().orEmpty()
                if (rawText.isEmpty()) return@repeat
                val jsonBody = rawText
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                val start = jsonBody.indexOf('{')
                val end = jsonBody.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    if (preparedBitmap != null && preparedBitmap != bitmap) {
                        runCatching { preparedBitmap.recycle() }
                    }
                    return JsonParser.parseString(jsonBody.substring(start, end + 1)).asJsonObject
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini AI attempt ${attempt + 1} failed: ${e.message}")
            }
        }
        if (preparedBitmap != null && preparedBitmap != bitmap) {
            runCatching { preparedBitmap.recycle() }
        }
        return null
    }

    private fun buildNutrientsResultFromJson(json: JsonObject, usedAi: Boolean): ScannedNutrientsResult {
        val map = linkedMapOf<String, NutrientValue>()
        val lowConfidence = mutableSetOf<String>()

        fun readPct(key: String, displayName: String, maxAllowed: Double = 100.0) {
            val v = json.optDouble(key) ?: return
            val (sanitized, isLow) = applyDecimalSanityCheck(key, v, maxAllowed)
            if (sanitized != null && sanitized > 0.0 && sanitized <= maxAllowed) {
                map[key] = NutrientValue(name = displayName, value = sanitized, unit = "%")
                if (isLow) lowConfidence.add(key)
            }
        }

        readPct("crudeProtein", "Crude Protein")
        readPct("crudeFat", "Crude Fat")
        readPct("crudeFiber", "Crude Fiber")
        readPct("moisture", "Moisture")
        readPct("ash", "Ash")
        readPct("calcium", "Calcium", maxAllowed = 15.0)
        readPct("phosphorus", "Phosphorus", maxAllowed = 15.0)
        readPct("taurine", "Taurine", maxAllowed = 10.0)
        readPct("omega3", "Omega-3", maxAllowed = 15.0)
        readPct("omega6", "Omega-6", maxAllowed = 20.0)
        readPct("sodium", "Sodium", maxAllowed = 5.0)

        var meVal = json.optDouble("metabolizableEnergy")
        var meUnit = json.optString("metabolizableEnergyUnit").ifBlank { "kcal/kg" }
        if (meVal != null && meVal > 0.0) {
            if (meUnit.equals("kcal/100g", ignoreCase = true)) {
                meVal *= 10.0
                meUnit = "kcal/kg"
            }
        } else {
            meVal = null
            meUnit = ""
        }

        val lowArr = json.getAsJsonArray("lowConfidenceKeys")
        lowArr?.forEach { elem ->
            val k = runCatching { elem.asString }.getOrNull()
            if (!k.isNullOrBlank()) lowConfidence.add(k)
        }

        val summary = map.values.joinToString(" · ") { "${it.name}: ${it.value}${it.unit}" }
        return ScannedNutrientsResult(
            nutrition = map,
            metabolizableEnergy = meVal,
            metabolizableEnergyUnit = meUnit.ifBlank { null },
            lowConfidenceKeys = lowConfidence,
            correctedSummaryText = summary,
            usedAi = usedAi
        )
    }

    private fun JsonObject.optString(key: String): String {
        val el = get(key) ?: return ""
        if (el.isJsonNull) return ""
        return runCatching { el.asString.trim() }.getOrDefault("")
    }

    private fun JsonObject.optDouble(key: String): Double? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return runCatching { el.asDouble }.getOrNull()
    }

    private fun JsonObject.optInt(key: String): Int {
        val el = get(key) ?: return 0
        if (el.isJsonNull) return 0
        return runCatching { el.asInt }.getOrDefault(0)
    }
}
