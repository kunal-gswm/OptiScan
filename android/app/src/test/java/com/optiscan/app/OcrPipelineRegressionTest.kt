package com.optiscan.app

import com.optiscan.app.ocr.OcrResultProcessor
import com.optiscan.app.ocr.RecognizedLine
import com.optiscan.app.ocr.TextBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression and unit test suite for the OptiScan on-device OCR pipeline:
 * - Preservation of prominent large headings ("PIZZA") alongside smaller lines ("FRESH TASTY. TO GO")
 * - Spatial top-to-bottom and left-to-right reading order
 * - Preservation of valid short lines ("TO GO", "HOT", "100%")
 * - Protection against accidental dropping of distinct lines that share common words at different locations
 * - Multi-pass line deduplication and subsumption of partial detections
 * - Honest handling of empty/whitespace OCR detections without fabrication or canned mock data
 */
class OcrPipelineRegressionTest {

    @Test
    fun `prominent large text is preserved alongside smaller text in natural reading order`() {
        val prominentHeading = RecognizedLine(
            text = "PIZZA",
            boundingBox = TextBoundingBox(top = 100, left = 150, bottom = 400, right = 850),
            isProminent = true
        )

        val smallerSlogan = RecognizedLine(
            text = "FRESH TASTY. TO GO",
            boundingBox = TextBoundingBox(top = 450, left = 200, bottom = 520, right = 800),
            isProminent = false
        )

        val merged = OcrResultProcessor.mergePasses(
            primaryLines = listOf(prominentHeading),
            secondaryLines = listOf(smallerSlogan)
        )

        assertEquals(2, merged.size)
        assertEquals("PIZZA", merged[0].text)
        assertEquals("FRESH TASTY. TO GO", merged[1].text)

        val output = OcrResultProcessor.formatOutput(merged)
        assertEquals("PIZZA\nFRESH TASTY. TO GO", output)
    }

    @Test
    fun `distinct short lines at different positions are preserved even if one is substring of another`() {
        // Heading "PIZZA" at the top
        val heading = RecognizedLine(
            text = "PIZZA",
            boundingBox = TextBoundingBox(top = 50, left = 100, bottom = 200, right = 600)
        )
        // Sub-banner "HOT FRESH PIZZA" further down on the box
        val subBanner = RecognizedLine(
            text = "HOT FRESH PIZZA",
            boundingBox = TextBoundingBox(top = 350, left = 80, bottom = 410, right = 620)
        )
        // Slogan "TO GO" in the corner
        val cornerBadge = RecognizedLine(
            text = "TO GO",
            boundingBox = TextBoundingBox(top = 500, left = 400, bottom = 550, right = 550)
        )

        val merged = OcrResultProcessor.deduplicate(listOf(heading, subBanner, cornerBadge))

        assertEquals(3, merged.size)
        assertEquals("PIZZA", merged[0].text)
        assertEquals("HOT FRESH PIZZA", merged[1].text)
        assertEquals("TO GO", merged[2].text)
    }

    @Test
    fun `spatial sorting orders blocks top to bottom and left to right within horizontal bands`() {
        val topTitle = RecognizedLine(
            text = "HOT & FRESH PIZZA",
            boundingBox = TextBoundingBox(top = 50, left = 100, bottom = 120, right = 500)
        )
        val leftSubtitle = RecognizedLine(
            text = "FAMILY SIZE",
            boundingBox = TextBoundingBox(top = 200, left = 50, bottom = 240, right = 250)
        )
        val rightSubtitle = RecognizedLine(
            text = "CRUST: THIN",
            boundingBox = TextBoundingBox(top = 200, left = 300, bottom = 240, right = 500)
        )
        val footerNotice = RecognizedLine(
            text = "SERVE HOT",
            boundingBox = TextBoundingBox(top = 600, left = 100, bottom = 650, right = 400)
        )

        // Shuffle lines to test spatial sorting robustness
        val shuffled = listOf(footerNotice, rightSubtitle, topTitle, leftSubtitle)
        val sorted = OcrResultProcessor.sortReadingOrder(shuffled)

        assertEquals("HOT & FRESH PIZZA", sorted[0].text)
        assertEquals("FAMILY SIZE", sorted[1].text)
        assertEquals("CRUST: THIN", sorted[2].text)
        assertEquals("SERVE HOT", sorted[3].text)
    }

    @Test
    fun `deduplication eliminates identical lines across multi-pass scans`() {
        val pass1 = listOf(
            RecognizedLine(text = "PIZZA", boundingBox = TextBoundingBox(top = 100, left = 100, bottom = 300, right = 500)),
            RecognizedLine(text = "DELICIOUS", boundingBox = TextBoundingBox(top = 350, left = 100, bottom = 400, right = 400))
        )
        val pass2 = listOf(
            RecognizedLine(text = "PIZZA", boundingBox = TextBoundingBox(top = 100, left = 100, bottom = 300, right = 500)),
            RecognizedLine(text = "FRESH TASTY. TO GO", boundingBox = TextBoundingBox(top = 450, left = 100, bottom = 500, right = 600))
        )

        val merged = OcrResultProcessor.mergePasses(pass1, pass2)

        assertEquals(3, merged.size)
        assertEquals("PIZZA", merged[0].text)
        assertEquals("DELICIOUS", merged[1].text)
        assertEquals("FRESH TASTY. TO GO", merged[2].text)
    }

    @Test
    fun `deduplication subsumes partial line with more complete line from subsequent pass`() {
        val pass1 = listOf(
            RecognizedLine(text = "FRESH TASTY", boundingBox = TextBoundingBox(top = 200, left = 100, bottom = 260, right = 400))
        )
        // Pass 2 captured the full punctuation and trailing words at the same location
        val pass2 = listOf(
            RecognizedLine(text = "FRESH TASTY. TO GO", boundingBox = TextBoundingBox(top = 200, left = 100, bottom = 260, right = 600))
        )

        val merged = OcrResultProcessor.mergePasses(pass1, pass2)

        assertEquals(1, merged.size)
        assertEquals("FRESH TASTY. TO GO", merged[0].text)
    }

    @Test
    fun `short valid words and numbers are never dropped`() {
        val shortLines = listOf(
            RecognizedLine(text = "PIZZA", boundingBox = TextBoundingBox(top = 50, left = 50, bottom = 150, right = 300)),
            RecognizedLine(text = "HOT", boundingBox = TextBoundingBox(top = 180, left = 50, bottom = 220, right = 150)),
            RecognizedLine(text = "TO GO", boundingBox = TextBoundingBox(top = 250, left = 50, bottom = 290, right = 200)),
            RecognizedLine(text = "100%", boundingBox = TextBoundingBox(top = 320, left = 50, bottom = 360, right = 180))
        )

        val merged = OcrResultProcessor.deduplicate(shortLines)

        assertEquals(4, merged.size)
        assertTrue(merged.any { it.text == "PIZZA" })
        assertTrue(merged.any { it.text == "HOT" })
        assertTrue(merged.any { it.text == "TO GO" })
        assertTrue(merged.any { it.text == "100%" })
    }

    @Test
    fun `empty OCR result yields honest fallback message and does not fabricate text`() {
        val emptyLines = emptyList<RecognizedLine>()
        val output = OcrResultProcessor.formatOutput(emptyLines)

        assertEquals("No printed text detected in the image.", output)
        assertFalse(output.contains("PIZZA"))
        assertFalse(output.contains("Amoxicillin"))
        assertFalse(output.contains("Mock"))
    }

    @Test
    fun `result processor never invents text or injects canned samples`() {
        val realDetections = listOf(
            RecognizedLine(text = "COFFEE BEANS", boundingBox = TextBoundingBox(top = 10, left = 10, bottom = 40, right = 200)),
            RecognizedLine(text = "NET WT 250G", boundingBox = TextBoundingBox(top = 50, left = 10, bottom = 70, right = 150))
        )
        val output = OcrResultProcessor.formatOutput(realDetections)

        assertEquals("COFFEE BEANS\nNET WT 250G", output)
        assertFalse(output.contains("PIZZA"))
        assertFalse(output.contains("VALLEY PHARMACY"))
        assertFalse(output.contains("Acetaminophen"))
    }

    @Test
    fun `lines with only whitespace or empty text are filtered out gracefully`() {
        val blankLines = listOf(
            RecognizedLine(text = "   ", boundingBox = TextBoundingBox(top = 10, left = 10, bottom = 30, right = 50)),
            RecognizedLine(text = "", boundingBox = TextBoundingBox(top = 40, left = 10, bottom = 60, right = 50))
        )
        val output = OcrResultProcessor.formatOutput(blankLines)

        assertEquals("No printed text detected in the image.", output)
    }

    @Test
    fun `normalization strips punctuation and collapses whitespace correctly for comparison`() {
        val norm1 = OcrResultProcessor.normalizeForComparison("Fresh Tasty. To Go!")
        val norm2 = OcrResultProcessor.normalizeForComparison("fresh   tasty to go")

        assertEquals("fresh tasty to go", norm1)
        assertEquals("fresh tasty to go", norm2)
        assertEquals(norm1, norm2)
    }

    @Test
    fun `multi-angle lines are preserved without false deduplication across different words`() {
        // Horizontal main headline
        val horizontal = listOf(
            RecognizedLine(text = "PIZZA", boundingBox = TextBoundingBox(top = 100, left = 100, bottom = 300, right = 500), angle = 0)
        )
        // Vertical side flap slogan detected in 90 degree pass
        val vertical = listOf(
            RecognizedLine(text = "FRESH TASTY. TO GO", boundingBox = TextBoundingBox(top = 350, left = 100, bottom = 420, right = 600), angle = 90)
        )

        val merged = OcrResultProcessor.mergePasses(horizontal, vertical)

        assertEquals(2, merged.size)
        val output = OcrResultProcessor.formatOutput(merged)
        assertTrue(output.contains("PIZZA"))
        assertTrue(output.contains("FRESH TASTY. TO GO"))
    }
}
