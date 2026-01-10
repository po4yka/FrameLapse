package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog showing the result of a photo import operation.
 *
 * Displays:
 * - Success/Warning icon based on failure count
 * - "Import Complete" or "Import Completed with Errors" title
 * - Success count text
 * - Failed count text (if any)
 * - Thumbnail row of imported photos
 * - Done / Retry Failed buttons
 *
 * @param successCount Number of successfully imported photos
 * @param failedCount Number of photos that failed to import
 * @param thumbnailPaths Paths to thumbnails of imported photos
 * @param onDismiss Called when user dismisses the dialog
 * @param onRetry Called when user wants to retry failed imports
 */
@Composable
fun ImportResultDialog(
    successCount: Int,
    failedCount: Int,
    thumbnailPaths: List<String>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasFailures = failedCount > 0
    val isFullSuccess = successCount > 0 && !hasFailures

    Dialog(onDismissRequest = onDismiss) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Icon
                ResultIcon(isFullSuccess = isFullSuccess)

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = if (hasFailures) "Import Completed with Errors" else "Import Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Success count
                if (successCount > 0) {
                    Text(
                        text = "$successCount photo${if (successCount != 1) "s" else ""} imported successfully",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Failed count
                if (hasFailures) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$failedCount photo${if (failedCount != 1) "s" else ""} failed to import",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Thumbnail row (if any imported successfully)
                if (thumbnailPaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ThumbnailRow(thumbnailPaths = thumbnailPaths)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (hasFailures) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Retry Failed")
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = if (hasFailures) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

/**
 * Icon showing success or warning state.
 */
@Composable
private fun ResultIcon(isFullSuccess: Boolean, modifier: Modifier = Modifier) {
    val backgroundColor = if (isFullSuccess) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    val iconTint = if (isFullSuccess) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFullSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (isFullSuccess) "Success" else "Warning",
            tint = iconTint,
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * Horizontal row of imported photo thumbnails.
 */
@Composable
private fun ThumbnailRow(thumbnailPaths: List<String>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(thumbnailPaths) { path ->
            ImportPreviewThumbnail(
                imagePath = path,
                contentDescription = "Imported photo",
                size = 56.dp,
            )
        }
    }
}
