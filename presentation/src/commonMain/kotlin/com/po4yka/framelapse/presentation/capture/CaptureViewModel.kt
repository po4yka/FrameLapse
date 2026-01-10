package com.po4yka.framelapse.presentation.capture

import androidx.lifecycle.viewModelScope
import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.repository.SettingsRepository
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.usecase.capture.CaptureImageUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetFramesUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetLatestFrameUseCase
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * ViewModel for the capture screen.
 * Handles camera state, capture flow, and ghost image overlay.
 */
@Factory
class CaptureViewModel(
    private val captureImageUseCase: CaptureImageUseCase,
    private val getLatestFrameUseCase: GetLatestFrameUseCase,
    private val getFramesUseCase: GetFramesUseCase,
    private val settingsRepository: SettingsRepository,
) : BaseViewModel<CaptureState, CaptureEvent, CaptureEffect>(CaptureState()) {

    /**
     * Camera controller reference set via [CaptureEvent.SetCameraController].
     * Must be set before capture operations can be performed.
     */
    private var cameraController: CameraController? = null

    override fun onEvent(event: CaptureEvent) {
        when (event) {
            is CaptureEvent.Initialize -> initialize(event.projectId)
            is CaptureEvent.CaptureImage -> captureImage()
            is CaptureEvent.ToggleFlash -> toggleFlash()
            is CaptureEvent.FlipCamera -> flipCamera()
            is CaptureEvent.ToggleGrid -> toggleGrid()
            is CaptureEvent.UpdateGhostOpacity -> updateGhostOpacity(event.opacity)
            is CaptureEvent.NavigateToGallery -> navigateToGallery()
            is CaptureEvent.CameraReady -> cameraReady()
            is CaptureEvent.CameraError -> cameraError()
            is CaptureEvent.SetCameraController -> setCameraController(event.controller)
        }
    }

    private fun setCameraController(controller: CameraController) {
        cameraController = controller
    }

    private fun initialize(projectId: String) {
        updateState { copy(projectId = projectId) }
        loadCaptureSettings()
        loadLatestFrame(projectId)
        loadFrameCount(projectId)
    }

    private fun loadCaptureSettings() {
        viewModelScope.launch {
            val showGridResult = settingsRepository.getBoolean(SETTINGS_KEY_SHOW_GRID, true)
            val ghostOpacityResult = settingsRepository.getFloat(SETTINGS_KEY_GHOST_OPACITY, DEFAULT_GHOST_OPACITY)

            val showGrid = showGridResult.getOrNull() ?: true
            val ghostOpacity = ghostOpacityResult.getOrNull() ?: DEFAULT_GHOST_OPACITY

            updateState {
                copy(
                    captureSettings = captureSettings.copy(
                        showGrid = showGrid,
                        ghostOpacity = ghostOpacity,
                    ),
                )
            }
        }
    }

    private fun loadLatestFrame(projectId: String) {
        viewModelScope.launch {
            getLatestFrameUseCase(projectId)
                .onSuccess { frame ->
                    val ghostPath = frame?.alignedPath ?: frame?.originalPath
                    updateState {
                        copy(
                            lastCapturedFrame = frame,
                            ghostImagePath = ghostPath,
                        )
                    }
                }
                .onError { _, message ->
                    sendEffect(CaptureEffect.ShowError(message ?: "Failed to load latest frame"))
                }
        }
    }

    private fun loadFrameCount(projectId: String) {
        viewModelScope.launch {
            getFramesUseCase(projectId)
                .onSuccess { frames ->
                    updateState { copy(frameCount = frames.size) }
                }
        }
    }

    private fun captureImage() {
        if (currentState.isProcessing || !currentState.isCameraReady) {
            return
        }

        val controller = cameraController ?: run {
            sendEffect(CaptureEffect.ShowError("Camera not ready"))
            return
        }

        updateState { copy(isProcessing = true, error = null) }

        viewModelScope.launch {
            captureImageUseCase(currentState.projectId, controller, alignFace = true)
                .onSuccess { frame ->
                    sendEffect(CaptureEffect.PlayCaptureSound)
                    val ghostPath = frame.alignedPath ?: frame.originalPath
                    updateState {
                        copy(
                            isProcessing = false,
                            lastCapturedFrame = frame,
                            ghostImagePath = ghostPath,
                            faceDetectionConfidence = frame.confidence,
                            frameCount = frameCount + 1,
                        )
                    }
                }
                .onError { _, message ->
                    updateState { copy(isProcessing = false) }
                    sendEffect(CaptureEffect.ShowError(message ?: "Failed to capture image"))
                }
        }
    }

    private fun toggleFlash() {
        val newFlashMode = when (currentState.captureSettings.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        cameraController?.setFlashMode(newFlashMode)
        updateState { copy(captureSettings = captureSettings.copy(flashMode = newFlashMode)) }
    }

    private fun flipCamera() {
        cameraController?.switchCamera()
        val newFacing = when (currentState.captureSettings.cameraFacing) {
            CameraFacing.FRONT -> CameraFacing.BACK
            CameraFacing.BACK -> CameraFacing.FRONT
        }
        updateState { copy(captureSettings = captureSettings.copy(cameraFacing = newFacing)) }
    }

    private fun toggleGrid() {
        val newShowGrid = !currentState.captureSettings.showGrid
        updateState { copy(captureSettings = captureSettings.copy(showGrid = newShowGrid)) }

        viewModelScope.launch {
            settingsRepository.setBoolean(SETTINGS_KEY_SHOW_GRID, newShowGrid)
        }
    }

    private fun updateGhostOpacity(opacity: Float) {
        val clampedOpacity = opacity.coerceIn(0f, 1f)
        updateState { copy(captureSettings = captureSettings.copy(ghostOpacity = clampedOpacity)) }

        viewModelScope.launch {
            settingsRepository.setFloat(SETTINGS_KEY_GHOST_OPACITY, clampedOpacity)
        }
    }

    private fun navigateToGallery() {
        sendEffect(CaptureEffect.NavigateToGallery(currentState.projectId))
    }

    private fun cameraReady() {
        updateState { copy(isCameraReady = true, error = null) }
    }

    private fun cameraError() {
        updateState { copy(isCameraReady = false, error = "Camera initialization failed") }
        sendEffect(CaptureEffect.ShowError("Camera initialization failed"))
    }

    companion object {
        private const val SETTINGS_KEY_SHOW_GRID = "capture_show_grid"
        private const val SETTINGS_KEY_GHOST_OPACITY = "capture_ghost_opacity"
        private const val DEFAULT_GHOST_OPACITY = 0.3f
    }
}
