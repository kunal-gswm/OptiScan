package com.optiscan.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.speech.SpeechInputManager
import com.optiscan.app.speech.SpeechRecognitionState
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ResultCard
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.components.SpeechControls
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.components.aurora.AssistantOrb
import com.optiscan.app.ui.components.aurora.AssistantState
import com.optiscan.app.ui.components.aurora.AuroraBackground
import com.optiscan.app.ui.components.aurora.VoiceStateIndicator
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.BackgroundMidnightNavy
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen H: Ask a Question
 * Conversational voice assistant screen featuring the reactive OptiScan Aurora AssistantOrb,
 * live audio amplitude reactivity, spoken response playback, and accessible typed fallback.
 */
@Composable
fun AskQuestionScreen(
    contextContent: String?,
    language: AppLanguage = AppLanguage.ENGLISH,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onSubmitQuestion: ((query: String, onAnswerReceived: (String) -> Unit) -> Unit)? = null,
    onBackClick: () -> Unit,
    onAnnounceScreen: () -> Unit,
    speechInputManagerProvider: ((Context) -> SpeechInputManager)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = AppStrings.get(language)
    val keyboardController = LocalSoftwareKeyboardController.current

    val speechInputManager = remember {
        speechInputManagerProvider?.invoke(context) ?: SpeechInputManager(context)
    }

    var typedQuestion by remember { mutableStateOf("") }
    var recognizedQuestion by remember { mutableStateOf<String?>(null) }
    var answerText by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var isProcessingQuestion by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var micAmplitude by remember { mutableFloatStateOf(0f) }

    val assistantState = when {
        speechError != null -> AssistantState.ERROR
        isListening -> AssistantState.LISTENING
        isProcessingQuestion -> AssistantState.PROCESSING
        isSpeaking -> AssistantState.SPEAKING
        answerText != null -> AssistantState.SUCCESS
        else -> AssistantState.IDLE
    }

    LaunchedEffect(Unit) {
        onAnnounceScreen()
    }

    fun submitQuestion(query: String) {
        if (query.isBlank()) return
        recognizedQuestion = query
        speechError = null
        isProcessingQuestion = true

        if (onSubmitQuestion != null) {
            onSubmitQuestion(query) { answer ->
                isProcessingQuestion = false
                answerText = answer
            }
        } else {
            val answer = MockData.answerQuestion(query, contextContent, language.code)
            isProcessingQuestion = false
            answerText = answer
            onSpeak(answer)
        }
    }

    fun startListening() {
        onStopSpeech()
        speechError = null
        isListening = true
        micAmplitude = 0f
        speechInputManager.startListening(language)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            val permMsg = strings.micPermissionRequiredWarning
            speechError = permMsg
            onSpeak(permMsg)
        }
    }

    DisposableEffect(speechInputManager, language) {
        speechInputManager.onRmsChanged = { rmsDb ->
            // Normalize -2 dB to 10 dB into a 0f..1f range for the visual pulse
            micAmplitude = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        }

        speechInputManager.onStateChanged = { state ->
            when (state) {
                is SpeechRecognitionState.Ready -> {
                    isListening = true
                }
                is SpeechRecognitionState.Listening -> {
                    isListening = true
                }
                is SpeechRecognitionState.Success -> {
                    isListening = false
                    micAmplitude = 0f
                    speechError = null
                    submitQuestion(state.recognizedText)
                }
                is SpeechRecognitionState.Error -> {
                    isListening = false
                    micAmplitude = 0f
                    speechError = state.message
                    onSpeak(state.message)
                }
                is SpeechRecognitionState.Idle -> {
                    isListening = false
                    micAmplitude = 0f
                }
            }
        }
        onDispose {
            speechInputManager.destroy()
        }
    }

    fun triggerVoiceInput() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundMidnightNavy)
    ) {
        AuroraBackground(
            modifier = Modifier.fillMaxSize(),
            primaryAlpha = 0.32f,
            secondaryAlpha = 0.22f
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                ScreenTitle(
                    title = strings.askQuestionTitle,
                    subtitle = strings.askQuestionScreenSubtitle,
                    onBackClick = onBackClick,
                    backContentDescription = strings.returnToPreviousScreen
                )

                if (!contextContent.isNullOrBlank()) {
                    StatusMessage(
                        message = strings.activeContextMessage,
                        level = StatusLevel.INFO,
                        title = strings.activeContextTitle
                    )
                }

                // Central Voice Assistant Orb Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                        .padding(vertical = 22.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = if (isListening) strings.listeningTitle else strings.tapToSpeak,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextWhite
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Luminous interactive Assistant Orb reacting to speech and audio amplitude
                    AssistantOrb(
                        state = assistantState,
                        amplitude = micAmplitude,
                        size = 130.dp,
                        onClick = {
                            if (isListening) {
                                speechInputManager.cancel()
                                isListening = false
                                micAmplitude = 0f
                                onStopSpeech()
                            } else {
                                triggerVoiceInput()
                            }
                        },
                        modifier = Modifier.semantics {
                            this.role = Role.Button
                            this.contentDescription = if (isListening) {
                                strings.micListeningContentDescription
                            } else {
                                strings.micButtonContentDescription
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Voice State Indicator Pill
                    VoiceStateIndicator(
                        state = assistantState,
                        language = language
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isListening) strings.listeningMicrophonePrompt else strings.touchToActivateVoice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLightGrey
                    )
                }

                // Speech Error Banner
                if (speechError != null) {
                    StatusMessage(
                        title = strings.errorPrefix.trimEnd(':', ' '),
                        message = speechError ?: "",
                        level = StatusLevel.ERROR
                    )
                }

                // Results Section (Spoken Answer + Transcript)
                if (answerText != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (recognizedQuestion != null) {
                            StatusMessage(
                                message = "\"$recognizedQuestion\"",
                                level = StatusLevel.INFO,
                                title = strings.questionAskedTitle
                            )
                        }

                        ResultCard(
                            title = strings.spokenAnswerTitle,
                            content = answerText ?: ""
                        )

                        SpeechControls(
                            isSpeaking = isSpeaking,
                            onReadAloud = { onSpeak(answerText ?: "") },
                            onStopSpeech = onStopSpeech,
                            readAloudText = strings.readAnswerAloud
                        )
                    }
                }

                // Text Input Fallback Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = strings.orTypeQuestion,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = typedQuestion,
                        onValueChange = { typedQuestion = it },
                        label = { Text(strings.enterQuestionHint, color = TextLightGrey) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            keyboardController?.hide()
                            submitQuestion(typedQuestion)
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = ActionPrimaryBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedLabelColor = ActionPrimaryBlue,
                            unfocusedLabelColor = TextLightGrey,
                            cursorColor = ActionPrimaryBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OptiScanButton(
                        text = strings.sendQuestion,
                        onClick = {
                            keyboardController?.hide()
                            submitQuestion(typedQuestion)
                        },
                        variant = ButtonVariant.AURORA,
                        icon = Icons.AutoMirrored.Filled.Send,
                        enabled = typedQuestion.isNotBlank(),
                        contentDesc = "${strings.sendQuestion} button",
                        minHeight = 54
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
