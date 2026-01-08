package com.po4yka.framelapse.domain.usecase.body

import com.po4yka.framelapse.domain.entity.BodyAlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import kotlin.math.sqrt

/**
 * Validates body pose detection results for alignment quality.
 */
class ValidateBodyAlignmentUseCase {
    /**
     * Validates if the detected landmarks meet quality requirements for body alignment.
     *
     * @param landmarks The detected body landmarks.
     * @param settings Body alignment configuration containing quality thresholds.
     * @return True if the landmarks are suitable for alignment.
     */
    operator fun invoke(landmarks: BodyLandmarks, settings: BodyAlignmentSettings = BodyAlignmentSettings()): Boolean =
        validateConfidence(landmarks, settings.minConfidence) &&
            validateShoulderDistance(landmarks) &&
            validateBoundingBox(landmarks) &&
            validateKeyLandmarksVisibility(landmarks)

    /**
     * Validates the detection confidence.
     *
     * @param landmarks The detected body landmarks.
     * @param minConfidence Minimum required confidence (0.0 to 1.0).
     * @return True if confidence meets the threshold.
     */
    fun validateConfidence(landmarks: BodyLandmarks, minConfidence: Float): Boolean =
        landmarks.confidence >= minConfidence

    /**
     * Validates that shoulder distance is reasonable.
     *
     * @param landmarks The detected body landmarks.
     * @return True if shoulders are properly detected and separated.
     */
    fun validateShoulderDistance(landmarks: BodyLandmarks): Boolean {
        val deltaX = landmarks.rightShoulder.x - landmarks.leftShoulder.x
        val deltaY = landmarks.rightShoulder.y - landmarks.leftShoulder.y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Shoulders should be separated by at least some minimum distance
        // and the right shoulder should be to the right of the left shoulder (from camera view)
        return distance > MIN_SHOULDER_DISTANCE && deltaX > 0
    }

    /**
     * Validates that the bounding box is reasonable.
     *
     * @param landmarks The detected body landmarks.
     * @return True if the bounding box is valid.
     */
    fun validateBoundingBox(landmarks: BodyLandmarks): Boolean {
        val box = landmarks.boundingBox
        return box.width > MIN_BODY_SIZE &&
            box.height > MIN_BODY_SIZE &&
            box.left >= 0 &&
            box.top >= 0
    }

    /**
     * Validates that key landmarks (shoulders and hips) are visible.
     *
     * @param landmarks The detected body landmarks.
     * @return True if all key landmarks are visible.
     */
    fun validateKeyLandmarksVisibility(landmarks: BodyLandmarks): Boolean {
        // Check that all key alignment landmarks have valid positions
        val keyPoints = listOf(
            landmarks.leftShoulder,
            landmarks.rightShoulder,
            landmarks.leftHip,
            landmarks.rightHip,
        )

        return keyPoints.all { point ->
            point.x in 0f..1f && point.y in 0f..1f
        }
    }

    /**
     * Gets a validation result with detailed information.
     *
     * @param landmarks The detected body landmarks.
     * @param settings Body alignment configuration.
     * @return ValidationResult with pass/fail status and details.
     */
    fun getDetailedValidation(
        landmarks: BodyLandmarks,
        settings: BodyAlignmentSettings = BodyAlignmentSettings(),
    ): ValidationResult {
        val issues = mutableListOf<String>()

        if (!validateConfidence(landmarks, settings.minConfidence)) {
            issues.add("Low detection confidence (${(landmarks.confidence * 100).toInt()}%)")
        }
        if (!validateShoulderDistance(landmarks)) {
            issues.add("Invalid shoulder detection")
        }
        if (!validateBoundingBox(landmarks)) {
            issues.add("Body too small or partially visible")
        }
        if (!validateKeyLandmarksVisibility(landmarks)) {
            issues.add("Key body landmarks not visible")
        }

        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            confidence = landmarks.confidence,
            shoulderDistance = landmarks.shoulderDistance,
        )
    }

    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
        val confidence: Float,
        val shoulderDistance: Float,
    )

    companion object {
        private const val MIN_SHOULDER_DISTANCE = 0.05f // 5% of image width
        private const val MIN_BODY_SIZE = 0.1f // 10% of image dimension
    }
}
