package com.po4yka.framelapse.ui.navigation.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.action_cancel
import framelapse.composeapp.generated.resources.action_delete
import framelapse.composeapp.generated.resources.delete_project_message
import framelapse.composeapp.generated.resources.delete_project_title
import org.jetbrains.compose.resources.stringResource

/**
 * Content for delete project confirmation dialog - rendered as Nav3 dialog scene.
 */
@Composable
fun DeleteProjectDialogContent(projectName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_project_title)) },
        text = {
            Text(
                stringResource(Res.string.delete_project_message, projectName),
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
