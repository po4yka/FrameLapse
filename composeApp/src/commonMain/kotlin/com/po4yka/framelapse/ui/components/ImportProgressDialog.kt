package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.po4yka.framelapse.domain.entity.ImportPhase
import com.po4yka.framelapse.domain.entity.ImportProgress

/**
 * Dialog showing progress during photo import.
 *
 * Displays:
 * - "Importing Photos" title
 * - Linear progress bar
 * - "X of Y" progress text
 * - Current phase description
 * - Error count if any
 * - Cancel button
 *
 * @param progress Current import progress
 * @param onCancel Called when user cancels the import
 */
@Composable
fun ImportProgressDialog(progress: ImportProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Dialog(
        onDismissRequest = { /* Cannot dismiss by clicking outside */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                // Title
                Text(
                    text = "Importing Photos",
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${progress.currentIndex} of ${progress.totalCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${progress.progressPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Phase description
                Text(
                    text = getPhaseDescription(progress.phase),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Error count if any
                if (progress.failedPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.height(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${progress.failedPhotos.size} photo(s) failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cancel button
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Returns a human-readable description for the import phase.
 */
private fun getPhaseDescription(phase: ImportPhase): String = when (phase) {
    ImportPhase.IDLE -> "Preparing..."
    ImportPhase.COPYING -> "Copying file..."
    ImportPhase.DETECTING -> "Detecting face..."
    ImportPhase.ALIGNING -> "Aligning photo..."
    ImportPhase.SAVING -> "Saving frame..."
    ImportPhase.COMPLETE -> "Complete"
    ImportPhase.CANCELLED -> "Cancelled"
}
