package com.optiscan.app.ondevice

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * State of the on-device inference engine.
 */
sealed interface InferenceState {
    data object Unloaded : InferenceState
    data object Loading : InferenceState
    data object Ready : InferenceState
    data class Generating(val partialText: String) : InferenceState
    data class Failed(val reason: String) : InferenceState
}

/**
 * On-device inference engine wrapping the llama.cpp Android JNI layer.
 *
 * Architecture:
 * - Uses llama.cpp's multimodal support (clip_image_encode + llama_eval) for Qwen2-VL
 * - The MMPROJ file contains the vision encoder (CLIP projector) weights
 * - The main GGUF contains the language model weights
 * - Both are loaded once and kept in memory; the image is encoded once per captured photo
 *
 * Threading: all native calls happen on Dispatchers.IO to keep UI thread free.
 *
 * Device requirements:
 * - Android API 26+ (minSdk matches app)
 * - ARMv8-A (arm64-v8a) CPU — required for NEON SIMD acceleration
 * - 3+ GB RAM recommended (Qwen2-VL-2B with Q4 quantization uses ~2.2 GB loaded)
 *
 * Privacy: no network calls, no data leaves the device.
 *
 * Note on JNI binding strategy:
 * We use a pure-Kotlin JNA/JNI reflection approach with llama.cpp's shared library (.so).
 * The .so is compiled separately and placed in jniLibs/arm64-v8a/.
 * For the hackathon/dev build we provide a STUB implementation that returns
 * realistic canned text so you can run the app without the native .so.
 * Set USE_STUB = false once the native llama.cpp .so is compiled and placed in jniLibs/.
 */
class OnDeviceInferenceEngine(private val context: Context) {

    private val tag = "OnDeviceInferenceEngine"

    companion object {
        /**
         * Set to false once the native llama.cpp .so is compiled and placed in
         * android/app/src/main/jniLibs/arm64-v8a/libllama.so
         *
         * The native build requires:
         * 1. Clone llama.cpp repo
         * 2. Run: mkdir build-android && cd build-android
         *    cmake .. -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
         *             -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
         *             -DLLAMA_LLAMAFILE=OFF -DLLAMA_NATIVE=OFF \
         *             -DLLAMA_BUILD_SERVER=OFF -DBUILD_SHARED_LIBS=ON
         *    make -j$(nproc)
         * 3. Copy build-android/src/libllama.so to jniLibs/arm64-v8a/
         * 4. Copy build-android/examples/llava/libllava.so to jniLibs/arm64-v8a/
         */
        const val USE_STUB = true

        /** Max new tokens to generate per response */
        const val MAX_NEW_TOKENS = 512

        /** Context window size (tokens) */
        const val CONTEXT_SIZE = 2048

        /** Number of CPU threads to use for inference */
        const val CPU_THREADS = 4

        /** GPU layers to offload (0 = CPU only; increase if device has Vulkan/OpenCL support) */
        const val GPU_LAYERS = 0

        init {
            if (!USE_STUB) {
                try {
                    System.loadLibrary("llama")
                    System.loadLibrary("llava")
                    Log.i("OnDeviceInferenceEngine", "Native llama.cpp libraries loaded")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w("OnDeviceInferenceEngine", "Native libraries not found — running in stub mode: ${e.message}")
                }
            }
        }
    }

    private val _inferenceState = MutableStateFlow<InferenceState>(InferenceState.Unloaded)
    val inferenceState: StateFlow<InferenceState> = _inferenceState.asStateFlow()

    /** Benchmark: time taken to load model into memory (ms) */
    var lastModelLoadTimeMs: Long = 0L
        private set

    /** Benchmark: time taken for last inference call (ms) */
    var lastInferenceTimeMs: Long = 0L
        private set

    /** Opaque handle to native llama context (0L = not loaded) */
    private var nativeCtxHandle: Long = 0L

    /** Opaque handle to native llava clip context (0L = not loaded) */
    private var nativeClipHandle: Long = 0L

    /**
     * Load the GGUF model into memory. Must be called before [generateResponse].
     * This is an expensive operation (~5-15 seconds on mid-range device) — run in a coroutine.
     */
    suspend fun loadModel(
        modelFile: File,
        mmprojFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        _inferenceState.value = InferenceState.Loading
        val startMs = System.currentTimeMillis()

        try {
            if (USE_STUB) {
                // Stub: simulate model loading delay
                kotlinx.coroutines.delay(1500L)
                Log.i(tag, "[STUB] Model loaded (stub mode — no native .so)")
                nativeCtxHandle = 1L   // non-zero signals "loaded"
                nativeClipHandle = 2L
            } else {
                // Native: call through JNI
                nativeCtxHandle = nativeLoadModel(
                    modelPath = modelFile.absolutePath,
                    contextSize = CONTEXT_SIZE,
                    numThreads = CPU_THREADS,
                    gpuLayers = GPU_LAYERS
                )
                nativeClipHandle = nativeLoadClip(mmprojFile.absolutePath, CPU_THREADS)

                if (nativeCtxHandle == 0L || nativeClipHandle == 0L) {
                    _inferenceState.value = InferenceState.Failed("Native model context could not be created. Device may lack sufficient RAM.")
                    return@withContext Result.failure(Exception("Native context creation failed"))
                }
            }

            lastModelLoadTimeMs = System.currentTimeMillis() - startMs
            Log.i(tag, "Model loaded in ${lastModelLoadTimeMs}ms")
            _inferenceState.value = InferenceState.Ready
            Result.success(Unit)

        } catch (e: UnsatisfiedLinkError) {
            val msg = "Native library not found (libllama.so). This build uses stub mode. Set USE_STUB=false and compile llama.cpp."
            Log.e(tag, msg, e)
            _inferenceState.value = InferenceState.Failed(msg)
            Result.failure(Exception(msg))
        } catch (e: CancellationException) {
            _inferenceState.value = InferenceState.Unloaded
            throw e
        } catch (e: Exception) {
            val msg = "Model load failed: ${e.message}"
            Log.e(tag, msg, e)
            _inferenceState.value = InferenceState.Failed(msg)
            Result.failure(e)
        }
    }

    /**
     * Generate a response for [userQuery] optionally using [imageBitmap] as visual context.
     * [conversationHistory] contains prior turns as alternating (user, assistant) strings.
     *
     * This is the main inference entry point. It runs fully on-device.
     * @return Result<String> with the generated answer, or failure with error info.
     */
    suspend fun generateResponse(
        userQuery: String,
        imageBitmap: Bitmap?,
        conversationHistory: List<ConversationTurn>,
        language: String = "en",
        onPartialToken: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (nativeCtxHandle == 0L) {
            return@withContext Result.failure(Exception("Model not loaded. Please load the model first."))
        }

        _inferenceState.value = InferenceState.Generating("")
        val startMs = System.currentTimeMillis()

        try {
            val result = if (USE_STUB) {
                generateStubResponse(userQuery, imageBitmap, conversationHistory, language)
            } else {
                generateNativeResponse(userQuery, imageBitmap, conversationHistory, onPartialToken)
            }

            lastInferenceTimeMs = System.currentTimeMillis() - startMs
            Log.i(tag, "Inference completed in ${lastInferenceTimeMs}ms (${result.length} chars)")
            _inferenceState.value = InferenceState.Ready
            Result.success(result)

        } catch (e: CancellationException) {
            _inferenceState.value = InferenceState.Ready
            throw e
        } catch (e: Exception) {
            val msg = "Inference failed: ${e.message ?: "Unknown error"}"
            Log.e(tag, msg, e)
            _inferenceState.value = InferenceState.Failed(msg)
            Result.failure(Exception(msg))
        }
    }

    /**
     * Stub response generator — realistic, coherent, multi-lingual.
     * Replaced by native inference once llama.cpp .so is compiled.
     */
    private suspend fun generateStubResponse(
        userQuery: String,
        imageBitmap: Bitmap?,
        history: List<ConversationTurn>,
        language: String
    ): String {
        // Simulate inference delay (proportional to expected output)
        kotlinx.coroutines.delay(800L + (userQuery.length * 8L).coerceAtMost(2000L))

        val isHindi = language == "hi"
        val hasImage = imageBitmap != null
        val queryLower = userQuery.lowercase()

        val prefix = if (isHindi) "[सिम्युलेटेड डेमो] " else "[Simulated Demo] "
        val body = when {
            // Image description queries
            hasImage && (queryLower.contains("what") || queryLower.contains("describe") ||
                         queryLower.contains("see") || queryLower.contains("क्या") ||
                         queryLower.contains("देख") || queryLower.contains("बताओ")) -> {
                if (isHindi) {
                    "छवि में एक इनडोर वातावरण दिख रहा है। सामने एक टेबल है जिस पर कुछ वस्तुएं रखी हैं। " +
                    "पृष्ठभूमि में एक खिड़की है जिससे प्राकृतिक रोशनी आ रही है। " +
                    "यह एक सुरक्षित और साफ स्थान प्रतीत होता है।"
                } else {
                    "I can see an indoor environment in the image. There is a table in the foreground " +
                    "with several objects placed on it. In the background, there is a window letting " +
                    "in natural light. The space appears to be clean and unobstructed. " +
                    "No immediate hazards are visible in the walking path."
                }
            }

            // Text reading queries
            hasImage && (queryLower.contains("text") || queryLower.contains("read") ||
                         queryLower.contains("write") || queryLower.contains("पढ़")) -> {
                if (isHindi) {
                    "छवि में कुछ लिखा हुआ है। मैं पढ़ सकता हूँ: 'सामग्री: पानी, चीनी, नमक। " +
                    "निर्माण तिथि: अक्टूबर 2024। समाप्ति तिथि: अक्टूबर 2025।' " +
                    "यह किसी उत्पाद का लेबल प्रतीत होता है।"
                } else {
                    "I can read the text in the image: 'Ingredients: Water, Sugar, Salt. " +
                    "Manufactured: October 2024. Best before: October 2025.' " +
                    "This appears to be a product label."
                }
            }

            // Follow-up questions (has history)
            history.isNotEmpty() -> {
                if (isHindi) {
                    "आपके पिछले प्रश्न के संदर्भ में: ${userQuery} — " +
                    "हाँ, मैं समझ सकता हूँ। पिछली छवि के आधार पर, " +
                    "यह जानकारी उपलब्ध है। क्या आप कोई और प्रश्न पूछना चाहेंगे?"
                } else {
                    "Following up on our conversation: regarding '${userQuery}' — " +
                    "Based on what I analyzed in the image, I can provide more context. " +
                    "The item you're asking about appears to be in the foreground of the scene. " +
                    "Would you like me to describe any specific aspect in more detail?"
                }
            }

            // Generic fallback
            else -> {
                if (isHindi) {
                    "मैं आपकी सहायता करने के लिए तैयार हूँ। कृपया अपना प्रश्न स्पष्ट करें या एक छवि कैप्चर करें " +
                    "ताकि मैं आपको सटीक जानकारी दे सकूं।"
                } else {
                    "I'm ready to help you analyze images and answer questions about your surroundings. " +
                    "Please capture a photo and I'll provide a detailed description, or ask me " +
                    "a specific question about what you'd like to know."
                }
            }
        }
        return prefix + body
    }

    /**
     * Native inference via llama.cpp JNI.
     * Called only when USE_STUB = false and libllama.so is loaded.
     */
    private suspend fun generateNativeResponse(
        userQuery: String,
        imageBitmap: Bitmap?,
        history: List<ConversationTurn>,
        onPartialToken: (String) -> Unit
    ): String {
        // Build the chat prompt in Qwen2-VL chat template format
        val prompt = buildQwenChatPrompt(userQuery, history, imageBitmap != null)

        // Encode image to JPEG bytes for llava clip encoder
        val imageBytes: ByteArray? = imageBitmap?.let { bmp ->
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        }

        // Call native inference (blocking — runs on Dispatchers.IO)
        return nativeInfer(
            ctxHandle = nativeCtxHandle,
            clipHandle = nativeClipHandle,
            prompt = prompt,
            imageBytes = imageBytes,
            maxNewTokens = MAX_NEW_TOKENS,
            temperature = 0.7f,
            topP = 0.9f
        )
    }

    /**
     * Build a Qwen2-VL compatible chat prompt string.
     * Format: <|im_start|>system\n...<|im_end|>\n<|im_start|>user\n...<|im_end|>\n<|im_start|>assistant\n
     */
    private fun buildQwenChatPrompt(
        userQuery: String,
        history: List<ConversationTurn>,
        hasImage: Boolean
    ): String {
        val sb = StringBuilder()

        // System prompt
        sb.append("<|im_start|>system\n")
        sb.append("You are OptiScan, an AI assistant designed specifically for blind and low-vision users. ")
        sb.append("Describe visual scenes clearly and concisely. ")
        sb.append("For scene descriptions, mention: objects, colors, distances, hazards, and navigation cues. ")
        sb.append("For text reading, read exactly what is written. ")
        sb.append("Be factual, calm, and precise. Keep responses under 3 sentences unless asked for more detail.")
        sb.append("<|im_end|>\n")

        // Conversation history
        for (turn in history) {
            sb.append("<|im_start|>${if (turn.isUser) "user" else "assistant"}\n")
            sb.append(turn.text)
            sb.append("<|im_end|>\n")
        }

        // Current user turn
        sb.append("<|im_start|>user\n")
        if (hasImage) {
            sb.append("<image>\n")  // llava image token placeholder
        }
        sb.append(userQuery)
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    /**
     * Release all native resources. Must be called when the engine is no longer needed.
     */
    fun release() {
        if (!USE_STUB && nativeCtxHandle != 0L) {
            try {
                nativeFreeModel(nativeCtxHandle)
                nativeFreeClip(nativeClipHandle)
            } catch (e: Exception) {
                Log.w(tag, "Error releasing native resources", e)
            }
        }
        nativeCtxHandle = 0L
        nativeClipHandle = 0L
        _inferenceState.value = InferenceState.Unloaded
        Log.i(tag, "Inference engine released")
    }

    fun isLoaded(): Boolean = nativeCtxHandle != 0L

    // ---- Native JNI declarations (implemented in libllama.so + JNI glue) ----
    // These are only called when USE_STUB = false.

    private external fun nativeLoadModel(
        modelPath: String,
        contextSize: Int,
        numThreads: Int,
        gpuLayers: Int
    ): Long

    private external fun nativeLoadClip(mmprojPath: String, numThreads: Int): Long

    private external fun nativeInfer(
        ctxHandle: Long,
        clipHandle: Long,
        prompt: String,
        imageBytes: ByteArray?,
        maxNewTokens: Int,
        temperature: Float,
        topP: Float
    ): String

    private external fun nativeFreeModel(ctxHandle: Long)
    private external fun nativeFreeClip(clipHandle: Long)
}

/**
 * A single turn in an on-device conversation.
 */
data class ConversationTurn(
    val text: String,
    val isUser: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
