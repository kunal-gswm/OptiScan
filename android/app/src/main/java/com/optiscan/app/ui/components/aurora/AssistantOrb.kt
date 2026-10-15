package com.optiscan.app.ui.components.aurora

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.optiscan.app.ui.theme.AuroraAmber
import com.optiscan.app.ui.theme.AuroraBlue
import com.optiscan.app.ui.theme.AuroraCyan
import com.optiscan.app.ui.theme.AuroraError
import com.optiscan.app.ui.theme.AuroraIndigo
import com.optiscan.app.ui.theme.AuroraTeal
import com.optiscan.app.ui.theme.AuroraViolet
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signature OptiScan Aurora Voice Assistant Orb.
 *
 * A multi-layered, luminous animated visual that reflects the live state machine of the
 * accessibility assistant (IDLE, LISTENING, PROCESSING, SPEAKING, SUCCESS, ERROR, STOPPED).
 *
 * Features:
 * - Fluid radial bloom core with electric cyan, vivid blue, and violet gradients.
 * - Outward expanding ripple rings in LISTENING state reacting to [amplitude].
 * - Orbital twin arcs flowing during PROCESSING state.
 * - Rhythmic harmonic waveform pulses during SPEAKING state.
 * - Accessible TalkBack semantics reporting current assistant status without spamming.
 */
@Composable
fun AssistantOrb(
    state: AssistantState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    amplitude: Float = 0f,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbAmbient")

    // Slow ambient rotation phase for processing / idle shimmer
    val rotationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.PROCESSING -> 2400
                    AssistantState.LISTENING -> 4000
                    AssistantState.SPEAKING -> 3200
                    else -> 9000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )

    // Gentle breathing pulse for idle / resting state
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreathing"
    )

    // Speaking waveform oscillation
    val waveOscillation by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveOscillation"
    )

    // Smooth transition for target scale depending on state and amplitude
    val targetScale = when (state) {
        AssistantState.STOPPED -> 0.90f
        AssistantState.IDLE, AssistantState.WAITING_FOR_USER -> breathingPulse
        AssistantState.LISTENING -> 1.0f + (amplitude.coerceIn(0f, 1f) * 0.28f)
        AssistantState.PROCESSING, AssistantState.COMMAND_PROCESSING -> 1.02f
        AssistantState.SPEAKING -> waveOscillation
        AssistantState.SUCCESS -> 1.14f
        AssistantState.ERROR -> 0.98f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "orbScale"
    )

    // Primary core color transition based on state
    val corePrimaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.ERROR -> AuroraError
            AssistantState.SUCCESS -> AuroraTeal
            AssistantState.LISTENING -> AuroraCyan
            AssistantState.PROCESSING, AssistantState.COMMAND_PROCESSING -> AuroraIndigo
            AssistantState.SPEAKING -> AuroraBlue
            AssistantState.STOPPED -> AuroraViolet.copy(alpha = 0.5f)
            AssistantState.IDLE, AssistantState.WAITING_FOR_USER -> AuroraCyan
        },
        animationSpec = tween(durationMillis = 400),
        label = "coreColor"
    )

    // Secondary glow color transition
    val coreSecondaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.ERROR -> AuroraAmber
            AssistantState.SUCCESS -> AuroraCyan
            AssistantState.LISTENING -> AuroraBlue
            AssistantState.PROCESSING, AssistantState.COMMAND_PROCESSING -> AuroraViolet
            AssistantState.SPEAKING -> AuroraViolet
            AssistantState.STOPPED -> Color.DarkGray
            AssistantState.IDLE, AssistantState.WAITING_FOR_USER -> AuroraViolet
        },
        animationSpec = tween(durationMillis = 400),
        label = "secondaryColor"
    )

    val stateDescription = when (state) {
        AssistantState.IDLE -> "OptiScan Voice Assistant. Ready. Double tap to activate voice."
        AssistantState.LISTENING -> "Listening for your voice. Speak now."
        AssistantState.PROCESSING -> "Analyzing visual information..."
        AssistantState.COMMAND_PROCESSING -> "Processing voice command..."
        AssistantState.WAITING_FOR_USER -> "Waiting for voice command."
        AssistantState.SPEAKING -> "Speaking aloud. Tap to pause or stop."
        AssistantState.SUCCESS -> "Task completed successfully."
        AssistantState.ERROR -> "Voice assistant encountered an issue."
        AssistantState.STOPPED -> "Assistant paused."
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                this.role = Role.Button
                this.contentDescription = stateDescription
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(bounded = false, radius = size / 2),
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.65f
            val currentRadius = baseRadius * animatedScale

            // 1. Outermost Ambient Bloom Ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        corePrimaryColor.copy(alpha = 0.28f),
                        coreSecondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.55f
                ),
                radius = currentRadius * 1.55f,
                center = center
            )

            // 2. Middle Luminescence Ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        corePrimaryColor.copy(alpha = 0.45f),
                        coreSecondaryColor.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.25f
                ),
                radius = currentRadius * 1.25f,
                center = center
            )

            // 3. Central Fluid Luminous Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        corePrimaryColor,
                        coreSecondaryColor,
                        coreSecondaryColor.copy(alpha = 0.70f)
                    ),
                    center = center,
                    radius = currentRadius
                ),
                radius = currentRadius,
                center = center
            )

            // 4. State-Specific Overlays
            when (state) {
                AssistantState.LISTENING -> {
                    // Reactive acoustic ripple rings expanding outward
                    val rippleAlpha = (0.7f - (amplitude * 0.3f)).coerceIn(0.1f, 0.8f)
                    drawCircle(
                        color = AuroraCyan.copy(alpha = rippleAlpha),
                        radius = currentRadius * (1.18f + amplitude * 0.25f),
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = AuroraBlue.copy(alpha = rippleAlpha * 0.5f),
                        radius = currentRadius * (1.35f + amplitude * 0.35f),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                AssistantState.PROCESSING, AssistantState.COMMAND_PROCESSING -> {
                    // Twin luminous orbital arcs rotating around perimeter
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, AuroraCyan, Color.Transparent)
                        ),
                        startAngle = rotationPhase,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(center.x - currentRadius * 1.18f, center.y - currentRadius * 1.18f),
                        size = androidx.compose.ui.geometry.Size(currentRadius * 2.36f, currentRadius * 2.36f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, AuroraViolet, Color.Transparent)
                        ),
                        startAngle = rotationPhase + 180f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(center.x - currentRadius * 1.18f, center.y - currentRadius * 1.18f),
                        size = androidx.compose.ui.geometry.Size(currentRadius * 2.36f, currentRadius * 2.36f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                AssistantState.SPEAKING -> {
                    // Rhythmic harmonic acoustic waves
                    val waveCount = 8
                    for (i in 0 until waveCount) {
                        val angle = (i * (360f / waveCount) + rotationPhase * 0.5f) * (Math.PI / 180f)
                        val length = (10.dp.toPx() * waveOscillation)
                        val startX = center.x + (currentRadius * 1.05f) * cos(angle).toFloat()
                        val startY = center.y + (currentRadius * 1.05f) * sin(angle).toFloat()
                        val endX = center.x + (currentRadius * 1.05f + length) * cos(angle).toFloat()
                        val endY = center.y + (currentRadius * 1.05f + length) * sin(angle).toFloat()

                        drawLine(
                            brush = Brush.linearGradient(listOf(AuroraCyan, AuroraViolet)),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                AssistantState.SUCCESS -> {
                    // Crisp emerald-teal confirmation aura
                    drawCircle(
                        color = AuroraTeal.copy(alpha = 0.6f),
                        radius = currentRadius * 1.25f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                AssistantState.ERROR -> {
                    // Amber warning ring
                    drawCircle(
                        color = AuroraAmber.copy(alpha = 0.7f),
                        radius = currentRadius * 1.20f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                AssistantState.IDLE, AssistantState.WAITING_FOR_USER, AssistantState.STOPPED -> {
                    // Crisp inner specular highlight
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = currentRadius * 0.35f,
                        center = Offset(center.x - currentRadius * 0.25f, center.y - currentRadius * 0.25f)
                    )
                }
            }
        }
    }
}
