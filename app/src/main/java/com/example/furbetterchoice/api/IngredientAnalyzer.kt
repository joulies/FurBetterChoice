package com.example.furbetterchoice.api

import java.util.Locale

data class IngredientExplanation(
    val name: String,
    val purpose: String,
    val benefits: String,
    val goodFor: String?,
    val risks: String,
    val suitability: String,
    val isBeneficial: Boolean,
    val category: String = "General",
    val allergenGroup: String? = null,
    val qualityTier: String? = null
)

data class IngredientQualitySummary(
    val totalCount: Int,
    val hasNamedAnimalProteinInTop3: Boolean,
    val namedAnimalProteinCount: Int,
    val unnamedByProductCount: Int,
    val syntheticAdditiveCount: Int,
    val commonAllergenGrainCount: Int,
    val allergenGroups: Set<String>
)

object IngredientAnalyzer {
    private val stopWords = setOf("and", "with", "of", "in", "for", "by", "from", "to", "or", "the", "a", "an", "&")

    private fun containsAnyWordOrPhrase(text: String, vararg phrases: String): Boolean {
        return phrases.any { phrase ->
            val escaped = Regex.escape(phrase.lowercase(Locale.ROOT))
            Regex("""(?<![a-z0-9])$escaped(?![a-z0-9])""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
    }

    private fun cleanIngredientName(raw: String): String {
        val cleanedWords = raw.replace("(", " ")
            .replace(")", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace("{", " ")
            .replace("}", " ")
            .replace("&", " ")
            .replace(",", " ")
            .replace(".", " ")
            .split(Regex("\\s+"))
            .filter { word -> word.lowercase(Locale.ROOT) !in stopWords && word.isNotBlank() }

        return cleanedWords.joinToString(" ").trim().replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    fun analyzeIngredients(ingredientsText: String?, species: String?): List<IngredientExplanation> {
        if (ingredientsText.isNullOrBlank()) {
            return listOf(
                IngredientExplanation(
                    name = "Balanced Formulation",
                    purpose = "Provides daily essential nutrients.",
                    benefits = "Supports overall energy and vitality.",
                    goodFor = "Good for overall health",
                    risks = "No specific risks; monitor pet tolerance.",
                    suitability = "Suitable for dogs and cats.",
                    isBeneficial = true,
                    category = "General"
                )
            )
        }

        val rawList = LabelAiCorrectionService.splitIngredientsPreservingParentheses(ingredientsText)
            .ifEmpty { ingredientsText.split(Regex("[,;\\n•-]")) }
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }

        val results = mutableListOf<IngredientExplanation>()
        val seen = mutableSetOf<String>()

        for (raw in rawList) {
            val cleaned = cleanIngredientName(raw)
            if (cleaned.length < 2 || seen.contains(cleaned.lowercase(Locale.ROOT))) continue
            seen.add(cleaned.lowercase(Locale.ROOT))

            results.add(getExplanationForIngredient(cleaned, species))
        }

        if (results.isEmpty()) {
            results.add(
                IngredientExplanation(
                    name = ingredientsText.take(50),
                    purpose = "Main recipe component.",
                    benefits = "Contributes to nutritional profile.",
                    goodFor = "Good for daily maintenance",
                    risks = "Check for individual sensitivities.",
                    suitability = "Generally suitable.",
                    isBeneficial = true,
                    category = "General"
                )
            )
        }

        return results
    }

    fun evaluateIngredientQualitySummary(ingredientsText: String?): IngredientQualitySummary {
        if (ingredientsText.isNullOrBlank()) {
            return IngredientQualitySummary(
                totalCount = 0,
                hasNamedAnimalProteinInTop3 = false,
                namedAnimalProteinCount = 0,
                unnamedByProductCount = 0,
                syntheticAdditiveCount = 0,
                commonAllergenGrainCount = 0,
                allergenGroups = emptySet()
            )
        }
        val analyzed = analyzeIngredients(ingredientsText, null)
        val top3 = analyzed.take(3)
        val hasNamedTop3 = top3.any {
            it.category in setOf("Animal Protein", "Marine Protein") &&
                it.qualityTier != "Unnamed / By-Product"
        }
        return IngredientQualitySummary(
            totalCount = analyzed.size,
            hasNamedAnimalProteinInTop3 = hasNamedTop3,
            namedAnimalProteinCount = analyzed.count {
                it.category in setOf("Animal Protein", "Marine Protein") &&
                    it.qualityTier != "Unnamed / By-Product"
            },
            unnamedByProductCount = analyzed.count { it.qualityTier == "Unnamed / By-Product" },
            syntheticAdditiveCount = analyzed.count { it.category == "Synthetic Additive" },
            commonAllergenGrainCount = analyzed.count { it.category == "Plant Filler / Allergen" },
            allergenGroups = analyzed.mapNotNull { it.allergenGroup }.toSet()
        )
    }

    private fun getExplanationForIngredient(name: String, species: String?): IngredientExplanation {
        val lower = name.lowercase(Locale.ROOT)
        val speciesNote = when {
            species?.contains("cat", ignoreCase = true) == true -> "cats"
            species?.contains("dog", ignoreCase = true) == true -> "dogs"
            else -> "dogs and cats"
        }

        return when {
            // 1. Controversial / Synthetic Additives (checked first so they are never shadowed)
            containsAnyWordOrPhrase(
                lower,
                "bha", "bht", "ethoxyquin", "propylene glycol", "sodium nitrite",
                "carrageenan", "caramel color", "artificial color", "artificial colors",
                "artificial flavor", "artificial flavors", "artificial preservative",
                "artificial preservatives", "red 40", "yellow 5", "yellow 6", "blue 2",
                "titanium dioxide", "menadione"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Synthetic preservation, coloring, or palatability additive.",
                benefits = "Extends shelf life or alters visual appearance.",
                goodFor = null,
                risks = "Controversial synthetic additive; linked to sensitivities or avoided in natural diets.",
                suitability = "Generally discouraged; natural tocopherols or whole-food options preferred.",
                isBeneficial = false,
                category = "Synthetic Additive",
                qualityTier = "Avoid / Synthetic"
            )

            // 2. Unnamed Animal By-Products / Generic Meals (checked before named meat/fish)
            containsAnyWordOrPhrase(
                lower,
                "meat and bone meal", "meat & bone meal", "meat meal", "bone meal",
                "animal digest", "poultry digest", "meat by-product", "meat by-products",
                "poultry by-product", "poultry by-products", "animal by-product",
                "animal fat", "poultry fat", "feather meal", "blood meal"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Rendered or hydrolyzed animal protein/fat blend.",
                benefits = "Supplies concentrated protein or energy at lower cost.",
                goodFor = null,
                risks = "Source species is unspecified, making quality and allergen tracking inconsistent.",
                suitability = "Acceptable in budget diets, but named species meats/meals are preferred.",
                isBeneficial = false,
                category = "By-Product / Unnamed",
                allergenGroup = if (lower.contains("poultry")) "Poultry" else null,
                qualityTier = "Unnamed / By-Product"
            )

            // 3. Plant / Grain Meals & Common Allergen Crops (checked BEFORE meat/fish meals!)
            containsAnyWordOrPhrase(
                lower,
                "corn", "corn gluten meal", "corn meal", "maize", "wheat",
                "wheat gluten", "wheat flour", "soy", "soybean", "soybean meal",
                "soy flour", "soy protein"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Plant-based energy, starch, and protein binder.",
                benefits = "Supplies carbohydrates and low-cost plant protein.",
                goodFor = "Good for quick energy",
                risks = "Common food allergen or filler that offers lower amino-acid completeness than meat.",
                suitability = "Use caution if your pet has grain, gluten, or soy sensitivities.",
                isBeneficial = false,
                category = "Plant Filler / Allergen",
                allergenGroup = if (containsAnyWordOrPhrase(lower, "soy", "soybean", "soybean meal", "soy flour", "soy protein")) "Soy" else "Grain",
                qualityTier = "Plant Filler"
            )

            // 4. Digestible Whole Grains & Starches
            containsAnyWordOrPhrase(
                lower,
                "oatmeal", "oats", "oat", "rolled oats", "barley", "pearl barley",
                "brown rice", "white rice", "brewers rice", "rice", "rice flour",
                "quinoa", "millet", "sorghum", "sweet potato", "sweet potatoes",
                "potato", "potatoes"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Digestible complex carbohydrate source.",
                benefits = "Provides steady energy, B-vitamins, and gentle dietary fiber.",
                goodFor = "Good for digestion and steady energy",
                risks = "Grain-sensitive pets may prefer potato or grain-free alternatives.",
                suitability = "Suitable for most $speciesNote unless grain-free is advised.",
                isBeneficial = true,
                category = "Digestible Carbohydrate",
                allergenGroup = if (containsAnyWordOrPhrase(lower, "oat", "oats", "oatmeal", "barley", "rice", "brown rice", "white rice", "brewers rice")) "Grain" else null,
                qualityTier = "Wholesome Carb"
            )

            // 5. Legumes & Dietary Fiber Sources
            containsAnyWordOrPhrase(
                lower,
                "peas", "pea", "green peas", "yellow peas", "pea protein", "pea fiber",
                "pea starch", "chickpeas", "lentils", "red lentils", "green lentils",
                "beet pulp", "dried beet pulp", "cellulose", "flaxseed", "flax",
                "pumpkin", "apple pomace", "cranberry", "blueberries", "carrots", "spinach"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Plant fiber, prebiotic support, or legume binder.",
                benefits = "Supports healthy stool quality, gut motility, and antioxidant intake.",
                goodFor = "Good for digestion and stool firmness",
                risks = "Heavy legume splitting (multiple pea/lentil fractions) may inflate label protein.",
                suitability = "Generally suitable for $speciesNote in balanced amounts.",
                isBeneficial = true,
                category = "Fiber / Produce",
                allergenGroup = if (containsAnyWordOrPhrase(lower, "peas", "pea", "pea protein", "chickpeas", "lentils")) "Legume" else null,
                qualityTier = "Functional Plant"
            )

            // 6. Named Marine Proteins & Fish Oils (explicit species/fish keywords only — no bare "meal")
            containsAnyWordOrPhrase(
                lower,
                "salmon", "tuna", "sardine", "sardines", "herring", "mackerel",
                "anchovy", "anchovies", "whitefish", "ocean fish", "trout", "cod",
                "pollock", "menhaden", "krill", "fish meal", "salmon meal",
                "menhaden fish meal", "whitefish meal", "herring meal", "fish oil",
                "salmon oil", "krill oil", "anchovy oil"
            ) -> {
                val isOil = containsAnyWordOrPhrase(lower, "fish oil", "salmon oil", "krill oil", "anchovy oil")
                IngredientExplanation(
                    name = name,
                    purpose = if (isOil) "Concentrated marine Omega-3 fatty acid source." else "Named marine animal protein and natural lipids.",
                    benefits = "Rich in EPA/DHA to nourish skin, coat, brain, and joint health.",
                    goodFor = "Good for skin, coat, brain, and joints",
                    risks = "May trigger sensitivities in fish-allergic pets.",
                    suitability = "Highly beneficial for $speciesNote.",
                    isBeneficial = true,
                    category = if (isOil) "Healthy Fat" else "Marine Protein",
                    allergenGroup = "Fish",
                    qualityTier = if (lower.contains("meal")) "Concentrated Meal" else "Whole / Oil"
                )
            }

            // 7. Named Poultry Proteins & Fats
            containsAnyWordOrPhrase(
                lower,
                "chicken", "deboned chicken", "chicken meal", "turkey", "deboned turkey",
                "turkey meal", "duck", "duck meal", "quail", "chicken fat", "turkey fat",
                "chicken liver", "chicken heart", "poultry", "egg", "dried egg", "egg product"
            ) -> {
                val isFat = containsAnyWordOrPhrase(lower, "chicken fat", "turkey fat")
                val isEgg = containsAnyWordOrPhrase(lower, "egg", "dried egg", "egg product")
                IngredientExplanation(
                    name = name,
                    purpose = when {
                        isFat -> "Highly palatable poultry fat rich in linoleic acid (Omega-6)."
                        isEgg -> "Highly bioavailable complete animal protein."
                        else -> "Named poultry animal protein source."
                    },
                    benefits = if (isFat) "Supports energy density, skin, and coat." else "Supports lean muscle maintenance and essential amino acids.",
                    goodFor = if (isFat) "Good for energy and coat" else "Good for muscles and vitality",
                    risks = if (isEgg) "Occasional sensitivity in egg-allergic pets." else "Poultry is a common trigger for food-sensitive pets.",
                    suitability = "Great for $speciesNote (avoid if poultry/egg allergic).",
                    isBeneficial = true,
                    category = if (isFat) "Healthy Fat" else "Animal Protein",
                    allergenGroup = if (isEgg) "Egg" else "Poultry",
                    qualityTier = if (lower.contains("meal")) "Concentrated Meal" else "Whole Meat"
                )
            }

            // 8. Named Red Meat / Novel Animal Proteins
            containsAnyWordOrPhrase(
                lower,
                "beef", "deboned beef", "beef meal", "beef liver", "lamb", "deboned lamb",
                "lamb meal", "venison", "bison", "pork", "pork meal", "rabbit", "kangaroo",
                "goat", "liver", "heart", "kidney"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Named terrestrial animal protein and organ nutrient source.",
                benefits = "Provides complete amino acids, iron, zinc, and B-vitamins.",
                goodFor = "Good for muscle development and iron",
                risks = "Beef and lamb can occasionally trigger food sensitivities.",
                suitability = "Suitable for $speciesNote (novel proteins help sensitive pets).",
                isBeneficial = true,
                category = "Animal Protein",
                allergenGroup = when {
                    lower.contains("beef") -> "Beef"
                    lower.contains("lamb") -> "Lamb"
                    lower.contains("pork") -> "Pork"
                    else -> null
                },
                qualityTier = if (lower.contains("meal")) "Concentrated Meal" else "Whole Meat"
            )

            // 9. Dairy Ingredients
            containsAnyWordOrPhrase(
                lower,
                "milk", "whey", "cheese", "yogurt", "casein", "goat milk"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Dairy-derived protein, calcium, or probiotic carrier.",
                benefits = "Provides calcium and palatable protein.",
                goodFor = "Good for palatability",
                risks = "Adult dogs and cats may be lactose intolerant.",
                suitability = "Monitor stool quality if pet is dairy-sensitive.",
                isBeneficial = true,
                category = "Dairy",
                allergenGroup = "Dairy",
                qualityTier = "Dairy"
            )

            // 10. Healthy Fats & Oils
            containsAnyWordOrPhrase(
                lower,
                "omega", "fatty acid", "dha", "epa", "sunflower oil", "coconut oil",
                "canola oil", "flaxseed oil", "safflower oil", "vegetable oil"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Essential dietary fatty acids.",
                benefits = "Promotes healthy skin barrier, shiny coat, and cognitive support.",
                goodFor = "Good for skin and coat",
                risks = "Excessive dietary fat can cause loose stools in sensitive pets.",
                suitability = "Recommended for $speciesNote.",
                isBeneficial = true,
                category = "Healthy Fat",
                qualityTier = "Healthy Fat"
            )

            // 11. Functional Amino Acids, Vitamins, Minerals, Prebiotics & Probiotics
            containsAnyWordOrPhrase(
                lower,
                "taurine", "l-carnitine", "dl-methionine", "l-lysine", "glucosamine",
                "chondroitin", "probiotic", "lactobacillus", "bacillus", "enterococcus",
                "chicory", "inulin", "fructooligosaccharide", "tocopherols", "mixed tocopherols",
                "vitamin", "mineral", "zinc", "iron", "calcium", "phosphorus", "potassium",
                "copper", "manganese", "selenium", "iodine", "biotin", "niacin", "riboflavin",
                "thiamine", "folic acid", "choline", "chelate", "proteinate", "yucca", "rosemary extract"
            ) -> IngredientExplanation(
                name = name,
                purpose = "Essential micronutrient, amino acid, joint, or digestive supplement.",
                benefits = when {
                    lower.contains("taurine") -> "Supports heart muscle function, vision, and bile acid synthesis."
                    containsAnyWordOrPhrase(lower, "glucosamine", "chondroitin") -> "Supports joint cartilage and mobility."
                    containsAnyWordOrPhrase(lower, "probiotic", "lactobacillus", "bacillus", "enterococcus", "chicory", "inulin", "fructooligosaccharide") -> "Supports gut microbiome balance and digestion."
                    containsAnyWordOrPhrase(lower, "tocopherols", "mixed tocopherols", "rosemary extract") -> "Natural antioxidant preservative keeping fats fresh."
                    else -> "Completes essential vitamin and mineral balance for daily health."
                },
                goodFor = when {
                    lower.contains("taurine") -> "Good for the heart and eyes"
                    containsAnyWordOrPhrase(lower, "glucosamine", "chondroitin") -> "Good for joints"
                    else -> "Good for immune system and metabolic balance"
                },
                risks = "Safe when balanced within formulated diet levels.",
                suitability = if (lower.contains("taurine")) "Essential for cats and beneficial for dogs." else "Essential for $speciesNote.",
                isBeneficial = true,
                category = "Functional Supplement",
                qualityTier = "Functional"
            )

            else -> IngredientExplanation(
                name = name,
                purpose = "Supporting formulation or palatability ingredient.",
                benefits = "Contributes to recipe balance and texture.",
                goodFor = "Supports recipe balance",
                risks = "Monitor individual pet tolerance.",
                suitability = "Generally suitable for $speciesNote.",
                isBeneficial = true,
                category = "General"
            )
        }
    }
}

