package com.po4yka.framelapse.presentation.calibration

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.viewModelScope
import com.po4yka.framelapse.domain.entity.AdjustmentPointType
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.usecase.calibration.CaptureCalibrationImageUseCase
import com.po4yka.framelapse.domain.usecase.calibration.GetCalibrationUseCase
import com.po4yka.framelapse.domain.usecase.calibration.SaveCalibrationUseCase
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * ViewModel for the calibration screen.
 * Handles reference photo capture, eye marker adjustment, and calibration persistence.
 */
@Factory
class CalibrationViewModel(
    private val captureCalibrationImageUseCase: CaptureCalibrationImageUseCase,
    private val saveCalibrationUseCase: SaveCalibrationUseCase,
    private val getCalibrationUseCase: GetCalibrationUseCase,
    private val projectRepository: ProjectRepository,
) : BaseViewModel<CalibrationState, CalibrationEvent, CalibrationEffect>(CalibrationState()) {

    /**
     * Camera controller reference set via [CalibrationEvent.SetCameraController].
     * Must be set before capture operations can be performed.
     */
    private var cameraController: CameraController? = null

    override fun onEvent(event: CalibrationEvent) {
        when (event) {
            is CalibrationEvent.Initialize -> initialize(event.projectId)
            is CalibrationEvent.CameraReady -> cameraReady()
            is CalibrationEvent.CameraError -> cameraError()
            is CalibrationEvent.CaptureReference -> captureReference()
            is CalibrationEvent.StartDrag -> startDrag(event.pointType)
            is CalibrationEvent.UpdateDrag -> updateDrag(event.delta)
            is CalibrationEvent.EndDrag -> endDrag()
            is CalibrationEvent.UpdateOffset -> updateOffset(event.offsetX, event.offsetY)
            is CalibrationEvent.SaveCalibration -> saveCalibration()
            is CalibrationEvent.RetakeReference -> retakeReference()
            is CalibrationEvent.ClearCalibration -> clearCalibration()
            is CalibrationEvent.Cancel -> cancel()
            is CalibrationEvent.DismissError -> dismissError()
            is CalibrationEvent.SetCameraController -> setCameraController(event.controller)
        }
    }

    private fun setCameraController(controller: CameraController) {
        cameraController = controller
    }

    private fun initialize(projectId: String) {
        updateState { copy(projectId = projectId) }
        loadExistingCalibration(projectId)
    }

    private fun loadExistingCalibration(projectId: String) {
        viewModelScope.launch {
            getCalibrationUseCase(projectId)
                .onSuccess { calibrationData ->
                    if (calibrationData != null) {
                        // Project has existing calibration - go to adjust phase
                        updateState {
                            copy(
                                phase = CalibrationPhase.ADJUST,
                                referenceImagePath = calibrationData.imagePath,
                                adjustedLeftEye = calibrationData.leftEye,
                                adjustedRightEye = calibrationData.rightEye,
                                offsetX = calibrationData.offsetX,
                                offsetY = calibrationData.offsetY,
                                hasExistingCalibration = true,
                            )
                        }
                    } else {
                        // No calibration - stay in capture phase
                        updateState { copy(hasExistingCalibration = false) }
                    }
                }
                .onError { _, message ->
                    sendEffect(CalibrationEffect.ShowError(message ?: "Failed to load calibration"))
                }
        }
    }

    private fun cameraReady() {
        updateState { copy(isCameraReady = true, error = null) }
    }

    private fun cameraError() {
        updateState { copy(isCameraReady = false, error = "Camera initialization failed") }
        sendEffect(CalibrationEffect.ShowError("Camera initialization failed"))
    }

    private fun captureReference() {
        if (currentState.isProcessing || !currentState.isCameraReady) {
            return
        }

        val controller = cameraController ?: run {
            sendEffect(CalibrationEffect.ShowError("Camera not ready"))
            return
        }

        updateState { copy(isProcessing = true, error = null) }

        viewModelScope.launch {
            captureCalibrationImageUseCase(currentState.projectId, controller)
                .onSuccess { result ->
                    sendEffect(CalibrationEffect.PlayCaptureSound)
                    updateState {
                        copy(
                            isProcessing = false,
                            phase = CalibrationPhase.ADJUST,
                            referenceImagePath = result.imagePath,
                            detectedLandmarks = result.landmarks,
                            adjustedLeftEye = result.landmarks.leftEyeCenter,
                            adjustedRightEye = result.landmarks.rightEyeCenter,
                            // Reset offsets for new capture
                            offsetX = 0f,
                            offsetY = 0f,
                        )
                    }
                }
                .onError { _, message ->
                    updateState { copy(isProcessing = false) }
                    sendEffect(CalibrationEffect.ShowError(message ?: "Failed to capture reference"))
                }
        }
    }

    private fun startDrag(pointType: AdjustmentPointType) {
        // Only allow LEFT_EYE and RIGHT_EYE for calibration
        if (pointType != AdjustmentPointType.LEFT_EYE && pointType != AdjustmentPointType.RIGHT_EYE) {
            return
        }
        updateState { copy(activeDragPoint = pointType) }
    }

    private fun updateDrag(delta: Offset) {
        val activePoint = currentState.activeDragPoint ?: return

        when (activePoint) {
            AdjustmentPointType.LEFT_EYE -> {
                currentState.adjustedLeftEye?.let { eye ->
                    val newX = (eye.x + delta.x).coerceIn(0f, 1f)
                    val newY = (eye.y + delta.y).coerceIn(0f, 1f)
                    updateState {
                        copy(adjustedLeftEye = LandmarkPoint(x = newX, y = newY, z = 0f))
                    }
                }
            }
            AdjustmentPointType.RIGHT_EYE -> {
                currentState.adjustedRightEye?.let { eye ->
                    val newX = (eye.x + delta.x).coerceIn(0f, 1f)
                    val newY = (eye.y + delta.y).coerceIn(0f, 1f)
                    updateState {
                        copy(adjustedRightEye = LandmarkPoint(x = newX, y = newY, z = 0f))
                    }
                }
            }
            else -> { /* Ignore other point types */ }
        }
    }

    private fun endDrag() {
        updateState { copy(activeDragPoint = null) }
    }

    private fun updateOffset(offsetX: Float, offsetY: Float) {
        val clampedX = offsetX.coerceIn(OFFSET_MIN, OFFSET_MAX)
        val clampedY = offsetY.coerceIn(OFFSET_MIN, OFFSET_MAX)
        updateState { copy(offsetX = clampedX, offsetY = clampedY) }
    }

    private fun saveCalibration() {
        val imagePath = currentState.referenceImagePath
        val leftEye = currentState.adjustedLeftEye
        val rightEye = currentState.adjustedRightEye

        if (imagePath == null || leftEye == null || rightEye == null) {
            sendEffect(CalibrationEffect.ShowError("Calibration data incomplete"))
            return
        }

        updateState { copy(isProcessing = true, error = null) }

        viewModelScope.launch {
            saveCalibrationUseCase(
                projectId = currentState.projectId,
                imagePath = imagePath,
                leftEye = leftEye,
                rightEye = rightEye,
                offsetX = currentState.offsetX,
                offsetY = currentState.offsetY,
            )
                .onSuccess {
                    updateState { copy(isProcessing = false, hasExistingCalibration = true) }
                    sendEffect(CalibrationEffect.ShowSuccess("Calibration saved"))
                    sendEffect(CalibrationEffect.NavigateBack)
                }
                .onError { _, message ->
                    updateState { copy(isProcessing = false) }
                    sendEffect(CalibrationEffect.ShowError(message ?: "Failed to save calibration"))
                }
        }
    }

    private fun retakeReference() {
        updateState {
            copy(
                phase = CalibrationPhase.CAPTURE,
                referenceImagePath = null,
                detectedLandmarks = null,
                adjustedLeftEye = null,
                adjustedRightEye = null,
                offsetX = 0f,
                offsetY = 0f,
            )
        }
    }

    private fun clearCalibration() {
        updateState { copy(isProcessing = true, error = null) }

        viewModelScope.launch {
            projectRepository.clearCalibration(currentState.projectId)
                .onSuccess {
                    updateState {
                        copy(
                            isProcessing = false,
                            phase = CalibrationPhase.CAPTURE,
                            referenceImagePath = null,
                            detectedLandmarks = null,
                            adjustedLeftEye = null,
                            adjustedRightEye = null,
                            offsetX = 0f,
                            offsetY = 0f,
                            hasExistingCalibration = false,
                        )
                    }
                    sendEffect(CalibrationEffect.ShowSuccess("Calibration cleared"))
                }
                .onError { _, message ->
                    updateState { copy(isProcessing = false) }
                    sendEffect(CalibrationEffect.ShowError(message ?: "Failed to clear calibration"))
                }
        }
    }

    private fun cancel() {
        sendEffect(CalibrationEffect.NavigateBack)
    }

    private fun dismissError() {
        updateState { copy(error = null) }
    }

    companion object {
        private const val OFFSET_MIN = -0.2f
        private const val OFFSET_MAX = 0.2f
    }
}
