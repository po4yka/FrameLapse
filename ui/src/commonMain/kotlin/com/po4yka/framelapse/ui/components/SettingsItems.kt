package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import framelapse.ui.generated.resources.Res
import framelapse.ui.generated.resources.settings_slider_content_description
import framelapse.ui.generated.resources.settings_switch_content_description
import framelapse.ui.generated.resources.state_disabled
import framelapse.ui.generated.resources.state_enabled
import org.jetbrains.compose.resources.stringResource

private val SECTION_PADDING = 16.dp
private val MIN_TOUCH_TARGET_HEIGHT = 48.dp
private val ITEM_PADDING_HORIZONTAL = 16.dp
private val ITEM_PADDING_VERTICAL = 12.dp

/**
 * Settings section with header.
 */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = SECTION_PADDING,
                vertical = ITEM_PADDING_VERTICAL,
            ),
        )
        content()
    }
}

/**
 * Switch setting item.
 */
@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val switchState = if (checked) {
        stringResource(Res.string.state_enabled)
    } else {
        stringResource(Res.string.state_disabled)
    }
    val switchContentDescription = stringResource(
        Res.string.settings_switch_content_description,
        title,
        switchState,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET_HEIGHT)
            .clickable { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                contentDescription = switchContentDescription
            }
            .padding(
                horizontal = ITEM_PADDING_HORIZONTAL,
                vertical = ITEM_PADDING_VERTICAL,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(ITEM_PADDING_HORIZONTAL))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Dropdown selection setting item.
 */
@Composable
fun <T> SettingsDropdown(
    title: String,
    selectedValue: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownContentDescription = stringResource(
        Res.string.settings_slider_content_description,
        title,
        valueLabel(selectedValue),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET_HEIGHT)
            .clickable { expanded = true }
            .semantics {
                role = Role.DropdownList
                contentDescription = dropdownContentDescription
            }
            .padding(
                horizontal = ITEM_PADDING_HORIZONTAL,
                vertical = ITEM_PADDING_VERTICAL,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(ITEM_PADDING_HORIZONTAL))
        Column {
            Text(
                text = valueLabel(selectedValue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(valueLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Slider setting item.
 */
@Composable
fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: @Composable ((Float) -> String)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = ITEM_PADDING_HORIZONTAL,
                vertical = ITEM_PADDING_VERTICAL,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (valueLabel != null) {
                Text(
                    text = valueLabel(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
