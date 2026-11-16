package com.example.furbetterchoice

import com.example.furbetterchoice.api.LabelRect
import com.example.furbetterchoice.api.LabelTextLanguageFilter
import com.example.furbetterchoice.api.PositionedTextLine
import com.example.furbetterchoice.api.TextBlockCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelTextLanguageFilterTest {

    @Test
    fun prioritizesEnglishBlocksAndDiscardsForeignBlocksOnBilingualBags() {
        val tagged = listOf(
            TextBlockCandidate("Análisis Garantizado", null) to "es",
            TextBlockCandidate("Proteína Cruda min 26%", null) to "es",
            TextBlockCandidate("Guaranteed Analysis", null) to "en",
            TextBlockCandidate("Crude Protein min 26.0%", null) to "en",
            TextBlockCandidate("26.0%", null) to "und",
            TextBlockCandidate("Analyse Garantie", null) to "fr"
        )

        val res = LabelTextLanguageFilter.prioritizeTaggedBlocks(tagged)

        assertTrue(res.isEnglishPrioritized)
        assertEquals("en", res.detectedPrimaryLanguage)
        assertEquals(3, res.discardedNonEnglishCount)
        assertTrue(res.prioritizedText.contains("Crude Protein min 26.0%"))
        assertFalse(res.prioritizedText.contains("Análisis Garantizado"))
        assertFalse(res.prioritizedText.contains("Analyse Garantie"))
    }

    @Test
    fun fallsBackToForeignBlocksWhenNoEnglishExists() {
        val tagged = listOf(
            TextBlockCandidate("Analyse Garantie", null) to "fr",
            TextBlockCandidate("Protéines Brutes 28%", null) to "fr"
        )

        val res = LabelTextLanguageFilter.prioritizeTaggedBlocks(tagged)

        assertFalse(res.isEnglishPrioritized)
        assertEquals("fr", res.detectedPrimaryLanguage)
        assertEquals(0, res.discardedNonEnglishCount)
        assertTrue(res.prioritizedText.contains("Protéines Brutes 28%"))
    }

    @Test
    fun strictFrameCroppingNeverFallsBackToOutOfFrameGuaranteedAnalysis() {
        val guideBox = LabelRect(left = 100, top = 500, right = 900, bottom = 900)
        val lines = listOf(
            // Above the frame (Guaranteed Analysis)
            PositionedTextLine("Guaranteed Analysis", LabelRect(120, 120, 500, 170)),
            PositionedTextLine("Crude Protein 26.0%", LabelRect(120, 190, 520, 240)),
            // Below the frame
            PositionedTextLine("Feeding Guide: 2 cups/day", LabelRect(120, 960, 620, 1010))
        )

        val kept = LabelTextLanguageFilter.filterLinesInsideBoxPure(lines, guideBox)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun alignsTwoColumnNutrientRowsLeftToRightByVerticalBand() {
        val lines = listOf(
            PositionedTextLine("Crude Protein (min)", LabelRect(100, 520, 440, 560)),
            PositionedTextLine("Crude Fat (min)", LabelRect(100, 580, 400, 620)),
            PositionedTextLine("28.0%", LabelRect(720, 522, 850, 560)),
            PositionedTextLine("15.5%", LabelRect(720, 582, 850, 620))
        )

        val aligned = LabelTextLanguageFilter.alignRowsPure(lines)
        assertEquals(2, aligned.size)
        assertEquals("Crude Protein (min)  28.0%", aligned[0].text)
        assertEquals("Crude Fat (min)  15.5%", aligned[1].text)
    }

    @Test
    fun mapsPreviewGuideBoxToImageRectUnderFillCenter() {
        val previewGuide = LabelRect(left = 100, top = 500, right = 900, bottom = 900)
        val mapped = LabelTextLanguageFilter.mapPreviewRectToImageRectPure(
            guideRectInPreview = previewGuide,
            previewWidth = 1000,
            previewHeight = 2000,
            imageWidth = 3000,
            imageHeight = 4000
        )

        assertNotNull(mapped)
        // scale = max(1000/3000, 2000/4000) = 0.5 -> scaledW = 1500, offsetX = 250
        assertEquals(700, mapped!!.left)
        assertEquals(1000, mapped.top)
        assertEquals(2300, mapped.right)
        assertEquals(1800, mapped.bottom)
    }

    @Test
    fun strictRuleRejectsNullGuideBoxAndOutOfFrameBarcodes() {
        val lines = listOf(
            PositionedTextLine("Crude Protein 26.0%", LabelRect(120, 190, 520, 240))
        )
        assertTrue(LabelTextLanguageFilter.filterLinesInsideBoxPure(lines, null).isEmpty())

        val guideBox = LabelRect(left = 200, top = 300, right = 800, bottom = 600)
        val insideBarcode = LabelRect(left = 260, top = 340, right = 740, bottom = 540)
        val outsideBarcode = LabelRect(left = 50, top = 50, right = 350, bottom = 220)
        val straddlingBarcode = LabelRect(left = 50, top = 340, right = 600, bottom = 540)

        assertTrue(LabelTextLanguageFilter.isBarcodeInsideGuideBoxPure(insideBarcode, guideBox))
        assertFalse(LabelTextLanguageFilter.isBarcodeInsideGuideBoxPure(outsideBarcode, guideBox))
        assertFalse(LabelTextLanguageFilter.isBarcodeInsideGuideBoxPure(straddlingBarcode, guideBox))
    }

    @Test
    fun foreignTableWithSharedCognateLikeCalciumKeepsAllForeignNutrientRows() {
        val tagged = listOf(
            TextBlockCandidate("Constituants Analytiques", null) to "fr",
            TextBlockCandidate("Protéines brutes 28%", null) to "fr",
            TextBlockCandidate("Matières grasses brutes 15%", null) to "fr",
            TextBlockCandidate("Calcium 1.2%", null) to "en"
        )

        val res = LabelTextLanguageFilter.prioritizeTaggedBlocks(tagged)
        assertFalse(res.isEnglishPrioritized)
        assertTrue(res.prioritizedText.contains("Protéines brutes 28%"))
        assertTrue(res.prioritizedText.contains("Matières grasses brutes 15%"))
        assertTrue(res.prioritizedText.contains("Calcium 1.2%"))
    }
}
