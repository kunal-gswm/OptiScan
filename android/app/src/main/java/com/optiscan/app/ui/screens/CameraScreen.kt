package com.optiscan.app.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.CameraPreviewView
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.components.rememberAccessibleTapHandler
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.AuroraCyan
import com.optiscan.app.ui.theme.BackgroundMidnightNavy
import com.optiscan.app.ui.theme.BorderHighlight
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.SurfaceCardElevated
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen C: Camera
 * Full-screen accessible viewfinder with OptiScan Aurora framing, live CameraX integration,
 * flash toggle, and prominent capture trigger.
 */
@Composable
fun CameraScreen(
    task: ScanTask,
    hasCameraPermission: Boolean,
    language: com.optiscan.app.navigation.AppLanguage = com.optiscan.app.navigation.AppLanguage.ENGLISH,
    onRequestCameraPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCaptureClicked: () -> Unit,
    onBackClick: () -> Unit,
    onInstructionSpoken: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = com.optiscan.app.ui.localization.AppStrings.get(language)
    var flashEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onInstructionSpoken()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundMidnightNavy
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Back & Flash
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circular Halo Back Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics {
                        this.role = Role.Button
                        this.contentDescription = strings.returnToPreviousScreen
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .background(SurfaceCard, CircleShape)
                            .border(1.dp, BorderSubtle, CircleShape)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (language == com.optiscan.app.navigation.AppLanguage.HINDI) "होम" else "Home",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )
                }

                // Flash Toggle Button
                OptiScanButton(
                    text = if (flashEnabled) strings.flashOn else strings.flashOff,
                    onClick = { flashEnabled = !flashEnabled },
                    variant = if (flashEnabled) ButtonVariant.AURORA else ButtonVariant.SURFACE,
                    icon = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDesc = if (flashEnabled) "${strings.flashOn}. Tap to toggle." else "${strings.flashOff}. Tap to toggle.",
                    minHeight = 48,
                    bypassTripleTap = true,
                    modifier = Modifier.width(150.dp)
                )
            }

            // Task banner & instruction
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.taskTitle(task),
                    style = MaterialTheme.typography.headlineMedium,
                    color = AuroraCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (language == com.optiscan.app.navigation.AppLanguage.HINDI) {
                        "कैमरा वस्तु की ओर करें। वॉल्यूम बटन दबाएं या कैप्चर पर टैप करें।"
                    } else {
                        "Point camera at object. Press volume button or tap Capture."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextWhite
                )
            }

            val viewfinderDesc = if (language == com.optiscan.app.navigation.AppLanguage.HINDI) {
                "कैमरा व्यूफ़ाइंडर। फ़ोटो खींचने के लिए वॉल्यूम बटन दबाएं या तीन बार टैप करें।"
            } else {
                "Camera Viewfinder. Double tap for guidance. Triple tap anywhere or press volume key to capture photo"
            }

            val viewfinderTapHandler = rememberAccessibleTapHandler(
                description = viewfinderDesc,
                onAction = {
                    if (hasCameraPermission) {
                        onCaptureClicked()
                    } else {
                        onRequestCameraPermission()
                    }
                }
            )

            // Viewfinder Area (Full-screen accessible tap target with luminous border)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCardElevated)
                    .border(1.5.dp, BorderHighlight.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (hasCameraPermission) "Capture photo from viewfinder" else strings.grantCameraPermission,
                        onClick = viewfinderTapHandler
                    )
                    .semantics {
                        this.role = Role.Button
                        this.contentDescription = viewfinderDesc
                    }
            ) {
                if (hasCameraPermission) {
                    CameraPreviewView(
                        onPreviewViewCreated = onPreviewReady,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Simulated / Permission Needed Viewfinder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        StatusMessage(
                            message = strings.cameraPermissionMessage,
                            level = StatusLevel.WARNING,
                            title = strings.cameraPermissionTitle
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OptiScanButton(
                            text = strings.grantCameraPermission,
                            onClick = onRequestCameraPermission,
                            variant = ButtonVariant.AURORA,
                            contentDesc = "${strings.grantCameraPermission} button",
                            bypassTripleTap = true
                        )
                    }
                }
            }

            // Bottom Capture Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                if (hasCameraPermission) {
                    OptiScanButton(
                        text = strings.capturePhoto,
                        onClick = onCaptureClicked,
                        variant = ButtonVariant.AURORA,
                        icon = Icons.Default.Camera,
                        contentDesc = "${strings.capturePhoto} button",
                        minHeight = 72
                    )
                } else {
                    OptiScanButton(
                        text = strings.grantCameraPermission,
                        onClick = onRequestCameraPermission,
                        variant = ButtonVariant.AURORA,
                        contentDesc = "${strings.grantCameraPermission} button",
                        minHeight = 72,
                        bypassTripleTap = true
                    )
                }
            }
        }
    }
}
