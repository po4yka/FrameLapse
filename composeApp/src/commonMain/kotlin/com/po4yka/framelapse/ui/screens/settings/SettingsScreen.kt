package com.po4yka.framelapse.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Resolution
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
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is SettingsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            is SettingsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is SettingsEffect.ScheduleReminder -> { /* TODO: Schedule notification */ }
            is SettingsEffect.CancelReminder -> { /* TODO: Cancel notification */ }
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
                    // TODO: Add time picker
                    Text(
                        text = "Reminder time: ${state.reminderTime}",
                        modifier = Modifier.padding(horizontal = CONTENT_PADDING),
                    )
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
    String.format("%.1f", sizeInMb / BYTES_THRESHOLD) + " GB"
} else {
    String.format("%.1f", sizeInMb)
}

private const val BYTES_THRESHOLD = 1024f
