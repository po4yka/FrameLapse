package com.po4yka.framelapse.ui.navigation.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.action_cancel
import framelapse.composeapp.generated.resources.action_delete
import framelapse.composeapp.generated.resources.delete_frames_message_plural
import framelapse.composeapp.generated.resources.delete_frames_message_single
import framelapse.composeapp.generated.resources.delete_frames_title
import org.jetbrains.compose.resources.stringResource

/**
 * Content for delete frames confirmation dialog - rendered as Nav3 dialog scene.
 */
@Composable
fun DeleteFramesDialogContent(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_frames_title)) },
        text = {
            Text(
                if (count == 1) {
                    stringResource(Res.string.delete_frames_message_single)
                } else {
                    stringResource(Res.string.delete_frames_message_plural, count)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
