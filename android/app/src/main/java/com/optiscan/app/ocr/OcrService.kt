package com.optiscan.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.optiscan.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service wrapper for on-device OCR using Google ML Kit Text Recognition.
 *
 * Implements a robust multi-pass pipeline:
 * 1. Primary pass on upright bitmap (0°).
 * 2. Multi-scale pass: downscales high-resolution camera captures (e.g. 12MP+) to ~1280px
 *    so that large, prominent text (such as "PIZZA") fits within ML Kit's CNN receptive field.
 * 3. Adaptive orientation sweep: checks 90°, 180°, 270° if initial pass yields sparse/no text,
 *    supporting blind users who may hold packaging upside-down or sideways.
 * 4. Adaptive contrast/grayscale pass: handles stylized, colored, or low-contrast packaging fonts.
 * 5. Spatial sorting & deduplication: preserves reading order, retains prominent headings,
 *    and keeps short lines without duplicate phrases.
 */
class OcrService(
    recognizer: TextRecognizer? = null
) {
    private val tag = "OcrService"
    private val recognizer: TextRecognizer by lazy {
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognize printed text from an in-memory Bitmap.
     *
     * @param bitmap Image to inspect for text.
     * @return Result containing extracted string or exception.
     */
    suspend fun recognizeText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        try {
            if (bitmap.isRecycled) {
                return@withContext Result.failure(IllegalStateException("Cannot run OCR on recycled bitmap"))
            }

            if (BuildConfig.DEBUG) {
                Log.d(tag, "Starting on-device OCR pipeline on bitmap: ${bitmap.width}x${bitmap.height}")
            }

            // Stage 1: Pass 1 on primary image (upright, 0 degrees)
            val primaryResult = processImageAtRotation(bitmap, rotationDegrees = 0)
            val primaryLines = OcrResultProcessor.extractLinesFromVisionText(primaryResult, scaleFactor = 1.0f, angle = 0)

            if (BuildConfig.DEBUG) {
                Log.d(tag, "Stage 1 (0 deg native) detected ${primaryLines.size} lines: '${primaryResult.text.take(80)}'")
            }

            // Stage 2: Multi-scale pass if image is high resolution (max dimension > 1600)
            // Critical for large, prominent fonts (like "PIZZA") whose glyphs exceed ML Kit's
            // receptive field at native camera capture resolutions (e.g. 3000-4000px).
            val maxDim = maxOf(bitmap.width, bitmap.height)
            val scaledLines = if (maxDim > 1600) {
                val targetDim = 1280f
                val scale = targetDim / maxDim
                val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

                val scaledBitmap = try {
                    Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                } catch (e: OutOfMemoryError) {
                    Log.w(tag, "OOM creating scaled bitmap for OCR: ${e.message}")
                    null
                }

                if (scaledBitmap != null) {
                    try {
                        val scaledResult = processImageAtRotation(scaledBitmap, rotationDegrees = 0)
                        val lines = OcrResultProcessor.extractLinesFromVisionText(scaledResult, scaleFactor = scale, angle = 0)
                        if (BuildConfig.DEBUG) {
                            Log.d(tag, "Stage 2 (scaled 1280px) detected ${lines.size} lines: '${scaledResult.text.take(80)}'")
                        }
                        lines
                    } finally {
                        if (scaledBitmap != bitmap) {
                            scaledBitmap.recycle()
                        }
                    }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Merge primary and scaled passes
            var mergedLines = OcrResultProcessor.mergePasses(primaryLines, scaledLines)

            // Stage 3: Adaptive orientation sweep if text is absent or sparse (< 2 lines or total len < 6)
            // Supports blind users who cannot visually align packaging or signs.
            if (mergedLines.isEmpty() || (mergedLines.size == 1 && mergedLines[0].text.length < 6)) {
                val candidateAngles = listOf(90, 180, 270)
                for (angle in candidateAngles) {
                    val angleResult = processImageAtRotation(bitmap, rotationDegrees = angle)
                    val angleLines = OcrResultProcessor.extractLinesFromVisionText(angleResult, scaleFactor = 1.0f, angle = angle)
                    if (angleLines.isNotEmpty()) {
                        if (BuildConfig.DEBUG) {
                            Log.d(tag, "Stage 3 (orientation $angle deg) detected ${angleLines.size} lines")
                        }
                        mergedLines = OcrResultProcessor.mergePasses(mergedLines, angleLines)
                        if (mergedLines.size >= 2 || mergedLines.any { it.text.length > 5 }) {
                            break
                        }
                    }
                }
            }

            // Stage 4: Contrast-enhancement pass if still empty
            // Aids white/yellow stylized fonts on dark/red packaging with glossy reflections
            if (mergedLines.isEmpty()) {
                val enhancedBitmap = createContrastEnhancedBitmap(bitmap)
                if (enhancedBitmap != null) {
                    try {
                        val enhancedResult = processImageAtRotation(enhancedBitmap, rotationDegrees = 0)
                        val enhancedLines = OcrResultProcessor.extractLinesFromVisionText(enhancedResult, scaleFactor = 1.0f, angle = 0)
                        if (BuildConfig.DEBUG) {
                            Log.d(tag, "Stage 4 (contrast enhanced) detected ${enhancedLines.size} lines")
                        }
                        mergedLines = OcrResultProcessor.mergePasses(mergedLines, enhancedLines)
                    } finally {
                        enhancedBitmap.recycle()
                    }
                }
            }

            val finalOutput = OcrResultProcessor.formatOutput(mergedLines)

            if (BuildConfig.DEBUG) {
                Log.d(tag, "OCR complete: ${mergedLines.size} lines retained. Output: '$finalOutput'")
            }

            Result.success(finalOutput)
        } catch (e: Exception) {
            Log.e(tag, "On-device OCR processing failed", e)
            Result.failure(e)
        }
    }

    private suspend fun processImageAtRotation(bitmap: Bitmap, rotationDegrees: Int): Text =
        suspendCancellableCoroutine { cont ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        cont.resume(visionText)
                    }
                    .addOnFailureListener { exception ->
                        cont.resumeWithException(exception)
                    }
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

    private fun createContrastEnhancedBitmap(bitmap: Bitmap): Bitmap? {
        return try {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint()
            val cm = ColorMatrix()
            cm.setSaturation(0f) // Grayscale
            val contrast = 1.35f
            val translate = (-128f * contrast + 128f)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            output
        } catch (e: Exception) {
            Log.w(tag, "Failed to create contrast-enhanced bitmap: ${e.message}")
            null
        }
    }

    /**
     * Release underlying ML Kit resources when no longer needed.
     */
    fun close() {
        recognizer.close()
    }
}
