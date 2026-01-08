package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Represents a single pass in the multi-pass stabilization algorithm.
 *
 * Each pass applies a transformation and measures the resulting score.
 */
@Serializable
data class StabilizationPass(
    /**
     * The pass number (1-indexed).
     */
    val passNumber: Int,

    /**
     * The stabilization stage for this pass.
     */
    val stage: StabilizationStage,

    /**
     * The alignment score before this pass was applied.
     */
    val scoreBefore: Float,

    /**
     * The alignment score after this pass was applied.
     */
    val scoreAfter: Float,

    /**
     * Whether this pass caused the algorithm to converge.
     */
    val converged: Boolean,

    /**
     * Duration of this pass in milliseconds.
     */
    val durationMs: Long = 0L,
) {
    /**
     * The improvement in score from this pass (positive = better).
     */
    val improvement: Float
        get() = scoreBefore - scoreAfter

    /**
     * Whether this pass improved the alignment.
     */
    val improved: Boolean
        get() = scoreAfter < scoreBefore
}
