package com.example.furbetterchoice

import com.example.furbetterchoice.api.LabelAiCorrectionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelAiCorrectionServiceTest {

    @Test
    fun consensusMergesNoisyFramesByPreferringFrequentCleanLines() {
        val frames = listOf(
            "Crvde Protein 26%\nCrude Fat 14%",
            "Crude Protein 26.5%\nCrude Fat 14.0%\nMoisture 10.0%",
            "Crude Protein 26.5%\nCrude Fat 14.0%\nMoisture 10.0%"
        )

        val merged = LabelAiCorrectionService.mergeConsensusFrames(frames)
        assertTrue(merged.contains("Crude Protein 26.5%"))
        assertTrue(merged.contains("Moisture 10.0%"))
    }

    @Test
    fun localNutrientParserFixesOcrTyposAndStoresStrictNumbers() {
        val messyOcr = """
            Guaranteed Analysis
            Crvde Pr0tein (min) .... 28.O%
            Crude F@t (min) ........ 15.5%
            Crude Flb3r (max) ...... 4.0%
            M01sture (max) ......... 1O.0%
            Calc1um (min) .......... 1.2%
            Ph0sphorus (min) ....... 0.9%
            3750 kcal/kg
        """.trimIndent()

        val result = LabelAiCorrectionService.parseNutrientsLocally(messyOcr)

        assertEquals(28.0, result.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(15.5, result.nutrition["crudeFat"]?.value ?: 0.0, 0.01)
        assertEquals(4.0, result.nutrition["crudeFiber"]?.value ?: 0.0, 0.01)
        assertEquals(10.0, result.nutrition["moisture"]?.value ?: 0.0, 0.01)
        assertEquals(1.2, result.nutrition["calcium"]?.value ?: 0.0, 0.01)
        assertEquals(0.9, result.nutrition["phosphorus"]?.value ?: 0.0, 0.01)
        assertEquals(3750.0, result.metabolizableEnergy ?: 0.0, 0.01)
        assertEquals("kcal/kg", result.metabolizableEnergyUnit)
    }

    @Test
    fun decimalSanityCheckFixesMissingDecimalPointAndFlagsLowConfidence() {
        val messyCalcium = "Calcium 125%"
        val result = LabelAiCorrectionService.parseNutrientsLocally(messyCalcium)

        val calcium = result.nutrition["calcium"]
        assertNotNull(calcium)
        assertEquals(1.25, calcium?.value ?: 0.0, 0.001)
        assertTrue(result.lowConfidenceKeys.contains("calcium"))
    }

    @Test
    fun localIngredientAutocorrectFixesCommonCameraMisspellings() {
        val messy = "Ingredients: Chlcken MeaI, Br0wn Rlce, Salm0n O1l, Taur1ne"
        val res = LabelAiCorrectionService.autocorrectIngredientsLocally(messy)

        assertFalse(res.ingredientsText.startsWith("Ingredients:", ignoreCase = true))
        assertTrue(res.ingredientsText.contains("Chicken Meal"))
        assertTrue(res.ingredientsText.contains("Brown Rice"))
        assertTrue(res.ingredientsText.contains("Salmon Oil"))
        assertTrue(res.ingredientsText.contains("Taurine"))
        assertTrue(res.correctionsAppliedCount >= 4)
    }

    @Test
    fun ingredientScannerStripsGuaranteedAnalysisAndFeedingGuideLines() {
        val mixedLabel = """
            Guaranteed Analysis
            Crude Protein (min) 28.0%
            Crude Fat (min) 15.0%
            Crude Fiber (max) 4.0%
            Moisture (max) 10.0%
            3750 kcal/kg
            Ingredients: Chicken Meal, Brown Rice, Chicken Fat, Taurine, Calcium Carbonate
            Feeding Guide: 2 cups/day for 20 lbs dog
        """.trimIndent()

        val split = LabelAiCorrectionService.classifyAndSplitSections(mixedLabel)
        assertTrue(split.nutrientSectionText.contains("Crude Protein (min) 28.0%"))
        assertFalse(split.ingredientSectionText.contains("Crude Protein"))
        assertFalse(split.ingredientSectionText.contains("3750 kcal/kg"))
        assertFalse(split.ingredientSectionText.contains("cups/day"))

        val ingRes = LabelAiCorrectionService.autocorrectIngredientsLocally(mixedLabel)
        assertTrue(ingRes.ingredientsText.contains("Chicken Meal"))
        assertTrue(ingRes.ingredientsText.contains("Calcium Carbonate"))
        assertFalse(ingRes.ingredientsText.contains("Crude Protein"))
        assertFalse(ingRes.ingredientsText.contains("28.0%"))
    }

    @Test
    fun ingredientScannerDetectsPureGuaranteedAnalysisAsWrongSectionAndExtractsNutrients() {
        val pureNutrients = """
            Guaranteed Analysis
            Crude Protein min 30.0%
            Crude Fat min 18.0%
            Moisture max 10.0%
        """.trimIndent()

        val ingRes = LabelAiCorrectionService.autocorrectIngredientsLocally(pureNutrients)
        assertEquals("", ingRes.ingredientsText)
        assertEquals("NUTRIENTS", ingRes.wrongSectionDetected)
        assertNotNull(ingRes.detectedNutrientsInstead)
        assertEquals(30.0, ingRes.detectedNutrientsInstead?.nutrition?.get("crudeProtein")?.value ?: 0.0, 0.01)
    }

    @Test
    fun nutrientScannerDetectsPureIngredientsAsWrongSection() {
        val pureIngredients = "Ingredients: Deboned Chicken, Chicken Meal, Peas, Brown Rice, Salmon Oil"
        val nutRes = LabelAiCorrectionService.parseNutrientsLocally(pureIngredients)

        assertEquals("INGREDIENTS", nutRes.wrongSectionDetected)
        assertTrue(nutRes.nutrition.isEmpty())
    }

    @Test
    fun consensusMergesUniqueRowsAcrossFramesInsteadOfDroppingBottomRows() {
        val frames = listOf(
            "Crude Protein 26.0%\nCrude Fat 14.0%",
            "Crude Fat 14.0%\nCrude Fiber 4.0%\nMoisture 10.0%"
        )

        val merged = LabelAiCorrectionService.mergeConsensusFrames(frames)
        assertTrue(merged.contains("Crude Protein 26.0%"))
        assertTrue(merged.contains("Crude Fat 14.0%"))
        assertTrue(merged.contains("Crude Fiber 4.0%"))
        assertTrue(merged.contains("Moisture 10.0%"))
    }

    @Test
    fun localNutrientParserExtractsOmega3Omega6SodiumAndKcalPerCup() {
        val extendedLabel = """
            Guaranteed Analysis
            Crude Protein (min) 32.0%
            Omega-3 Fatty Acids (min) 0.85%
            Omega-6 Fatty Acids (min) 2.6%
            Sodium (max) 0.45%
            410 kcal/cup
        """.trimIndent()

        val res = LabelAiCorrectionService.parseNutrientsLocally(extendedLabel)
        assertEquals(32.0, res.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(0.85, res.nutrition["omega3"]?.value ?: 0.0, 0.01)
        assertEquals(2.6, res.nutrition["omega6"]?.value ?: 0.0, 0.01)
        assertEquals(0.45, res.nutrition["sodium"]?.value ?: 0.0, 0.01)
        assertEquals(410.0, res.metabolizableEnergy ?: 0.0, 0.01)
        assertEquals("kcal/cup", res.metabolizableEnergyUnit)
    }

    @Test
    fun ingredientScannerRejoinsWrappedLinesAndStrayCommaSplitCompoundWords() {
        val wrappedAndSplit = """
            Ingredients: Deboned
            Chicken, Chicken, Meal, Brown
            Rice, Dried, Beet, Pulp, Vitamin E, Supplement, Calcium, Carbonate, ChickenFat
        """.trimIndent()

        val res = LabelAiCorrectionService.autocorrectIngredientsLocally(wrappedAndSplit)
        assertTrue("Expected Deboned Chicken in '${res.ingredientsText}'", res.ingredientsText.contains("Deboned Chicken"))
        assertTrue("Expected Chicken Meal in '${res.ingredientsText}'", res.ingredientsText.contains("Chicken Meal"))
        assertTrue("Expected Brown Rice in '${res.ingredientsText}'", res.ingredientsText.contains("Brown Rice"))
        assertTrue("Expected Dried Beet Pulp in '${res.ingredientsText}'", res.ingredientsText.contains("Dried Beet Pulp"))
        assertTrue("Expected Vitamin E Supplement in '${res.ingredientsText}'", res.ingredientsText.contains("Vitamin E Supplement"))
        assertTrue("Expected Calcium Carbonate in '${res.ingredientsText}'", res.ingredientsText.contains("Calcium Carbonate"))
        assertTrue("Expected Chicken Fat in '${res.ingredientsText}'", res.ingredientsText.contains("Chicken Fat"))
        assertFalse("Should not have standalone ', Meal'", res.ingredientsText.contains(", Meal"))
        assertFalse("Should not have standalone ', Pulp'", res.ingredientsText.contains(", Pulp"))
    }

    @Test
    fun ingredientScannerPreservesCommaSeparatedIngredients() {
        val commaSeparated = "Ingredients: Chicken Meal, Brown Rice, Salmon Oil, Taurine, Calcium Carbonate"
        val res = LabelAiCorrectionService.autocorrectIngredientsLocally(commaSeparated)
        assertEquals("Chicken Meal, Brown Rice, Salmon Oil, Taurine, Calcium Carbonate", res.ingredientsText)
    }

    @Test
    fun filterIngredientInputTextStripsEmojisAndSpecialCharacters() {
        val inputWithEmojisAndSpecialSymbols = "Chicken Meal 🍗, Brown Rice 🍚 @ # $ % & (Omega-3)"
        val filtered = LabelAiCorrectionService.filterIngredientInputText(inputWithEmojisAndSpecialSymbols)
        assertEquals("Chicken Meal , Brown Rice     % & (Omega-3)", filtered)
    }

    @Test
    fun strictIngredientDetectionRejectsHeaderlessMarketingStorageAafcoAndGibberish() {
        val nonIngredientNoise = """
            100% Complete and Balanced Nutrition for Adult Dogs
            Formulated to meet the nutritional levels established by the AAFCO Dog Food Nutrient Profiles
            Store in a cool, dry place. Keep fresh water available at all times.
            Distributed by PetCo Foods Inc., St. Louis, MO 63102
            Best before 12/2027 Lot 9982XKQ
            xkqz plmnv 9812
        """.trimIndent()

        val split = LabelAiCorrectionService.classifyAndSplitSections(nonIngredientNoise)
        assertEquals("", split.ingredientSectionText)

        val res = LabelAiCorrectionService.autocorrectIngredientsLocally(nonIngredientNoise)
        assertEquals("", res.ingredientsText)
    }

    @Test
    fun strictIngredientDetectionPreservesParentheticalSubIngredientsAndEuPercentages() {
        val complexLabel = """
            Composition: Chicken (26%), Fresh Salmon 18%, Brown Rice, Chicken Fat (Preserved with Mixed Tocopherols, Citric Acid), Vitamins (Vitamin E Supplement, Niacin Supplement, Thiamine Mononitrate), Dried
        """.trimIndent()

        val res = LabelAiCorrectionService.autocorrectIngredientsLocally(complexLabel)
        val items = LabelAiCorrectionService.extractValidatedIngredientsList(res.ingredientsText)

        assertTrue("Expected Chicken (26%) in $items", items.any { it.equals("Chicken (26%)", ignoreCase = true) })
        assertTrue("Expected Fresh Salmon 18% in $items", items.any { it.equals("Fresh Salmon 18%", ignoreCase = true) })
        assertTrue(
            "Expected parenthetical sub-ingredients preserved intact in $items",
            items.any { it.contains("Chicken Fat (Preserved with Mixed Tocopherols, Citric Acid)", ignoreCase = true) }
        )
        assertTrue(
            "Expected grouped vitamins preserved intact in $items",
            items.any { it.contains("Vitamins (Vitamin E Supplement, Niacin Supplement, Thiamine Mononitrate)", ignoreCase = true) }
        )
        // Trailing stranded modifier "Dried" cut off at frame edge should be rejected
        assertFalse("Stranded trailing modifier 'Dried' should be rejected: $items", items.any { it.equals("Dried", ignoreCase = true) })
    }

    @Test
    fun strictIngredientDetectionTruncatesAtAafcoAndStorageBoundaryAndHealsFuzzyTypos() {
        val labelWithTrailingAafco = """
            Ingredients: Deboned Chlcken, Oatmeal, Blueberrles, Pyridoxlne Hydrochloride, Taurine. Formulated to meet the nutritional levels established by the AAFCO Dog Food Nutrient Profiles for maintenance. Store in a cool dry place.
        """.trimIndent()

        val res = LabelAiCorrectionService.autocorrectIngredientsLocally(labelWithTrailingAafco)
        assertTrue("Expected Deboned Chicken in '${res.ingredientsText}'", res.ingredientsText.contains("Deboned Chicken"))
        assertTrue("Expected Oatmeal in '${res.ingredientsText}'", res.ingredientsText.contains("Oatmeal"))
        assertTrue("Expected Blueberries in '${res.ingredientsText}'", res.ingredientsText.contains("Blueberries"))
        assertTrue("Expected Pyridoxine Hydrochloride in '${res.ingredientsText}'", res.ingredientsText.contains("Pyridoxine Hydrochloride"))
        assertFalse("Should not contain AAFCO text in '${res.ingredientsText}'", res.ingredientsText.contains("AAFCO", ignoreCase = true))
        assertFalse("Should not contain storage text in '${res.ingredientsText}'", res.ingredientsText.contains("cool", ignoreCase = true))
    }

    @Test
    fun manualIngredientInputValidatorAcceptsRealIngredientsAndRejectsNoise() {
        assertEquals(null, LabelAiCorrectionService.validateManualIngredientInput("Chicken Meal, Brown Rice"))
        assertEquals(null, LabelAiCorrectionService.validateManualIngredientInput("Chicken (26%)"))
        assertNotNull(LabelAiCorrectionService.validateManualIngredientInput("Crude Protein 26%"))
        assertNotNull(LabelAiCorrectionService.validateManualIngredientInput("Store in a cool dry place"))
        assertNotNull(LabelAiCorrectionService.validateManualIngredientInput("xkqzpt"))
    }

    @Test
    fun nutrientParserHandlesParagraphAndValueFirstFormats() {
        val euParagraph = """
            Analytical constituents: protein 28.5%, fat content 16%, crude ash 7.5%, crude fibres 2.5%, moisture 10%, calcium 1.2%, phosphorus 0.9%, omega-6 fatty acids 2.4%, omega-3 fatty acids 0.6%.
        """.trimIndent()

        val res = LabelAiCorrectionService.parseNutrientsLocally(euParagraph)
        assertEquals(28.5, res.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(16.0, res.nutrition["crudeFat"]?.value ?: 0.0, 0.01)
        assertEquals(7.5, res.nutrition["ash"]?.value ?: 0.0, 0.01)
        assertEquals(2.5, res.nutrition["crudeFiber"]?.value ?: 0.0, 0.01)
        assertEquals(10.0, res.nutrition["moisture"]?.value ?: 0.0, 0.01)
        assertEquals(1.2, res.nutrition["calcium"]?.value ?: 0.0, 0.01)
        assertEquals(0.9, res.nutrition["phosphorus"]?.value ?: 0.0, 0.01)
        assertEquals(2.4, res.nutrition["omega6"]?.value ?: 0.0, 0.01)
        assertEquals(0.6, res.nutrition["omega3"]?.value ?: 0.0, 0.01)

        val valueFirst = "Guaranteed Analysis: 30% Crude Protein, 18% Crude Fat, 3.5% Crude Fiber, 10% Moisture"
        val resValueFirst = LabelAiCorrectionService.parseNutrientsLocally(valueFirst)
        assertEquals(30.0, resValueFirst.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(18.0, resValueFirst.nutrition["crudeFat"]?.value ?: 0.0, 0.01)
        assertEquals(3.5, resValueFirst.nutrition["crudeFiber"]?.value ?: 0.0, 0.01)
        assertEquals(10.0, resValueFirst.nutrition["moisture"]?.value ?: 0.0, 0.01)
    }

    @Test
    fun nutrientParserHandlesMultilingualTableAndForeignOnlyLabels() {
        val multilingualTable = """
            Guaranteed Analysis / Analyse Garantie / Analytische Bestandteile / Análisis Garantizado
            Crude Protein / Protéines brutes / Rohprotein / Proteína bruta / Proteina grezza (min) ... 32.0%
            Crude Fat / Matières grasses brutes / Rohfett / Grasa bruta / Grassi grezzi (min) ........ 17.5%
            Crude Fiber / Cellulose brute / Rohfaser / Fibra bruta / Fibra grezza (max) .............. 3.2%
            Moisture / Humidité / Feuchtigkeit / Humedad / Umidità (max) ............................. 10.0%
            Omega-3 Fatty Acids / Acides gras Oméga-3 / Omega-3-Fettsäuren (min) ..................... 0.85%
        """.trimIndent()

        val multiRes = LabelAiCorrectionService.parseNutrientsLocally(multilingualTable)
        assertEquals(32.0, multiRes.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(17.5, multiRes.nutrition["crudeFat"]?.value ?: 0.0, 0.01)
        assertEquals(3.2, multiRes.nutrition["crudeFiber"]?.value ?: 0.0, 0.01)
        assertEquals(10.0, multiRes.nutrition["moisture"]?.value ?: 0.0, 0.01)
        assertEquals(0.85, multiRes.nutrition["omega3"]?.value ?: 0.0, 0.01)

        val frenchGermanSpanishOnly = """
            Constituants analytiques:
            Protéines brutes: 29,5%
            Matières grasses brutes: 15,0%
            Cellulose brute: 2,8%
            Cendres brutes: 7,2%
            Humidité: 9,5%
            Calcium: 1,3%
            Phosphore: 1,0%
        """.trimIndent()

        val foreignRes = LabelAiCorrectionService.parseNutrientsLocally(frenchGermanSpanishOnly)
        assertEquals(29.5, foreignRes.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(15.0, foreignRes.nutrition["crudeFat"]?.value ?: 0.0, 0.01)
        assertEquals(2.8, foreignRes.nutrition["crudeFiber"]?.value ?: 0.0, 0.01)
        assertEquals(7.2, foreignRes.nutrition["ash"]?.value ?: 0.0, 0.01)
        assertEquals(9.5, foreignRes.nutrition["moisture"]?.value ?: 0.0, 0.01)
        assertEquals(1.3, foreignRes.nutrition["calcium"]?.value ?: 0.0, 0.01)
        assertEquals(1.0, foreignRes.nutrition["phosphorus"]?.value ?: 0.0, 0.01)
    }

    @Test
    fun nutrientParserReconstructsSplitColumnTablesAndConvertsUnits() {
        val splitColumnsAndUnits = """
            Guaranteed Analysis
            Crude Protein (min)
            Crude Fat (min)
            Crude Fiber (max)
            Moisture (max)
            27.0%
            14.0%
            4.0%
            10.0%
            Calcium: 12 g/kg
            Taurine: 1500 mg/kg
            1560 kJ/100g
        """.trimIndent()

        val res = LabelAiCorrectionService.parseNutrientsLocally(splitColumnsAndUnits)
        assertEquals(27.0, res.nutrition["crudeProtein"]?.value ?: 0.0, 0.01)
        assertEquals(14.0, res.nutrition["crudeFat"]?.value ?: 0.0, 0.01)
        assertEquals(4.0, res.nutrition["crudeFiber"]?.value ?: 0.0, 0.01)
        assertEquals(10.0, res.nutrition["moisture"]?.value ?: 0.0, 0.01)
        assertEquals(1.2, res.nutrition["calcium"]?.value ?: 0.0, 0.01)
        assertEquals(0.15, res.nutrition["taurine"]?.value ?: 0.0, 0.005)
        assertEquals(3728.5, res.metabolizableEnergy ?: 0.0, 5.0)
        assertEquals("kcal/kg", res.metabolizableEnergyUnit)
    }

    @Test
    fun biologicalProximateSanityCheckRepairsInflatedDryFoodMoistureAndFlagsLowConfidence() {
        val misreadMoistureDryFood = """
            Guaranteed Analysis
            Crude Protein 28.0%
            Crude Fat 16.0%
            Crude Fiber 4.0%
            Crude Ash 7.5%
            Moisture 70.0%
        """.trimIndent()

        val res = LabelAiCorrectionService.parseNutrientsLocally(misreadMoistureDryFood)
        // 28 + 16 + 4 + 7.5 + 70 = 125.5% (> 105% biological limit); Moisture "70.0%" is repaired to "10.0%" and flagged
        assertEquals(10.0, res.nutrition["moisture"]?.value ?: 0.0, 0.01)
        assertTrue(res.lowConfidenceKeys.contains("moisture"))
    }

    @Test
    fun productAndBrandNameValidatorRejectsBadWordsGibberishAndAcceptsValidNames() {
        assertEquals(null, LabelAiCorrectionService.validateProductOrBrandName("Royal Canin Adult Dry Food", "Product Name"))
        assertEquals(null, LabelAiCorrectionService.validateProductOrBrandName("Purina Pro Plan", "Brand Name"))
        assertEquals(null, LabelAiCorrectionService.validateProductOrBrandName("Hill's Science Diet", "Brand Name"))

        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("", "Product Name"))
        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("123456", "Product Name"))
        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("xkqzpt", "Brand Name"))
        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("asdfghjkl", "Product Name"))
        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("aaaaaaa", "Brand Name"))
        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("Crude Protein 26%", "Product Name"))
        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("Store in a cool dry place", "Product Name"))
        assertNotNull(LabelAiCorrectionService.validateProductOrBrandName("Bullshit Kibble", "Product Name"))
    }
}


