package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.action_cancel
import framelapse.composeapp.generated.resources.action_confirm
import framelapse.composeapp.generated.resources.projects_create
import org.jetbrains.compose.resources.stringResource

/**
 * Base dialog template providing consistent styling for card-based dialogs.
 *
 * This template extracts common patterns from ImportResultDialog and ImportProgressDialog,
 * providing a reusable structure with:
 * - Consistent card styling (rounded corners, elevation, colors)
 * - Optional icon slot at the top
 * - Title text
 * - Content slot for main dialog content
 * - Actions slot for buttons
 *
 * @param title The dialog title text
 * @param onDismissRequest Called when the dialog should be dismissed (back press, outside click)
 * @param modifier Modifier for the dialog card
 * @param dismissOnBackPress Whether back press dismisses the dialog (default true)
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog (default true)
 * @param icon Optional composable slot for an icon displayed above the title
 * @param content Main content of the dialog
 * @param actions Action buttons displayed at the bottom of the dialog
 */
@Composable
fun DialogTemplate(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Icon slot
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content slot
                content()

                Spacer(modifier = Modifier.height(24.dp))

                // Actions slot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    actions()
                }
            }
        }
    }
}

/**
 * Simplified dialog template with left-aligned content (for progress dialogs).
 *
 * Similar to [DialogTemplate] but with left-aligned content, suitable for
 * dialogs that show progress or lists of information.
 *
 * @param title The dialog title text
 * @param onDismissRequest Called when the dialog should be dismissed
 * @param modifier Modifier for the dialog card
 * @param dismissOnBackPress Whether back press dismisses the dialog (default true)
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog (default true)
 * @param content Main content of the dialog
 * @param actions Action buttons displayed at the bottom of the dialog
 */
@Composable
fun DialogTemplateLeftAligned(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
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
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content slot
                content()

                Spacer(modifier = Modifier.height(16.dp))

                // Actions slot
                actions()
            }
        }
    }
}

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
