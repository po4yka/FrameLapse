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
import kotlinx.coroutines.launch
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
                title = "Settings",
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
            SettingsSection(title = "Default Capture Settings") {
                SettingsDropdown(
                    title = "Resolution",
                    selectedValue = state.defaultResolution,
                    options = Resolution.entries,
                    onSelect = { onEvent(SettingsEvent.UpdateDefaultResolution(it)) },
                    valueLabel = { it.displayName },
                )

                SettingsSlider(
                    title = "Frame Rate",
                    value = state.defaultFps.toFloat(),
                    onValueChange = { onEvent(SettingsEvent.UpdateDefaultFps(it.toInt())) },
                    valueRange = MIN_FPS..MAX_FPS,
                    steps = FPS_STEPS,
                    valueLabel = { "${it.toInt()} FPS" },
                )

                SettingsDropdown(
                    title = "Orientation",
                    selectedValue = state.defaultOrientation,
                    options = Orientation.entries,
                    onSelect = { onEvent(SettingsEvent.UpdateDefaultOrientation(it)) },
                    valueLabel = { it.displayName },
                )
            }

            HorizontalDivider()

            // Reminder settings
            SettingsSection(title = "Daily Reminder") {
                SettingsSwitch(
                    title = "Enable Reminder",
                    subtitle = "Get notified daily to capture your photo",
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
                            text = "Reminder time",
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
            SettingsSection(title = "Storage") {
                Text(
                    text = "Used: ${formatStorageSize(state.storageUsedMb)} MB",
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING, vertical = 8.dp),
                )

                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.ClearCache) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CONTENT_PADDING),
                ) {
                    Text("Clear Cache")
                }
            }

            HorizontalDivider()

            // About section
            SettingsSection(title = "About") {
                Text(
                    text = "FrameLapse v1.0.0",
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING, vertical = 8.dp),
                )
                Text(
                    text = "Create beautiful face timelapse videos",
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING, vertical = 4.dp),
                )
            }
        }
    }
}

private fun formatStorageSize(sizeInMb: Float): String = if (sizeInMb >= BYTES_THRESHOLD) {
    val gb = sizeInMb / BYTES_THRESHOLD
    "${(gb * 10).toInt() / 10.0} GB"
} else {
    "${(sizeInMb * 10).toInt() / 10.0} MB"
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
        title = { Text("Set Reminder Time") },
        text = {
            Column {
                // Hour selection
                SettingsSlider(
                    title = "Hour",
                    value = selectedHour.toFloat(),
                    onValueChange = { selectedHour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22,
                    valueLabel = { formatTwoDigits(it.toInt()) },
                )

                // Minute selection
                SettingsSlider(
                    title = "Minute",
                    value = selectedMinute.toFloat(),
                    onValueChange = { selectedMinute = it.toInt() },
                    valueRange = 0f..59f,
                    steps = 58,
                    valueLabel = { formatTwoDigits(it.toInt()) },
                )

                Text(
                    text = "Selected: ${formatTime(selectedHour, selectedMinute)}",
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
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
