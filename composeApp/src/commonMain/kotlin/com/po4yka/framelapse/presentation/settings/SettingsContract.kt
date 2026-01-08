package com.po4yka.framelapse.presentation.settings

import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.MuscleRegion
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState

/**
 * UI state for the settings screen.
 */
data class SettingsState(
    val defaultFps: Int = DEFAULT_FPS,
    val defaultResolution: Resolution = Resolution.HD_1080P,
    val defaultOrientation: Orientation = Orientation.PORTRAIT,
    val defaultContentType: ContentType = ContentType.FACE,
    val defaultMuscleRegion: MuscleRegion = MuscleRegion.FULL_BODY,
    val reminderEnabled: Boolean = false,
    val reminderTime: String = DEFAULT_REMINDER_TIME,
    val storageUsedBytes: Long = 0,
    val isLoading: Boolean = false,
) : UiState {
    val storageUsedMb: Float get() = storageUsedBytes / BYTES_PER_MB

    companion object {
        private const val DEFAULT_FPS = 30
        private const val DEFAULT_REMINDER_TIME = "09:00"
        private const val BYTES_PER_MB = 1024f * 1024f
    }
}

/**
 * User events for the settings screen.
 */
sealed interface SettingsEvent : UiEvent {
    /**
     * Load current settings.
     */
    data object LoadSettings : SettingsEvent

    /**
     * Update default FPS for new projects.
     */
    data class UpdateDefaultFps(val fps: Int) : SettingsEvent

    /**
     * Update default resolution for new projects.
     */
    data class UpdateDefaultResolution(val resolution: Resolution) : SettingsEvent

    /**
     * Update default orientation for new projects.
     */
    data class UpdateDefaultOrientation(val orientation: Orientation) : SettingsEvent

    /**
     * Update default content type for new projects.
     */
    data class UpdateDefaultContentType(val contentType: ContentType) : SettingsEvent

    /**
     * Update default muscle region for new MUSCLE projects.
     */
    data class UpdateDefaultMuscleRegion(val region: MuscleRegion) : SettingsEvent

    /**
     * Toggle reminder notifications.
     */
    data class UpdateReminderEnabled(val enabled: Boolean) : SettingsEvent

    /**
     * Update reminder time.
     */
    data class UpdateReminderTime(val time: String) : SettingsEvent

    /**
     * Clear cached data.
     */
    data object ClearCache : SettingsEvent
}

/**
 * One-time side effects for the settings screen.
 */
sealed interface SettingsEffect : UiEffect {
    /**
     * Show a success message.
     */
    data class ShowMessage(val message: String) : SettingsEffect

    /**
     * Show an error message.
     */
    data class ShowError(val message: String) : SettingsEffect

    /**
     * Schedule daily reminder notification.
     */
    data object ScheduleReminder : SettingsEffect

    /**
     * Cancel scheduled reminder notification.
     */
    data object CancelReminder : SettingsEffect
}
