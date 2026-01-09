package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration settings for multi-pass face stabilization algorithm.
 */
@Serializable
data class StabilizationSettings(
    /**
     * Stabilization mode (FAST or SLOW).
     */
    val mode: StabilizationMode = StabilizationMode.FAST,

    /**
     * Rotation stop threshold in pixels.
     * Rotation refinement stops when eye delta Y <= this value.
     * Default: 0.1 pixels
     */
    val rotationStopThreshold: Float = DEFAULT_ROTATION_STOP_THRESHOLD,

    /**
     * Scale error threshold in pixels.
     * Scale refinement stops when eye distance error <= this value.
     * Default: 1.0 pixel
     */
    val scaleErrorThreshold: Float = DEFAULT_SCALE_ERROR_THRESHOLD,

    /**
     * Score convergence threshold.
     * Translation refinement stops when score improvement < this value.
     * Default: 0.05 score points
     */
    val convergenceThreshold: Float = DEFAULT_CONVERGENCE_THRESHOLD,

    /**
     * Success score threshold.
     * Stabilization is considered successful if final score < this value.
     * Default: 20.0
     */
    val successScoreThreshold: Float = DEFAULT_SUCCESS_SCORE_THRESHOLD,

    /**
     * No-action score threshold.
     * If initial score < this value, no correction is needed.
     * Default: 0.5
     */
    val noActionScoreThreshold: Float = DEFAULT_NO_ACTION_SCORE_THRESHOLD,

    /**
     * Minimum face size ratio.
     * Faces smaller than this fraction of image width are rejected.
     * Default: 0.1 (10% of image width)
     */
    val minFaceSizeRatio: Float = DEFAULT_MIN_FACE_SIZE_RATIO,

    /**
     * Eye validity ratio.
     * Eye pairs with distance < this fraction of goal distance are rejected.
     * Default: 0.75 (75% of goal distance)
     */
    val eyeValidityRatio: Float = DEFAULT_EYE_VALIDITY_RATIO,
) {
    /**
     * Maximum number of passes for the current mode.
     */
    val maxPasses: Int
        get() = when (mode) {
            StabilizationMode.FAST -> MAX_PASSES_FAST
            StabilizationMode.SLOW -> MAX_PASSES_SLOW
        }

    init {
        require(rotationStopThreshold > 0f) {
            "Rotation stop threshold must be positive"
        }
        require(scaleErrorThreshold > 0f) {
            "Scale error threshold must be positive"
        }
        require(convergenceThreshold > 0f) {
            "Convergence threshold must be positive"
        }
        require(successScoreThreshold > 0f) {
            "Success score threshold must be positive"
        }
        require(noActionScoreThreshold >= 0f) {
            "No-action score threshold must be non-negative"
        }
        require(minFaceSizeRatio in 0f..1f) {
            "Minimum face size ratio must be between 0 and 1"
        }
        require(eyeValidityRatio in 0f..1f) {
            "Eye validity ratio must be between 0 and 1"
        }
    }

    companion object {
        const val DEFAULT_ROTATION_STOP_THRESHOLD = 0.1f
        const val DEFAULT_SCALE_ERROR_THRESHOLD = 1.0f
        const val DEFAULT_CONVERGENCE_THRESHOLD = 0.05f
        const val DEFAULT_SUCCESS_SCORE_THRESHOLD = 20.0f
        const val DEFAULT_NO_ACTION_SCORE_THRESHOLD = 0.5f
        const val DEFAULT_MIN_FACE_SIZE_RATIO = 0.1f
        const val DEFAULT_EYE_VALIDITY_RATIO = 0.75f

        const val MAX_PASSES_FAST = 4
        const val MAX_PASSES_SLOW = 11 // 10 refinement + 1 cleanup
    }
}
