package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration for face alignment algorithm.
 */
@Serializable
data class AlignmentSettings(
    /** Minimum confidence threshold for face detection (0.0 to 1.0). */
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    /** Target distance between eyes as a fraction of output size (0.0 to 1.0). */
    val targetEyeDistance: Float = DEFAULT_TARGET_EYE_DISTANCE,
    /** Output image size in pixels (square). */
    val outputSize: Int = DEFAULT_OUTPUT_SIZE,
    /** Vertical offset for face centering as a fraction of output size. */
    val verticalOffset: Float = DEFAULT_VERTICAL_OFFSET,
    /** Multi-pass stabilization settings. */
    val stabilizationSettings: StabilizationSettings = StabilizationSettings(),
) {
    init {
        require(minConfidence in 0f..1f) {
            "Minimum confidence must be between 0 and 1"
        }
        require(targetEyeDistance in 0.1f..0.9f) {
            "Target eye distance must be between 0.1 and 0.9"
        }
        require(outputSize in MIN_OUTPUT_SIZE..MAX_OUTPUT_SIZE) {
            "Output size must be between $MIN_OUTPUT_SIZE and $MAX_OUTPUT_SIZE"
        }
        require(verticalOffset in -0.5f..0.5f) {
            "Vertical offset must be between -0.5 and 0.5"
        }
    }

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.7f
        const val DEFAULT_TARGET_EYE_DISTANCE = 0.3f
        const val DEFAULT_OUTPUT_SIZE = 512
        const val DEFAULT_VERTICAL_OFFSET = 0.1f
        const val MIN_OUTPUT_SIZE = 128
        const val MAX_OUTPUT_SIZE = 2048
    }
}
