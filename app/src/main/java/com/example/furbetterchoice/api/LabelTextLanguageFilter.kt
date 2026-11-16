package com.example.furbetterchoice.api

import android.graphics.Rect
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class LabelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val isEmpty: Boolean get() = right <= left || bottom <= top
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun containsPoint(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    fun containsBoxStrictly(other: LabelRect, minOverlapRatio: Float = 0.85f): Boolean {
        if (isEmpty || other.isEmpty) return false
        if (!containsPoint(other.centerX, other.centerY)) return false
        val overlapLeft = maxOf(left, other.left)
        val overlapTop = maxOf(top, other.top)
        val overlapRight = minOf(right, other.right)
        val overlapBottom = minOf(bottom, other.bottom)
        val overlapW = (overlapRight - overlapLeft).coerceAtLeast(0)
        val overlapH = (overlapBottom - overlapTop).coerceAtLeast(0)
        return overlapW >= other.width * minOverlapRatio &&
            overlapH >= other.height * minOverlapRatio
    }
}

data class PositionedTextLine(
    val text: String,
    val box: LabelRect?
)

data class TextBlockCandidate(
    val text: String,
    val boundingBox: Rect?
)

data class LanguageFilteredResult(
    val prioritizedText: String,
    val detectedPrimaryLanguage: String,
    val isEnglishPrioritized: Boolean,
    val keptBlockCount: Int,
    val discardedNonEnglishCount: Int
)

/**
 * Crops ML Kit text blocks to the camera guide box and prioritizes English ("en")
 * text over foreign-language sections on bilingual/multilingual pet food bags.
 */
object LabelTextLanguageFilter {

    private val STRICT_ENGLISH_PET_FOOD_HINTS = listOf(
        "crude", "fat", "fiber", "fibre", "moisture", "ash",
        "phosphorus", "ingredients", "guaranteed", "nutritional",
        "calories", "metabolizable", "chicken", "salmon", "beef",
        "lamb", "rice", "corn", "vitamins", "minerals", "meal", "oil", "water"
    )

    private val COGNATE_PET_FOOD_HINTS = listOf(
        "protein", "calcium", "taurine", "analysis", "kcal", "sodium", "omega"
    )

    private val FOREIGN_NUTRIENT_MARKER_REGEX = Regex(
        """\b(?:prot[eé]ines?|mati[eè]res?\s+grasses?|cellulose\s+brute|humidit[eé]|cendres?\s+brutes?|rohprotein|rohfett|rohfaser|feuchtigkeit|rohasche|analytische\s+bestandteile|constituants\s+analytiques|analyse\s+garantie|prote[ií]nas?\s+(?:brutas?|crudas?)|grasas?\s+(?:brutas?|crudas?)|fibra\s+(?:bruta|cruda|grezza)|humedad|cenizas?|an[aá]lisis\s+garantizado|componentes\s+anal[ií]ticos|proteina\s+grezza|grassi\s+grezzi|umidit[aà]|ceneri\s+grezze|componenti\s+analitici|ruw\s+eiwit|ruw\s+vet|ruwe\s+celstof|ruwe\s+as|bia[lł]ko\s+surowe|t[lł]uszcz\s+surowy|w[lł][oó]kno\s+surowe)\b""",
        RegexOption.IGNORE_CASE
    )

    private val NUMERIC_OR_PERCENT_REGEX = Regex("""^[\d.,%\s:/()<>-]+$""")

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.45f)
                .build()
        )
    }

    private fun Rect.toLabelRect(): LabelRect = LabelRect(left, top, right, bottom)
    private fun LabelRect.toAndroidRect(): Rect = Rect(left, top, right, bottom)

    fun isBarcodeInsideGuideBox(barcodeBox: Rect?, guideBox: Rect?): Boolean {
        return isBarcodeInsideGuideBoxPure(barcodeBox?.toLabelRect(), guideBox?.toLabelRect())
    }

    fun isBarcodeInsideGuideBoxPure(barcodeBox: LabelRect?, guideBox: LabelRect?): Boolean {
        if (barcodeBox == null || barcodeBox.isEmpty) return false
        if (guideBox == null || guideBox.isEmpty) return false
        return guideBox.containsBoxStrictly(barcodeBox, minOverlapRatio = 0.85f)
    }

    fun filterBlocksInsideGuideBox(
        visionText: Text,
        guideBox: Rect?
    ): List<TextBlockCandidate> {
        val labelGuide = guideBox?.toLabelRect()
        if (labelGuide == null || labelGuide.isEmpty) return emptyList()

        // Extract at word/element and line granularity so ONLY words strictly inside
        // the green guideBox are kept and anything outside is discarded.
        val lineCandidates = visionText.textBlocks.flatMap { block ->
            if (block.lines.isNotEmpty()) {
                block.lines.mapNotNull { line ->
                    val insideElements = line.elements.mapNotNull { element ->
                        val elemText = element.text.trim()
                        val elemBox = element.boundingBox?.toLabelRect()
                        if (elemText.isEmpty() || elemBox == null) null
                        else if (labelGuide.containsBoxStrictly(elemBox, minOverlapRatio = 0.85f)) {
                            PositionedTextLine(elemText, elemBox)
                        } else {
                            null
                        }
                    }
                    when {
                        insideElements.isNotEmpty() -> {
                            val ordered = insideElements.sortedBy { it.box!!.left }
                            val union = LabelRect(
                                left = ordered.minOf { it.box!!.left },
                                top = ordered.minOf { it.box!!.top },
                                right = ordered.maxOf { it.box!!.right },
                                bottom = ordered.maxOf { it.box!!.bottom }
                            )
                            PositionedTextLine(
                                text = ordered.joinToString(" ") { it.text },
                                box = union
                            )
                        }
                        line.elements.isEmpty() -> {
                            val trimmed = line.text.trim()
                            val box = (line.boundingBox ?: block.boundingBox)?.toLabelRect()
                            if (trimmed.isEmpty() || box == null || !labelGuide.containsBoxStrictly(box)) null
                            else PositionedTextLine(trimmed, box)
                        }
                        else -> null
                    }
                }
            } else {
                val trimmed = block.text.trim()
                val box = block.boundingBox?.toLabelRect()
                if (trimmed.isEmpty() || box == null || !labelGuide.containsBoxStrictly(box)) emptyList()
                else listOf(PositionedTextLine(trimmed, box))
            }
        }
        val filtered = filterLinesInsideBoxPure(lineCandidates, labelGuide)
        return alignRowsPure(filtered).map {
            TextBlockCandidate(text = it.text, boundingBox = it.box?.toAndroidRect())
        }
    }

    /**
     * Pure, unit-testable strict frame cropper.
     * ONLY lines whose bounding box falls strictly inside [guideBox] are kept —
     * NEVER falls back to out-of-frame text, and returns empty when [guideBox] is null/empty.
     */
    fun filterLinesInsideBoxPure(
        lines: List<PositionedTextLine>,
        guideBox: LabelRect?
    ): List<PositionedTextLine> {
        if (lines.isEmpty()) return emptyList()
        if (guideBox == null || guideBox.isEmpty) return emptyList()

        return lines.filter { line ->
            val box = line.box ?: return@filter false
            guideBox.containsBoxStrictly(box)
        }
    }

    /**
     * Reconstructs 2-column nutrition tables (e.g. "Crude Protein" on left, "26.0%" on right)
     * into single horizontal rows sorted top-to-bottom, left-to-right.
     */
    fun alignRowsPure(lines: List<PositionedTextLine>): List<PositionedTextLine> {
        if (lines.size <= 1) return lines
        val withBoxes = lines.filter { it.box != null }
        val withoutBoxes = lines.filter { it.box == null }
        if (withBoxes.isEmpty()) return lines

        val sorted = withBoxes.sortedWith(
            compareBy<PositionedTextLine> { it.box!!.centerY }
                .thenBy { it.box!!.left }
        )

        val rows = mutableListOf<MutableList<PositionedTextLine>>()
        for (item in sorted) {
            val box = item.box!!
            val matchingRow = rows.firstOrNull { candidateRow ->
                val anchor = candidateRow.first().box!!
                val minH = minOf(anchor.height, box.height).coerceAtLeast(1)
                val overlapTop = maxOf(anchor.top, box.top)
                val overlapBottom = minOf(anchor.bottom, box.bottom)
                val verticalOverlap = (overlapBottom - overlapTop).coerceAtLeast(0)
                // Allow slight camera tilt across wide 2-column tables (up to ~4% of horizontal span)
                val horizontalSpan = kotlin.math.abs(box.centerX - anchor.centerX)
                val tiltAllowance = (horizontalSpan * 0.045f).toInt().coerceAtMost(24)
                val tolerance = maxOf(12, (minH * 0.65f).toInt()) + tiltAllowance
                verticalOverlap >= minH * 0.35f ||
                    kotlin.math.abs(anchor.centerY - box.centerY) <= tolerance
            }
            if (matchingRow != null) {
                matchingRow.add(item)
            } else {
                rows.add(mutableListOf(item))
            }
        }

        val mergedRows = rows.map { row ->
            val ordered = row.sortedBy { it.box!!.left }
            val union = LabelRect(
                left = ordered.minOf { it.box!!.left },
                top = ordered.minOf { it.box!!.top },
                right = ordered.maxOf { it.box!!.right },
                bottom = ordered.maxOf { it.box!!.bottom }
            )
            PositionedTextLine(
                text = ordered.joinToString("  ") { it.text },
                box = union
            )
        }
        return mergedRows + withoutBoxes
    }

    /**
     * Maps a targeting [guideRectInPreview] (in PreviewView pixel space) to rotated
     * InputImage pixel coordinates assuming PreviewView.ScaleType.FILL_CENTER.
     */
    fun mapPreviewRectToImageRect(
        guideRectInPreview: Rect?,
        previewWidth: Int,
        previewHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): Rect? {
        if (guideRectInPreview == null) return null
        return mapPreviewRectToImageRectPure(
            guideRectInPreview = guideRectInPreview.toLabelRect(),
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )?.toAndroidRect()
    }

    fun mapPreviewRectToImageRectPure(
        guideRectInPreview: LabelRect?,
        previewWidth: Int,
        previewHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): LabelRect? {
        if (guideRectInPreview == null || guideRectInPreview.isEmpty) return null
        if (previewWidth <= 0 || previewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null

        val scale = maxOf(
            previewWidth.toFloat() / imageWidth.toFloat(),
            previewHeight.toFloat() / imageHeight.toFloat()
        )
        val scaledW = imageWidth * scale
        val scaledH = imageHeight * scale
        val offsetX = (scaledW - previewWidth) / 2f
        val offsetY = (scaledH - previewHeight) / 2f

        val left = ((guideRectInPreview.left + offsetX) / scale).toInt().coerceIn(0, imageWidth)
        val top = ((guideRectInPreview.top + offsetY) / scale).toInt().coerceIn(0, imageHeight)
        val right = ((guideRectInPreview.right + offsetX) / scale).toInt().coerceIn(0, imageWidth)
        val bottom = ((guideRectInPreview.bottom + offsetY) / scale).toInt().coerceIn(0, imageHeight)

        if (right <= left || bottom <= top) return null
        return LabelRect(left, top, right, bottom)
    }

    suspend fun prioritizeEnglishBlocks(
        blocks: List<TextBlockCandidate>
    ): LanguageFilteredResult {
        if (blocks.isEmpty()) {
            return LanguageFilteredResult(
                prioritizedText = "",
                detectedPrimaryLanguage = "und",
                isEnglishPrioritized = false,
                keptBlockCount = 0,
                discardedNonEnglishCount = 0
            )
        }

        val tagged = blocks.map { candidate ->
            val lang = detectBlockLanguage(candidate.text)
            candidate to lang
        }

        return prioritizeTaggedBlocks(tagged)
    }

    /**
     * Pure, unit-testable prioritization given pre-tagged (candidate, languageCode) pairs.
     */
    fun prioritizeTaggedBlocks(
        taggedBlocks: List<Pair<TextBlockCandidate, String>>
    ): LanguageFilteredResult {
        if (taggedBlocks.isEmpty()) {
            return LanguageFilteredResult(
                prioritizedText = "",
                detectedPrimaryLanguage = "und",
                isEnglishPrioritized = false,
                keptBlockCount = 0,
                discardedNonEnglishCount = 0
            )
        }

        val englishBlocks = mutableListOf<TextBlockCandidate>()
        val neutralNumericBlocks = mutableListOf<TextBlockCandidate>()
        val otherBlocks = mutableListOf<Pair<TextBlockCandidate, String>>()

        for ((candidate, rawLang) in taggedBlocks) {
            val lang = rawLang.lowercase(Locale.ROOT)
            val lower = candidate.text.lowercase(Locale.ROOT)
            val hasStrictEnglishHint = STRICT_ENGLISH_PET_FOOD_HINTS.any { hint ->
                Regex("""\b${Regex.escape(hint)}\b""").containsMatchIn(lower)
            }
            val hasCognateHint = COGNATE_PET_FOOD_HINTS.any { hint ->
                Regex("""\b${Regex.escape(hint)}\b""").containsMatchIn(lower)
            }
            val hasForeignMarker = FOREIGN_NUTRIENT_MARKER_REGEX.containsMatchIn(lower)
            val isNumericOnly = NUMERIC_OR_PERCENT_REGEX.matches(candidate.text.trim())

            when {
                // Multilingual slash-row containing both English and foreign terms: always keep!
                hasStrictEnglishHint -> englishBlocks.add(candidate)
                // Purely foreign nutrient line (e.g. "Protéines brutes 28%" or "Calcium 1.2%" tagged as "fr")
                hasForeignMarker -> otherBlocks.add(candidate to (if (lang == "en" || lang == "und") "fr" else lang))
                lang == "en" || (lang == "und" && hasCognateHint) -> englishBlocks.add(candidate)
                isNumericOnly || lang == "und" -> neutralNumericBlocks.add(candidate)
                else -> otherBlocks.add(candidate to lang)
            }
        }

        // If the only "English" blocks found were shared cognates (like "Calcium 1.2%") while
        // the rest of the table is in a foreign language, keep all blocks together as foreign!
        val hasGenuineEnglishBlock = englishBlocks.any { blk ->
            val lower = blk.text.lowercase(Locale.ROOT)
            STRICT_ENGLISH_PET_FOOD_HINTS.any { hint ->
                Regex("""\b${Regex.escape(hint)}\b""").containsMatchIn(lower)
            }
        }
        if (!hasGenuineEnglishBlock && otherBlocks.isNotEmpty()) {
            otherBlocks.addAll(englishBlocks.map { it to (otherBlocks.first().second) })
            englishBlocks.clear()
        }

        return if (englishBlocks.isNotEmpty()) {
            // Keep English blocks + adjacent numeric/unit blocks that appear near English headers
            val kept = mutableListOf<TextBlockCandidate>()
            kept.addAll(englishBlocks)
            for (numBlock in neutralNumericBlocks) {
                val box = numBlock.boundingBox
                val nearEnglish = box == null || englishBlocks.any { en ->
                    val enBox = en.boundingBox ?: return@any true
                    val verticalOverlap = kotlin.math.abs(enBox.centerY() - box.centerY()) <=
                        maxOf(enBox.height(), box.height()) * 2 + 40
                    val horizontalOverlap = kotlin.math.abs(enBox.centerX() - box.centerX()) <=
                        maxOf(enBox.width(), box.width()) * 2 + 80
                    verticalOverlap && horizontalOverlap
                }
                if (nearEnglish) kept.add(numBlock)
            }

            // Preserve top-to-bottom, left-to-right reading order
            val ordered = kept.sortedWith(
                compareBy<TextBlockCandidate> { it.boundingBox?.top ?: 0 }
                    .thenBy { it.boundingBox?.left ?: 0 }
            )

            LanguageFilteredResult(
                prioritizedText = ordered.joinToString("\n") { it.text },
                detectedPrimaryLanguage = "en",
                isEnglishPrioritized = true,
                keptBlockCount = ordered.size,
                discardedNonEnglishCount = otherBlocks.size
            )
        } else {
            val allFallback = (neutralNumericBlocks + otherBlocks.map { it.first })
                .sortedWith(
                    compareBy<TextBlockCandidate> { it.boundingBox?.top ?: 0 }
                        .thenBy { it.boundingBox?.left ?: 0 }
                )
            val topLang = otherBlocks.groupingBy { it.second }.eachCount()
                .maxByOrNull { it.value }?.key ?: "und"

            LanguageFilteredResult(
                prioritizedText = allFallback.joinToString("\n") { it.text },
                detectedPrimaryLanguage = topLang,
                isEnglishPrioritized = false,
                keptBlockCount = allFallback.size,
                discardedNonEnglishCount = 0
            )
        }
    }

    private suspend fun detectBlockLanguage(text: String): String {
        val cleaned = text.trim()
        if (cleaned.length < 3 || NUMERIC_OR_PERCENT_REGEX.matches(cleaned)) return "und"
        return try {
            languageIdentifier.identifyLanguage(cleaned).await()
        } catch (_: Exception) {
            "und"
        }
    }
}
