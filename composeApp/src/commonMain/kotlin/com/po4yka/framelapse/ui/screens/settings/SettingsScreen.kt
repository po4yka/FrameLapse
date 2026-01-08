package com.po4yka.framelapse.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.MuscleRegion
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.presentation.settings.SettingsEffect
import com.po4yka.framelapse.presentation.settings.SettingsEvent
import com.po4yka.framelapse.presentation.settings.SettingsState
import com.po4yka.framelapse.presentation.settings.SettingsViewModel
import com.po4yka.framelapse.ui.components.FrameLapseTopBar
import com.po4yka.framelapse.ui.components.SettingsDropdown
import com.po4yka.framelapse.ui.components.SettingsSection
import com.po4yka.framelapse.ui.components.SettingsSlider
import com.po4yka.framelapse.ui.components.SettingsSwitch
import com.po4yka.framelapse.ui.util.HandleEffects
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.action_cancel
import framelapse.composeapp.generated.resources.action_ok
import framelapse.composeapp.generated.resources.app_name
import framelapse.composeapp.generated.resources.fps_label
import framelapse.composeapp.generated.resources.reminder_dialog_title
import framelapse.composeapp.generated.resources.reminder_hour
import framelapse.composeapp.generated.resources.reminder_minute
import framelapse.composeapp.generated.resources.reminder_selected_time
import framelapse.composeapp.generated.resources.settings_about_description
import framelapse.composeapp.generated.resources.settings_about_section
import framelapse.composeapp.generated.resources.settings_app_version
import framelapse.composeapp.generated.resources.settings_capture_section
import framelapse.composeapp.generated.resources.settings_clear_cache
import framelapse.composeapp.generated.resources.settings_content_type
import framelapse.composeapp.generated.resources.settings_frame_rate
import framelapse.composeapp.generated.resources.settings_muscle_region
import framelapse.composeapp.generated.resources.settings_orientation
import framelapse.composeapp.generated.resources.settings_reminder_description
import framelapse.composeapp.generated.resources.settings_reminder_enabled
import framelapse.composeapp.generated.resources.settings_reminder_section
import framelapse.composeapp.generated.resources.settings_reminder_time
import framelapse.composeapp.generated.resources.settings_resolution
import framelapse.composeapp.generated.resources.settings_storage_section
import framelapse.composeapp.generated.resources.settings_storage_used
import framelapse.composeapp.generated.resources.settings_title
import framelapse.composeapp.generated.resources.storage_size_gb
import framelapse.composeapp.generated.resources.storage_size_mb
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val CONTENT_PADDING = 16.dp
private const val MIN_FPS = 1f
private const val MAX_FPS = 60f
private const val FPS_STEPS = 59

/**
 * Settings screen for app configuration.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel<SettingsViewModel>(),
    notificationScheduler: NotificationScheduler = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is SettingsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            is SettingsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is SettingsEffect.ScheduleReminder -> {
                scope.launch {
                    val (hour, minute) = parseTime(state.reminderTime)
                    notificationScheduler.scheduleDaily(hour, minute)
                }
            }
            is SettingsEffect.CancelReminder -> {
                scope.launch {
                    notificationScheduler.cancel()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onEvent(SettingsEvent.LoadSettings)
    }

    SettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FrameLapseTopBar(
                title = stringResource(Res.string.settings_title),
                onBackClick = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            // Default capture settings
            SettingsSection(title = stringResource(Res.string.settings_capture_section)) {
                SettingsDropdown(
                    title = stringResource(Res.string.settings_resolution),
                    selectedValue = state.defaultResolution,
                    options = Resolution.entries,
                    onSelect = { onEvent(SettingsEvent.UpdateDefaultResolution(it)) },
                    valueLabel = { it.displayName },
                )

                SettingsSlider(
                    title = stringResource(Res.string.settings_frame_rate),
                    value = state.defaultFps.toFloat(),
                    onValueChange = { onEvent(SettingsEvent.UpdateDefaultFps(it.toInt())) },
                    valueRange = MIN_FPS..MAX_FPS,
                    steps = FPS_STEPS,
                    valueLabel = { stringResource(Res.string.fps_label, it.toInt()) },
                )

                SettingsDropdown(
                    title = stringResource(Res.string.settings_orientation),
                    selectedValue = state.defaultOrientation,
                    options = Orientation.entries,
                    onSelect = { onEvent(SettingsEvent.UpdateDefaultOrientation(it)) },
                    valueLabel = { it.displayName },
                )

                SettingsDropdown(
                    title = stringResource(Res.string.settings_content_type),
                    selectedValue = state.defaultContentType,
                    options = ContentType.entries,
                    onSelect = { onEvent(SettingsEvent.UpdateDefaultContentType(it)) },
                    valueLabel = { it.displayName },
                )

                // Show muscle region selector only when content type is MUSCLE
                if (state.defaultContentType == ContentType.MUSCLE) {
                    SettingsDropdown(
                        title = stringResource(Res.string.settings_muscle_region),
                        selectedValue = state.defaultMuscleRegion,
                        options = MuscleRegion.entries,
                        onSelect = { onEvent(SettingsEvent.UpdateDefaultMuscleRegion(it)) },
                        valueLabel = { it.displayName },
                    )
                }
            }

            HorizontalDivider()

            // Reminder settings
            SettingsSection(title = stringResource(Res.string.settings_reminder_section)) {
                SettingsSwitch(
                    title = stringResource(Res.string.settings_reminder_enabled),
                    subtitle = stringResource(Res.string.settings_reminder_description),
                    checked = state.reminderEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.UpdateReminderEnabled(it)) },
                )

                if (state.reminderEnabled) {
                    var showTimePicker by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                            .padding(horizontal = CONTENT_PADDING, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_reminder_time),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = state.reminderTime,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (showTimePicker) {
                        TimePickerDialog(
                            currentTime = state.reminderTime,
                            onTimeSelected = { time ->
                                onEvent(SettingsEvent.UpdateReminderTime(time))
                                showTimePicker = false
                            },
                            onDismiss = { showTimePicker = false },
                        )
                    }
                }
            }

            HorizontalDivider()

            // Storage settings
            SettingsSection(title = stringResource(Res.string.settings_storage_section)) {
                Text(
                    text = stringResource(
                        Res.string.settings_storage_used,
                        formatStorageSize(state.storageUsedMb),
                    ),
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING, vertical = 8.dp),
                )

                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.ClearCache) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CONTENT_PADDING),
                ) {
                    Text(stringResource(Res.string.settings_clear_cache))
                }
            }

            HorizontalDivider()

            // About section
            SettingsSection(title = stringResource(Res.string.settings_about_section)) {
                Text(
                    text = stringResource(
                        Res.string.settings_app_version,
                        stringResource(Res.string.app_name),
                        APP_VERSION_NAME,
                    ),
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING, vertical = 8.dp),
                )
                Text(
                    text = stringResource(Res.string.settings_about_description),
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun formatStorageSize(sizeInMb: Float): String = if (sizeInMb >= BYTES_THRESHOLD) {
    val gb = sizeInMb / BYTES_THRESHOLD
    stringResource(Res.string.storage_size_gb, (gb * 10).toInt() / 10.0)
} else {
    stringResource(Res.string.storage_size_mb, (sizeInMb * 10).toInt() / 10.0)
}

/**
 * Simple time picker dialog using hour/minute selection.
 */
@Composable
private fun TimePickerDialog(currentTime: String, onTimeSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val (initialHour, initialMinute) = parseTime(currentTime)
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder_dialog_title)) },
        text = {
            Column {
                // Hour selection
                SettingsSlider(
                    title = stringResource(Res.string.reminder_hour),
                    value = selectedHour.toFloat(),
                    onValueChange = { selectedHour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22,
                    valueLabel = { formatTwoDigits(it.toInt()) },
                )

                // Minute selection
                SettingsSlider(
                    title = stringResource(Res.string.reminder_minute),
                    value = selectedMinute.toFloat(),
                    onValueChange = { selectedMinute = it.toInt() },
                    valueRange = 0f..59f,
                    steps = 58,
                    valueLabel = { formatTwoDigits(it.toInt()) },
                )

                Text(
                    text = stringResource(
                        Res.string.reminder_selected_time,
                        formatTime(selectedHour, selectedMinute),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val formattedTime = formatTime(selectedHour, selectedMinute)
                    onTimeSelected(formattedTime)
                },
            ) {
                Text(stringResource(Res.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/**
 * Parse time string "HH:mm" into hour and minute.
 */
private fun parseTime(time: String): Pair<Int, Int> {
    val parts = time.split(":")
    return if (parts.size == 2) {
        val hour = parts[0].toIntOrNull() ?: DEFAULT_HOUR
        val minute = parts[1].toIntOrNull() ?: DEFAULT_MINUTE
        Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    } else {
        Pair(DEFAULT_HOUR, DEFAULT_MINUTE)
    }
}

/**
 * Format a number as two digits with leading zero if needed.
 */
private fun formatTwoDigits(value: Int): String = if (value < 10) "0$value" else value.toString()

/**
 * Format hour and minute as "HH:mm".
 */
private fun formatTime(hour: Int, minute: Int): String = "${formatTwoDigits(hour)}:${formatTwoDigits(minute)}"

private const val BYTES_THRESHOLD = 1024f
private const val DEFAULT_HOUR = 9
private const val DEFAULT_MINUTE = 0
private const val APP_VERSION_NAME = "1.0.0"
