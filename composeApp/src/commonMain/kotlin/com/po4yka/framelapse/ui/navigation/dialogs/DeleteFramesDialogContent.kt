package com.po4yka.framelapse.ui.navigation.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Content for delete frames confirmation dialog - rendered as Nav3 dialog scene.
 */
@Composable
fun DeleteFramesDialogContent(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Frames") },
        text = {
            Text(
                if (count == 1) {
                    "Are you sure you want to delete this frame? This action cannot be undone."
                } else {
                    "Are you sure you want to delete $count frames? This action cannot be undone."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
