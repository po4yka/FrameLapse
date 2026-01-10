package com.po4yka.framelapse.presentation.capture

import com.po4yka.framelapse.domain.entity.CaptureSettings
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState
import com.po4yka.framelapse.presentation.common.CommonEffect

/**
 * UI state for the capture screen.
 */
data class CaptureState(
    val projectId: String = "",
    val isProcessing: Boolean = false,
    val isCameraReady: Boolean = false,
    val captureSettings: CaptureSettings = CaptureSettings(),
    val ghostImagePath: String? = null,
    val lastCapturedFrame: Frame? = null,
    val faceDetectionConfidence: Float? = null,
    val currentFaceLandmarks: FaceLandmarks? = null,
    val referenceLandmarks: FaceLandmarks? = null,
    val frameCount: Int = 0,
    val error: String? = null,
) : UiState

/**
 * User events for the capture screen.
 */
sealed interface CaptureEvent : UiEvent {
    /**
     * Initialize the capture screen for a project.
     */
    data class Initialize(val projectId: String) : CaptureEvent

    /**
     * Capture a new image.
     */
    data object CaptureImage : CaptureEvent

    /**
     * Toggle flash mode.
     */
    data object ToggleFlash : CaptureEvent

    /**
     * Flip between front and back camera.
     */
    data object FlipCamera : CaptureEvent

    /**
     * Toggle grid overlay visibility.
     */
    data object ToggleGrid : CaptureEvent

    /**
     * Update ghost image opacity.
     */
    data class UpdateGhostOpacity(val opacity: Float) : CaptureEvent

    /**
     * Navigate to the gallery screen.
     */
    data object NavigateToGallery : CaptureEvent

    /**
     * Camera is ready for capture.
     */
    data object CameraReady : CaptureEvent

    /**
     * Camera encountered an error.
     */
    data object CameraError : CaptureEvent

    /**
     * Set the camera controller instance.
     * Called from the UI layer when the camera is initialized.
     */
    data class SetCameraController(val controller: CameraController) : CaptureEvent
}

/**
 * One-time side effects for the capture screen.
 */
sealed interface CaptureEffect : UiEffect {
    /**
     * Trigger camera capture.
     */
    data object TriggerCapture : CaptureEffect

    /**
     * Show an error message. Delegates to [CommonEffect.ShowError].
     */
    data class ShowError(val message: String) : CaptureEffect {
        /** Convert to common effect for unified handling. */
        fun toCommon(): CommonEffect.ShowError = CommonEffect.ShowError(message)
    }

    /**
     * Navigate to gallery screen.
     */
    data class NavigateToGallery(val projectId: String) : CaptureEffect

    /**
     * Play capture sound/haptic feedback.
     */
    data object PlayCaptureSound : CaptureEffect
}
