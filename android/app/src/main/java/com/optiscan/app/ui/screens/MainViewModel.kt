package com.optiscan.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optiscan.app.camera.CameraManager
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.data.repository.ScanRepository
import com.optiscan.app.data.repository.ScanRepositoryImpl
import com.optiscan.app.data.preferences.PreferencesManager
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.speech.SpeechRecognitionState
import com.optiscan.app.speech.TtsManager
import com.optiscan.app.ui.components.aurora.AssistantState
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.voice.VoiceCommand
import com.optiscan.app.voice.VoiceCommandRouter
import com.optiscan.app.voice.VoiceController
import com.optiscan.app.voice.VoiceControllerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * State machine phases for interactive guided physical assistance (AROMA / Be My Eyes model).
 */
enum class GuidedTaskState {
    IDLE,
    OBSERVING,
    AWAITING_CLARIFICATION,
    INSTRUCTING,
    AWAITING_USER_ACTION,
    COMPLETED,
    STOPPED
}

/**
 * Session data keeping track of the user's current goal, procedural steps, and clarification state.
 */
data class GuidedTaskSession(
    val goal: String = "make tea",
    val state: GuidedTaskState = GuidedTaskState.IDLE,
    val currentStepIndex: Int = 0,
    val currentInstruction: String = "",
    val needsClarification: Boolean = false,
    val isTaskComplete: Boolean = false,
    val stepHistory: List<String> = emptyList()
)

/**
 * Single source of truth for OptiScan navigation, camera state, visual analysis, accessibility, and volume key control.
 */
class MainViewModel(
    val cameraManager: CameraManager = CameraManager(),
    val repository: ScanRepository = ScanRepositoryImpl(),
    private var preferencesManager: PreferencesManager? = null
) : ViewModel() {

    private val tag = "MainViewModel"
    private var ttsManager: TtsManager? = null
    var voiceController: VoiceController? = null
        private set
    private var appContext: Context? = null

    // Navigation Stack
    private val backStack = mutableListOf<Screen>()
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Welcome)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Camera State
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _capturedBytes = MutableStateFlow<ByteArray?>(null)
    val capturedBytes: StateFlow<ByteArray?> = _capturedBytes.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _isSpeechRecognitionAvailable = MutableStateFlow(true)
    val isSpeechRecognitionAvailable: StateFlow<Boolean> = _isSpeechRecognitionAvailable.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    // Speech State
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // TalkBack / Screen Reader State
    private val _isTalkBackActive = MutableStateFlow(false)
    val isTalkBackActive: StateFlow<Boolean> = _isTalkBackActive.asStateFlow()

    // Analysis State
    private var analysisJob: Job? = null
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // Guided Task State
    private val _guidedTaskSession = MutableStateFlow<GuidedTaskSession?>(null)
    val guidedTaskSession: StateFlow<GuidedTaskSession?> = _guidedTaskSession.asStateFlow()

    // Voice Input & Assistant State Machine
    private val _speechState = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    val speechState: StateFlow<SpeechRecognitionState> = _speechState.asStateFlow()

    private val _voiceControllerState = MutableStateFlow(VoiceControllerState.IDLE)
    val voiceControllerState: StateFlow<VoiceControllerState> = _voiceControllerState.asStateFlow()

    private val _speechAmplitude = MutableStateFlow(0f)
    val speechAmplitude: StateFlow<Float> = _speechAmplitude.asStateFlow()

    // Line-by-line reading state for OCR
    private var ocrLines: List<String> = emptyList()
    private var currentOcrLineIndex: Int = -1

    val assistantState: StateFlow<AssistantState> = combine(
        _isAnalyzing,
        _isSpeaking,
        _speechState,
        _voiceControllerState,
        _analysisError
    ) { isAnalyzing, isSpeaking, speechState, voiceState, error ->
        when {
            error != null || voiceState == VoiceControllerState.ERROR -> AssistantState.ERROR
            voiceState == VoiceControllerState.COMMAND_PROCESSING -> AssistantState.COMMAND_PROCESSING
            voiceState == VoiceControllerState.LISTENING || speechState is SpeechRecognitionState.Listening || speechState is SpeechRecognitionState.Ready -> AssistantState.LISTENING
            voiceState == VoiceControllerState.WAITING_FOR_USER -> AssistantState.WAITING_FOR_USER
            isAnalyzing -> AssistantState.PROCESSING
            isSpeaking || voiceState == VoiceControllerState.SPEAKING -> AssistantState.SPEAKING
            voiceState == VoiceControllerState.STOPPED -> AssistantState.STOPPED
            speechState is SpeechRecognitionState.Success -> AssistantState.SUCCESS
            else -> AssistantState.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantState.IDLE)

    fun updateSpeechState(state: SpeechRecognitionState) {
        _speechState.value = state
    }

    fun updateSpeechAmplitude(rmsdB: Float) {
        // Map decibels to normalized 0f..1f range for visual feedback
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _speechAmplitude.value = normalized
    }

    init {
        val loadedSettings = preferencesManager?.loadSettings() ?: AppSettings()
        _settings.value = loadedSettings
        repository.setMockMode(loadedSettings.useMockBackend)
        if (loadedSettings.customBackendUrl.isNotBlank()) {
            repository.updateBaseUrl(loadedSettings.customBackendUrl)
        }
    }

    fun attachPreferencesManager(prefs: PreferencesManager) {
        this.preferencesManager = prefs
        val loaded = prefs.loadSettings()
        _settings.value = loaded
        repository.setMockMode(loaded.useMockBackend)
        if (loaded.customBackendUrl.isNotBlank()) {
            repository.updateBaseUrl(loaded.customBackendUrl)
        }
        applySettingsToTts(loaded)
    }

    fun attachTtsManager(tts: TtsManager) {
        this.ttsManager = tts
        tts.onSpeechStateChanged = { speaking ->
            _isSpeaking.value = speaking
        }
        tts.onLanguageFallback = { lang ->
            Log.w(tag, "TTS language fallback occurred for $lang")
        }
        applySettingsToTts(_settings.value)
    }

    fun attachContext(context: Context) {
        this.appContext = context.applicationContext
    }

    fun setAudioPermission(granted: Boolean) {
        _hasAudioPermission.value = granted
        voiceController?.hasAudioPermission = granted
    }

    fun attachVoiceController(controller: VoiceController) {
        this.voiceController = controller
        controller.hasAudioPermission = _hasAudioPermission.value
        controller.currentScreen = _currentScreen.value
        controller.currentLanguage = _settings.value.language

        controller.onCommandDispatched = { command, rawText ->
            handleVoiceCommand(command, rawText)
        }
        controller.onFeedbackMessage = { msg ->
            speak(msg)
        }

        viewModelScope.launch {
            controller.voiceState.collect { vState ->
                _voiceControllerState.value = vState
            }
        }
        viewModelScope.launch {
            controller.micAmplitude.collect { amp ->
                _speechAmplitude.value = amp
            }
        }
    }

    fun initAccessibility(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        _isTalkBackActive.value = am?.isTouchExplorationEnabled == true
        am?.addTouchExplorationStateChangeListener { enabled ->
            _isTalkBackActive.value = enabled
        }
    }

    fun setCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _capturedBitmap.value = bitmap
    }

    fun setCapturedImage(bitmap: Bitmap?, bytes: ByteArray?) {
        _capturedBitmap.value = bitmap
        _capturedBytes.value = bytes
    }

    // Navigation methods
    fun navigateTo(screen: Screen, clearBackstack: Boolean = false) {
        if (screen !is Screen.ReadTextResult && screen !is Screen.DescriptionResult && screen !is Screen.GuidedTask) {
            stopSpeech()
        }
        if (clearBackstack) {
            backStack.clear()
        } else {
            backStack.add(_currentScreen.value)
        }
        _currentScreen.value = screen
        voiceController?.currentScreen = screen
        if (screen is Screen.ReadTextResult) {
            initOcrLines(screen.text)
        }
    }

    fun navigateBack(): Boolean {
        stopSpeech()
        val handled = if (backStack.isNotEmpty()) {
            val prev = backStack.removeAt(backStack.size - 1)
            _currentScreen.value = prev
            true
        } else if (_currentScreen.value !is Screen.Home && _currentScreen.value !is Screen.Welcome) {
            _currentScreen.value = Screen.Home
            true
        } else {
            false
        }
        if (handled) {
            voiceController?.currentScreen = _currentScreen.value
            val curr = _currentScreen.value
            if (curr is Screen.ReadTextResult) {
                initOcrLines(curr.text)
            }
        }
        return handled
    }

    /**
     * Executes photo capture from CameraX and moves to ImagePreview or directly analyzes.
     * Prevents overlapping concurrent captures and verifies camera permission.
     */
    fun capturePhoto(context: Context, task: ScanTask, autoAnalyze: Boolean = false) {
        if (_isCapturing.value) {
            Log.d(tag, "Capture already in progress; ignoring duplicate request.")
            return
        }

        val isHindi = _settings.value.language == AppLanguage.HINDI
        val strings = AppStrings.get(_settings.value.language)

        if (!_hasCameraPermission.value) {
            speak(strings.cameraPermissionMessage)
            return
        }

        _isCapturing.value = true
        viewModelScope.launch {
            try {
                speak(if (isHindi) "फोटो खींच रहे हैं। कृपया फोन स्थिर रखें।" else "Capturing photo. Please hold steady.")
                val result = cameraManager.capturePhoto(context)
                if (result.isSuccess) {
                    val bytes = result.getOrNull()
                    val bmp = bytes?.let { cameraManager.bytesToBitmap(it) }
                    setCapturedImage(bmp, bytes)
                    if (autoAnalyze) {
                        navigateTo(Screen.Processing(task, bytes))
                        analyzeCapturedImage(task, bytes)
                    } else {
                        navigateTo(Screen.ImagePreview(task, bytes))
                    }
                } else {
                    val err = if (isHindi) {
                        "फोटो खींचने में विफलता। कृपया दोबारा प्रयास करें।"
                    } else {
                        "Failed to capture photo. Please try again."
                    }
                    speak(err)
                }
            } finally {
                _isCapturing.value = false
            }
        }
    }

    /**
     * Hardware Volume Key controller allowing blind users to operate the app physically.
     */
    fun handleVolumeKey(context: Context, keyCode: Int): Boolean {
        val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (!isVolumeUp && !isVolumeDown) return false

        val isHindi = _settings.value.language == AppLanguage.HINDI
        val strings = AppStrings.get(_settings.value.language)

        when (val screen = _currentScreen.value) {
            is Screen.Camera -> {
                // Shutter trigger on Camera screen
                capturePhoto(context, screen.task)
                return true
            }

            is Screen.ImagePreview -> {
                // Volume Up: Use photo; Volume Down: Retake
                if (isVolumeUp) {
                    speak(if (isHindi) "फोटो का उपयोग कर रहे हैं। विश्लेषण जारी है।" else "Using photo. Analyzing.")
                    navigateTo(Screen.Processing(screen.task, screen.imageBytes))
                } else {
                    speak(if (isHindi) "फोटो दोबारा खींच रहे हैं। कैमरा खुला।" else "Retaking photo. Camera opened.")
                    navigateTo(Screen.Camera(screen.task))
                }
                return true
            }

            is Screen.ReadTextResult -> {
                // Toggle speech playback
                if (_isSpeaking.value) {
                    stopSpeech()
                } else {
                    speak(screen.text)
                }
                return true
            }

            is Screen.DescriptionResult -> {
                // Toggle speech playback
                if (_isSpeaking.value) {
                    stopSpeech()
                } else {
                    speak(screen.description)
                }
                return true
            }

            is Screen.GuidedTask -> {
                // Toggle speech playback of current guided instruction
                val instruction = _guidedTaskSession.value?.currentInstruction ?: screen.currentInstruction ?: ""
                if (_isSpeaking.value) {
                    stopSpeech()
                } else if (instruction.isNotBlank()) {
                    speak(instruction)
                }
                return true
            }

            is Screen.Welcome -> {
                speak(if (isHindi) "शुरू करें सक्रिय हुआ। ऑप्टीस्कैन होम में आपका स्वागत है।" else "Get Started activated. Welcome to OptiScan Home.")
                navigateTo(Screen.Home, clearBackstack = true)
                return true
            }

            is Screen.Home -> {
                speak(strings.homeSpokenAnnouncement)
                return true
            }

            else -> return false
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        val previousSettings = _settings.value
        val previousLanguage = previousSettings.language
        _settings.value = newSettings
        preferencesManager?.saveSettings(newSettings)
        repository.setMockMode(newSettings.useMockBackend)
        if (newSettings.customBackendUrl != previousSettings.customBackendUrl && newSettings.customBackendUrl.isNotBlank()) {
            repository.updateBaseUrl(newSettings.customBackendUrl)
        }
        applySettingsToTts(newSettings)
        voiceController?.currentLanguage = newSettings.language

        // Announce language change to user with immediate accessibility feedback
        if (previousLanguage != newSettings.language) {
            val strings = AppStrings.get(newSettings.language)
            if (ttsManager?.isLanguageFallbackActive == true && newSettings.language == AppLanguage.HINDI) {
                speak(strings.hindiTtsFallbackNotice)
            } else {
                speak(strings.languageChangedAnnouncement)
            }
        }
    }

    private fun applySettingsToTts(appSettings: AppSettings) {
        ttsManager?.voiceFeedbackEnabled = appSettings.voiceFeedbackEnabled
        ttsManager?.setSpeechRate(appSettings.speechRate)
        ttsManager?.setLanguage(appSettings.language)
    }

    // Visual Analysis Pipeline
    fun analyzeCapturedImage(task: ScanTask, imageBytes: ByteArray? = null) {
        // Prevent duplicate concurrent requests
        if (_isAnalyzing.value) {
            Log.d(tag, "Analysis already in progress; ignoring duplicate request.")
            return
        }

        val lang = _settings.value.language
        val strings = AppStrings.get(lang)
        val bytesToAnalyze = imageBytes ?: _capturedBytes.value
        val isMock = repository.isMockMode()

        if (bytesToAnalyze == null && !isMock) {
            val err = if (lang == AppLanguage.HINDI) {
                "कोई फोटो डेटा नहीं मिला। कृपया पहले फोटो लें।"
            } else {
                "No captured image data found. Please capture a photo first."
            }
            _analysisError.value = err
            speak(err)
            return
        }

        _isAnalyzing.value = true
        _analysisError.value = null

        analysisJob = viewModelScope.launch {
            try {
                when (task) {
                    ScanTask.READ_TEXT -> {
                        speak(strings.processingReadingText)

                        // Step 1: Run on-device ML Kit OCR if bitmap is available
                        var onDeviceText: String? = null
                        val bmp = _capturedBitmap.value
                        if (bmp != null) {
                            try {
                                val ocrRes = repository.readTextOnDevice(bmp)
                                if (ocrRes.isSuccess) {
                                    val recognized = ocrRes.getOrNull()?.trim()
                                    if (!recognized.isNullOrBlank() && recognized != "No printed text detected in the image.") {
                                        onDeviceText = recognized
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(tag, "On-device OCR check exception", e)
                            }
                        }

                        // Independent On-Device Mode: If backend is not configured, complete directly with ML Kit OCR
                        if (!repository.isConfigured() && !isMock) {
                            _isAnalyzing.value = false
                            val displayText = onDeviceText ?: if (lang == AppLanguage.HINDI) {
                                "छवि में कोई छपा हुआ टेक्स्ट नहीं मिला।"
                            } else {
                                "No printed text detected in the image."
                            }
                            navigateTo(
                                Screen.ReadTextResult(
                                    text = displayText,
                                    shouldRecapture = false,
                                    recaptureReason = strings.ocrWorksOfflineNotice,
                                    isLiveResult = false
                                )
                            )
                            val speechNotice = if (onDeviceText != null) {
                                "${strings.ocrWorksOfflineNotice} $displayText"
                            } else {
                                displayText
                            }
                            speak(speechNotice)
                            return@launch
                        }

                        // Step 2: Query repository with image and optional on-device OCR text in selected language
                        val result = repository.analyzeImage(
                            imageBytes = bytesToAnalyze ?: ByteArray(0),
                            task = TaskTypes.READ_TEXT,
                            ocrText = onDeviceText,
                            language = lang.code
                        )

                        result.onSuccess { response ->
                            _isAnalyzing.value = false
                            if (response.shouldRecapture) {
                                val reason = response.reason ?: strings.recaptureBlurryReason
                                navigateTo(
                                    Screen.ReadTextResult(
                                        text = "",
                                        shouldRecapture = true,
                                        recaptureReason = reason,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.recaptureAnnouncement} $reason")
                            } else {
                                val finalAnswer = response.answer ?: onDeviceText ?: if (lang == AppLanguage.HINDI) {
                                    "छवि में कोई छपा हुआ टेक्स्ट नहीं मिला।"
                                } else {
                                    "No printed text detected in the image."
                                }
                                navigateTo(
                                    Screen.ReadTextResult(
                                        text = finalAnswer,
                                        shouldRecapture = false,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.textExtractedAnnouncement} $finalAnswer")
                            }
                        }.onFailure { ex ->
                            // Step 3: Graceful fallback to on-device OCR if cloud call failed
                            if (!onDeviceText.isNullOrBlank()) {
                                _isAnalyzing.value = false
                                navigateTo(
                                    Screen.ReadTextResult(
                                        text = onDeviceText,
                                        shouldRecapture = false,
                                        recaptureReason = strings.onDeviceFallbackNotice,
                                        isLiveResult = false
                                    )
                                )
                                speak("${strings.onDeviceFallbackSpoken} $onDeviceText")
                            } else {
                                _isAnalyzing.value = false
                                val errorMsg = ex.message ?: if (lang == AppLanguage.HINDI) {
                                    "छवि से टेक्स्ट पढ़ने में विफलता।"
                                } else {
                                    "Failed to read text from image."
                                }
                                _analysisError.value = errorMsg
                                speak("${strings.errorPrefix} $errorMsg. ${strings.retry}")
                            }
                        }
                    }

                    ScanTask.DESCRIBE_SURROUNDINGS -> {
                        // Unconfigured backend check for live scene/object analysis
                        if (!repository.isConfigured() && !isMock) {
                            _isAnalyzing.value = false
                            val unavailableMsg = strings.noBackendConnectedObjectDescription
                            _analysisError.value = unavailableMsg
                            speak(unavailableMsg)
                            return@launch
                        }

                        speak(strings.processingDescribing)
                        val result = repository.analyzeImage(
                            imageBytes = bytesToAnalyze ?: ByteArray(0),
                            task = TaskTypes.DESCRIBE_IMAGE,
                            language = lang.code
                        )

                        result.onSuccess { response ->
                            _isAnalyzing.value = false
                            if (response.shouldRecapture) {
                                val reason = response.reason ?: strings.recaptureBlurryReason
                                navigateTo(
                                    Screen.DescriptionResult(
                                        description = "",
                                        shouldRecapture = true,
                                        recaptureReason = reason,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.recaptureAnnouncement} $reason")
                            } else {
                                val description = response.answer ?: if (lang == AppLanguage.HINDI) {
                                    "कोई विवरण उपलब्ध नहीं हो सका।"
                                } else {
                                    "No description could be generated."
                                }
                                navigateTo(
                                    Screen.DescriptionResult(
                                        description = description,
                                        shouldRecapture = false,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.sceneDescriptionAnnouncement} $description")
                            }
                        }.onFailure { ex ->
                            _isAnalyzing.value = false
                            val errorMsg = if (ex is java.io.IOException || ex.message?.contains("connect", ignoreCase = true) == true || ex.message?.contains("AI object description is unavailable", ignoreCase = true) == true) {
                                strings.noBackendConnectedObjectDescription
                            } else {
                                ex.message ?: strings.noBackendConnectedObjectDescription
                            }
                            _analysisError.value = errorMsg
                            speak(errorMsg)
                        }
                    }

                    ScanTask.GUIDED_TASK -> {
                        if (!repository.isConfigured() && !isMock) {
                            _isAnalyzing.value = false
                            val unavailableMsg = strings.noBackendConnectedObjectDescription
                            _analysisError.value = unavailableMsg
                            speak(unavailableMsg)
                            return@launch
                        }

                        val currentSession = _guidedTaskSession.value ?: GuidedTaskSession(goal = "make tea")
                        speak(strings.processingGuidedTask)

                        val historyJson = if (currentSession.stepHistory.isNotEmpty()) {
                            JSONArray(currentSession.stepHistory).toString()
                        } else null

                        val result = repository.analyzeImage(
                            imageBytes = bytesToAnalyze ?: ByteArray(0),
                            task = TaskTypes.GUIDED_TASK,
                            language = lang.code,
                            taskGoal = currentSession.goal,
                            stepHistory = historyJson
                        )

                        result.onSuccess { response ->
                            _isAnalyzing.value = false
                            if (_guidedTaskSession.value?.state == GuidedTaskState.STOPPED || _currentScreen.value is Screen.Home) {
                                Log.i(tag, "Ignoring guided task response received after session stopped.")
                                return@onSuccess
                            }
                            if (response.shouldRecapture) {
                                val reason = response.reason ?: strings.recaptureBlurryReason
                                speak("${strings.recaptureAnnouncement} $reason")
                                navigateTo(Screen.GuidedTask(goal = currentSession.goal, currentInstruction = reason))
                            } else {
                                val instruction = response.answer ?: if (lang == AppLanguage.HINDI) {
                                    "निर्देश उपलब्ध नहीं हो सका।"
                                } else {
                                    "No instruction could be determined."
                                }
                                val isComplete = response.isTaskComplete == true
                                val needsClarify = response.needsClarification == true
                                val stepIdx = response.stepIndex ?: (currentSession.currentStepIndex + 1)
                                val updatedHistory = currentSession.stepHistory.toMutableList().apply {
                                    add("Step $stepIdx: $instruction")
                                }

                                val newState = when {
                                    isComplete -> GuidedTaskState.COMPLETED
                                    needsClarify -> GuidedTaskState.AWAITING_CLARIFICATION
                                    else -> GuidedTaskState.INSTRUCTING
                                }

                                _guidedTaskSession.value = currentSession.copy(
                                    state = newState,
                                    currentStepIndex = stepIdx,
                                    currentInstruction = instruction,
                                    needsClarification = needsClarify,
                                    isTaskComplete = isComplete,
                                    stepHistory = updatedHistory
                                )

                                navigateTo(Screen.GuidedTask(goal = currentSession.goal, currentInstruction = instruction))
                                speak(instruction)
                            }
                        }.onFailure { ex ->
                            _isAnalyzing.value = false
                            val errorMsg = ex.message ?: if (lang == AppLanguage.HINDI) {
                                "कार्य निर्देश प्राप्त करने में विफलता।"
                            } else {
                                "Failed to obtain task instructions."
                            }
                            _analysisError.value = errorMsg
                            speak("${strings.errorPrefix} $errorMsg. ${strings.retry}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                _isAnalyzing.value = false
                Log.i(tag, "Analysis task cancelled")
            } catch (e: Exception) {
                _isAnalyzing.value = false
                val errorMsg = e.message ?: if (lang == AppLanguage.HINDI) {
                    "एक अप्रत्याशित त्रुटि उत्पन्न हुई।"
                } else {
                    "An unexpected error occurred."
                }
                _analysisError.value = errorMsg
                speak("${strings.errorPrefix} $errorMsg")
            }
        }
    }

    fun cancelAnalysis(task: ScanTask = ScanTask.DESCRIBE_SURROUNDINGS, navigateToCamera: Boolean = true) {
        analysisJob?.cancel()
        _isAnalyzing.value = false
        _analysisError.value = null
        val strings = AppStrings.get(_settings.value.language)
        if (navigateToCamera) {
            navigateTo(Screen.Camera(task))
        }
        speak(strings.analysisCancelledSpoken)
    }

    fun askQuestion(
        query: String,
        contextContent: String?,
        onAnswerReceived: (String) -> Unit
    ) {
        if (query.isBlank()) return
        val bytes = _capturedBytes.value
        val isMock = repository.isMockMode()
        val lang = _settings.value.language
        val strings = AppStrings.get(lang)

        if (isMock) {
            val answer = MockData.answerQuestion(query, contextContent, lang.code)
            onAnswerReceived(answer)
            speak(answer)
            return
        }

        if (!repository.isConfigured()) {
            val unavailableMsg = strings.noBackendConnectedObjectDescription
            onAnswerReceived(unavailableMsg)
            speak(unavailableMsg)
            return
        }

        if (bytes == null) {
            val noImageMsg = strings.noImageForQuestionNotice
            onAnswerReceived(noImageMsg)
            speak(noImageMsg)
            return
        }

        viewModelScope.launch {
            speak(strings.findingAnswerSpoken)
            val hasTextContext = !contextContent.isNullOrBlank()
            val taskType = if (hasTextContext) TaskTypes.READ_TEXT else TaskTypes.DESCRIBE_IMAGE
            val result = repository.analyzeImage(
                imageBytes = bytes,
                task = taskType,
                question = query,
                ocrText = contextContent,
                language = lang.code
            )
            result.onSuccess { response ->
                val answer = response.answer ?: if (lang == AppLanguage.HINDI) {
                    "इस प्रश्न के लिए कोई उत्तर नहीं मिल सका।"
                } else {
                    "No answer could be determined for this question."
                }
                onAnswerReceived(answer)
                speak(answer)
            }.onFailure { ex ->
                val errorMsg = if (lang == AppLanguage.HINDI) {
                    "AI सेवा से उत्तर प्राप्त करने में विफलता: ${ex.message ?: "नेटवर्क समस्या"}।"
                } else {
                    "Failed to get answer from AI service: ${ex.message ?: "Network error"}."
                }
                onAnswerReceived(errorMsg)
                speak(errorMsg)
            }
        }
    }

    // Guided Task Actions
    fun startGuidedTask(goal: String = "make tea", autoNavigateToCamera: Boolean = true) {
        _guidedTaskSession.value = GuidedTaskSession(
            goal = goal,
            state = GuidedTaskState.OBSERVING,
            currentStepIndex = 0,
            currentInstruction = "",
            stepHistory = emptyList()
        )
        val strings = AppStrings.get(_settings.value.language)
        speak(strings.guidedTaskSpokenIntro)
        if (autoNavigateToCamera) {
            navigateTo(Screen.Camera(ScanTask.GUIDED_TASK))
        }
    }

    fun advanceGuidedTask() {
        val session = _guidedTaskSession.value ?: return
        if (session.isTaskComplete) {
            navigateTo(Screen.Home, clearBackstack = true)
            return
        }
        if (repository.isMockMode()) {
            analyzeCapturedImage(ScanTask.GUIDED_TASK, _capturedBytes.value)
        } else {
            // For real-world live assistance, require fresh visual observation of the updated workspace
            rescanGuidedTask()
        }
    }

    fun repeatGuidedTaskInstruction() {
        val instruction = _guidedTaskSession.value?.currentInstruction
        if (!instruction.isNullOrBlank()) {
            speak(instruction)
        }
    }

    fun rescanGuidedTask() {
        val strings = AppStrings.get(_settings.value.language)
        speak(strings.cameraInstructionSpoken)
        navigateTo(Screen.Camera(ScanTask.GUIDED_TASK))
    }

    fun stopGuidedTask() {
        analysisJob?.cancel()
        _isAnalyzing.value = false
        stopSpeech()
        _guidedTaskSession.value = _guidedTaskSession.value?.copy(
            state = GuidedTaskState.STOPPED
        )
        val strings = AppStrings.get(_settings.value.language)
        speak(strings.taskStoppedSpoken)
        navigateTo(Screen.Home, clearBackstack = true)
    }

    // Spoken feedback methods
    fun speak(text: String, isScreenAnnouncement: Boolean = false) {
        // If TalkBack is active and this is just an auto screen title announcement, suppress raw TTS
        // so it doesn't talk over TalkBack's native heading announcement.
        if (isScreenAnnouncement && _isTalkBackActive.value) {
            return
        }

        if (_settings.value.voiceFeedbackEnabled) {
            ttsManager?.speak(text)
        }
    }

    fun stopSpeech() {
        ttsManager?.stop()
        _isSpeaking.value = false
    }

    fun testSpeechRate(rate: Float) {
        ttsManager?.setSpeechRate(rate)
        val strings = AppStrings.get(_settings.value.language)
        speak(strings.testSpeechSentence)
    }

    fun testBackendConnection(url: String? = null, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!url.isNullOrBlank()) {
                repository.updateBaseUrl(url)
            }
            val isHealthy = repository.checkBackendHealth()
            val strings = AppStrings.get(_settings.value.language)
            if (isHealthy) {
                speak(strings.connectionStatusConnected)
            } else {
                speak(strings.connectionStatusFailed)
            }
            onResult(isHealthy)
        }
    }

    // OCR Line-by-line Reading Navigation
    private fun initOcrLines(text: String) {
        ocrLines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        currentOcrLineIndex = -1
    }

    fun readNextOcrLine() {
        val isHindi = _settings.value.language == AppLanguage.HINDI
        if (ocrLines.isEmpty()) {
            val curr = _currentScreen.value
            if (curr is Screen.ReadTextResult) {
                initOcrLines(curr.text)
            }
        }
        if (ocrLines.isEmpty()) {
            speak(if (isHindi) "कोई टेक्स्ट उपलब्ध नहीं है।" else "No text available.")
            return
        }
        if (currentOcrLineIndex < ocrLines.size - 1) {
            currentOcrLineIndex++
            speak(ocrLines[currentOcrLineIndex])
        } else {
            speak(if (isHindi) "दस्तावेज़ का अंत हो गया है।" else "End of document reached.")
        }
    }

    fun readPreviousOcrLine() {
        val isHindi = _settings.value.language == AppLanguage.HINDI
        if (ocrLines.isEmpty()) {
            val curr = _currentScreen.value
            if (curr is Screen.ReadTextResult) {
                initOcrLines(curr.text)
            }
        }
        if (ocrLines.isEmpty()) {
            speak(if (isHindi) "कोई टेक्स्ट उपलब्ध नहीं है।" else "No text available.")
            return
        }
        if (currentOcrLineIndex > 0) {
            currentOcrLineIndex--
            speak(ocrLines[currentOcrLineIndex])
        } else {
            speak(if (isHindi) "आप दस्तावेज़ की शुरुआत में हैं।" else "You are at the beginning of the document.")
        }
    }

    fun repeatCurrentOcrLine() {
        if (currentOcrLineIndex in ocrLines.indices) {
            speak(ocrLines[currentOcrLineIndex])
        } else {
            val curr = _currentScreen.value
            if (curr is Screen.ReadTextResult) {
                speak(curr.text)
            }
        }
    }

    fun resumeOcrReading() {
        if (currentOcrLineIndex in ocrLines.indices) {
            speak(ocrLines[currentOcrLineIndex])
        } else {
            val curr = _currentScreen.value
            if (curr is Screen.ReadTextResult) {
                speak(curr.text)
            }
        }
    }

    /**
     * Dispatcher for deterministic voice commands recognized across the app.
     */
    fun handleVoiceCommand(command: VoiceCommand, rawText: String) {
        val isHindi = _settings.value.language == AppLanguage.HINDI
        val strings = AppStrings.get(_settings.value.language)

        when (command) {
            is VoiceCommand.Help, is VoiceCommand.WhatCanISay -> {
                val help = VoiceCommandRouter.getHelpText(_currentScreen.value, _settings.value.language)
                speak(help)
            }

            is VoiceCommand.GoHome -> {
                navigateTo(Screen.Home, clearBackstack = true)
                speak(if (isHindi) "होम स्क्रीन पर आ गए।" else "Navigated to home screen.")
            }

            is VoiceCommand.GoBack -> {
                if (navigateBack()) {
                    speak(if (isHindi) "वापस जा रहे हैं।" else "Going back.")
                } else {
                    speak(if (isHindi) "आप पहले से होम स्क्रीन पर हैं।" else "Already at home screen.")
                }
            }

            is VoiceCommand.OpenCamera -> {
                navigateTo(Screen.Camera(command.task))
                val prompt = if (isHindi) {
                    "कैमरा खुल गया है। फोटो खींचने के लिए 'कैप्चर' बोलें।"
                } else {
                    "Camera opened. Point at an object and say Capture."
                }
                speak(prompt)
            }

            is VoiceCommand.ReadText -> {
                navigateTo(Screen.Camera(ScanTask.READ_TEXT))
                val prompt = if (isHindi) {
                    "टेक्स्ट पढ़ने का मोड। दस्तावेज़ की ओर कैमरा रखें और 'कैप्चर' बोलें।"
                } else {
                    "Read text mode. Point camera at document and say Capture."
                }
                speak(prompt)
            }

            is VoiceCommand.DescribeSurroundings -> {
                navigateTo(Screen.Camera(ScanTask.DESCRIBE_SURROUNDINGS))
                val prompt = if (isHindi) {
                    "आसपास का विवरण मोड। कैमरा रखें और 'कैप्चर' बोलें।"
                } else {
                    "Describe surroundings mode. Point camera and say Capture."
                }
                speak(prompt)
            }

            is VoiceCommand.AskQuestion -> {
                val query = command.query
                val current = _currentScreen.value
                if (current is Screen.AskQuestion) {
                    if (!query.isNullOrBlank()) {
                        askQuestion(query, current.contextContent) { answer ->
                            speak(answer)
                        }
                    } else {
                        speak(if (isHindi) "कृपया अपना सवाल पूछें।" else "Please ask your question.")
                    }
                } else {
                    navigateTo(Screen.AskQuestion(contextTitle = strings.askQuestionTitle, contextContent = null))
                    if (!query.isNullOrBlank()) {
                        askQuestion(query, null) { answer ->
                            speak(answer)
                        }
                    } else {
                        speak(if (isHindi) "सवाल पूछें मोड। अपना सवाल बोलें।" else "Ask a question mode. Speak your question.")
                    }
                }
            }

            is VoiceCommand.GuidedTask -> {
                startGuidedTask("Guided Task")
            }

            is VoiceCommand.Repeat -> {
                when (val current = _currentScreen.value) {
                    is Screen.ReadTextResult -> repeatCurrentOcrLine()
                    is Screen.DescriptionResult -> speak(current.description)
                    is Screen.GuidedTask -> repeatGuidedTaskInstruction()
                    else -> speak(strings.homeSpokenAnnouncement)
                }
            }

            is VoiceCommand.StopSpeaking -> {
                stopSpeech()
            }

            is VoiceCommand.StartListening -> {
                voiceController?.startListening()
            }

            is VoiceCommand.StopListening -> {
                voiceController?.stopListening(userRequested = true)
            }

            is VoiceCommand.Cancel -> {
                if (_isAnalyzing.value) {
                    cancelAnalysis()
                } else if (_currentScreen.value !is Screen.Home && _currentScreen.value !is Screen.Welcome) {
                    navigateBack()
                    speak(if (isHindi) "रद्द किया गया।" else "Cancelled.")
                } else {
                    speak(if (isHindi) "रद्द किया गया।" else "Cancelled.")
                }
            }

            // Context-sensitive Camera Commands
            is VoiceCommand.CapturePhoto -> {
                when (val current = _currentScreen.value) {
                    is Screen.Camera -> {
                        appContext?.let { ctx ->
                            capturePhoto(ctx, current.task, autoAnalyze = true)
                        } ?: speak(if (isHindi) "कैमरा संदर्भ उपलब्ध नहीं है।" else "Camera context unavailable.")
                    }
                    is Screen.GuidedTask -> {
                        rescanGuidedTask()
                    }
                    else -> {
                        val prompt = if (isHindi) {
                            "कैमरा खुला नहीं है। पहले 'कैमरा खोलो' बोलें।"
                        } else {
                            "Camera is not open. Say 'Open camera' first."
                        }
                        speak(prompt)
                    }
                }
            }

            is VoiceCommand.RetakePhoto -> {
                when (val current = _currentScreen.value) {
                    is Screen.ImagePreview -> {
                        navigateTo(Screen.Camera(current.task))
                        speak(if (isHindi) "कैमरा दोबारा खुला। फोटो खींचने के लिए 'कैप्चर' बोलें।" else "Camera reopened. Say Capture.")
                    }
                    is Screen.Camera -> {
                        speak(if (isHindi) "कैमरा पहले से खुला है। फोटो खींचने के लिए 'कैप्चर' बोलें।" else "Camera is already open. Say Capture.")
                    }
                    else -> {
                        speak(if (isHindi) "दोबारा फोटो लेने के लिए कैमरा खुला होना चाहिए।" else "Camera must be open to retake photo.")
                    }
                }
            }

            is VoiceCommand.ToggleFlash -> {
                val success = cameraManager.setTorchEnabled(command.enable)
                if (success) {
                    val prompt = if (command.enable) {
                        if (isHindi) "फ्लैश चालू किया गया।" else "Flash turned on."
                    } else {
                        if (isHindi) "फ्लैश बंद किया गया।" else "Flash turned off."
                    }
                    speak(prompt)
                } else {
                    speak(if (isHindi) "इस डिवाइस पर फ्लैश उपलब्ध नहीं है।" else "Flash is not available on this device.")
                }
            }

            // Context-sensitive Image Preview Commands
            is VoiceCommand.UsePhoto -> {
                val current = _currentScreen.value
                if (current is Screen.ImagePreview) {
                    navigateTo(Screen.Processing(current.task, current.imageBytes))
                    analyzeCapturedImage(current.task, current.imageBytes)
                } else {
                    speak(if (isHindi) "कोई समीक्षाधीन फोटो नहीं है।" else "No photo currently under preview.")
                }
            }

            // Context-sensitive OCR Results Commands
            is VoiceCommand.ReadAll -> {
                val current = _currentScreen.value
                if (current is Screen.ReadTextResult) {
                    speak(current.text)
                } else {
                    speak(if (isHindi) "पढ़ने के लिए कोई टेक्स्ट स्क्रीन पर नहीं है।" else "No text on screen to read.")
                }
            }

            is VoiceCommand.NextLine -> {
                readNextOcrLine()
            }

            is VoiceCommand.PreviousLine -> {
                readPreviousOcrLine()
            }

            is VoiceCommand.RepeatLine -> {
                repeatCurrentOcrLine()
            }

            is VoiceCommand.PauseReading -> {
                stopSpeech()
            }

            is VoiceCommand.ResumeReading -> {
                resumeOcrReading()
            }

            is VoiceCommand.ReadFaster -> {
                val newRate = (_settings.value.speechRate + 0.25f).coerceAtMost(2.5f)
                updateSettings(_settings.value.copy(speechRate = newRate))
                speak(if (isHindi) "पढ़ने की गति बढ़ा दी गई है।" else "Speech rate increased.")
            }

            is VoiceCommand.ReadSlower -> {
                val newRate = (_settings.value.speechRate - 0.25f).coerceAtLeast(0.5f)
                updateSettings(_settings.value.copy(speechRate = newRate))
                speak(if (isHindi) "पढ़ने की गति घटा दी गई है।" else "Speech rate decreased.")
            }

            is VoiceCommand.AskAboutThisText -> {
                val current = _currentScreen.value
                if (current is Screen.ReadTextResult) {
                    navigateTo(Screen.AskQuestion(contextTitle = strings.extractedTextTitle, contextContent = current.text))
                    speak(if (isHindi) "इस दस्तावेज़ के बारे में अपना सवाल पूछें।" else "Ask your question about this document.")
                } else {
                    speak(if (isHindi) "कोई टेक्स्ट संदर्भ उपलब्ध नहीं है।" else "No text context available.")
                }
            }

            // Context-sensitive Scene & Guided Task Commands
            is VoiceCommand.ScanAgain -> {
                when (_currentScreen.value) {
                    is Screen.DescriptionResult -> {
                        navigateTo(Screen.Camera(ScanTask.DESCRIBE_SURROUNDINGS))
                        speak(if (isHindi) "कैमरा दोबारा खुल गया है।" else "Camera reopened for rescanning.")
                    }
                    is Screen.ReadTextResult -> {
                        navigateTo(Screen.Camera(ScanTask.READ_TEXT))
                        speak(if (isHindi) "कैमरा दोबारा खुल गया है।" else "Camera reopened for rescanning.")
                    }
                    is Screen.GuidedTask -> {
                        rescanGuidedTask()
                    }
                    else -> {
                        navigateTo(Screen.Camera(ScanTask.DESCRIBE_SURROUNDINGS))
                        speak(if (isHindi) "कैमरा खुल गया है।" else "Camera opened.")
                    }
                }
            }

            is VoiceCommand.NextStep -> {
                val current = _currentScreen.value
                if (current is Screen.GuidedTask) {
                    advanceGuidedTask()
                } else {
                    speak(if (isHindi) "कोई स्टेप बाय स्टेप टास्क सक्रिय नहीं है।" else "No guided task is currently active.")
                }
            }

            is VoiceCommand.RepeatStep -> {
                repeatGuidedTaskInstruction()
            }

            is VoiceCommand.StopTask -> {
                stopGuidedTask()
            }

            // Fallback for unrecognized speech
            is VoiceCommand.Unknown -> {
                val current = _currentScreen.value
                if (current is Screen.AskQuestion) {
                    askQuestion(command.rawText, current.contextContent) { answer ->
                        speak(answer)
                    }
                } else {
                    val prompt = if (isHindi) {
                        "माफ़ करें, मुझे समझ नहीं आया। क्या कर सकते हैं जानने के लिए 'मदद' बोलें।"
                    } else {
                        "Command not recognized. Say 'Help' to hear available options."
                    }
                    speak(prompt)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        voiceController?.onDestroy()
        cameraManager.unbind()
        ttsManager?.shutdown()
    }
}
