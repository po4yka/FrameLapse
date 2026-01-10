package com.po4yka.framelapse.presentation.export

import com.po4yka.framelapse.domain.entity.DateRange
import com.po4yka.framelapse.domain.entity.ExportQuality
import com.po4yka.framelapse.domain.entity.ExportSettings
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.entity.VideoCodec
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState
import com.po4yka.framelapse.presentation.common.CommonEffect

/**
 * UI state for the export screen.
 */
data class ExportState(
    val projectId: String = "",
    val project: Project? = null,
    val frameCount: Int = 0,
    val exportSettings: ExportSettings = ExportSettings(),
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val exportedVideoPath: String? = null,
    val error: String? = null,
) : UiState {
    val canExport: Boolean get() = frameCount > 0 && !isExporting
    val estimatedDuration: Float get() = if (exportSettings.fps > 0) frameCount.toFloat() / exportSettings.fps else 0f
}

/**
 * User events for the export screen.
 */
sealed interface ExportEvent : UiEvent {
    /**
     * Initialize the export screen for a project.
     */
    data class Initialize(val projectId: String) : ExportEvent

    /**
     * Update the export resolution.
     */
    data class UpdateResolution(val resolution: Resolution) : ExportEvent

    /**
     * Update the export FPS.
     */
    data class UpdateFps(val fps: Int) : ExportEvent

    /**
     * Update the video codec.
     */
    data class UpdateCodec(val codec: VideoCodec) : ExportEvent

    /**
     * Update the export quality.
     */
    data class UpdateQuality(val quality: ExportQuality) : ExportEvent

    /**
     * Update the date range filter.
     */
    data class UpdateDateRange(val dateRange: DateRange?) : ExportEvent

    /**
     * Start the video export.
     */
    data object StartExport : ExportEvent

    /**
     * Cancel the ongoing export.
     */
    data object CancelExport : ExportEvent

    /**
     * Share the exported video.
     */
    data object ShareVideo : ExportEvent

    /**
     * Dismiss the export result and reset.
     */
    data object DismissResult : ExportEvent
}

/**
 * One-time side effects for the export screen.
 */
sealed interface ExportEffect : UiEffect {
    /**
     * Show an error message. Delegates to [CommonEffect.ShowError].
     */
    data class ShowError(val message: String) : ExportEffect {
        /** Convert to common effect for unified handling. */
        fun toCommon(): CommonEffect.ShowError = CommonEffect.ShowError(message)
    }

    /**
     * Share the exported video file.
     */
    data class ShareVideo(val path: String) : ExportEffect

    /**
     * Open the exported video file.
     */
    data class OpenVideo(val path: String) : ExportEffect

    /**
     * Export completed successfully.
     */
    data object ExportComplete : ExportEffect

    /**
     * Show a success/informational message. Delegates to [CommonEffect.ShowSnackbar].
     */
    data class ShowMessage(val message: String) : ExportEffect {
        /** Convert to common effect for unified handling. */
        fun toCommon(): CommonEffect.ShowSnackbar = CommonEffect.ShowSnackbar(message)
    }
}
