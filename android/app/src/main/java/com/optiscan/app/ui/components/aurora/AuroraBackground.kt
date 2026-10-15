package com.optiscan.app.ui.components.aurora

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.optiscan.app.ui.theme.AuroraBlue
import com.optiscan.app.ui.theme.AuroraCyan
import com.optiscan.app.ui.theme.AuroraViolet
import com.optiscan.app.ui.theme.BackgroundMidnightNavy
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient, fluid Aurora gradient background for the OptiScan Aurora design system.
 *
 * Renders multiple soft, slowly moving radial light bands (electric cyan, vivid blue, deep violet)
 * over deep midnight navy. Uses native Compose Canvas drawing with minimal redraw overhead.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0.55f,
    primaryAlpha: Float = 0.35f,
    secondaryAlpha: Float = 0.25f,
    content: (@Composable () -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "auroraAmbient")

    // Slow ambient wave phase (0 to 2*PI over 16 seconds)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )

    // Subtle breathing pulse for light bloom
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundMidnightNavy)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            // Center 1: Top-right drifting cyan/blue band
            val x1 = width * (0.65f + 0.15f * cos(phase))
            val y1 = height * (0.18f + 0.08f * sin(phase))
            val r1 = (width * 0.75f) * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuroraCyan.copy(alpha = primaryAlpha * 0.50f * intensity),
                        AuroraBlue.copy(alpha = primaryAlpha * 0.28f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(x1, y1),
                    radius = r1
                ),
                radius = r1,
                center = Offset(x1, y1)
            )

            // Center 2: Middle-left drifting violet/indigo band
            val x2 = width * (0.25f - 0.12f * sin(phase))
            val y2 = height * (0.45f + 0.10f * cos(phase))
            val r2 = (width * 0.85f) * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuroraViolet.copy(alpha = secondaryAlpha * 0.60f * intensity),
                        AuroraBlue.copy(alpha = secondaryAlpha * 0.30f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(x2, y2),
                    radius = r2
                ),
                radius = r2,
                center = Offset(x2, y2)
            )

            // Center 3: Bottom ambient glow anchor
            val x3 = width * (0.50f + 0.10f * sin(phase * 0.5f))
            val y3 = height * 0.85f
            val r3 = width * 0.90f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuroraBlue.copy(alpha = primaryAlpha * 0.35f * intensity),
                        AuroraCyan.copy(alpha = primaryAlpha * 0.15f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(x3, y3),
                    radius = r3
                ),
                radius = r3,
                center = Offset(x3, y3)
            )
        }

        // Screen content rendered cleanly on top of the ambient canvas
        content?.invoke()
    }
}
