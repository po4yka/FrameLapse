package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration for body pose alignment algorithm.
 *
 * Similar to AlignmentSettings but optimized for body-based alignment
 * using shoulder positions as reference points instead of eyes.
 */
@Serializable
data class BodyAlignmentSettings(
    /** Minimum confidence threshold for pose detection (0.0 to 1.0). */
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,

    /**
     * Target distance between shoulders as a fraction of output size (0.0 to 1.0).
     * Typically larger than face's eye distance (0.3) since shoulders are wider.
     */
    val targetShoulderDistance: Float = DEFAULT_TARGET_SHOULDER_DISTANCE,

    /** Output image size in pixels (square). */
    val outputSize: Int = DEFAULT_OUTPUT_SIZE,

    /**
     * Vertical offset for body centering as a fraction of output size.
     * Negative values shift the body up (show more torso below shoulders).
     */
    val verticalOffset: Float = DEFAULT_VERTICAL_OFFSET,

    /**
     * Ratio of visible body from head to waist (0.0 to 1.0).
     * Controls how much of the body is shown in the frame.
     * 0.7 means 70% of the frame height is head-to-waist.
     */
    val headToWaistRatio: Float = DEFAULT_HEAD_TO_WAIST_RATIO,

    /** Multi-pass stabilization settings. */
    val stabilizationSettings: StabilizationSettings = StabilizationSettings(),
) {
    init {
        require(minConfidence in 0f..1f) {
            "Minimum confidence must be between 0 and 1"
        }
        require(targetShoulderDistance in 0.2f..0.9f) {
            "Target shoulder distance must be between 0.2 and 0.9"
        }
        require(outputSize in MIN_OUTPUT_SIZE..MAX_OUTPUT_SIZE) {
            "Output size must be between $MIN_OUTPUT_SIZE and $MAX_OUTPUT_SIZE"
        }
        require(verticalOffset in -0.5f..0.5f) {
            "Vertical offset must be between -0.5 and 0.5"
        }
        require(headToWaistRatio in 0.3f..1.0f) {
            "Head to waist ratio must be between 0.3 and 1.0"
        }
    }

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.5f
        const val DEFAULT_TARGET_SHOULDER_DISTANCE = 0.4f
        const val DEFAULT_OUTPUT_SIZE = 512
        const val DEFAULT_VERTICAL_OFFSET = -0.1f
        const val DEFAULT_HEAD_TO_WAIST_RATIO = 0.7f
        const val MIN_OUTPUT_SIZE = 128
        const val MAX_OUTPUT_SIZE = 2048
    }
}
