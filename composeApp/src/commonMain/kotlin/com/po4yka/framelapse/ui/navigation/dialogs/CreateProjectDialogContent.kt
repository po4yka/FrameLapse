package com.po4yka.framelapse.ui.navigation.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import framelapse.ui.generated.resources.Res
import framelapse.ui.generated.resources.action_cancel
import framelapse.ui.generated.resources.create_project_name_hint
import framelapse.ui.generated.resources.create_project_title
import framelapse.ui.generated.resources.projects_create
import org.jetbrains.compose.resources.stringResource

/**
 * Content for create project dialog - rendered as Nav3 dialog scene.
 */
@Composable
fun CreateProjectDialogContent(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var projectName by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.create_project_title)) },
        text = {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                placeholder = { Text(stringResource(Res.string.create_project_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(projectName) },
                enabled = projectName.isNotBlank(),
            ) {
                Text(stringResource(Res.string.projects_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
