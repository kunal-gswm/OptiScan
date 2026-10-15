package com.optiscan.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.ondevice.ConversationTurn
import com.optiscan.app.ondevice.DeviceCapabilityInfo
import com.optiscan.app.ondevice.InferenceState
import com.optiscan.app.ondevice.ModelDownloadState
import com.optiscan.app.ondevice.OnDeviceConversationViewModel
import com.optiscan.app.speech.SpeechInputManager
import com.optiscan.app.speech.SpeechRecognitionState
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.ActionSecondaryGreen
import com.optiscan.app.ui.theme.AlertError
import com.optiscan.app.ui.theme.AlertWarning
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.BorderHighlight
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.SurfaceDarkCharcoal
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * On-Device Conversation Screen — Qwen2-VL Local AI
 *
 * Allows the user to:
 *  1. Trigger a photo capture (image is analyzed on-device — never uploaded)
 *  2. Hear a spoken description of the scene
 *  3. Ask follow-up questions by voice or (fallback) by text
 *  4. Continue a multi-turn conversation with full image context
 *
 * On first launch: shows model download flow with progress, storage info,
 * device capability warning if RAM < 3 GB, and cancel/retry controls.
 *
 * All inference runs strictly on-device. The privacy guarantee is enforced at
 * the InferenceEngine level — no network calls occur during conversation.
 */
@Composable
fun OnDeviceConversationScreen(
    onDeviceViewModel: OnDeviceConversationViewModel,
    capturedBitmap: Bitmap?,
    language: AppLanguage,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onCaptureNewPhoto: () -> Unit,
    onBackClick: () -> Unit,
    showDiagnostics: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHindi = language == AppLanguage.HINDI

    val downloadState by onDeviceViewModel.downloadState.collectAsState()
    val inferenceState by onDeviceViewModel.inferenceState.collectAsState()
    val conversationHistory by onDeviceViewModel.conversationHistory.collectAsState()
    val currentImageBitmap by onDeviceViewModel.currentImageBitmap.collectAsState()
    val errorMessage by onDeviceViewModel.errorMessage.collectAsState()
    val deviceInfo by onDeviceViewModel.deviceInfo.collectAsState()

    // Initialize ViewModel with context
    LaunchedEffect(Unit) {
        onDeviceViewModel.initialize(context)
    }

    // Sync captured bitmap into conversation ViewModel when a new photo is captured
    LaunchedEffect(capturedBitmap) {
        if (capturedBitmap != null) {
            onDeviceViewModel.setCurrentImage(capturedBitmap)
            // Auto-generate initial description
            onDeviceViewModel.generateAnswer(
                userQuery = if (isHindi) "इस छवि का विस्तार से वर्णन करें।" else "Describe this image in detail.",
                language = language,
                onAnswerReady = { result ->
                    result.onSuccess { answer ->
                        onSpeak(answer)
                    }
                }
            )
        }
    }

    // Speech input manager
    val speechManager = remember { SpeechInputManager(context) }
    var isListening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    val isGenerating = inferenceState is InferenceState.Generating

    DisposableEffect(speechManager) {
        speechManager.onStateChanged = { state ->
            when (state) {
                is SpeechRecognitionState.Ready -> isListening = true
                is SpeechRecognitionState.Listening -> isListening = true
                is SpeechRecognitionState.Success -> {
                    isListening = false
                    speechError = null
                    val query = state.recognizedText
                    onStopSpeech()
                    onDeviceViewModel.generateAnswer(
                        userQuery = query,
                        language = language,
                        onAnswerReady = { result ->
                            result.onSuccess { answer -> onSpeak(answer) }
                            result.onFailure { ex ->
                                speechError = ex.message
                                onSpeak(ex.message ?: "Error generating response")
                            }
                        }
                    )
                }
                is SpeechRecognitionState.Error -> {
                    isListening = false
                    speechError = state.message
                }
                is SpeechRecognitionState.Idle -> isListening = false
            }
        }
        onDispose { speechManager.destroy() }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechManager.startListening(language)
            isListening = true
        } else {
            speechError = if (isHindi) "माइक्रोफ़ोन अनुमति आवश्यक है।" else "Microphone permission required for voice input."
        }
    }

    fun startVoiceInput() {
        onStopSpeech()
        speechError = null
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            speechManager.startListening(language)
            isListening = true
        } else {
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-scroll to bottom of conversation
    val listState = rememberLazyListState()
    LaunchedEffect(conversationHistory.size) {
        if (conversationHistory.isNotEmpty()) {
            listState.animateScrollToItem(conversationHistory.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            ScreenTitle(
                title = if (isHindi) "[प्रोटोटाइप] ऑन-डिवाइस AI" else "[Prototype] On-Device AI",
                subtitle = if (isHindi) "सिम्युलेटेड डेमो • ऑफलाइन प्रोटोटाइप" else "Simulated Demo • Offline Prototype",
                onBackClick = onBackClick,
                backContentDescription = if (isHindi) "पिछली स्क्रीन पर वापस जाएं" else "Return to previous screen"
            )

            // Prototype / Simulated Demo Notice Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2000), RoundedCornerShape(8.dp))
                    .border(1.dp, AlertWarning, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isHindi)
                        "⚠️ प्रोटोटाइप डेमो: Qwen2-VL मॉडल वेट्स स्थानीय रूप से बंडल नहीं हैं। यह सुविधा सिमुलेशन मोड में चल रही है।"
                    else
                        "⚠️ Prototype Demo: Real on-device Qwen2-VL model weights are not bundled (~1.8 GB). Operating in simulated prototype mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertWarning
                )
            }

            // Device warning if RAM is low
            deviceInfo?.let { info ->
                if (!info.meetsMinimumRam) {
                    DeviceWarningCard(info = info, isHindi = isHindi)
                }
            }

            // Model not downloaded — show download flow
            if (downloadState !is ModelDownloadState.Ready) {
                ModelDownloadSection(
                    downloadState = downloadState,
                    isHindi = isHindi,
                    storageInfo = onDeviceViewModel.storageInfo(),
                    onStartDownload = { onDeviceViewModel.startDownload() },
                    onCancelDownload = { onDeviceViewModel.cancelDownload() },
                    onRetryDownload = { onDeviceViewModel.startDownload() }
                )
            } else {
                // Model is ready — show conversation interface
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Model loading indicator
                    if (inferenceState == InferenceState.Loading) {
                        ModelLoadingCard(isHindi = isHindi)
                    }

                    // Image context row
                    CurrentImageContextRow(
                        bitmap = currentImageBitmap,
                        isHindi = isHindi,
                        onCaptureNew = onCaptureNewPhoto
                    )

                    // Privacy guarantee badge
                    PrivacyBadge(isHindi = isHindi)

                    // Conversation history
                    if (conversationHistory.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(conversationHistory) { turn ->
                                ConversationBubble(turn = turn, isHindi = isHindi)
                            }

                            if (isGenerating) {
                                item {
                                    ThinkingBubble(isHindi = isHindi)
                                }
                            }
                        }
                    } else {
                        // Empty state
                        EmptyConversationState(
                            hasImage = currentImageBitmap != null,
                            isHindi = isHindi,
                            isModelLoading = inferenceState == InferenceState.Loading
                        )
                    }

                    // Error message
                    errorMessage?.let { err ->
                        ErrorBanner(
                            message = err,
                            onDismiss = { onDeviceViewModel.clearError() },
                            isHindi = isHindi
                        )
                    }

                    speechError?.let { err ->
                        ErrorBanner(
                            message = err,
                            onDismiss = { speechError = null },
                            isHindi = isHindi
                        )
                    }
                }

                // Voice + action controls
                ConversationControls(
                    isListening = isListening,
                    isGenerating = isGenerating,
                    hasImage = currentImageBitmap != null,
                    isSpeaking = isSpeaking,
                    isHindi = isHindi,
                    onVoiceInput = { startVoiceInput() },
                    onCancelVoice = {
                        speechManager.cancel()
                        isListening = false
                    },
                    onCancelInference = { onDeviceViewModel.cancelInference() },
                    onStopSpeech = onStopSpeech,
                    onClearConversation = {
                        onDeviceViewModel.clearConversation()
                        if (isHindi) onSpeak("बातचीत साफ की गई।") else onSpeak("Conversation cleared.")
                    }
                )

                // Diagnostics panel (developer mode)
                if (showDiagnostics) {
                    DiagnosticsPanel(
                        diagnosticsText = onDeviceViewModel.formattedDiagnostics()
                    )
                }

                // Delete model option
                OptiScanButton(
                    text = if (isHindi) "मॉडल हटाएं (${OnDeviceModelManager.MODEL_APPROX_BYTES / (1024 * 1024 * 1024)}GB खाली करें)" else "Delete Model (free ~${(OnDeviceModelManager.MODEL_APPROX_BYTES + OnDeviceModelManager.MMPROJ_APPROX_BYTES) / (1024 * 1024)} MB)",
                    onClick = {
                        onDeviceViewModel.deleteModel()
                        if (isHindi) onSpeak("मॉडल हटा दिया गया।") else onSpeak("Model deleted.")
                    },
                    variant = ButtonVariant.SURFACE,
                    icon = Icons.Default.Delete,
                    contentDesc = if (isHindi) "मॉडल हटाएं और संग्रहण खाली करें" else "Delete model files and free storage",
                    minHeight = 44
                )
            }
        }
    }
}

// ---- Sub-composables ----

@Composable
private fun ModelDownloadSection(
    downloadState: ModelDownloadState,
    isHindi: Boolean,
    storageInfo: String,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
            .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = ActionPrimaryBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isHindi) "ऑन-डिवाइस AI मॉडल" else "On-Device AI Model",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite
            )
        }

        Text(
            text = if (isHindi)
                "Qwen2-VL-2B (Q4) — ~1.8 GB — एक बार डाउनलोड, हमेशा ऑफलाइन उपलब्ध।\nछवियाँ और बातचीत कभी अपलोड नहीं होती।"
            else
                "Qwen2-VL-2B (Q4) — ~1.8 GB — Download once, works fully offline.\nImages and conversations never leave your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLightGrey
        )

        // Storage info
        Text(
            text = "📦 $storageInfo",
            style = MaterialTheme.typography.bodySmall,
            color = TextLightGrey,
            fontFamily = FontFamily.Monospace
        )

        // State-specific content
        when (downloadState) {
            is ModelDownloadState.Idle, is ModelDownloadState.Cancelled -> {
                OptiScanButton(
                    text = if (downloadState is ModelDownloadState.Cancelled)
                        (if (isHindi) "पुनः डाउनलोड करें" else "Retry Download")
                    else
                        (if (isHindi) "डाउनलोड करें" else "Download Model"),
                    onClick = if (downloadState is ModelDownloadState.Cancelled) onRetryDownload else onStartDownload,
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.Default.Download,
                    contentDesc = if (isHindi) "ऑन-डिवाइस मॉडल डाउनलोड करें" else "Download on-device model",
                    minHeight = 56
                )
            }

            is ModelDownloadState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isHindi) "डाउनलोड हो रहा है..." else "Downloading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite
                        )
                        Text(
                            text = "${downloadState.progressPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ActionPrimaryBlue
                        )
                    }

                    LinearProgressIndicator(
                        progress = { downloadState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ActionPrimaryBlue,
                        trackColor = SurfaceCard
                    )

                    Text(
                        text = "${String.format("%.1f", downloadState.downloadedMb)} MB / ${String.format("%.0f", downloadState.totalMb)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLightGrey,
                        fontFamily = FontFamily.Monospace
                    )

                    OptiScanButton(
                        text = if (isHindi) "डाउनलोड रद्द करें" else "Cancel Download",
                        onClick = onCancelDownload,
                        variant = ButtonVariant.SURFACE,
                        icon = Icons.Default.Stop,
                        contentDesc = if (isHindi) "डाउनलोड रद्द करें" else "Cancel download",
                        minHeight = 48
                    )
                }
            }

            is ModelDownloadState.Verifying -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = ActionSecondaryGreen,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isHindi) "फ़ाइलें सत्यापित हो रही हैं..." else "Verifying files...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite
                    )
                }
            }

            is ModelDownloadState.Failed -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "⚠ ${downloadState.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertError
                    )
                    OptiScanButton(
                        text = if (isHindi) "पुनः प्रयास करें" else "Retry Download",
                        onClick = onRetryDownload,
                        variant = ButtonVariant.PRIMARY,
                        icon = Icons.Default.Refresh,
                        contentDesc = if (isHindi) "डाउनलोड पुनः प्रयास करें" else "Retry download",
                        minHeight = 52
                    )
                }
            }

            is ModelDownloadState.Ready -> { /* Handled by outer when */ }
        }
    }
}

@Composable
private fun ModelLoadingCard(isHindi: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDarkCharcoal, RoundedCornerShape(12.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .semantics { contentDescription = if (isHindi) "मॉडल लोड हो रहा है" else "Loading model into memory" }
    ) {
        CircularProgressIndicator(
            color = ActionPrimaryBlue,
            modifier = Modifier.size(22.dp).rotate(rotation),
            strokeWidth = 3.dp
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = if (isHindi) "मॉडल मेमोरी में लोड हो रहा है..." else "Loading model into memory...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite
            )
            Text(
                text = if (isHindi) "पहली बार 5–15 सेकंड लग सकते हैं" else "First load may take 5–15 seconds",
                style = MaterialTheme.typography.bodySmall,
                color = TextLightGrey
            )
        }
    }
}

@Composable
private fun CurrentImageContextRow(
    bitmap: Bitmap?,
    isHindi: Boolean,
    onCaptureNew: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = if (isHindi) "वर्तमान छवि संदर्भ" else "Current image context",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.dp, ActionSecondaryGreen, RoundedCornerShape(10.dp))
                    .semantics { role = Role.Image }
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDarkCharcoal)
                    .border(2.dp, BorderSubtle, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = TextLightGrey,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (bitmap != null)
                    (if (isHindi) "📷 छवि संदर्भ सक्रिय" else "📷 Image context active")
                else
                    (if (isHindi) "कोई छवि नहीं — पहले फोटो लें" else "No image — capture a photo first"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (bitmap != null) ActionSecondaryGreen else TextLightGrey
            )
            if (bitmap != null) {
                Text(
                    text = if (isHindi) "प्रश्न पूछने के लिए माइक टैप करें" else "Tap mic to ask a question",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLightGrey
                )
            }
        }

        OptiScanButton(
            text = if (isHindi) "फोटो" else "Photo",
            onClick = onCaptureNew,
            variant = ButtonVariant.OUTLINE,
            icon = Icons.Default.CameraAlt,
            contentDesc = if (isHindi) "नई फोटो लें" else "Capture new photo",
            minHeight = 44,
            modifier = Modifier.width(100.dp)
        )
    }
}

@Composable
private fun PrivacyBadge(isHindi: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D2818), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = ActionSecondaryGreen,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isHindi) "🔒 100% ऑन-डिवाइस प्रोटोटाइप • कोई डेटा अपलोड नहीं"
            else "🔒 100% On-Device Prototype • No data ever leaves your phone",
            style = MaterialTheme.typography.bodySmall,
            color = ActionSecondaryGreen
        )
    }
}

@Composable
private fun ConversationBubble(turn: ConversationTurn, isHindi: Boolean) {
    val isUser = turn.isUser
    val bgColor = if (isUser) ActionPrimaryBlue.copy(alpha = 0.2f) else SurfaceDarkCharcoal
    val borderColor = if (isUser) ActionPrimaryBlue.copy(alpha = 0.5f) else BorderSubtle
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isUser) (if (isHindi) "आप" else "You") else "AI",
            style = MaterialTheme.typography.labelSmall,
            color = TextLightGrey,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .semantics {
                    contentDescription = "${if (isUser) "You" else "AI"}: ${turn.text}"
                    role = Role.Image
                }
        ) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun ThinkingBubble(isHindi: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("AI", style = MaterialTheme.typography.labelSmall, color = TextLightGrey,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))

        Box(
            modifier = Modifier
                .background(SurfaceDarkCharcoal, RoundedCornerShape(12.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .semantics { contentDescription = if (isHindi) "AI सोच रहा है" else "AI is thinking" }
        ) {
            Text(
                text = if (isHindi) "● ● ● सोच रहा है..." else "● ● ● Thinking...",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = TextLightGrey.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun EmptyConversationState(
    hasImage: Boolean,
    isHindi: Boolean,
    isModelLoading: Boolean
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(SurfaceDarkCharcoal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Text(
            text = when {
                isModelLoading -> if (isHindi) "मॉडल लोड हो रहा है, कृपया प्रतीक्षा करें..." else "Loading AI model, please wait..."
                !hasImage -> if (isHindi) "📷 बातचीत शुरू करने के लिए पहले फोटो लें" else "📷 Capture a photo to start the conversation"
                else -> if (isHindi) "🎤 प्रश्न पूछने के लिए माइक बटन टैप करें" else "🎤 Tap the mic button to ask your first question"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextLightGrey,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConversationControls(
    isListening: Boolean,
    isGenerating: Boolean,
    hasImage: Boolean,
    isSpeaking: Boolean,
    isHindi: Boolean,
    onVoiceInput: () -> Unit,
    onCancelVoice: () -> Unit,
    onCancelInference: () -> Unit,
    onStopSpeech: () -> Unit,
    onClearConversation: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Primary: microphone button
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Mic button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            isListening -> AlertError
                            isGenerating -> ActionPrimaryBlue.copy(alpha = 0.4f)
                            !hasImage -> SurfaceDarkCharcoal
                            else -> ActionPrimaryBlue
                        }
                    )
                    .border(2.dp, BorderHighlight, RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = !isGenerating && hasImage,
                        onClick = { if (isListening) onCancelVoice() else onVoiceInput() }
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = when {
                            isListening -> if (isHindi) "सुन रहा है। रद्द करने के लिए टैप करें।" else "Listening. Tap to cancel."
                            isGenerating -> if (isHindi) "AI सोच रहा है" else "AI is thinking"
                            !hasImage -> if (isHindi) "पहले फोटो लें" else "Capture a photo first"
                            else -> if (isHindi) "आवाज़ से प्रश्न पूछें" else "Ask a question by voice"
                        }
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = when {
                            isListening -> if (isHindi) "सुन रहा है..." else "Listening..."
                            isGenerating -> if (isHindi) "सोच रहा है..." else "Thinking..."
                            else -> if (isHindi) "बोलें" else "Ask"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextWhite
                    )
                }
            }

            // Secondary controls column
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(96.dp)
            ) {
                if (isGenerating) {
                    OptiScanButton(
                        text = if (isHindi) "रोकें" else "Stop",
                        onClick = onCancelInference,
                        variant = ButtonVariant.SURFACE,
                        icon = Icons.Default.Stop,
                        contentDesc = if (isHindi) "AI प्रतिक्रिया रोकें" else "Cancel AI response",
                        minHeight = 44
                    )
                } else if (isSpeaking) {
                    OptiScanButton(
                        text = if (isHindi) "चुप" else "Mute",
                        onClick = onStopSpeech,
                        variant = ButtonVariant.SURFACE,
                        icon = Icons.Default.Stop,
                        contentDesc = if (isHindi) "आवाज़ बंद करें" else "Stop speech",
                        minHeight = 44
                    )
                }

                OptiScanButton(
                    text = if (isHindi) "साफ" else "Clear",
                    onClick = onClearConversation,
                    variant = ButtonVariant.OUTLINE,
                    icon = Icons.Default.Refresh,
                    contentDesc = if (isHindi) "बातचीत साफ करें" else "Clear conversation",
                    minHeight = 44
                )
            }
        }

        // Instruction text
        Text(
            text = when {
                isListening -> if (isHindi) "माइक सुन रहा है — स्पष्ट रूप से बोलें" else "Microphone listening — speak clearly"
                isGenerating -> if (isHindi) "AI आपके प्रश्न का उत्तर दे रहा है..." else "AI is analyzing your question..."
                !hasImage -> if (isHindi) "पहले कैमरे से फोटो लें" else "Capture a photo with the camera first"
                else -> if (isHindi) "माइक टैप करें या मुख्य ऐप से प्रश्न पूछें" else "Tap mic to ask, or use volume keys"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextLightGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeviceWarningCard(info: DeviceCapabilityInfo, isHindi: Boolean) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF3A1500), RoundedCornerShape(10.dp))
            .border(1.dp, AlertWarning, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = AlertWarning, modifier = Modifier.size(18.dp))
        Text(
            text = if (isHindi)
                "⚠ आपके डिवाइस में ${info.totalRamMb} MB RAM है। Qwen2-VL के लिए 3 GB+ अनुशंसित है। प्रदर्शन धीमा हो सकता है।"
            else
                "⚠ Device has ${info.totalRamMb} MB RAM. Qwen2-VL needs 3 GB+ RAM. Performance may be slow or model may fail to load.",
            style = MaterialTheme.typography.bodySmall,
            color = AlertWarning
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, isHindi: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(AlertError.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .border(1.dp, AlertError.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = AlertError,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Stop, contentDescription = if (isHindi) "त्रुटि बंद करें" else "Dismiss error",
                tint = AlertError, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DiagnosticsPanel(diagnosticsText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text = diagnosticsText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFF00FF88),
            lineHeight = 18.sp
        )
    }
}

// ---- Missing theme colors (add if not present in Theme.kt) ----
val AlertWarning = Color(0xFFFF9800)

// Export for use in OnDeviceModelManager reference from UI
val OnDeviceModelManager = com.optiscan.app.ondevice.OnDeviceModelManager
