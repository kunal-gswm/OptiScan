package com.optiscan.app.ui.components.aurora

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optiscan.app.ui.theme.AuroraAmber
import com.optiscan.app.ui.theme.AuroraCyan
import com.optiscan.app.ui.theme.AuroraError
import com.optiscan.app.ui.theme.AuroraTeal
import com.optiscan.app.ui.theme.AuroraViolet
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCardElevated
import com.optiscan.app.ui.theme.TextCoolWhite

import com.optiscan.app.navigation.AppLanguage

/**
 * Contextual status indicator pill displayed with the AssistantOrb.
 * Displays a glowing colored state dot and a concise status description.
 */
@Composable
fun VoiceStateIndicator(
    state: AssistantState,
    language: AppLanguage = AppLanguage.ENGLISH,
    customMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when (state) {
        AssistantState.IDLE -> AuroraCyan
        AssistantState.LISTENING -> AuroraCyan
        AssistantState.PROCESSING -> AuroraViolet
        AssistantState.SPEAKING -> AuroraCyan
        AssistantState.SUCCESS -> AuroraTeal
        AssistantState.ERROR -> AuroraError
        AssistantState.STOPPED -> AuroraAmber
    }

    val isHindi = language == AppLanguage.HINDI
    val defaultText = when (state) {
        AssistantState.IDLE -> if (isHindi) "तैयार हूँ" else "Ready when you are"
        AssistantState.LISTENING -> if (isHindi) "सुन रहा हूँ..." else "I'm listening..."
        AssistantState.PROCESSING -> if (isHindi) "विश्लेषण हो रहा है..." else "Analyzing..."
        AssistantState.SPEAKING -> if (isHindi) "बोल रहा हूँ..." else "Speaking aloud"
        AssistantState.SUCCESS -> if (isHindi) "पूर्ण हुआ" else "Done"
        AssistantState.ERROR -> if (isHindi) "सेवा अनुपलब्ध" else "Service unavailable"
        AssistantState.STOPPED -> if (isHindi) "सहायक रुका हुआ है" else "Assistant paused"
    }

    val displayText = customMessage ?: defaultText

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .background(SurfaceCardElevated.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        // Glowing status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(indicatorColor, CircleShape)
        )

        AnimatedContent(
            targetState = displayText,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "statusText"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp
                ),
                color = TextCoolWhite
            )
        }
    }
}
