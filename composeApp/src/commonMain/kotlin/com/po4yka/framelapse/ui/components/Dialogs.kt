package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.action_cancel
import framelapse.composeapp.generated.resources.action_confirm
import framelapse.composeapp.generated.resources.projects_create
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmation dialog with confirm and cancel buttons.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
) {
    val confirmText = confirmLabel ?: stringResource(Res.string.action_confirm)
    val dismissText = dismissLabel ?: stringResource(Res.string.action_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}

/**
 * Dialog with a text input field.
 */
@Composable
fun TextInputDialog(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
) {
    val confirmText = confirmLabel ?: stringResource(Res.string.projects_create)
    val dismissText = dismissLabel ?: stringResource(Res.string.action_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.isNotBlank(),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}
