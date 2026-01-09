package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Represents the alignment quality score for face stabilization.
 *
 * The score measures how close the detected eye positions are to the goal positions.
 * Lower scores indicate better alignment.
 *
 * Score thresholds:
 * - < 0.5: No correction needed (already well-aligned)
 * - < 20.0: Successful stabilization
 * - >= 20.0: Stabilization failed
 */
@Serializable
data class StabilizationScore(
    /**
     * The computed alignment score.
     * Formula: ((leftEyeDist + rightEyeDist) * 1000 / 2) / canvasHeight
     */
    val value: Float,

    /**
     * Distance from detected left eye to goal left eye position (in pixels).
     */
    val leftEyeDistance: Float,

    /**
     * Distance from detected right eye to goal right eye position (in pixels).
     */
    val rightEyeDistance: Float,
) {
    /**
     * Whether the stabilization was successful (score < 20.0).
     */
    val isSuccess: Boolean
        get() = value < SUCCESS_THRESHOLD

    /**
     * Whether correction is needed (score >= 0.5).
     */
    val needsCorrection: Boolean
        get() = value >= NO_ACTION_THRESHOLD

    companion object {
        const val SUCCESS_THRESHOLD = 20.0f
        const val NO_ACTION_THRESHOLD = 0.5f

        /**
         * Calculates the stabilization score from eye positions.
         *
         * @param detectedLeftEyeX Detected left eye X position (pixels)
         * @param detectedLeftEyeY Detected left eye Y position (pixels)
         * @param detectedRightEyeX Detected right eye X position (pixels)
         * @param detectedRightEyeY Detected right eye Y position (pixels)
         * @param goalLeftEyeX Goal left eye X position (pixels)
         * @param goalLeftEyeY Goal left eye Y position (pixels)
         * @param goalRightEyeX Goal right eye X position (pixels)
         * @param goalRightEyeY Goal right eye Y position (pixels)
         * @param canvasHeight Canvas height in pixels (used for normalization)
         * @return The calculated StabilizationScore
         */
        fun calculate(
            detectedLeftEyeX: Float,
            detectedLeftEyeY: Float,
            detectedRightEyeX: Float,
            detectedRightEyeY: Float,
            goalLeftEyeX: Float,
            goalLeftEyeY: Float,
            goalRightEyeX: Float,
            goalRightEyeY: Float,
            canvasHeight: Int,
        ): StabilizationScore {
            val leftDeltaX = detectedLeftEyeX - goalLeftEyeX
            val leftDeltaY = detectedLeftEyeY - goalLeftEyeY
            val leftEyeDistance = sqrt(leftDeltaX * leftDeltaX + leftDeltaY * leftDeltaY)

            val rightDeltaX = detectedRightEyeX - goalRightEyeX
            val rightDeltaY = detectedRightEyeY - goalRightEyeY
            val rightEyeDistance = sqrt(rightDeltaX * rightDeltaX + rightDeltaY * rightDeltaY)

            // Score formula from AgeLapse: ((distL + distR) * 1000 / 2) / canvasHeight
            val averageDistance = (leftEyeDistance + rightEyeDistance) / 2f
            val normalizedScore = (averageDistance * 1000f) / canvasHeight.toFloat()

            return StabilizationScore(
                value = normalizedScore,
                leftEyeDistance = leftEyeDistance,
                rightEyeDistance = rightEyeDistance,
            )
        }

        /**
         * Calculates the stabilization score from LandmarkPoint objects.
         */
        fun calculate(
            detectedLeftEye: LandmarkPoint,
            detectedRightEye: LandmarkPoint,
            goalLeftEye: LandmarkPoint,
            goalRightEye: LandmarkPoint,
            canvasHeight: Int,
        ): StabilizationScore = calculate(
            detectedLeftEyeX = detectedLeftEye.x,
            detectedLeftEyeY = detectedLeftEye.y,
            detectedRightEyeX = detectedRightEye.x,
            detectedRightEyeY = detectedRightEye.y,
            goalLeftEyeX = goalLeftEye.x,
            goalLeftEyeY = goalLeftEye.y,
            goalRightEyeX = goalRightEye.x,
            goalRightEyeY = goalRightEye.y,
            canvasHeight = canvasHeight,
        )
    }
}
