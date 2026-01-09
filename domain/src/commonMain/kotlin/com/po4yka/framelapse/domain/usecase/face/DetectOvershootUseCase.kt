package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.OvershootCorrection
import com.po4yka.framelapse.domain.entity.StabilizationScore

/**
 * Detects overshoot in eye positions and calculates correction.
 *
 * During multi-pass stabilization, eyes may "overshoot" their goal positions.
 * This use case detects such overshoots and determines if correction is needed.
 *
 * Overshoot occurs when:
 * - Both eyes pass the goal position in the same direction (X or Y)
 * - The alignment score is still above the no-action threshold
 *
 * Correction is applied by subtracting the average overshoot from the translation.
 */
class DetectOvershootUseCase {

    /**
     * Detects overshoot from detected landmarks and goal positions.
     *
     * @param detectedLandmarks The detected face landmarks from the current image.
     * @param goalLeftEye Goal position for the left eye (in pixels).
     * @param goalRightEye Goal position for the right eye (in pixels).
     * @param currentScore Current alignment score.
     * @return OvershootCorrection with calculated overshoot values.
     */
    operator fun invoke(
        detectedLandmarks: FaceLandmarks,
        goalLeftEye: LandmarkPoint,
        goalRightEye: LandmarkPoint,
        currentScore: StabilizationScore,
    ): OvershootCorrection {
        val detectedLeftEye = detectedLandmarks.leftEyeCenter
        val detectedRightEye = detectedLandmarks.rightEyeCenter

        return OvershootCorrection.calculate(
            detectedLeftEye = detectedLeftEye,
            detectedRightEye = detectedRightEye,
            goalLeftEye = goalLeftEye,
            goalRightEye = goalRightEye,
            currentScore = currentScore.value,
        )
    }

    /**
     * Detects overshoot from pixel coordinates.
     *
     * @param detectedLeftEyeX Detected left eye X position (pixels).
     * @param detectedLeftEyeY Detected left eye Y position (pixels).
     * @param detectedRightEyeX Detected right eye X position (pixels).
     * @param detectedRightEyeY Detected right eye Y position (pixels).
     * @param goalLeftEyeX Goal left eye X position (pixels).
     * @param goalLeftEyeY Goal left eye Y position (pixels).
     * @param goalRightEyeX Goal right eye X position (pixels).
     * @param goalRightEyeY Goal right eye Y position (pixels).
     * @param currentScore Current alignment score value.
     * @return OvershootCorrection with calculated overshoot values.
     */
    operator fun invoke(
        detectedLeftEyeX: Float,
        detectedLeftEyeY: Float,
        detectedRightEyeX: Float,
        detectedRightEyeY: Float,
        goalLeftEyeX: Float,
        goalLeftEyeY: Float,
        goalRightEyeX: Float,
        goalRightEyeY: Float,
        currentScore: Float,
    ): OvershootCorrection = OvershootCorrection.calculate(
        detectedLeftEyeX = detectedLeftEyeX,
        detectedLeftEyeY = detectedLeftEyeY,
        detectedRightEyeX = detectedRightEyeX,
        detectedRightEyeY = detectedRightEyeY,
        goalLeftEyeX = goalLeftEyeX,
        goalLeftEyeY = goalLeftEyeY,
        goalRightEyeX = goalRightEyeX,
        goalRightEyeY = goalRightEyeY,
        currentScore = currentScore,
    )
}
