package com.optiscan.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.optiscan.app.R
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.aurora.AuroraBackground
import com.optiscan.app.ui.theme.AuroraBlue
import com.optiscan.app.ui.theme.AuroraCyan
import com.optiscan.app.ui.theme.AuroraViolet
import com.optiscan.app.ui.theme.BackgroundMidnightNavy
import com.optiscan.app.ui.theme.BorderHighlight
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.SurfaceCardElevated
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen A: Welcome / Splash
 * Luminous OptiScan Aurora aesthetic, voice-first introduction, and accessible touch target.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSpeakIntro: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onSpeakIntro()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundMidnightNavy)
    ) {
        // Ambient Aurora background light
        AuroraBackground(
            modifier = Modifier.fillMaxSize(),
            primaryAlpha = 0.35f,
            secondaryAlpha = 0.25f
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Main Hero Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Luminous Badge Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(SurfaceCardElevated)
                        .border(2.dp, BorderHighlight.copy(alpha = 0.5f), CircleShape)
                        .semantics { contentDescription = "OptiScan Vision Assistant Logo" }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(112.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // App Title
                Text(
                    text = "OptiScan",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Luminous Subtitle
                Text(
                    text = "See the world with your voice.",
                    style = MaterialTheme.typography.titleMedium,
                    color = AuroraCyan,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Accessible descriptive text
                Text(
                    text = "An accessible AI assistant empowering blind and low-vision users to explore surroundings, read text, and navigate tasks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Feature Highlights Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    WelcomeFeaturePill(icon = Icons.Default.Visibility, label = "Scene AI", accent = AuroraCyan)
                    WelcomeFeaturePill(icon = Icons.Default.Description, label = "Offline OCR", accent = AuroraBlue)
                    WelcomeFeaturePill(icon = Icons.Default.RecordVoiceOver, label = "Voice First", accent = AuroraViolet)
                }
            }

            // Primary Bottom Action
            OptiScanButton(
                text = "Get Started",
                onClick = onGetStarted,
                variant = ButtonVariant.AURORA,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDesc = "Get Started button. Double-tap to enter the main menu.",
                minHeight = 64,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WelcomeFeaturePill(
    icon: ImageVector,
    label: String,
    accent: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextWhite,
            fontWeight = FontWeight.Medium
        )
    }
}
