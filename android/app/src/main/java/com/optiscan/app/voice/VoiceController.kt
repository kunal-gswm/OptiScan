package com.optiscan.app.voice

import android.content.Context
import android.speech.SpeechRecognizer
import android.util.Log
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.Screen
import com.optiscan.app.speech.SpeechInputManager
import com.optiscan.app.speech.SpeechRecognitionState
import com.optiscan.app.speech.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Explicit states for hands-free voice interaction lifecycle.
 */
enum class VoiceControllerState {
    IDLE,
    LISTENING,
    COMMAND_PROCESSING,
    WAITING_FOR_USER,
    SPEAKING,
    ERROR,
    STOPPED
}

/**
 * Shared lifecycle-aware Voice Controller coordinating SpeechRecognizer, TextToSpeech,
 * command routing, and hands-free automatic listening resumption without audio echo.
 */
class VoiceController(
    private val context: Context,
    private val ttsManager: TtsManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val speechInputManagerFactory: ((Context) -> SpeechInputManager)? = null
) {
    private val tag = "VoiceController"

    private val speechInputManager: SpeechInputManager =
        speechInputManagerFactory?.invoke(context) ?: SpeechInputManager(context)

    private val _voiceState = MutableStateFlow(VoiceControllerState.IDLE)
    val voiceState: StateFlow<VoiceControllerState> = _voiceState.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow<String?>(null)
    val lastRecognizedText: StateFlow<String?> = _lastRecognizedText.asStateFlow()

    var onCommandDispatched: ((VoiceCommand, rawText: String) -> Unit)? = null
    var onFeedbackMessage: ((String) -> Unit)? = null

    // State & Coordination Flags
    var currentScreen: Screen = Screen.Welcome
    var currentLanguage: AppLanguage = AppLanguage.ENGLISH
    var hasAudioPermission: Boolean = false
    var isActivityForeground: Boolean = false
        private set
    var isUserStopped: Boolean = false
        private set

    private var consecutiveSilenceCount: Int = 0
    private var restartJob: Job? = null
    private var isTtsSpeaking: Boolean = false

    init {
        // Connect to SpeechRecognizer events
        speechInputManager.onRmsChanged = { rmsDb ->
            if (_voiceState.value == VoiceControllerState.LISTENING) {
                val normalized = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
                _micAmplitude.value = normalized
            } else {
                _micAmplitude.value = 0f
            }
        }

        speechInputManager.onStateChanged = { state ->
            handleSpeechState(state)
        }

        // Connect to TTS events for speech/listening coordination
        ttsManager.onSpeechStateChanged = { speaking ->
            isTtsSpeaking = speaking
            if (speaking) {
                // If TTS starts talking, stop recognition immediately to prevent echo
                restartJob?.cancel()
                speechInputManager.stopListening()
                _micAmplitude.value = 0f
                _voiceState.value = VoiceControllerState.SPEAKING
            }
        }

        ttsManager.onSpeechCompleted = {
            isTtsSpeaking = false
            Log.d(tag, "TTS completed. Evaluating automatic listening resumption.")
            // When TTS finishes vocalizing, resume listening if app is active and allowed
            if (isActivityForeground && !isUserStopped && hasAudioPermission) {
                scope.launch {
                    delay(300) // Brief pause to ensure audio hardware settles
                    if (!isTtsSpeaking && isActivityForeground && !isUserStopped) {
                        startListeningInternal()
                    }
                }
            } else if (!isUserStopped) {
                _voiceState.value = VoiceControllerState.IDLE
            }
        }
    }

    /**
     * Start listening for voice commands if eligible.
     */
    fun startListening() {
        isUserStopped = false
        consecutiveSilenceCount = 0
        if (!hasAudioPermission) {
            _voiceState.value = VoiceControllerState.ERROR
            val msg = if (currentLanguage == AppLanguage.HINDI) {
                "माइक्रोफ़ोन अनुमति आवश्यक है। कृपया अनुमति दें।"
            } else {
                "Microphone permission is required for voice control. Please grant permission."
            }
            onFeedbackMessage?.invoke(msg)
            return
        }
        if (!speechInputManager.isRecognitionAvailable()) {
            _voiceState.value = VoiceControllerState.ERROR
            val msg = if (currentLanguage == AppLanguage.HINDI) {
                "इस डिवाइस पर आवाज़ पहचान उपलब्ध नहीं है।"
            } else {
                "Speech recognition is not available on this device."
            }
            onFeedbackMessage?.invoke(msg)
            return
        }
        startListeningInternal()
    }

    private fun startListeningInternal() {
        if (!isActivityForeground) {
            Log.d(tag, "Suppressing listening start because activity is in background.")
            return
        }
        if (isTtsSpeaking) {
            Log.d(tag, "Suppressing listening start because TTS is actively speaking.")
            return
        }
        if (!hasAudioPermission) {
            _voiceState.value = VoiceControllerState.ERROR
            return
        }

        restartJob?.cancel()
        _voiceState.value = VoiceControllerState.LISTENING
        Log.i(tag, "Starting speech recognition in $currentLanguage on $currentScreen")
        speechInputManager.startListening(currentLanguage)
    }

    /**
     * Stop active listening session.
     */
    fun stopListening(userRequested: Boolean = false) {
        if (userRequested) {
            isUserStopped = true
            _voiceState.value = VoiceControllerState.STOPPED
        } else if (_voiceState.value != VoiceControllerState.SPEAKING) {
            _voiceState.value = VoiceControllerState.IDLE
        }
        restartJob?.cancel()
        speechInputManager.cancel()
        _micAmplitude.value = 0f
    }

    /**
     * Stop active speech output immediately and return to listening.
     */
    fun stopSpeaking() {
        ttsManager.stop()
        isTtsSpeaking = false
        if (isActivityForeground && !isUserStopped && hasAudioPermission) {
            scope.launch {
                delay(200)
                startListeningInternal()
            }
        } else {
            _voiceState.value = VoiceControllerState.IDLE
        }
    }

    private fun handleSpeechState(state: SpeechRecognitionState) {
        when (state) {
            is SpeechRecognitionState.Ready -> {
                if (_voiceState.value != VoiceControllerState.SPEAKING) {
                    _voiceState.value = VoiceControllerState.LISTENING
                }
            }

            is SpeechRecognitionState.Listening -> {
                if (_voiceState.value != VoiceControllerState.SPEAKING) {
                    _voiceState.value = VoiceControllerState.LISTENING
                }
            }

            is SpeechRecognitionState.Success -> {
                _micAmplitude.value = 0f
                consecutiveSilenceCount = 0
                val text = state.recognizedText
                Log.i(tag, "Recognized speech: \"$text\"")
                _lastRecognizedText.value = text
                _voiceState.value = VoiceControllerState.COMMAND_PROCESSING

                // Route through deterministic parser
                val command = VoiceCommandRouter.parse(text, currentScreen, currentLanguage)
                Log.i(tag, "Parsed command: $command")

                // Handle local voice controller commands or dispatch to ViewModel
                when (command) {
                    is VoiceCommand.StopSpeaking -> {
                        stopSpeaking()
                    }

                    is VoiceCommand.StopListening -> {
                        stopListening(userRequested = true)
                        val msg = if (currentLanguage == AppLanguage.HINDI) {
                            "आवाज़ सुनना रोक दिया गया है। शुरू करने के लिए सुनना शुरू करो बोलें।"
                        } else {
                            "Voice listening paused. Say Start listening to resume."
                        }
                        ttsManager.speak(msg)
                    }

                    is VoiceCommand.StartListening -> {
                        startListening()
                    }

                    else -> {
                        onCommandDispatched?.invoke(command, text)
                    }
                }
            }

            is SpeechRecognitionState.Error -> {
                _micAmplitude.value = 0f
                handleRecognitionError(state.errorCode, state.message)
            }

            is SpeechRecognitionState.Idle -> {
                _micAmplitude.value = 0f
            }
        }
    }

    private fun handleRecognitionError(errorCode: Int, message: String) {
        Log.w(tag, "Speech recognition error code: $errorCode - $message")

        when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                consecutiveSilenceCount++
                if (isActivityForeground && !isUserStopped && !isTtsSpeaking) {
                    if (consecutiveSilenceCount < 3) {
                        // Restart recognition after brief backoff
                        restartJob = scope.launch {
                            delay(400)
                            if (isActivityForeground && !isUserStopped && !isTtsSpeaking) {
                                startListeningInternal()
                            }
                        }
                    } else {
                        // Settle into WAITING_FOR_USER to prevent battery drain from rapid endless loops
                        _voiceState.value = VoiceControllerState.WAITING_FOR_USER
                        Log.d(tag, "Consecutive silences reached limit ($consecutiveSilenceCount). Entering WAITING_FOR_USER.")
                    }
                } else {
                    _voiceState.value = VoiceControllerState.IDLE
                }
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                // Recover from recognizer busy state with backoff
                restartJob = scope.launch {
                    delay(600)
                    if (isActivityForeground && !isUserStopped && !isTtsSpeaking) {
                        startListeningInternal()
                    }
                }
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                hasAudioPermission = false
                _voiceState.value = VoiceControllerState.ERROR
            }

            else -> {
                if (isActivityForeground && !isUserStopped && !isTtsSpeaking) {
                    restartJob = scope.launch {
                        delay(1000)
                        if (isActivityForeground && !isUserStopped && !isTtsSpeaking) {
                            startListeningInternal()
                        }
                    }
                } else {
                    _voiceState.value = VoiceControllerState.ERROR
                }
            }
        }
    }

    // --- Lifecycle Callbacks ---

    fun onActivityResume() {
        isActivityForeground = true
        Log.i(tag, "Activity resumed. Permission: $hasAudioPermission, userStopped: $isUserStopped")
        if (hasAudioPermission && !isUserStopped && !isTtsSpeaking) {
            scope.launch {
                delay(500) // Allow UI layout and window focus to settle
                if (isActivityForeground && !isUserStopped && !isTtsSpeaking) {
                    startListening()
                }
            }
        }
    }

    fun onActivityPause() {
        isActivityForeground = false
        Log.i(tag, "Activity paused. Ceasing speech recognition and TTS.")
        restartJob?.cancel()
        speechInputManager.cancel()
        ttsManager.stop()
        _micAmplitude.value = 0f
        _voiceState.value = VoiceControllerState.IDLE
    }

    fun onDestroy() {
        isActivityForeground = false
        restartJob?.cancel()
        speechInputManager.destroy()
        _micAmplitude.value = 0f
        _voiceState.value = VoiceControllerState.IDLE
    }
}
