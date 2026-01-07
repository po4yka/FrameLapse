package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for platform-specific face detection.
 *
 * Implementations will use:
 * - Android: MediaPipe Face Landmarker or ML Kit Face Detection
 * - iOS: Vision Framework VNDetectFaceLandmarksRequest
 */
interface FaceDetector {

    /**
     * Whether face detection is available on this device.
     */
    val isAvailable: Boolean

    /**
     * Detects a face in the provided image data.
     *
     * @param imageData The image to analyze.
     * @return Result containing FaceLandmarks or null if no face found.
     */
    suspend fun detectFace(imageData: ImageData): Result<FaceLandmarks?>

    /**
     * Detects a face in an image file.
     *
     * @param imagePath Path to the image file.
     * @return Result containing FaceLandmarks or null if no face found.
     */
    suspend fun detectFaceFromPath(imagePath: String): Result<FaceLandmarks?>

    /**
     * Detects faces for real-time preview (lower accuracy, higher speed).
     *
     * @param imageData The camera frame to analyze.
     * @return Result containing FaceLandmarks or null if no face found.
     */
    suspend fun detectFaceRealtime(imageData: ImageData): Result<FaceLandmarks?>

    /**
     * Releases resources held by the face detector.
     */
    fun release()
}
