package com.optiscan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.AccessibleActionCard
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.ActionSecondaryGreen
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen B: Home
 * Displays accessible, full-width actions for TalkBack and low-vision accessibility.
 */
@Composable
fun HomeScreen(
    language: AppLanguage = AppLanguage.ENGLISH,
    onNavigateToCamera: (ScanTask) -> Unit,
    onStartGuidedTask: () -> Unit = { onNavigateToCamera(ScanTask.GUIDED_TASK) },
    onNavigateToAskQuestion: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onAnnounceHome: () -> Unit,
    onNavigateToOnDeviceConversation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.get(language)

    LaunchedEffect(Unit) {
        onAnnounceHome()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar with Title and Settings Icon
            ScreenTitle(
                title = strings.appName,
                subtitle = strings.homeSubtitle,
                trailingContent = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(52.dp)
                            .semantics {
                                this.contentDescription = strings.openSettingsContentDescription
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action 1: Describe Surroundings & Objects (Primary Vision Feature)
            AccessibleActionCard(
                title = strings.describeSurroundingsTitle,
                subtitle = strings.describeSurroundingsSubtitle,
                icon = Icons.Default.Visibility,
                accentColor = ActionPrimaryBlue,
                onClick = { onNavigateToCamera(ScanTask.DESCRIBE_SURROUNDINGS) }
            )

            // Action 2: Read Text (On-Device OCR)
            AccessibleActionCard(
                title = strings.readTextTitle,
                subtitle = strings.readTextSubtitle,
                icon = Icons.Default.Description,
                accentColor = ActionSecondaryGreen,
                onClick = { onNavigateToCamera(ScanTask.READ_TEXT) }
            )

            // Action 3: Ask a Question
            AccessibleActionCard(
                title = strings.askQuestionTitle,
                subtitle = strings.askQuestionSubtitle,
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                accentColor = ActionPrimaryBlue,
                onClick = onNavigateToAskQuestion
            )

            // Action 4: Guided Task Assistance (Secondary Step-by-Step Feature)
            AccessibleActionCard(
                title = strings.guidedTaskActionTitle,
                subtitle = strings.guidedTaskActionSubtitle,
                icon = Icons.Default.TaskAlt,
                accentColor = ActionSecondaryGreen,
                onClick = onStartGuidedTask
            )

            // Action 5: On-Device AI Voice Conversation (Offline Mode)
            if (onNavigateToOnDeviceConversation != null) {
                AccessibleActionCard(
                    title = strings.onDeviceAiModeButton,
                    subtitle = strings.onDeviceAiModeSubtitle,
                    icon = Icons.Default.PhoneAndroid,
                    accentColor = ActionSecondaryGreen,
                    onClick = onNavigateToOnDeviceConversation
                )
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Secondary explicit Settings button at the bottom for easy reach
            OptiScanButton(
                text = strings.settingsButton,
                onClick = onNavigateToSettings,
                variant = ButtonVariant.SURFACE,
                icon = Icons.Default.Settings,
                contentDesc = strings.openSettingsContentDescription
            )
        }
    }
}
