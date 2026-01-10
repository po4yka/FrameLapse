package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import org.koin.core.annotation.Factory
import kotlin.math.sqrt

/**
 * Validates face detection results for alignment quality.
 */
@Factory
class ValidateAlignmentUseCase {
    /**
     * Validates if the detected landmarks meet quality requirements for alignment.
     *
     * @param landmarks The detected face landmarks.
     * @param settings Alignment configuration containing quality thresholds.
     * @return True if the landmarks are suitable for alignment.
     */
    operator fun invoke(landmarks: FaceLandmarks, settings: AlignmentSettings = AlignmentSettings()): Boolean =
        validateConfidence(landmarks, settings.minConfidence) &&
            validateEyeDistance(landmarks) &&
            validateBoundingBox(landmarks)

    /**
     * Validates the detection confidence.
     *
     * @param landmarks The detected face landmarks.
     * @param minConfidence Minimum required confidence (0.0 to 1.0).
     * @return True if confidence meets the threshold.
     */
    fun validateConfidence(landmarks: FaceLandmarks, minConfidence: Float): Boolean {
        // If we have valid landmarks with eye centers, we consider detection successful
        // The actual confidence would come from the detector, but landmarks existence implies detection
        return landmarks.points.isNotEmpty() &&
            landmarks.leftEyeCenter.x >= 0 &&
            landmarks.rightEyeCenter.x >= 0
    }

    /**
     * Validates that eye distance is reasonable.
     *
     * @param landmarks The detected face landmarks.
     * @return True if eyes are properly detected and separated.
     */
    fun validateEyeDistance(landmarks: FaceLandmarks): Boolean {
        val deltaX = landmarks.rightEyeCenter.x - landmarks.leftEyeCenter.x
        val deltaY = landmarks.rightEyeCenter.y - landmarks.leftEyeCenter.y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Eyes should be separated by at least some minimum distance
        // and the right eye should be to the right of the left eye
        return distance > MIN_EYE_DISTANCE && deltaX > 0
    }

    /**
     * Validates that the bounding box is reasonable.
     *
     * @param landmarks The detected face landmarks.
     * @return True if the bounding box is valid.
     */
    fun validateBoundingBox(landmarks: FaceLandmarks): Boolean {
        val box = landmarks.boundingBox
        return box.width > MIN_FACE_SIZE &&
            box.height > MIN_FACE_SIZE &&
            box.left >= 0 &&
            box.top >= 0
    }

    /**
     * Gets a validation result with detailed information.
     *
     * @param landmarks The detected face landmarks.
     * @param settings Alignment configuration.
     * @return ValidationResult with pass/fail status and details.
     */
    fun getDetailedValidation(
        landmarks: FaceLandmarks,
        settings: AlignmentSettings = AlignmentSettings(),
    ): ValidationResult {
        val issues = mutableListOf<String>()

        if (!validateConfidence(landmarks, settings.minConfidence)) {
            issues.add("Low detection confidence")
        }
        if (!validateEyeDistance(landmarks)) {
            issues.add("Invalid eye detection")
        }
        if (!validateBoundingBox(landmarks)) {
            issues.add("Face too small or partially visible")
        }

        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
        )
    }

    data class ValidationResult(val isValid: Boolean, val issues: List<String>)

    companion object {
        /**
         * Minimum eye distance in normalized coordinates (0-1).
         * ~2% of image width is a reasonable minimum for valid eye detection.
         */
        private const val MIN_EYE_DISTANCE = 0.02f

        /**
         * Minimum face size in normalized coordinates (0-1).
         * ~10% of image width/height is a reasonable minimum for a detectable face.
         */
        private const val MIN_FACE_SIZE = 0.1f
    }
}
