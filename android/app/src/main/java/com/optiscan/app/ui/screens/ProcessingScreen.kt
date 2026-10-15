package com.optiscan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.components.aurora.AssistantOrb
import com.optiscan.app.ui.components.aurora.AssistantState
import com.optiscan.app.ui.components.aurora.AuroraBackground
import com.optiscan.app.ui.components.aurora.VoiceStateIndicator
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.BackgroundMidnightNavy
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen E: Processing
 * Displays the signature OptiScan Aurora AssistantOrb in active processing or error state,
 * with accessible TalkBack polite announcements and immediate cancellation controls.
 */
@Composable
fun ProcessingScreen(
    task: ScanTask,
    language: AppLanguage = AppLanguage.ENGLISH,
    isProcessing: Boolean = true,
    errorMessage: String? = null,
    onStartAnalysis: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onAnnounceProcessing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.get(language)
    val statusText = when (task) {
        ScanTask.READ_TEXT -> strings.processingReadingText
        ScanTask.DESCRIBE_SURROUNDINGS -> strings.processingDescribing
        ScanTask.GUIDED_TASK -> strings.processingGuidedTask
    }

    LaunchedEffect(task) {
        if (errorMessage == null) {
            onAnnounceProcessing()
            onStartAnalysis()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundMidnightNavy)
    ) {
        AuroraBackground(
            modifier = Modifier.fillMaxSize(),
            primaryAlpha = 0.30f,
            secondaryAlpha = 0.20f
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { innerPadding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    if (errorMessage != null) {
                        Text(
                            text = strings.analysisIssueTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.analysisIssueMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextLightGrey
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        AssistantOrb(
                            state = AssistantState.ERROR,
                            size = 110.dp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        StatusMessage(
                            title = strings.errorPrefix.trimEnd(':', ' '),
                            message = errorMessage,
                            level = StatusLevel.ERROR
                        )
                    } else {
                        Text(
                            text = strings.processingTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.processingSubtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextLightGrey
                        )

                        Spacer(modifier = Modifier.height(36.dp))

                        // Assistant Orb showing fluid orbital motion
                        AssistantOrb(
                            state = AssistantState.PROCESSING,
                            size = 140.dp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        VoiceStateIndicator(
                            state = AssistantState.PROCESSING,
                            language = language
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (isProcessing) statusText else strings.preparingAnalysis,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextWhite
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = strings.processingDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLightGrey
                        )
                    }
                }

                // Bottom Actions
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (errorMessage != null) {
                        OptiScanButton(
                            text = strings.retry,
                            onClick = onRetry,
                            variant = ButtonVariant.AURORA,
                            icon = Icons.Default.Refresh,
                            contentDesc = "${strings.retry} button",
                            minHeight = 56,
                            bypassTripleTap = true
                        )

                        OptiScanButton(
                            text = strings.returnToCamera,
                            onClick = onCancel,
                            variant = ButtonVariant.SURFACE,
                            icon = Icons.Default.Close,
                            contentDesc = "${strings.returnToCamera} button",
                            minHeight = 56,
                            bypassTripleTap = true
                        )
                    } else {
                        OptiScanButton(
                            text = strings.cancel,
                            onClick = onCancel,
                            variant = ButtonVariant.SURFACE,
                            icon = Icons.Default.Close,
                            contentDesc = "${strings.cancel} button",
                            minHeight = 56,
                            bypassTripleTap = true
                        )
                    }
                }
            }
        }
    }
}
