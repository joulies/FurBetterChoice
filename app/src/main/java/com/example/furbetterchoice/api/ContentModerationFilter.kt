package com.example.furbetterchoice.api

import java.util.Locale

data class ModerationResult(
    val containsBadWords: Boolean,
    val matchedWords: List<String>,
    val highlightRanges: List<IntRange>
) {
    companion object {
        val CLEAN = ModerationResult(
            containsBadWords = false,
            matchedWords = emptyList(),
            highlightRanges = emptyList()
        )
    }
}

/**
 * Real-time profanity & bad-word detector used across scanned product edits
 * and product reviews. Returns exact character ranges in the original text
 * so UI inputs can highlight flagged words in red and block submission.
 */
object ContentModerationFilter {

    private val BAD_WORDS = setOf(
        "fuck", "fucking", "fucker", "fucked", "fucks", "motherfucker",
        "shit", "shitty", "bullshit", "horseshit",
        "bitch", "bitches", "bitching",
        "asshole", "dumbass", "jackass", "ass",
        "bastard", "dick", "dickhead", "cock", "pussy", "cunt",
        "whore", "slut", "crap", "damn", "hell", "piss", "pissed",
        "retard", "retarded", "idiot", "moron",
        "faggot", "nigger", "nigga",
        "puta", "putangina", "gago", "gaga", "tanga", "tarantado",
        "ulol", "bobo", "pakyu", "leche", "hindot", "kantot", "kupal", "punyeta"
    )

    // Pet-food & normal words that contain substrings like "ash", "ass", "cock", "shit", etc.
    private val SAFE_WORDS = setOf(
        "ash", "ashes", "bass", "cassava", "molasses", "grass", "grasses",
        "pass", "class", "mass", "classic", "glass", "brass", "shiitake",
        "shitake", "cockatiel", "cocker", "peacock", "haddock", "shellfish",
        "shell", "hella", "damned", "titanium", "potassium", "passion"
    )

    private val OBFUSCATION_TOKEN_REGEX = Regex("""[A-Za-z0-9@$!*._\-']+""")

    fun analyze(text: String?): ModerationResult {
        if (text.isNullOrBlank()) return ModerationResult.CLEAN

        val ranges = mutableListOf<IntRange>()
        val matched = linkedSetOf<String>()

        // 1. Check each whitespace-separated or punctuation-bounded token
        for (match in OBFUSCATION_TOKEN_REGEX.findAll(text)) {
            val rawToken = match.value
            val normalized = normalizeToken(rawToken)
            if (normalized.isBlank() || normalized in SAFE_WORDS) continue

            if (normalized in BAD_WORDS || matchesObfuscatedStem(normalized) || matchesAsteriskMask(rawToken)) {
                ranges.add(match.range)
                matched.add(rawToken)
                continue
            }

            // Also inspect sub-tokens split on dots/underscores/hyphens (e.g. "bad.word")
            val subMatches = Regex("""[A-Za-z0-9@$!*]+""").findAll(rawToken)
            for (sub in subMatches) {
                val subNorm = normalizeToken(sub.value)
                if (subNorm.isBlank() || subNorm in SAFE_WORDS) continue
                if (subNorm in BAD_WORDS || matchesObfuscatedStem(subNorm) || matchesAsteriskMask(sub.value)) {
                    val absStart = match.range.first + sub.range.first
                    val absEnd = match.range.first + sub.range.last
                    ranges.add(absStart..absEnd)
                    matched.add(sub.value)
                }
            }
        }

        // 2. Catch spaced-out obfuscations like "f u c k" or "s h i t"
        val letterTokens = Regex("""[A-Za-z0-9@$!*]+""").findAll(text).toList()
        var idx = 0
        while (idx < letterTokens.size) {
            if (letterTokens[idx].value.length == 1) {
                val startIdx = idx
                val sb = StringBuilder()
                while (idx < letterTokens.size && letterTokens[idx].value.length == 1) {
                    sb.append(letterTokens[idx].value)
                    idx++
                }
                if (sb.length >= 3) {
                    val collapsed = normalizeToken(sb.toString())
                    if (collapsed in BAD_WORDS) {
                        val range = letterTokens[startIdx].range.first..letterTokens[idx - 1].range.last
                        ranges.add(range)
                        matched.add(text.substring(range.first, range.last + 1))
                    }
                }
            } else {
                idx++
            }
        }

        val mergedRanges = mergeOverlappingRanges(ranges)
        return ModerationResult(
            containsBadWords = mergedRanges.isNotEmpty(),
            matchedWords = matched.toList(),
            highlightRanges = mergedRanges
        )
    }

    fun containsAnyBadWords(vararg texts: String?): Boolean {
        return texts.any { analyze(it).containsBadWords }
    }

    private fun normalizeToken(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase(Locale.ROOT)) {
            val mapped = when (ch) {
                '@' -> 'a'
                '0' -> 'o'
                '1', '!' -> 'i'
                '3' -> 'e'
                '$' -> 's'
                '5' -> 's'
                '7' -> 't'
                '*', '.', '_', '-', '\'' -> continue
                else -> ch
            }
            if (mapped in 'a'..'z') {
                sb.append(mapped)
            }
        }
        return collapseTripleRepeats(sb.toString())
    }

    private fun collapseTripleRepeats(input: String): String {
        if (input.length <= 2) return input
        val sb = StringBuilder(input.length)
        var repeatCount = 0
        var prev = '\u0000'
        for (ch in input) {
            if (ch == prev) {
                repeatCount++
                if (repeatCount < 2) sb.append(ch)
            } else {
                repeatCount = 0
                prev = ch
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun matchesObfuscatedStem(normalized: String): Boolean {
        if (normalized.length < 4) return false
        return BAD_WORDS.any { bad ->
            bad.length >= 4 && normalized.contains(bad) && normalized !in SAFE_WORDS
        }
    }

    private fun matchesAsteriskMask(rawToken: String): Boolean {
        if (!rawToken.contains('*') || rawToken.length < 3) return false
        val patternBuilder = StringBuilder("^")
        for (ch in rawToken.lowercase(Locale.ROOT)) {
            when (ch) {
                '*' -> patternBuilder.append("[a-z]")
                '@' -> patternBuilder.append('a')
                '0' -> patternBuilder.append('o')
                '1', '!' -> patternBuilder.append('i')
                '3' -> patternBuilder.append('e')
                '$', '5' -> patternBuilder.append('s')
                '7' -> patternBuilder.append('t')
                in 'a'..'z' -> patternBuilder.append(ch)
            }
        }
        patternBuilder.append("$")
        val regex = Regex(patternBuilder.toString())
        return BAD_WORDS.any { bad -> regex.matches(bad) }
    }

    private fun mergeOverlappingRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val prev = merged.last()
            val curr = sorted[i]
            if (curr.first <= prev.last + 1) {
                merged[merged.lastIndex] = prev.first..maxOf(prev.last, curr.last)
            } else {
                merged.add(curr)
            }
        }
        return merged
    }
}
