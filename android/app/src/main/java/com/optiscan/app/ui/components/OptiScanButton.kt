package com.optiscan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.ActionSecondaryGreen
import com.optiscan.app.ui.theme.AlertError
import com.optiscan.app.ui.theme.BorderHighlight
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

enum class ButtonVariant {
    PRIMARY,
    SECONDARY,
    SURFACE,
    OUTLINE,
    DANGER,
    AURORA
}

/**
 * Accessibility-first button enforcing minimum touch targets, high contrast, and clear semantics.
 * Styled with OptiScan Aurora luminous accents.
 */
@Composable
fun OptiScanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    contentDesc: String? = null,
    enabled: Boolean = true,
    minHeight: Int = 60,
    bypassTripleTap: Boolean = false
) {
    val containerColor = when (variant) {
        ButtonVariant.PRIMARY -> ActionPrimaryBlue
        ButtonVariant.SECONDARY -> SurfaceCard
        ButtonVariant.SURFACE -> SurfaceCard
        ButtonVariant.OUTLINE -> Color.Transparent
        ButtonVariant.DANGER -> AlertError
        ButtonVariant.AURORA -> ActionPrimaryBlue
    }

    val contentColor = TextWhite

    val border = when (variant) {
        ButtonVariant.AURORA -> BorderStroke(1.5.dp, BorderHighlight)
        ButtonVariant.OUTLINE -> BorderStroke(2.dp, BorderHighlight.copy(alpha = 0.8f))
        ButtonVariant.SURFACE -> BorderStroke(1.5.dp, BorderSubtle)
        ButtonVariant.SECONDARY -> BorderStroke(1.5.dp, BorderSubtle)
        ButtonVariant.DANGER -> BorderStroke(1.dp, AlertError.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, BorderHighlight.copy(alpha = 0.35f))
    }

    val description = contentDesc ?: text
    val accessibleOnClick = rememberAccessibleTapHandler(
        description = description,
        enabled = enabled,
        tripleTapMode = if (bypassTripleTap) false else LocalTripleTapModeEnabled.current,
        onAction = onClick
    )

    Button(
        onClick = accessibleOnClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = SurfaceCard,
            disabledContentColor = TextLightGrey
        ),
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .semantics {
                this.role = Role.Button
                this.contentDescription = description
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // decorative when text is present
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}
