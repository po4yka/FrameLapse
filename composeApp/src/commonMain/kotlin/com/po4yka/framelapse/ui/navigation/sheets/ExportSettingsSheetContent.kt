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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Export settings configuration.
 */
data class ExportSettings(val fps: Int = 30, val quality: Float = 0.8f)

/**
 * Content for export settings bottom sheet - rendered as Nav3 bottom sheet scene.
 */
@Suppress("UnusedParameter")
@Composable
fun ExportSettingsSheetContent(
    projectId: String,
    onDismiss: () -> Unit,
    onApply: (ExportSettings) -> Unit = { onDismiss() },
) {
    var fps by rememberSaveable { mutableIntStateOf(30) }
    var quality by rememberSaveable { mutableFloatStateOf(0.8f) }

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
                text = "Export Settings",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // FPS setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Frames per second")
                Text(
                    text = "$fps FPS",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = fps.toFloat(),
                onValueChange = { fps = it.toInt() },
                valueRange = 1f..60f,
                steps = 58,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quality setting
            Text("Video quality: ${(quality * 100).toInt()}%")
            Slider(
                value = quality,
                onValueChange = { quality = it },
                valueRange = 0.1f..1f,
                steps = 8,
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
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onApply(ExportSettings(fps, quality))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
