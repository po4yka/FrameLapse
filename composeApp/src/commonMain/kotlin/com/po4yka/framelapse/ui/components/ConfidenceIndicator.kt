package com.po4yka.framelapse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.percentage_value
import org.jetbrains.compose.resources.stringResource

/**
 * Confidence indicator component for face detection.
 * Shows a colored bar and percentage indicating alignment quality.
 *
 * @param confidence The confidence value from 0.0 to 1.0
 * @param modifier Modifier for the indicator
 * @param showPercentage Whether to show the percentage text
 */
@Composable
fun ConfidenceIndicator(confidence: Float?, modifier: Modifier = Modifier, showPercentage: Boolean = true) {
    if (confidence == null) {
        return
    }

    val normalizedConfidence = confidence.coerceIn(0f, 1f)

    val indicatorColor by animateColorAsState(
        targetValue = getConfidenceColor(normalizedConfidence),
        animationSpec = tween(durationMillis = 300),
        label = "confidence_color",
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Confidence bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier
                    .width((40 * normalizedConfidence).dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(indicatorColor),
            )
        }

        // Percentage text
        if (showPercentage) {
            Text(
                text = stringResource(
                    Res.string.percentage_value,
                    (normalizedConfidence * 100).toInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = indicatorColor,
            )
        }
    }
}

/**
 * Returns the appropriate color based on confidence level.
 * Colors are chosen for WCAG AA compliance on dark backgrounds.
 * - Red: < 50% (poor alignment)
 * - Amber: 50-70% (acceptable alignment)
 * - Green: > 70% (good alignment)
 */
private fun getConfidenceColor(confidence: Float): Color = when {
    confidence < LOW_CONFIDENCE_THRESHOLD -> Color(0xFFFF6B6B) // Red (lightened for contrast)
    confidence < HIGH_CONFIDENCE_THRESHOLD -> Color(0xFFFFB300) // Amber (darkened for contrast)
    else -> Color(0xFF81C784) // Green (lightened for contrast)
}

private const val LOW_CONFIDENCE_THRESHOLD = 0.5f
private const val HIGH_CONFIDENCE_THRESHOLD = 0.7f
