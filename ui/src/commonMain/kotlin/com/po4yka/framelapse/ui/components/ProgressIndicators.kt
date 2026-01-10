package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import framelapse.ui.generated.resources.Res
import framelapse.ui.generated.resources.action_cancel
import framelapse.ui.generated.resources.export_complete_description
import framelapse.ui.generated.resources.export_complete_done
import framelapse.ui.generated.resources.export_complete_share
import framelapse.ui.generated.resources.export_complete_title
import framelapse.ui.generated.resources.export_progress_title
import framelapse.ui.generated.resources.percentage_value
import org.jetbrains.compose.resources.stringResource

private val CARD_PADDING = 24.dp
private val ICON_SIZE = 64.dp
private val SPACING = 16.dp
private const val PROGRESS_MAX = 100

/**
 * Export progress card with cancel button.
 */
@Composable
fun ExportProgressCard(progress: Int, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.export_progress_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(SPACING))

            LinearProgressIndicator(
                progress = { progress / PROGRESS_MAX.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(SPACING / 2))

            Text(
                text = stringResource(Res.string.percentage_value, progress),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SPACING))

            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    }
}

/**
 * Export complete card with share button.
 */
@Composable
fun ExportCompleteCard(onShare: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SPACING))

            Text(
                text = stringResource(Res.string.export_complete_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(SPACING / 2))

            Text(
                text = stringResource(Res.string.export_complete_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(SPACING))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.export_complete_done))
                }

                Spacer(modifier = Modifier.width(SPACING))

                Button(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.export_complete_share))
                }
            }
        }
    }
}
