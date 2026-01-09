package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for platform-specific camera control.
 *
 * Implementations will use:
 * - Android: CameraX
 * - iOS: AVFoundation AVCaptureSession
 */
interface CameraController {

    /**
     * The current camera facing direction.
     */
    val currentFacing: CameraFacing

    /**
     * The current flash mode.
     */
    val currentFlashMode: FlashMode

    /**
     * Whether the camera is currently previewing.
     */
    val isPreviewing: Boolean

    /**
     * Captures an image and saves it to the specified path.
     *
     * @param outputPath Destination path for the captured image.
     * @return Result containing the saved file path or an error.
     */
    suspend fun captureImage(outputPath: String): Result<String>

    /**
     * Starts the camera preview.
     */
    fun startPreview()

    /**
     * Stops the camera preview.
     */
    fun stopPreview()

    /**
     * Sets the flash mode for capture.
     *
     * @param mode The flash mode to set.
     */
    fun setFlashMode(mode: FlashMode)

    /**
     * Switches between front and back cameras.
     */
    fun switchCamera()

    /**
     * Sets the camera to a specific facing direction.
     *
     * @param facing The camera facing to use.
     */
    fun setCameraFacing(facing: CameraFacing)

    /**
     * Checks if the specified camera is available.
     *
     * @param facing The camera facing to check.
     * @return True if the camera is available.
     */
    fun isCameraAvailable(facing: CameraFacing): Boolean

    /**
     * Releases camera resources.
     */
    fun release()
}
