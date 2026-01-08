package com.po4yka.framelapse.presentation.settings

import androidx.lifecycle.viewModelScope
import com.po4yka.framelapse.data.storage.StorageCleanupManager
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.repository.SettingsRepository
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 * Handles app preferences and storage management.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val storageCleanupManager: StorageCleanupManager,
) : BaseViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

    init {
        loadSettings()
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.LoadSettings -> loadSettings()
            is SettingsEvent.UpdateDefaultFps -> updateDefaultFps(event.fps)
            is SettingsEvent.UpdateDefaultResolution -> updateDefaultResolution(event.resolution)
            is SettingsEvent.UpdateDefaultOrientation -> updateDefaultOrientation(event.orientation)
            is SettingsEvent.UpdateReminderEnabled -> updateReminderEnabled(event.enabled)
            is SettingsEvent.UpdateReminderTime -> updateReminderTime(event.time)
            is SettingsEvent.ClearCache -> clearCache()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            val fpsResult = settingsRepository.getInt(KEY_DEFAULT_FPS, DEFAULT_FPS)
            val resolutionResult = settingsRepository.getString(KEY_DEFAULT_RESOLUTION)
            val orientationResult = settingsRepository.getString(KEY_DEFAULT_ORIENTATION)
            val reminderEnabledResult = settingsRepository.getBoolean(KEY_REMINDER_ENABLED, false)
            val reminderTimeResult = settingsRepository.getString(KEY_REMINDER_TIME)

            val fps = fpsResult.getOrNull() ?: DEFAULT_FPS
            val resolution = Resolution.fromString(resolutionResult.getOrNull() ?: Resolution.HD_1080P.name)
            val orientation = Orientation.fromString(orientationResult.getOrNull() ?: Orientation.PORTRAIT.name)
            val reminderEnabled = reminderEnabledResult.getOrNull() ?: false
            val reminderTime = reminderTimeResult.getOrNull() ?: DEFAULT_REMINDER_TIME

            val storageUsage = storageCleanupManager.getStorageUsage()

            updateState {
                copy(
                    defaultFps = fps,
                    defaultResolution = resolution,
                    defaultOrientation = orientation,
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    storageUsedBytes = storageUsage.totalBytes,
                    isLoading = false,
                )
            }
        }
    }

    private fun updateDefaultFps(fps: Int) {
        val clampedFps = fps.coerceIn(MIN_FPS, MAX_FPS)
        updateState { copy(defaultFps = clampedFps) }

        viewModelScope.launch {
            settingsRepository.setInt(KEY_DEFAULT_FPS, clampedFps)
        }
    }

    private fun updateDefaultResolution(resolution: Resolution) {
        updateState { copy(defaultResolution = resolution) }

        viewModelScope.launch {
            settingsRepository.setString(KEY_DEFAULT_RESOLUTION, resolution.name)
        }
    }

    private fun updateDefaultOrientation(orientation: Orientation) {
        updateState { copy(defaultOrientation = orientation) }

        viewModelScope.launch {
            settingsRepository.setString(KEY_DEFAULT_ORIENTATION, orientation.name)
        }
    }

    private fun updateReminderEnabled(enabled: Boolean) {
        updateState { copy(reminderEnabled = enabled) }

        viewModelScope.launch {
            settingsRepository.setBoolean(KEY_REMINDER_ENABLED, enabled)
        }

        if (enabled) {
            sendEffect(SettingsEffect.ScheduleReminder)
        } else {
            sendEffect(SettingsEffect.CancelReminder)
        }
    }

    private fun updateReminderTime(time: String) {
        updateState { copy(reminderTime = time) }

        viewModelScope.launch {
            settingsRepository.setString(KEY_REMINDER_TIME, time)
        }

        if (currentState.reminderEnabled) {
            sendEffect(SettingsEffect.ScheduleReminder)
        }
    }

    private fun clearCache() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            // Get updated storage usage after potential cleanup
            val storageUsage = storageCleanupManager.getStorageUsage()
            updateState {
                copy(
                    storageUsedBytes = storageUsage.totalBytes,
                    isLoading = false,
                )
            }
            sendEffect(SettingsEffect.ShowMessage("Cache cleared"))
        }
    }

    companion object {
        private const val KEY_DEFAULT_FPS = "settings_default_fps"
        private const val KEY_DEFAULT_RESOLUTION = "settings_default_resolution"
        private const val KEY_DEFAULT_ORIENTATION = "settings_default_orientation"
        private const val KEY_REMINDER_ENABLED = "settings_reminder_enabled"
        private const val KEY_REMINDER_TIME = "settings_reminder_time"

        private const val DEFAULT_FPS = 30
        private const val DEFAULT_REMINDER_TIME = "09:00"
        private const val MIN_FPS = 1
        private const val MAX_FPS = 60
    }
}
