package com.optiscan.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.R
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.ActionSecondaryGreen
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.SurfaceDarkCharcoal
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite
import com.optiscan.app.utils.NetworkConfig
import kotlin.math.roundToInt

/**
 * Screen I: Settings & Accessibility Preferences
 * Configures language, speech rate, voice feedback toggle, haptics, text scale,
 * backend server URL, and offline mock demonstration mode.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onTestSpeechRate: (Float) -> Unit,
    onTestConnection: ((String, (Boolean) -> Unit) -> Unit)? = null,
    onBackClick: () -> Unit,
    onAnnounceSettings: () -> Unit,
    onOpenOnDeviceConversation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.get(settings.language)

    LaunchedEffect(Unit) {
        onAnnounceSettings()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            ScreenTitle(
                title = strings.settingsTitle,
                subtitle = strings.settingsSubtitle,
                onBackClick = onBackClick,
                backContentDescription = strings.returnToPreviousScreen
            )

            // Section 1: App Language Selection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = strings.languageSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = strings.languageSectionSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = settings.language == lang
                        OptiScanButton(
                            text = if (lang == AppLanguage.HINDI) "हिंदी (Hindi)" else "English",
                            onClick = {
                                if (settings.language != lang) {
                                    onUpdateSettings(settings.copy(language = lang))
                                }
                            },
                            variant = if (isSelected) ButtonVariant.PRIMARY else ButtonVariant.SURFACE,
                            minHeight = 48,
                            contentDesc = if (isSelected) {
                                "${lang.displayName} selected"
                            } else {
                                "Select ${lang.displayName} language"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 2: Voice Feedback Switch
            SettingToggleCard(
                title = strings.voiceFeedbackTitle,
                description = strings.voiceFeedbackSubtitle,
                checked = settings.voiceFeedbackEnabled,
                onCheckedChange = { checked ->
                    onUpdateSettings(settings.copy(voiceFeedbackEnabled = checked))
                }
            )

            // Section 3: Speech Rate Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.speechRateTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )
                    Text(
                        text = "${String.format("%.2f", settings.speechRate)}x",
                        style = MaterialTheme.typography.titleMedium,
                        color = ActionPrimaryBlue
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = strings.speechRateSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = settings.speechRate,
                    onValueChange = { newRate ->
                        val roundedRate = (newRate * 4).roundToInt() / 4f
                        onUpdateSettings(settings.copy(speechRate = roundedRate))
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = ActionPrimaryBlue,
                        activeTrackColor = ActionPrimaryBlue,
                        inactiveTrackColor = SurfaceCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            this.contentDescription = "Speech rate slider: ${String.format("%.2f", settings.speechRate)} speed multiplier"
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OptiScanButton(
                    text = strings.testSpeechSpeed,
                    onClick = { onTestSpeechRate(settings.speechRate) },
                    variant = ButtonVariant.SURFACE,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDesc = "Play test speech sample at current rate",
                    minHeight = 48
                )
            }

            // Section 4: Text Size Preference
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = strings.textSizeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Current setting: ${settings.textSizeLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val scales = listOf(
                        Triple("Default", 1.0f, settings.textSizeScale == 1.0f),
                        Triple("Large", 1.2f, settings.textSizeScale == 1.2f),
                        Triple("X-Large", 1.4f, settings.textSizeScale == 1.4f)
                    )

                    scales.forEach { (label, scaleVal, isSelected) ->
                        OptiScanButton(
                            text = label,
                            onClick = { onUpdateSettings(settings.copy(textSizeScale = scaleVal)) },
                            variant = if (isSelected) ButtonVariant.PRIMARY else ButtonVariant.SURFACE,
                            minHeight = 48,
                            contentDesc = "Set text size to $label",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 5: Haptic Feedback Toggle
            SettingToggleCard(
                title = strings.hapticFeedbackTitle,
                description = strings.hapticFeedbackSubtitle,
                checked = settings.hapticFeedbackEnabled,
                onCheckedChange = { checked ->
                    onUpdateSettings(settings.copy(hapticFeedbackEnabled = checked))
                }
            )

            // Section 6: Backend Server Configuration
            var enteredUrl by remember(settings.customBackendUrl) {
                mutableStateOf(settings.customBackendUrl.ifBlank { NetworkConfig.activeBaseUrl })
            }
            var connectionStatus by remember { mutableStateOf<Boolean?>(null) }
            var isTestingConnection by remember { mutableStateOf(false) }

            val isConfiguredStatus = NetworkConfig.isValidUrl(enteredUrl)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.serverConfigTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        modifier = Modifier.weight(1f)
                    )

                    // Environment status badge
                    val badgeText = if (NetworkConfig.isLocalAddress(enteredUrl)) {
                        strings.localDevModeBadge
                    } else if (NetworkConfig.isValidHostedUrl(enteredUrl)) {
                        "Hosted Backend"
                    } else if (isConfiguredStatus) {
                        "Custom Backend"
                    } else {
                        strings.noHostedBackendConfiguredStatus
                    }
                    val badgeColor = if (isConfiguredStatus) ActionSecondaryGreen else MaterialTheme.colorScheme.error

                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeColor,
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (!isConfiguredStatus) {
                        strings.noHostedBackendConfiguredExplanation
                    } else {
                        strings.serverConfigSubtitle
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Local Dev Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = ActionPrimaryBlue
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OptiScanButton(
                        text = "Emulator",
                        onClick = { enteredUrl = NetworkConfig.EMULATOR_BASE_URL },
                        variant = ButtonVariant.SURFACE,
                        minHeight = 40,
                        modifier = Modifier.weight(1f)
                    )
                    OptiScanButton(
                        text = "USB Reverse",
                        onClick = { enteredUrl = NetworkConfig.PHYSICAL_DEVICE_REVERSE_URL },
                        variant = ButtonVariant.SURFACE,
                        minHeight = 40,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "For USB testing, run: adb reverse tcp:8000 tcp:8000\nFor Wi-Fi LAN testing, enter http://<computer-lan-ip>:8000/",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLightGrey
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = enteredUrl,
                    onValueChange = { enteredUrl = it },
                    label = { Text(strings.serverUrlLabel, color = TextLightGrey) },
                    placeholder = {
                        Text(
                            "http://127.0.0.1:8000/ or https://...",
                            color = TextLightGrey
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = ActionPrimaryBlue,
                        unfocusedBorderColor = BorderSubtle,
                        cursorColor = ActionPrimaryBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            this.contentDescription = "${strings.serverUrlLabel}: $enteredUrl"
                        }
                )

                if (enteredUrl.isNotBlank() && NetworkConfig.isLocalAddress(enteredUrl)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Local Dev Mode: Connect phone via USB (adb reverse tcp:8000 tcp:8000) or Wi-Fi LAN IP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ActionPrimaryBlue
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OptiScanButton(
                        text = strings.saveServerUrlButton,
                        onClick = {
                            val normalized = NetworkConfig.normalizeUrl(enteredUrl)
                            onUpdateSettings(settings.copy(customBackendUrl = normalized))
                        },
                        variant = ButtonVariant.PRIMARY,
                        minHeight = 48,
                        bypassTripleTap = true,
                        modifier = Modifier.weight(1f)
                    )

                    OptiScanButton(
                        text = if (isTestingConnection) "..." else strings.testConnectionButton,
                        onClick = {
                            if (onTestConnection != null) {
                                isTestingConnection = true
                                val urlToTest = NetworkConfig.normalizeUrl(enteredUrl)
                                onTestConnection(urlToTest) { success ->
                                    isTestingConnection = false
                                    connectionStatus = success
                                }
                            }
                        },
                        variant = ButtonVariant.SURFACE,
                        minHeight = 48,
                        bypassTripleTap = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (connectionStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (connectionStatus == true) strings.connectionStatusConnected else strings.connectionStatusFailed,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connectionStatus == true) ActionSecondaryGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics {
                            this.contentDescription = if (connectionStatus == true) strings.connectionStatusConnected else strings.connectionStatusFailed
                        }
                    )
                }
            }

            // Section 7: Offline Mock Mode
            SettingToggleCard(
                title = strings.offlineMockTitle,
                description = strings.offlineMockSubtitle,
                checked = settings.useMockBackend,
                onCheckedChange = { checked ->
                    onUpdateSettings(settings.copy(useMockBackend = checked))
                }
            )

            // Section 8: 3-Tap Accessible Navigation Toggle
            val isHindi = settings.language == AppLanguage.HINDI
            SettingToggleCard(
                title = if (isHindi) "3-टैप नेविगेशन (टच सुरक्षा)" else "3-Tap Navigation (Touch Protection)",
                description = if (isHindi)
                    "सिंगल टैप: सुरक्षा के लिए अनदेखा करता है। डबल टैप: बटन का विवरण बोलता है। ट्रिपल टैप: क्रिया को सक्रिय करता है।"
                else
                    "Single tap: ignores action for safety. Double tap: speaks button description aloud. Triple tap: activates the action.",
                checked = settings.tripleTapNavigationEnabled,
                onCheckedChange = { checked ->
                    onUpdateSettings(settings.copy(tripleTapNavigationEnabled = checked))
                }
            )

            // Section 9: On-Device AI (Qwen2-VL)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.onDeviceAiSettingsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
                Text(
                    text = strings.onDeviceAiSettingsSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Toggle: On-Device AI vs Cloud Gemini
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (settings.useOnDeviceAi)
                            (if (isHindi) "ऑन-डिवाइस AI (चालू)" else "On-Device AI (Active)")
                        else
                            (if (isHindi) "क्लाउड Gemini (चालू)" else "Cloud Gemini (Active)"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.Switch(
                        checked = settings.useOnDeviceAi,
                        onCheckedChange = { checked ->
                            onUpdateSettings(settings.copy(useOnDeviceAi = checked))
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = TextWhite,
                            checkedTrackColor = ActionSecondaryGreen,
                            uncheckedThumbColor = TextLightGrey,
                            uncheckedTrackColor = SurfaceCard
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = if (settings.useOnDeviceAi)
                                "On-Device AI mode enabled. Tap to switch to Cloud Gemini."
                            else
                                "Cloud Gemini mode enabled. Tap to switch to On-Device AI."
                        }
                    )
                }
                // Toggle: Diagnostics display
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.onDeviceAiDiagnosticsTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite
                        )
                        Text(
                            text = strings.onDeviceAiDiagnosticsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLightGrey
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = settings.showOnDeviceDiagnostics,
                        onCheckedChange = { checked ->
                            onUpdateSettings(settings.copy(showOnDeviceDiagnostics = checked))
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = TextWhite,
                            checkedTrackColor = ActionPrimaryBlue,
                            uncheckedThumbColor = TextLightGrey,
                            uncheckedTrackColor = SurfaceCard
                        )
                    )
                }
                // Open On-Device Conversation button
                if (onOpenOnDeviceConversation != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    OptiScanButton(
                        text = strings.onDeviceAiModeButton,
                        onClick = onOpenOnDeviceConversation,
                        variant = ButtonVariant.PRIMARY,
                        contentDesc = strings.onDeviceAiModeButton,
                        minHeight = 52
                    )
                }
            }

            // Section 10: About & Version
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.aboutTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = strings.aboutDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )
            }

            // Back to Home Button
            OptiScanButton(
                text = strings.backToHome,
                onClick = onBackClick,
                variant = ButtonVariant.OUTLINE,
                icon = Icons.Default.Home,
                contentDesc = strings.returnToPreviousScreen,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
            .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(18.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = "$title. $description. Currently ${if (checked) "enabled" else "disabled"}."
            }
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextLightGrey
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextWhite,
                checkedTrackColor = ActionPrimaryBlue,
                uncheckedThumbColor = TextLightGrey,
                uncheckedTrackColor = SurfaceCard
            ),
            modifier = Modifier.semantics {
                this.contentDescription = "$title toggle"
            }
        )
    }
}
