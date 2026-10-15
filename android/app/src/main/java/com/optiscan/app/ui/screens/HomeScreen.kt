package com.optiscan.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.AccessibleActionCard
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.components.aurora.AssistantOrb
import com.optiscan.app.ui.components.aurora.AssistantState
import com.optiscan.app.ui.components.aurora.AuroraBackground
import com.optiscan.app.ui.components.aurora.VoiceStateIndicator
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.AuroraAmber
import com.optiscan.app.ui.theme.AuroraBlue
import com.optiscan.app.ui.theme.AuroraCyan
import com.optiscan.app.ui.theme.AuroraTeal
import com.optiscan.app.ui.theme.AuroraViolet
import com.optiscan.app.ui.theme.BackgroundMidnightNavy
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen B: Home
 * Centered around the signature OptiScan Aurora AssistantOrb, featuring responsive
 * voice state visualization, accessible touch targets, and distinct task cards.
 */
@Composable
fun HomeScreen(
    language: AppLanguage = AppLanguage.ENGLISH,
    assistantState: AssistantState = AssistantState.IDLE,
    speechAmplitude: Float = 0f,
    onNavigateToCamera: (ScanTask) -> Unit,
    onStartGuidedTask: () -> Unit = { onNavigateToCamera(ScanTask.GUIDED_TASK) },
    onNavigateToAskQuestion: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onAnnounceHome: () -> Unit,
    onNavigateToOnDeviceConversation: (() -> Unit)? = null,
    onResumeListening: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.get(language)

    LaunchedEffect(Unit) {
        onAnnounceHome()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundMidnightNavy)
    ) {
        // Fluid, low-overhead background aurora
        AuroraBackground(
            modifier = Modifier.fillMaxSize(),
            primaryAlpha = 0.28f,
            secondaryAlpha = 0.20f
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
                // Header Bar with Title and Halo Settings Icon
                ScreenTitle(
                    title = strings.appName,
                    subtitle = strings.homeSubtitle,
                    trailingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SurfaceCard)
                        ) {
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .semantics {
                                        this.contentDescription = strings.openSettingsContentDescription
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = TextWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                )

                // Voice Assistant Hero Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceCard)
                        .padding(vertical = 18.dp, horizontal = 16.dp)
                ) {
                    // Central luminous assistant orb
                    AssistantOrb(
                        state = assistantState,
                        amplitude = speechAmplitude,
                        size = 120.dp,
                        onClick = {
                            if (assistantState == AssistantState.WAITING_FOR_USER || assistantState == AssistantState.STOPPED) {
                                onResumeListening?.invoke() ?: onNavigateToAskQuestion()
                            } else {
                                onNavigateToAskQuestion()
                            }
                        },
                        modifier = Modifier.semantics {
                            this.role = Role.Button
                            this.contentDescription = "Voice Assistant Orb. Double tap to speak."
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real-time status indicator pill
                    VoiceStateIndicator(
                        state = assistantState,
                        language = language
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (language == AppLanguage.HINDI) "आवाज़ से प्रश्न पूछने के लिए गोला टैप करें" else "Tap orb or use Voice Assistant to speak",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextLightGrey
                    )
                }

                Text(
                    text = if (language == AppLanguage.HINDI) "कार्यों का चयन करें" else "Select a task",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 4.dp)
                        .semantics { heading() }
                )

                // Action 1: Describe Surroundings & Objects (Primary Vision Feature - Electric Cyan)
                AccessibleActionCard(
                    title = strings.describeSurroundingsTitle,
                    subtitle = strings.describeSurroundingsSubtitle,
                    icon = Icons.Default.Visibility,
                    accentColor = AuroraCyan,
                    onClick = { onNavigateToCamera(ScanTask.DESCRIBE_SURROUNDINGS) }
                )

                // Action 2: Read Text (On-Device OCR - Vivid Blue)
                AccessibleActionCard(
                    title = strings.readTextTitle,
                    subtitle = strings.readTextSubtitle,
                    icon = Icons.Default.Description,
                    accentColor = AuroraBlue,
                    onClick = { onNavigateToCamera(ScanTask.READ_TEXT) }
                )

                // Action 3: Ask a Question (Voice Assistant - Deep Violet)
                AccessibleActionCard(
                    title = strings.askQuestionTitle,
                    subtitle = strings.askQuestionSubtitle,
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    accentColor = AuroraViolet,
                    onClick = onNavigateToAskQuestion
                )

                // Action 4: Guided Task Assistance (Step-by-step - Restrained Teal)
                AccessibleActionCard(
                    title = strings.guidedTaskActionTitle,
                    subtitle = strings.guidedTaskActionSubtitle,
                    icon = Icons.Default.TaskAlt,
                    accentColor = AuroraTeal,
                    onClick = onStartGuidedTask
                )

                // Action 5: On-Device AI Voice Conversation (Offline Prototype - Warm Amber)
                if (onNavigateToOnDeviceConversation != null) {
                    AccessibleActionCard(
                        title = strings.onDeviceAiModeButton,
                        subtitle = strings.onDeviceAiModeSubtitle,
                        icon = Icons.Default.PhoneAndroid,
                        accentColor = AuroraAmber,
                        onClick = onNavigateToOnDeviceConversation
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Settings quick access button at the bottom for low-dexterity reach
                OptiScanButton(
                    text = strings.settingsButton,
                    onClick = onNavigateToSettings,
                    variant = ButtonVariant.SURFACE,
                    icon = Icons.Default.Settings,
                    contentDesc = strings.openSettingsContentDescription,
                    minHeight = 56
                )

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
