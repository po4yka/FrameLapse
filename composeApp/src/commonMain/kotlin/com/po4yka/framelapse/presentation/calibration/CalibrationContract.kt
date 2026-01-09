package com.po4yka.framelapse.presentation.calibration

import androidx.compose.ui.geometry.Offset
import com.po4yka.framelapse.domain.entity.AdjustmentPointType
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState

/**
 * Calibration flow phases.
 */
enum class CalibrationPhase {
    /** Initial phase where user captures a reference photo. */
    CAPTURE,

    /** Adjustment phase where user can drag eye markers and adjust offsets. */
    ADJUST,
}

/**
 * UI state for the calibration screen.
 */
data class CalibrationState(
    /** The project ID being calibrated. */
    val projectId: String = "",

    /** Current phase of the calibration flow. */
    val phase: CalibrationPhase = CalibrationPhase.CAPTURE,

    /** Path to the captured reference image (set after capture). */
    val referenceImagePath: String? = null,

    /** Face landmarks detected in the reference image. */
    val detectedLandmarks: FaceLandmarks? = null,

    /** Adjusted left eye position (normalized 0-1). */
    val adjustedLeftEye: LandmarkPoint? = null,

    /** Adjusted right eye position (normalized 0-1). */
    val adjustedRightEye: LandmarkPoint? = null,

    /** Horizontal offset adjustment (-0.2 to +0.2). */
    val offsetX: Float = 0f,

    /** Vertical offset adjustment (-0.2 to +0.2). */
    val offsetY: Float = 0f,

    /** Whether the camera is ready for capture. */
    val isCameraReady: Boolean = false,

    /** Whether an operation is in progress. */
    val isProcessing: Boolean = false,

    /** Currently active drag point (null if not dragging). */
    val activeDragPoint: AdjustmentPointType? = null,

    /** Whether the project has existing calibration data. */
    val hasExistingCalibration: Boolean = false,

    /** Error message to display (null if no error). */
    val error: String? = null,
) : UiState

/**
 * User events for the calibration screen.
 */
sealed interface CalibrationEvent : UiEvent {
    /**
     * Initialize the calibration screen for a project.
     * Loads existing calibration data if present.
     */
    data class Initialize(val projectId: String) : CalibrationEvent

    /**
     * Camera is ready for capture.
     */
    data object CameraReady : CalibrationEvent

    /**
     * Camera encountered an error.
     */
    data object CameraError : CalibrationEvent

    /**
     * Capture a reference photo for calibration.
     */
    data object CaptureReference : CalibrationEvent

    /**
     * Start dragging an eye marker.
     */
    data class StartDrag(val pointType: AdjustmentPointType) : CalibrationEvent

    /**
     * Update eye marker position during drag.
     * @param delta Normalized delta offset (0-1 scale).
     */
    data class UpdateDrag(val delta: Offset) : CalibrationEvent

    /**
     * End dragging an eye marker.
     */
    data object EndDrag : CalibrationEvent

    /**
     * Update the alignment offset values.
     */
    data class UpdateOffset(val offsetX: Float, val offsetY: Float) : CalibrationEvent

    /**
     * Save calibration data to the project.
     */
    data object SaveCalibration : CalibrationEvent

    /**
     * Retake the reference photo.
     */
    data object RetakeReference : CalibrationEvent

    /**
     * Clear existing calibration data.
     */
    data object ClearCalibration : CalibrationEvent

    /**
     * Cancel calibration and navigate back.
     */
    data object Cancel : CalibrationEvent

    /**
     * Dismiss error message.
     */
    data object DismissError : CalibrationEvent
}

/**
 * One-time side effects for the calibration screen.
 */
sealed interface CalibrationEffect : UiEffect {
    /**
     * Navigate back to the previous screen.
     */
    data object NavigateBack : CalibrationEffect

    /**
     * Trigger camera capture.
     */
    data object TriggerCapture : CalibrationEffect

    /**
     * Show an error message.
     */
    data class ShowError(val message: String) : CalibrationEffect

    /**
     * Play capture sound/haptic feedback.
     */
    data object PlayCaptureSound : CalibrationEffect

    /**
     * Show success message after saving calibration.
     */
    data class ShowSuccess(val message: String) : CalibrationEffect
}
