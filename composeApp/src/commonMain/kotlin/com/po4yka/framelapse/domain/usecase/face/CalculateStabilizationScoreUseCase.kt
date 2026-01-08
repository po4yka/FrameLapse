package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.StabilizationScore

/**
 * Calculates the stabilization score for face alignment quality.
 *
 * The score measures how close the detected eye positions are to the goal positions.
 * Lower scores indicate better alignment.
 *
 * Algorithm (from AgeLapse):
 * ```
 * score = ((dist(leftEye, goalLeft) + dist(rightEye, goalRight)) * 1000 / 2) / canvasHeight
 * ```
 *
 * Score interpretation:
 * - < 0.5: No correction needed (already well-aligned)
 * - < 20.0: Successful stabilization
 * - >= 20.0: Stabilization failed
 */
class CalculateStabilizationScoreUseCase {

    /**
     * Calculates the stabilization score from detected landmarks and goal positions.
     *
     * @param detectedLandmarks The detected face landmarks from the current image.
     * @param goalLeftEye Goal position for the left eye (in pixels).
     * @param goalRightEye Goal position for the right eye (in pixels).
     * @param canvasHeight Canvas height in pixels (used for score normalization).
     * @return The calculated StabilizationScore.
     */
    operator fun invoke(
        detectedLandmarks: FaceLandmarks,
        goalLeftEye: LandmarkPoint,
        goalRightEye: LandmarkPoint,
        canvasHeight: Int,
    ): StabilizationScore {
        val detectedLeftEye = detectedLandmarks.leftEyeCenter
        val detectedRightEye = detectedLandmarks.rightEyeCenter

        return StabilizationScore.calculate(
            detectedLeftEye = detectedLeftEye,
            detectedRightEye = detectedRightEye,
            goalLeftEye = goalLeftEye,
            goalRightEye = goalRightEye,
            canvasHeight = canvasHeight,
        )
    }

    /**
     * Calculates the stabilization score from pixel coordinates.
     *
     * @param detectedLeftEyeX Detected left eye X position (pixels).
     * @param detectedLeftEyeY Detected left eye Y position (pixels).
     * @param detectedRightEyeX Detected right eye X position (pixels).
     * @param detectedRightEyeY Detected right eye Y position (pixels).
     * @param goalLeftEyeX Goal left eye X position (pixels).
     * @param goalLeftEyeY Goal left eye Y position (pixels).
     * @param goalRightEyeX Goal right eye X position (pixels).
     * @param goalRightEyeY Goal right eye Y position (pixels).
     * @param canvasHeight Canvas height in pixels.
     * @return The calculated StabilizationScore.
     */
    fun invoke(
        detectedLeftEyeX: Float,
        detectedLeftEyeY: Float,
        detectedRightEyeX: Float,
        detectedRightEyeY: Float,
        goalLeftEyeX: Float,
        goalLeftEyeY: Float,
        goalRightEyeX: Float,
        goalRightEyeY: Float,
        canvasHeight: Int,
    ): StabilizationScore = StabilizationScore.calculate(
        detectedLeftEyeX = detectedLeftEyeX,
        detectedLeftEyeY = detectedLeftEyeY,
        detectedRightEyeX = detectedRightEyeX,
        detectedRightEyeY = detectedRightEyeY,
        goalLeftEyeX = goalLeftEyeX,
        goalLeftEyeY = goalLeftEyeY,
        goalRightEyeX = goalRightEyeX,
        goalRightEyeY = goalRightEyeY,
        canvasHeight = canvasHeight,
    )
}
