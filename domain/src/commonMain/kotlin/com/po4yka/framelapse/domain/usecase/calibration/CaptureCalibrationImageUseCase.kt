package com.po4yka.framelapse.domain.usecase.calibration

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.service.MediaStore
import com.po4yka.framelapse.domain.util.Result
import org.koin.core.annotation.Factory

/**
 * Captures a calibration reference image and detects face landmarks.
 *
 * This use case:
 * 1. Captures a photo using the camera
 * 2. Saves it to calibration storage
 * 3. Detects face and extracts eye positions
 * 4. Returns the image path and landmarks for adjustment
 */
@Factory
class CaptureCalibrationImageUseCase(
    private val mediaStore: MediaStore,
    private val faceDetector: FaceDetector,
    private val imageProcessor: ImageProcessor,
    private val clock: Clock,
) {

    /**
     * Captures a calibration reference photo and detects face landmarks.
     *
     * @param projectId The project ID for storage path.
     * @param cameraController The camera controller to use for capturing.
     * @return Result containing the captured image path and detected face landmarks.
     */
    suspend operator fun invoke(
        projectId: String,
        cameraController: CameraController,
    ): Result<CalibrationCaptureResult> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        // Check if face detection is available
        if (!faceDetector.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Face detection is not available"),
                "Face detection not available",
            )
        }

        // Generate output path for calibration image
        val timestamp = clock.nowMillis()
        val capturePath = mediaStore.getCalibrationPath(projectId, timestamp)

        // Capture image
        val captureResult = cameraController.captureImage(capturePath)
        if (captureResult.isError) {
            return Result.Error(
                captureResult.exceptionOrNull()!!,
                "Failed to capture image",
            )
        }

        val savedPath = captureResult.getOrNull()!!

        // Load the captured image for face detection
        val loadResult = imageProcessor.loadImage(savedPath)
        if (loadResult.isError) {
            mediaStore.deleteImage(savedPath)
            return Result.Error(
                loadResult.exceptionOrNull()!!,
                "Failed to load captured image",
            )
        }

        val imageData = loadResult.getOrNull()!!

        // Detect face landmarks
        val detectResult = faceDetector.detectFace(imageData)
        if (detectResult.isError) {
            mediaStore.deleteImage(savedPath)
            return Result.Error(
                detectResult.exceptionOrNull()!!,
                "Face detection failed",
            )
        }

        val landmarks = detectResult.getOrNull()
        if (landmarks == null) {
            mediaStore.deleteImage(savedPath)
            return Result.Error(
                IllegalStateException("No face detected in image"),
                "No face detected. Please ensure your face is visible and try again.",
            )
        }

        return Result.Success(
            CalibrationCaptureResult(
                imagePath = savedPath,
                landmarks = landmarks,
            ),
        )
    }
}

/**
 * Result of calibration image capture.
 */
data class CalibrationCaptureResult(
    /** Path to the captured calibration image. */
    val imagePath: String,
    /** Detected face landmarks with eye positions. */
    val landmarks: FaceLandmarks,
)
