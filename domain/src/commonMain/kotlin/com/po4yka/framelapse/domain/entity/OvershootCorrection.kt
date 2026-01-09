package com.po4yka.framelapse.domain.entity

/**
 * Represents overshoot detection and correction data.
 *
 * When eyes pass their goal positions during stabilization,
 * this class captures the overshoot amount and calculates the correction needed.
 */
data class OvershootCorrection(
    /**
     * Left eye overshoot in X direction (pixels).
     * Positive = eye is to the right of goal.
     */
    val overshotLeftX: Float,

    /**
     * Left eye overshoot in Y direction (pixels).
     * Positive = eye is below goal.
     */
    val overshotLeftY: Float,

    /**
     * Right eye overshoot in X direction (pixels).
     * Positive = eye is to the right of goal.
     */
    val overshotRightX: Float,

    /**
     * Right eye overshoot in Y direction (pixels).
     * Positive = eye is below goal.
     */
    val overshotRightY: Float,

    /**
     * The current alignment score.
     */
    val currentScore: Float,
) {
    /**
     * Average overshoot in X direction.
     */
    val averageOvershootX: Float
        get() = (overshotLeftX + overshotRightX) / 2f

    /**
     * Average overshoot in Y direction.
     */
    val averageOvershootY: Float
        get() = (overshotLeftY + overshotRightY) / 2f

    /**
     * Whether both eyes overshot in the same X direction.
     * This indicates a consistent translation error that can be corrected.
     */
    val sameDirectionX: Boolean
        get() = (overshotLeftX > 0f && overshotRightX > 0f) ||
            (overshotLeftX < 0f && overshotRightX < 0f)

    /**
     * Whether both eyes overshot in the same Y direction.
     * This indicates a consistent translation error that can be corrected.
     */
    val sameDirectionY: Boolean
        get() = (overshotLeftY > 0f && overshotRightY > 0f) ||
            (overshotLeftY < 0f && overshotRightY < 0f)

    /**
     * Whether correction is needed based on score and overshoot patterns.
     *
     * Correction is needed if:
     * - Score is above no-action threshold (>= 0.5), OR
     * - Both eyes overshot in the same direction (indicates consistent error)
     */
    val needsCorrection: Boolean
        get() = currentScore >= StabilizationScore.NO_ACTION_THRESHOLD ||
            sameDirectionX ||
            sameDirectionY

    companion object {
        /**
         * Calculates overshoot from detected and goal eye positions.
         *
         * @param detectedLeftEyeX Detected left eye X position (pixels)
         * @param detectedLeftEyeY Detected left eye Y position (pixels)
         * @param detectedRightEyeX Detected right eye X position (pixels)
         * @param detectedRightEyeY Detected right eye Y position (pixels)
         * @param goalLeftEyeX Goal left eye X position (pixels)
         * @param goalLeftEyeY Goal left eye Y position (pixels)
         * @param goalRightEyeX Goal right eye X position (pixels)
         * @param goalRightEyeY Goal right eye Y position (pixels)
         * @param currentScore Current alignment score
         * @return OvershootCorrection with calculated values
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
            currentScore: Float,
        ): OvershootCorrection = OvershootCorrection(
            overshotLeftX = detectedLeftEyeX - goalLeftEyeX,
            overshotLeftY = detectedLeftEyeY - goalLeftEyeY,
            overshotRightX = detectedRightEyeX - goalRightEyeX,
            overshotRightY = detectedRightEyeY - goalRightEyeY,
            currentScore = currentScore,
        )

        /**
         * Calculates overshoot from LandmarkPoint objects.
         */
        fun calculate(
            detectedLeftEye: LandmarkPoint,
            detectedRightEye: LandmarkPoint,
            goalLeftEye: LandmarkPoint,
            goalRightEye: LandmarkPoint,
            currentScore: Float,
        ): OvershootCorrection = calculate(
            detectedLeftEyeX = detectedLeftEye.x,
            detectedLeftEyeY = detectedLeftEye.y,
            detectedRightEyeX = detectedRightEye.x,
            detectedRightEyeY = detectedRightEye.y,
            goalLeftEyeX = goalLeftEye.x,
            goalLeftEyeY = goalLeftEye.y,
            goalRightEyeX = goalRightEye.x,
            goalRightEyeY = goalRightEye.y,
            currentScore = currentScore,
        )
    }
}
