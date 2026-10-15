package com.optiscan.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.optiscan.app.navigation.AppLanguage

/**
 * Lifecycle states for speech recognition.
 */
sealed interface SpeechRecognitionState {
    data object Idle : SpeechRecognitionState
    data object Ready : SpeechRecognitionState
    data object Listening : SpeechRecognitionState
    data class Success(val recognizedText: String) : SpeechRecognitionState
    data class Error(val errorCode: Int, val message: String) : SpeechRecognitionState
}

/**
 * Manages speech recognition for spoken questions supporting English ("en-US") and Hindi ("hi-IN")
 * with robust error handling and typed input fallback.
 */
class SpeechInputManager(
    private val context: Context,
    private val recognizerFactory: ((Context) -> SpeechRecognizer?) = { ctx ->
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } else {
            null
        }
    }
) {
    private val tag = "SpeechInputManager"
    private var recognizer: SpeechRecognizer? = null

    var onStateChanged: ((SpeechRecognitionState) -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null

    /**
     * Check if voice recognition service is supported on this device.
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Start listening for spoken questions in the selected application language.
     */
    fun startListening(language: AppLanguage) {
        if (!isRecognitionAvailable()) {
            val msg = if (language == AppLanguage.HINDI) {
                "इस डिवाइस पर आवाज़ पहचान उपलब्ध नहीं है। कृपया लिखकर प्रश्न पूछें।"
            } else {
                "Speech recognition is not available on this device. Please type your question."
            }
            onStateChanged?.invoke(SpeechRecognitionState.Error(-1, msg))
            return
        }

        try {
            destroy()
            recognizer = recognizerFactory(context)
            if (recognizer == null) {
                val msg = if (language == AppLanguage.HINDI) {
                    "आवाज़ पहचान सेवा शुरू नहीं हो सकी। कृपया लिखकर प्रश्न पूछें।"
                } else {
                    "Could not initialize speech recognition service. Please type your question."
                }
                onStateChanged?.invoke(SpeechRecognitionState.Error(-1, msg))
                return
            }

            val targetLocaleTag = if (language == AppLanguage.HINDI) "hi-IN" else "en-US"

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocaleTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocaleTag)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(targetLocaleTag))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStateChanged?.invoke(SpeechRecognitionState.Ready)
                }

                override fun onBeginningOfSpeech() {
                    onStateChanged?.invoke(SpeechRecognitionState.Listening)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    onRmsChanged?.invoke(rmsdB)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val errorMsg = mapErrorCodeToMessage(error, language)
                    Log.w(tag, "Speech recognition error: $error -> $errorMsg")
                    onStateChanged?.invoke(SpeechRecognitionState.Error(error, errorMsg))
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (!text.isNullOrBlank()) {
                        Log.i(tag, "Speech recognized: $text")
                        onStateChanged?.invoke(SpeechRecognitionState.Success(text))
                    } else {
                        val emptyMsg = if (language == AppLanguage.HINDI) {
                            "कोई आवाज़ नहीं पहचानी गई। कृपया दोबारा प्रयास करें या टाइप करें।"
                        } else {
                            "No speech recognized. Please try again or type your question."
                        }
                        onStateChanged?.invoke(SpeechRecognitionState.Error(SpeechRecognizer.ERROR_NO_MATCH, emptyMsg))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer?.startListening(intent)
            onStateChanged?.invoke(SpeechRecognitionState.Ready)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start speech recognition", e)
            val msg = e.message ?: "Speech recognition error"
            onStateChanged?.invoke(SpeechRecognitionState.Error(-1, msg))
        }
    }

    /**
     * Stop listening to current utterance.
     */
    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
    }

    /**
     * Cancel active recognition session.
     */
    fun cancel() {
        try {
            recognizer?.cancel()
            onStateChanged?.invoke(SpeechRecognitionState.Idle)
        } catch (_: Exception) {}
    }

    /**
     * Clean up recognizer resources.
     */
    fun destroy() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    companion object {
        fun mapErrorCodeToMessage(errorCode: Int, language: AppLanguage): String {
            val isHindi = language == AppLanguage.HINDI
            return when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO ->
                    if (isHindi) "ऑडियो रिकॉर्डिंग में त्रुटि। कृपया माइक्रोफ़ोन जांचें।" else "Audio recording error. Please check your microphone."
                SpeechRecognizer.ERROR_CLIENT ->
                    if (isHindi) "क्लाइंट त्रुटि। कृपया पुनः प्रयास करें।" else "Speech recognition client error. Please try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    if (isHindi) "माइक्रोफ़ोन अनुमति आवश्यक है। कृपया अनुमति दें या नीचे टाइप करें।" else "Microphone permission is required for voice input. Please grant permission or type below."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    if (isHindi) "नेटवर्क त्रुटि। कृपया टाइप करके प्रश्न पूछें।" else "Network error during speech recognition. Please type your question."
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    if (isHindi) "कोई आवाज़ नहीं सुनाई दी। कृपया दोबारा बोलें या टाइप करें।" else "No speech detected. Please speak clearly or type your question."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    if (isHindi) "पहचान सेवा व्यस्त है। कृपया एक क्षण बाद प्रयास करें।" else "Speech recognition is busy. Please wait a moment."
                SpeechRecognizer.ERROR_SERVER ->
                    if (isHindi) "सर्वर त्रुटि। कृपया लिखकर प्रश्न पूछें।" else "Server error during recognition. Please type your question."
                else ->
                    if (isHindi) "आवाज़ पहचान में समस्या आई। कृपया टाइप करें।" else "Voice recognition issue. Please type your question."
            }
        }
    }
}
