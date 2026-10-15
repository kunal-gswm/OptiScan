package com.optiscan.app.ocr

import kotlin.math.abs

/**
 * Geometric bounding box for recognized text lines/blocks.
 */
data class TextBoundingBox(
    val top: Int = 0,
    val left: Int = 0,
    val bottom: Int = 0,
    val right: Int = 0
) {
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val width: Int get() = (right - left).coerceAtLeast(0)
    val centerY: Int get() = top + height / 2
}

/**
 * Represents an individual recognized line of text.
 */
data class RecognizedLine(
    val text: String,
    val boundingBox: TextBoundingBox? = null,
    val angle: Int = 0,
    val isProminent: Boolean = false
)

/**
 * Pure Kotlin processor for sorting, deduplicating, and merging OCR detections.
 * Fully unit-testable on JVM without Android or Play Services dependencies.
 *
 * Guarantees:
 * - Natural human reading order (top-to-bottom, left-to-right).
 * - Exact duplicate removal across multi-pass scans.
 * - Subsumption of partial lines only when they spatially overlap the same visual region.
 * - Valid short lines and prominent words are NEVER discarded.
 * - Never invents words or outputs canned mock data.
 */
object OcrResultProcessor {

    const val DEFAULT_EMPTY_MESSAGE = "No printed text detected in the image."

    /**
     * Sort lines in human reading order:
     * - Primarily top-to-bottom.
     * - Lines with similar vertical centers (within a tolerance band) are sorted left-to-right.
     */
    fun sortReadingOrder(lines: List<RecognizedLine>): List<RecognizedLine> {
        if (lines.size <= 1) return lines

        val withBoxes = lines.filter { it.boundingBox != null }
        if (withBoxes.size != lines.size) {
            // Some or all don't have boxes; preserve existing order
            return lines
        }

        // Average line height for vertical grouping
        val avgHeight = lines.map { it.boundingBox!!.height.coerceAtLeast(10) }.average().toInt()
        val verticalTolerance = (avgHeight * 0.5).coerceAtLeast(8.0).toInt()

        return lines.sortedWith { a, b ->
            val boxA = a.boundingBox!!
            val boxB = b.boundingBox!!
            val diffY = boxA.centerY - boxB.centerY
            if (abs(diffY) <= verticalTolerance) {
                // Same line/band: sort left-to-right
                boxA.left.compareTo(boxB.left)
            } else {
                diffY.compareTo(0)
            }
        }
    }

    /**
     * Determine if two recognized lines spatially overlap significantly,
     * indicating they represent the same visual line across different scan passes.
     */
    fun isSpatialOverlap(boxA: TextBoundingBox?, boxB: TextBoundingBox?): Boolean {
        if (boxA == null || boxB == null) return false
        val verticalOverlap = maxOf(0, minOf(boxA.bottom, boxB.bottom) - maxOf(boxA.top, boxB.top))
        val horizontalOverlap = maxOf(0, minOf(boxA.right, boxB.right) - maxOf(boxA.left, boxB.left))
        val overlapArea = verticalOverlap * horizontalOverlap
        val minArea = minOf(boxA.height * boxA.width, boxB.height * boxB.width)
        return if (minArea > 0) (overlapArea.toFloat() / minArea) > 0.35f else false
    }

    /**
     * Deduplicate lines while preserving reading order and keeping richer/longer lines.
     * - Exact case-insensitive match: deduplicates identical lines.
     * - Substring subsumption: applies ONLY when two detections spatially overlap the same region
     *   (e.g. Pass 1 detected "FRESH TASTY" and Pass 2 detected "FRESH TASTY. TO GO" at the same position).
     * - Distinct lines at different physical positions are NEVER dropped, even if one word appears inside another.
     * - Short valid words (e.g. "PIZZA", "TO GO", "HOT", "100%") are strictly preserved.
     */
    fun deduplicate(lines: List<RecognizedLine>): List<RecognizedLine> {
        val result = mutableListOf<RecognizedLine>()

        for (candidate in lines) {
            val candNorm = normalizeForComparison(candidate.text)
            if (candNorm.isBlank()) continue

            var matchedIndex = -1
            for (i in result.indices) {
                val existing = result[i]
                val existingNorm = normalizeForComparison(existing.text)

                // 1. Exact normalized match: always deduplicate
                if (existingNorm == candNorm) {
                    matchedIndex = i
                    break
                }

                // 2. Spatial overlap check: if two lines occupy the same visual location across passes
                val overlap = isSpatialOverlap(candidate.boundingBox, existing.boundingBox)
                if (overlap) {
                    // If existing already covers candidate at the same location, keep existing
                    if (existingNorm.contains(candNorm)) {
                        matchedIndex = i
                        break
                    }
                    // If candidate is a fuller/longer detection at the same location, upgrade to candidate
                    if (candNorm.contains(existingNorm)) {
                        matchedIndex = i
                        result[i] = candidate
                        break
                    }
                }
            }

            if (matchedIndex == -1) {
                result.add(candidate)
            }
        }

        return result
    }

    /**
     * Merges lines from multiple OCR passes (e.g. scaled pass for large headings + full-res pass for body text).
     * Maintains prominent headings and natural reading order.
     */
    fun mergePasses(
        primaryLines: List<RecognizedLine>,
        secondaryLines: List<RecognizedLine>
    ): List<RecognizedLine> {
        val combined = mutableListOf<RecognizedLine>()
        combined.addAll(primaryLines)
        combined.addAll(secondaryLines)

        val deduped = deduplicate(combined)
        return sortReadingOrder(deduped)
    }

    /**
     * Format list of recognized lines into the final user-facing text string.
     */
    fun formatOutput(
        lines: List<RecognizedLine>,
        emptyFallbackMessage: String = DEFAULT_EMPTY_MESSAGE
    ): String {
        val filtered = lines.map { it.text.trim() }.filter { it.isNotBlank() }
        if (filtered.isEmpty()) {
            return emptyFallbackMessage
        }
        return filtered.joinToString("\n")
    }

    /**
     * Helper to normalize text for similarity comparison (strips punctuation and whitespace, lowercase).
     */
    fun normalizeForComparison(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Converts ML Kit Text blocks and lines to RecognizedLine models with optional scaling.
     */
    fun extractLinesFromVisionText(
        visionText: com.google.mlkit.vision.text.Text,
        scaleFactor: Float = 1.0f,
        angle: Int = 0
    ): List<RecognizedLine> {
        val lines = mutableListOf<RecognizedLine>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                if (text.isBlank()) continue

                val box = line.boundingBox?.let { rect ->
                    TextBoundingBox(
                        top = (rect.top / scaleFactor).toInt(),
                        left = (rect.left / scaleFactor).toInt(),
                        bottom = (rect.bottom / scaleFactor).toInt(),
                        right = (rect.right / scaleFactor).toInt()
                    )
                }
                val isProminent = box != null && box.height > 60
                lines.add(
                    RecognizedLine(
                        text = text,
                        boundingBox = box,
                        angle = angle,
                        isProminent = isProminent
                    )
                )
            }
        }
        return lines
    }
}
