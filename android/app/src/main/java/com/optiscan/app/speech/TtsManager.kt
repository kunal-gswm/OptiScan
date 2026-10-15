package com.optiscan.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.optiscan.app.navigation.AppLanguage
import java.util.Locale

/**
 * Text-to-Speech manager providing accessible spoken feedback to visually impaired users.
 */
open class TtsManager(
    context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val tag = "TtsManager"
    private var tts: TextToSpeech? = try {
        TextToSpeech(context.applicationContext, this)
    } catch (_: Exception) {
        null
    }

    var isReady: Boolean = false
        internal set

    var voiceFeedbackEnabled: Boolean = true
    var currentSpeechRate: Float = 1.0f
        private set

    var currentLanguage: AppLanguage = AppLanguage.ENGLISH
        private set

    var isLanguageFallbackActive: Boolean = false
        internal set

    var onLanguageFallback: ((AppLanguage) -> Unit)? = null

    var onSpeechStateChanged: ((isSpeaking: Boolean) -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLanguage(currentLanguage)
            tts?.setSpeechRate(currentSpeechRate)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(false)
                    onSpeechCompleted?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(false)
                    onSpeechCompleted?.invoke()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onSpeechStateChanged?.invoke(false)
                    onSpeechCompleted?.invoke()
                }
            })

            isReady = true
            Log.i(tag, "TTS initialized successfully")
            onInitComplete?.invoke(true)
        } else {
            Log.e(tag, "TTS initialization failed with status: $status")
            isReady = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Set language for speech output with graceful fallback to US English.
     * Returns true if the requested language was successfully configured.
     */
    open fun setLanguage(language: AppLanguage): Boolean {
        currentLanguage = language
        if (!isReady) return false
        return applyLanguage(language)
    }

    internal fun applyLanguage(language: AppLanguage): Boolean {
        val targetLocale = when (language) {
            AppLanguage.HINDI -> Locale("hi", "IN")
            AppLanguage.ENGLISH -> Locale.US
        }
        val availability = tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        val isSupported = availability != TextToSpeech.LANG_NOT_SUPPORTED && availability != TextToSpeech.LANG_MISSING_DATA

        if (isSupported) {
            val result = tts?.setLanguage(targetLocale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isLanguageFallbackActive = false
                Log.i(tag, "TTS language successfully configured to: $targetLocale")
                return true
            }
        }

        // Graceful fallback to English if Hindi voice data is missing or unsupported
        Log.w(tag, "Requested locale $targetLocale unavailable (status code: $availability). Falling back to Locale.US")
        tts?.language = Locale.US
        val fallbackOccurred = (language != AppLanguage.ENGLISH)
        isLanguageFallbackActive = fallbackOccurred
        if (fallbackOccurred) {
            onLanguageFallback?.invoke(language)
        }
        return false
    }

    internal fun setTtsForTesting(testTts: TextToSpeech?, ready: Boolean = true) {
        this.tts = testTts
        this.isReady = ready
    }

    /**
     * Set playback speech rate (e.g. 0.75f, 1.0f, 1.5f).
     */
    open fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.5f)
        tts?.setSpeechRate(currentSpeechRate)
    }

    /**
     * Speak text aloud if voice feedback is enabled.
     */
    open fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!voiceFeedbackEnabled) {
            Log.d(tag, "Voice feedback is disabled in settings; suppressing speech")
            return
        }
        if (!isReady || text.isBlank()) {
            Log.w(tag, "TTS not ready or empty text requested")
            return
        }
        val utteranceId = "OptiScan_${System.currentTimeMillis()}"
        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Stop any ongoing speech immediately.
     */
    open fun stop() {
        tts?.stop()
        onSpeechStateChanged?.invoke(false)
    }

    /**
     * Check if TTS engine is currently vocalizing.
     */
    open fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    /**
     * Release TTS engine resources.
     */
    open fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
