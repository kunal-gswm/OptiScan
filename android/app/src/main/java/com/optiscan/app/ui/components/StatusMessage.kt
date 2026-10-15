package com.optiscan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.ui.theme.AlertError
import com.optiscan.app.ui.theme.AlertInfo
import com.optiscan.app.ui.theme.AlertSuccess
import com.optiscan.app.ui.theme.AlertWarning
import com.optiscan.app.ui.theme.SurfaceDarkCharcoal
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

enum class StatusLevel {
    INFO,
    WARNING,
    ERROR,
    SUCCESS
}

/**
 * Accessible status announcement banner.
 * Does not rely on color alone; includes explicit icon and text prefix.
 */
@Composable
fun StatusMessage(
    message: String,
    modifier: Modifier = Modifier,
    level: StatusLevel = StatusLevel.INFO,
    title: String? = null
) {
    val (icon, color, prefix) = when (level) {
        StatusLevel.INFO -> Triple(Icons.Default.Info, AlertInfo, "Notice")
        StatusLevel.WARNING -> Triple(Icons.Default.Warning, AlertWarning, "Warning")
        StatusLevel.ERROR -> Triple(Icons.Default.Error, AlertError, "Error")
        StatusLevel.SUCCESS -> Triple(Icons.Default.CheckCircle, AlertSuccess, "Success")
    }

    val displayTitle = title ?: prefix
    val accessibleText = "$displayTitle: $message"

    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDarkCharcoal, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
            .padding(16.dp)
            .semantics {
                this.contentDescription = accessibleText
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite
            )
        }
    }
}
