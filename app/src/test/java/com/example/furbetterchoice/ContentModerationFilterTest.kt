package com.example.furbetterchoice

import com.example.furbetterchoice.api.ContentModerationFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentModerationFilterTest {

    @Test
    fun cleanPetFoodIngredientsAreAllowed() {
        val ingredients = "Chicken Meal, Brown Rice, Bass, Cassava Root, Crude Ash, Shiitake Mushroom, Cockatiel Seed, Potassium Chloride, Taurine"
        val res = ContentModerationFilter.analyze(ingredients)

        assertFalse(res.containsBadWords)
        assertTrue(res.highlightRanges.isEmpty())
    }

    @Test
    fun detectsDirectAndObfuscatedBadWordsWithExactRanges() {
        val text = "Great food but has f*ck and sh1t and @sshole"
        val res = ContentModerationFilter.analyze(text)

        assertTrue(res.containsBadWords)
        assertEquals(3, res.highlightRanges.size)
        val highlighted = res.highlightRanges.map { text.substring(it.first, it.last + 1) }
        assertTrue(highlighted.contains("f*ck"))
        assertTrue(highlighted.contains("sh1t"))
        assertTrue(highlighted.contains("@sshole"))
    }

    @Test
    fun detectsSpacedOutProfanityAndTagalogBadWords() {
        val spaced = ContentModerationFilter.analyze("this is f u c k bad")
        assertTrue(spaced.containsBadWords)

        val tagalog = ContentModerationFilter.analyze("wag bilhin tanga gago")
        assertTrue(tagalog.containsBadWords)
        assertEquals(2, tagalog.matchedWords.size)
    }

    @Test
    fun containsAnyBadWordsChecksMultipleFields() {
        assertTrue(
            ContentModerationFilter.containsAnyBadWords(
                "Healthy Kibble",
                "Good Brand",
                "Chicken, Rice, bullshit"
            )
        )
        assertFalse(
            ContentModerationFilter.containsAnyBadWords(
                "Healthy Kibble",
                "Ocean Bass Formula",
                "Bass, Cassava, Ash, Taurine"
            )
        )
    }
}
