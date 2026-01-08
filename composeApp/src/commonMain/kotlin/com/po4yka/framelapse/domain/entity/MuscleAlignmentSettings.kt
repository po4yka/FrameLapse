package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration for muscle-focused alignment with region-specific cropping.
 *
 * Muscle Mode performs body alignment first (shoulder-based), then crops
 * to the specified muscle region for targeted fitness progress tracking.
 */
@Serializable
data class MuscleAlignmentSettings(
    /** The target muscle region for cropping. */
    val muscleRegion: MuscleRegion = MuscleRegion.FULL_BODY,

    /**
     * Padding around the detected region as a fraction of region size (0.0 to 0.5).
     * Adds breathing room around the cropped area.
     */
    val regionPadding: Float = DEFAULT_REGION_PADDING,

    /** Output image size in pixels (square). */
    val outputSize: Int = DEFAULT_OUTPUT_SIZE,

    /** Minimum confidence threshold for body pose detection (0.0 to 1.0). */
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,

    /**
     * Underlying body alignment settings for shoulder-based stabilization.
     * The body is first aligned using these settings, then cropped to the muscle region.
     */
    val bodyAlignmentSettings: BodyAlignmentSettings = BodyAlignmentSettings(),
) {
    init {
        require(regionPadding in 0f..0.5f) {
            "Region padding must be between 0.0 and 0.5"
        }
        require(outputSize in MIN_OUTPUT_SIZE..MAX_OUTPUT_SIZE) {
            "Output size must be between $MIN_OUTPUT_SIZE and $MAX_OUTPUT_SIZE"
        }
        require(minConfidence in 0f..1f) {
            "Minimum confidence must be between 0 and 1"
        }
    }

    companion object {
        const val DEFAULT_REGION_PADDING = 0.1f
        const val DEFAULT_OUTPUT_SIZE = 512
        const val DEFAULT_MIN_CONFIDENCE = 0.5f
        const val MIN_OUTPUT_SIZE = 256
        const val MAX_OUTPUT_SIZE = 2048
    }
}
