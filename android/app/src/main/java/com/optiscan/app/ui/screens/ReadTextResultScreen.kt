package com.optiscan.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * Screen F: Read Text Results
 * Displays extracted text from product labels or documents with Read Aloud, Copy, Q&A,
 * and high-visibility recapture guidance if the image is too blurry.
 */
@Composable
fun ReadTextResultScreen(
    text: String = MockData.SAMPLE_PRODUCT_LABEL,
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
    val context = LocalContext.current
    val strings = AppStrings.get(language)
    var showCopyBanner by remember { mutableStateOf(false) }

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
                title = if (shouldRecapture) strings.recaptureTitle else strings.extractedTextTitle,
                subtitle = if (shouldRecapture) strings.recaptureSubtitle else strings.extractedTextSubtitle,
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
                // Copy feedback announcement banner
                if (showCopyBanner) {
                    StatusMessage(
                        message = strings.copiedToClipboard,
                        level = StatusLevel.SUCCESS,
                        title = "Copied"
                    )
                }

                val notice = when {
                    isLiveResult -> if (language == AppLanguage.HINDI) "Google Gemini द्वारा AI टेक्स्ट विश्लेषण" else "AI Text Extraction by Google Gemini"
                    !recaptureReason.isNullOrBlank() -> recaptureReason
                    else -> if (language == AppLanguage.HINDI) "[ऑफलाइन OCR] ऑन-डिवाइस ML Kit आउटपुट" else "[On-Device OCR] Offline ML Kit Extraction"
                }

                // Text Result Card
                ResultCard(
                    title = strings.documentTextCardTitle,
                    content = text,
                    noticeText = notice,
                    onCopyClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("OptiScan Extracted Text", text)
                        clipboard.setPrimaryClip(clip)
                        showCopyBanner = true
                    }
                )

                // Primary Read Aloud & Stop Speech Controls
                SpeechControls(
                    isSpeaking = isSpeaking,
                    onReadAloud = { onReadAloud(text) },
                    onStopSpeech = onStopSpeech,
                    readAloudText = strings.readAloudAllText,
                    stopText = strings.stopSpeech
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action: Ask a Question about this text
                OptiScanButton(
                    text = strings.askQuestionAboutText,
                    onClick = { onAskQuestion(text) },
                    variant = ButtonVariant.AURORA,
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDesc = "${strings.askQuestionAboutText} button"
                )

                // Action: Capture Another Image
                OptiScanButton(
                    text = strings.captureAnotherDocument,
                    onClick = onCaptureAnother,
                    variant = ButtonVariant.SURFACE,
                    icon = Icons.Default.CameraAlt,
                    contentDesc = "${strings.captureAnotherDocument} button"
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
