package com.optiscan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ResultCard
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.components.SpeechControls
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.BackgroundMidnightNavy

/**
 * Screen G: Image Description Results
 * Presents scene understanding descriptions with spoken playback,
 * prominent recapture guidance when image is blurry or dark, and conversational follow-up triggers.
 */
@Composable
fun DescriptionResultScreen(
    description: String = MockData.SAMPLE_SCENE_DESCRIPTION,
    language: AppLanguage = AppLanguage.ENGLISH,
    shouldRecapture: Boolean = false,
    recaptureReason: String? = null,
    isLiveResult: Boolean = true,
    isSpeaking: Boolean,
    onReadAloud: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onAskQuestion: (contextContent: String) -> Unit,
    onCaptureAnother: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.get(language)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundMidnightNavy
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            ScreenTitle(
                title = if (shouldRecapture) strings.recaptureTitle else strings.sceneDescriptionTitle,
                subtitle = if (shouldRecapture) strings.recaptureSubtitle else strings.sceneDescriptionSubtitle,
                onBackClick = onBackToHome,
                backContentDescription = strings.returnToPreviousScreen
            )

            if (shouldRecapture) {
                // High-visibility Recapture Warning
                StatusMessage(
                    title = strings.recaptureTitle,
                    message = recaptureReason ?: strings.recaptureBlurryReason,
                    level = StatusLevel.WARNING
                )

                Spacer(modifier = Modifier.height(8.dp))

                OptiScanButton(
                    text = strings.retakePhoto,
                    onClick = onCaptureAnother,
                    variant = ButtonVariant.AURORA,
                    icon = Icons.Default.CameraAlt,
                    contentDesc = "${strings.retakePhoto} button",
                    bypassTripleTap = true
                )

                OptiScanButton(
                    text = strings.backToHome,
                    onClick = onBackToHome,
                    variant = ButtonVariant.OUTLINE,
                    icon = Icons.Default.Home,
                    contentDesc = strings.backToHome
                )
            } else {
                val notice = if (isLiveResult) {
                    if (language == AppLanguage.HINDI) "Google Gemini द्वारा AI दृश्य विश्लेषण" else "AI Scene Analysis by Google Gemini"
                } else {
                    if (language == AppLanguage.HINDI) "[ऑफलाइन मॉक] डेमो आउटपुट" else "[Offline Mock] Simulated demonstration output"
                }

                // Result Card
                ResultCard(
                    title = strings.surroundingsCardTitle,
                    content = description,
                    noticeText = notice
                )

                // Speech Controls
                SpeechControls(
                    isSpeaking = isSpeaking,
                    onReadAloud = { onReadAloud(description) },
                    onStopSpeech = onStopSpeech,
                    readAloudText = strings.readAloudDescription,
                    stopText = strings.stopSpeech
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action: Ask a Question
                OptiScanButton(
                    text = strings.askQuestionAboutScene,
                    onClick = { onAskQuestion(description) },
                    variant = ButtonVariant.AURORA,
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDesc = "${strings.askQuestionAboutScene} button"
                )

                // Action: Capture Another Image
                OptiScanButton(
                    text = strings.captureAnotherScene,
                    onClick = onCaptureAnother,
                    variant = ButtonVariant.SURFACE,
                    icon = Icons.Default.CameraAlt,
                    contentDesc = "${strings.captureAnotherScene} button"
                )

                // Navigation: Back to Home
                OptiScanButton(
                    text = strings.backToHome,
                    onClick = onBackToHome,
                    variant = ButtonVariant.OUTLINE,
                    icon = Icons.Default.Home,
                    contentDesc = strings.backToHome
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
