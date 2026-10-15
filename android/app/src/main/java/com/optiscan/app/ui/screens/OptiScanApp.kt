package com.optiscan.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.OptiScanTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.optiscan.app.ui.components.HapticType
import com.optiscan.app.ui.components.LocalAccessibleAnnouncer
import com.optiscan.app.ui.components.LocalHapticFeedbackHandler
import com.optiscan.app.ui.components.LocalTripleTapModeEnabled
import androidx.lifecycle.viewmodel.compose.viewModel
import com.optiscan.app.ondevice.OnDeviceConversationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Root Composable orchestrating state-driven navigation across all 9 OptiScan screens.
 */
@Composable
fun OptiScanApp(
    viewModel: MainViewModel,
    onRequestCameraPermission: () -> Unit,
    onBindCamera: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val speechAmplitude by viewModel.speechAmplitude.collectAsState()
    val strings = AppStrings.get(settings.language)
    val onDeviceViewModel: OnDeviceConversationViewModel = viewModel()

    fun performHaptic(type: HapticType = HapticType.CONFIRMED_ACTION) {
        if (settings.hapticFeedbackEnabled) {
            try {
                val (durationMs, amplitude) = when (type) {
                    HapticType.SUBTLE_TICK -> Pair(20L, 50)
                    HapticType.DOUBLE_TICK -> Pair(35L, 120)
                    HapticType.CONFIRMED_ACTION -> Pair(55L, 255)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }
            } catch (_: Exception) {}
        }
    }

    // Intercept hardware and gesture back button
    BackHandler(enabled = currentScreen !is Screen.Home && currentScreen !is Screen.Welcome) {
        viewModel.navigateBack()
    }

    CompositionLocalProvider(
        LocalAccessibleAnnouncer provides { text -> viewModel.speak(text) },
        LocalHapticFeedbackHandler provides { type -> performHaptic(type) },
        LocalTripleTapModeEnabled provides settings.tripleTapNavigationEnabled
    ) {
        OptiScanTheme(textScale = settings.textSizeScale) {
            when (val screen = currentScreen) {
                is Screen.Welcome -> {
                    WelcomeScreen(
                        onGetStarted = {
                            performHaptic()
                            viewModel.navigateTo(Screen.Home, clearBackstack = true)
                        },
                        onSpeakIntro = {
                            viewModel.speak(strings.welcomeSpokenIntro, isScreenAnnouncement = true)
                        },
                        modifier = modifier.fillMaxSize()
                    )
                }

                is Screen.Home -> {
                    HomeScreen(
                        language = settings.language,
                        assistantState = assistantState,
                        speechAmplitude = speechAmplitude,
                        onNavigateToCamera = { task ->
                            performHaptic()
                            viewModel.navigateTo(Screen.Camera(task))
                        },
                        onStartGuidedTask = {
                            performHaptic()
                            viewModel.startGuidedTask()
                        },
                        onNavigateToAskQuestion = {
                            performHaptic()
                            viewModel.navigateTo(Screen.AskQuestion())
                        },
                        onNavigateToSettings = {
                            performHaptic()
                            viewModel.navigateTo(Screen.Settings)
                        },
                        onAnnounceHome = {
                            viewModel.speak(strings.homeSpokenAnnouncement, isScreenAnnouncement = true)
                        },
                        onNavigateToOnDeviceConversation = {
                            performHaptic()
                            viewModel.navigateTo(Screen.OnDeviceConversation)
                        },
                        modifier = modifier.fillMaxSize()
                    )
                }

            is Screen.Camera -> {
                CameraScreen(
                    task = screen.task,
                    hasCameraPermission = hasCameraPermission,
                    language = settings.language,
                    onRequestCameraPermission = onRequestCameraPermission,
                    onPreviewReady = onBindCamera,
                    onCaptureClicked = {
                        performHaptic(HapticType.CONFIRMED_ACTION)
                        viewModel.capturePhoto(context, screen.task)
                    },
                    onBackClick = {
                        viewModel.navigateBack()
                    },
                    onInstructionSpoken = {
                        val isHindi = settings.language == AppLanguage.HINDI
                        val instruction = if (isHindi) {
                            "कैमरा वस्तु की ओर करें। फोटो खींचने के लिए वॉल्यूम बटन दबाएं या फोटो कैप्चर पर टैप करें।"
                        } else {
                            "Point camera at object. Press volume button, tap viewfinder, or tap Capture Photo."
                        }
                        viewModel.speak(instruction, isScreenAnnouncement = true)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.ImagePreview -> {
                ImagePreviewScreen(
                    task = screen.task,
                    bitmap = capturedBitmap,
                    language = settings.language,
                    onUsePhoto = {
                        performHaptic()
                        if (settings.useOnDeviceAi) {
                            viewModel.navigateTo(Screen.OnDeviceConversation)
                        } else {
                            viewModel.navigateTo(Screen.Processing(screen.task, screen.imageBytes))
                        }
                    },
                    onRetakePhoto = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Camera(screen.task))
                    },
                    onAnnounce = {
                        viewModel.speak(strings.previewSpokenAnnouncement)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.Processing -> {
                ProcessingScreen(
                    task = screen.task,
                    language = settings.language,
                    isProcessing = isAnalyzing,
                    errorMessage = analysisError,
                    onStartAnalysis = {
                        viewModel.analyzeCapturedImage(screen.task, screen.imageBytes)
                    },
                    onRetry = {
                        viewModel.analyzeCapturedImage(screen.task, screen.imageBytes)
                    },
                    onCancel = {
                        viewModel.cancelAnalysis(screen.task)
                    },
                    onAnnounceProcessing = {
                        // Analysis initiation already announces processing status in analyzeCapturedImage
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.ReadTextResult -> {
                ReadTextResultScreen(
                    text = screen.text,
                    language = settings.language,
                    shouldRecapture = screen.shouldRecapture,
                    recaptureReason = screen.recaptureReason,
                    isLiveResult = screen.isLiveResult,
                    isSpeaking = isSpeaking,
                    onReadAloud = { txt -> viewModel.speak(txt) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    onAskQuestion = { contextText ->
                        performHaptic()
                        viewModel.navigateTo(Screen.AskQuestion(contextTitle = strings.extractedTextTitle, contextContent = contextText))
                    },
                    onCaptureAnother = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Camera(ScanTask.READ_TEXT))
                    },
                    onBackToHome = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Home, clearBackstack = true)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.DescriptionResult -> {
                DescriptionResultScreen(
                    description = screen.description,
                    language = settings.language,
                    shouldRecapture = screen.shouldRecapture,
                    recaptureReason = screen.recaptureReason,
                    isLiveResult = screen.isLiveResult,
                    isSpeaking = isSpeaking,
                    onReadAloud = { desc -> viewModel.speak(desc) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    onAskQuestion = { contextText ->
                        performHaptic()
                        viewModel.navigateTo(Screen.AskQuestion(contextTitle = strings.sceneDescriptionTitle, contextContent = contextText))
                    },
                    onCaptureAnother = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Camera(ScanTask.DESCRIBE_SURROUNDINGS))
                    },
                    onBackToHome = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Home, clearBackstack = true)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.AskQuestion -> {
                AskQuestionScreen(
                    contextContent = screen.contextContent,
                    language = settings.language,
                    isSpeaking = isSpeaking,
                    onSpeak = { query -> viewModel.speak(query) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    onSubmitQuestion = { query, onAnswer ->
                        viewModel.askQuestion(query, screen.contextContent, onAnswer)
                    },
                    onBackClick = { viewModel.navigateBack() },
                    onAnnounceScreen = {
                        viewModel.speak(strings.askQuestionSpokenIntro)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.GuidedTask -> {
                val guidedSession by viewModel.guidedTaskSession.collectAsState()
                GuidedTaskScreen(
                    goal = guidedSession?.goal ?: screen.goal,
                    stepIndex = guidedSession?.currentStepIndex ?: 1,
                    instruction = guidedSession?.currentInstruction ?: screen.currentInstruction ?: "",
                    needsClarification = guidedSession?.needsClarification ?: false,
                    isTaskComplete = guidedSession?.isTaskComplete ?: false,
                    isSpeaking = isSpeaking,
                    language = settings.language,
                    onNextStep = {
                        performHaptic()
                        viewModel.advanceGuidedTask()
                    },
                    onRepeatStep = {
                        performHaptic()
                        viewModel.repeatGuidedTaskInstruction()
                    },
                    onScanAgain = {
                        performHaptic()
                        viewModel.rescanGuidedTask()
                    },
                    onAskQuestion = {
                        performHaptic()
                        val currentText = guidedSession?.currentInstruction ?: screen.currentInstruction ?: ""
                        viewModel.navigateTo(
                            Screen.AskQuestion(
                                contextTitle = strings.guidedTaskTitle,
                                contextContent = currentText
                            )
                        )
                    },
                    onStopTask = {
                        performHaptic()
                        viewModel.stopGuidedTask()
                    },
                    onReadAloud = { text -> viewModel.speak(text) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        performHaptic()
                        viewModel.updateSettings(newSettings)
                    },
                    onTestSpeechRate = { rate ->
                        viewModel.testSpeechRate(rate)
                    },
                    onTestConnection = { url, callback ->
                        viewModel.testBackendConnection(url, callback)
                    },
                    onBackClick = { viewModel.navigateBack() },
                    onAnnounceSettings = {
                        viewModel.speak("${strings.settingsTitle}. ${strings.settingsSubtitle}")
                    },
                    onOpenOnDeviceConversation = {
                        performHaptic()
                        viewModel.navigateTo(Screen.OnDeviceConversation)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.OnDeviceConversation -> {
                OnDeviceConversationScreen(
                    onDeviceViewModel = onDeviceViewModel,
                    capturedBitmap = capturedBitmap,
                    language = settings.language,
                    isSpeaking = isSpeaking,
                    onSpeak = { txt -> viewModel.speak(txt) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    onCaptureNewPhoto = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Camera(ScanTask.DESCRIBE_SURROUNDINGS))
                    },
                    onBackClick = {
                        performHaptic()
                        viewModel.navigateBack()
                    },
                    showDiagnostics = settings.showOnDeviceDiagnostics,
                    modifier = modifier.fillMaxSize()
                )
            }
        }
    }
}
}

