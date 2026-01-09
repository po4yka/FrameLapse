package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for platform-specific body pose detection.
 *
 * Implementations will use:
 * - Android: MediaPipe PoseLandmarker (33 keypoints)
 * - iOS: Vision Framework VNDetectHumanBodyPoseRequest (17 keypoints)
 *
 * Primary use case: Body-based alignment for fitness/progress timelapses
 * where shoulder positions serve as reference points for stabilization.
 */
interface BodyPoseDetector {

    /**
     * Whether body pose detection is available on this device.
     */
    val isAvailable: Boolean

    /**
     * Detects a body pose in the provided image data.
     *
     * @param imageData The image to analyze.
     * @return Result containing BodyLandmarks or null if no body found.
     */
    suspend fun detectBodyPose(imageData: ImageData): Result<BodyLandmarks?>

    /**
     * Detects a body pose in an image file.
     *
     * @param imagePath Path to the image file.
     * @return Result containing BodyLandmarks or null if no body found.
     */
    suspend fun detectBodyPoseFromPath(imagePath: String): Result<BodyLandmarks?>

    /**
     * Detects body pose for real-time preview (lower accuracy, higher speed).
     *
     * @param imageData The camera frame to analyze.
     * @return Result containing BodyLandmarks or null if no body found.
     */
    suspend fun detectBodyPoseRealtime(imageData: ImageData): Result<BodyLandmarks?>

    /**
     * Releases resources held by the body pose detector.
     */
    fun release()
}
