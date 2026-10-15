package com.optiscan.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.theme.AuroraCyan
import com.optiscan.app.ui.theme.BackgroundMidnightNavy
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.SurfaceCardElevated
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen D: Image Preview
 * Displays captured photo with clear Aurora styling and two high-contrast actions.
 */
@Composable
fun ImagePreviewScreen(
    task: ScanTask,
    bitmap: Bitmap?,
    language: com.optiscan.app.navigation.AppLanguage = com.optiscan.app.navigation.AppLanguage.ENGLISH,
    onUsePhoto: () -> Unit,
    onRetakePhoto: () -> Unit,
    onAnnounce: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = com.optiscan.app.ui.localization.AppStrings.get(language)

    LaunchedEffect(Unit) {
        onAnnounce()
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
            // Header
            ScreenTitle(
                title = strings.previewTitle,
                subtitle = "${strings.previewSubtitle} ${strings.taskTitle(task)}",
                onBackClick = onRetakePhoto,
                backContentDescription = strings.returnToPreviousScreen
            )

            // Captured Image Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCardElevated)
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    .semantics {
                        this.contentDescription = strings.photoCapturedCardTitle
                    }
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = strings.photoCapturedCardTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Simulated capture representation
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = AuroraCyan,
                            modifier = Modifier.size(68.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.photoCapturedCardTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.photoCapturedCardSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLightGrey
                        )
                    }
                }
            }

            // Two Large Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                OptiScanButton(
                    text = strings.useThisPhoto,
                    onClick = onUsePhoto,
                    variant = ButtonVariant.AURORA,
                    icon = Icons.Default.Check,
                    contentDesc = "${strings.useThisPhoto} button",
                    minHeight = 64
                )

                OptiScanButton(
                    text = strings.retakePhoto,
                    onClick = onRetakePhoto,
                    variant = ButtonVariant.SURFACE,
                    icon = Icons.Default.Refresh,
                    contentDesc = "${strings.retakePhoto} button",
                    minHeight = 60,
                    bypassTripleTap = true
                )
            }
        }
    }
}
