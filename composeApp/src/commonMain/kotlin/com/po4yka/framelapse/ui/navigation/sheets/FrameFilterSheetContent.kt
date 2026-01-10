package com.po4yka.framelapse.ui.navigation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import framelapse.ui.generated.resources.Res
import framelapse.ui.generated.resources.action_apply
import framelapse.ui.generated.resources.action_cancel
import framelapse.ui.generated.resources.frame_filter_date_range
import framelapse.ui.generated.resources.frame_filter_min_confidence
import framelapse.ui.generated.resources.frame_filter_title
import org.jetbrains.compose.resources.stringResource

/**
 * Frame filter settings for the gallery.
 */
data class FrameFilters(val dateRangeEnabled: Boolean = false, val minConfidence: Float = 0.7f)

/**
 * Content for frame filter bottom sheet - rendered as Nav3 bottom sheet scene.
 */
@Suppress("UnusedParameter")
@Composable
fun FrameFilterSheetContent(
    projectId: String,
    onDismiss: () -> Unit,
    onApply: (FrameFilters) -> Unit = { onDismiss() },
) {
    var dateRangeEnabled by rememberSaveable { mutableStateOf(false) }
    var minConfidence by rememberSaveable { mutableFloatStateOf(0.7f) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.frame_filter_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Date range filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.frame_filter_date_range))
                Switch(
                    checked = dateRangeEnabled,
                    onCheckedChange = { dateRangeEnabled = it },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confidence threshold slider
            Text(stringResource(Res.string.frame_filter_min_confidence, (minConfidence * 100).toInt()))
            Slider(
                value = minConfidence,
                onValueChange = { minConfidence = it },
                valueRange = 0f..1f,
                steps = 9,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Button(
                    onClick = {
                        onApply(FrameFilters(dateRangeEnabled, minConfidence))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.action_apply))
                }
            }
        }
    }
}
