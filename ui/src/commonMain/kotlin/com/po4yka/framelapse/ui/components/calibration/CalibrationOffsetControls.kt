package com.po4yka.framelapse.ui.components.calibration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Offset controls for fine-tuning calibration alignment target.
 *
 * Provides horizontal (X) and vertical (Y) offset sliders that allow
 * the user to shift the alignment target position.
 *
 * @param offsetX Current horizontal offset (-0.2 to +0.2).
 * @param offsetY Current vertical offset (-0.2 to +0.2).
 * @param onOffsetChange Callback when offset values change.
 * @param modifier Modifier for the container.
 * @param enabled Whether the controls are enabled.
 */
@Composable
fun CalibrationOffsetControls(
    offsetX: Float,
    offsetY: Float,
    onOffsetChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Alignment Offset",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal offset slider
            OffsetSlider(
                label = "Horizontal",
                value = offsetX,
                onValueChange = { onOffsetChange(it, offsetY) },
                enabled = enabled,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Vertical offset slider
            OffsetSlider(
                label = "Vertical",
                value = offsetY,
                onValueChange = { onOffsetChange(offsetX, it) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun OffsetSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.width(80.dp),
            )

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = OFFSET_RANGE,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )

            Text(
                text = formatOffsetValue(value),
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.width(48.dp),
            )
        }
    }
}

private fun formatOffsetValue(value: Float): String {
    val percentage = (value * 100).roundToInt()
    return when {
        percentage > 0 -> "+$percentage%"
        else -> "$percentage%"
    }
}

private val OFFSET_RANGE = -0.2f..0.2f
