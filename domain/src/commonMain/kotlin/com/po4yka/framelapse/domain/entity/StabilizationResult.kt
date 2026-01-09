package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Complete result of multi-pass face stabilization.
 *
 * Contains the final score, all pass details, and metadata about the stabilization process.
 */
@Serializable
data class StabilizationResult(
    /**
     * Whether stabilization was successful (final score < 20.0).
     */
    val success: Boolean,

    /**
     * The final alignment score after all passes.
     */
    val finalScore: StabilizationScore,

    /**
     * Number of passes executed.
     */
    val passesExecuted: Int,

    /**
     * Details of each stabilization pass.
     */
    val passes: List<StabilizationPass>,

    /**
     * The stabilization mode used (FAST or SLOW).
     */
    val mode: StabilizationMode,

    /**
     * Reason for early termination, if applicable.
     * Null if all passes were executed.
     */
    val earlyStopReason: EarlyStopReason? = null,

    /**
     * Total duration of stabilization in milliseconds.
     */
    val totalDurationMs: Long,

    /**
     * Initial score before any stabilization.
     */
    val initialScore: Float,

    /**
     * Final eye delta Y in pixels (rotation accuracy).
     */
    val finalEyeDeltaY: Float? = null,

    /**
     * Final eye distance in pixels.
     */
    val finalEyeDistance: Float? = null,

    /**
     * Goal eye distance in pixels.
     */
    val goalEyeDistance: Float? = null,

    /**
     * Diagnostics captured during alignment (optional).
     */
    val diagnostics: AlignmentDiagnostics? = null,
) {
    /**
     * Total improvement in score from initial to final.
     */
    val totalImprovement: Float
        get() = initialScore - finalScore.value

    /**
     * Percentage improvement in score.
     */
    val improvementPercent: Float
        get() = if (initialScore > 0f) {
            (totalImprovement / initialScore) * 100f
        } else {
            0f
        }

    /**
     * Average duration per pass in milliseconds.
     */
    val averagePassDurationMs: Long
        get() = if (passesExecuted > 0) {
            totalDurationMs / passesExecuted
        } else {
            0L
        }

    /**
     * Whether the algorithm terminated early.
     */
    val terminatedEarly: Boolean
        get() = earlyStopReason != null

    companion object {
        /**
         * Creates a failed result with no passes executed.
         */
        fun failed(
            mode: StabilizationMode,
            reason: EarlyStopReason,
            initialScore: Float = Float.MAX_VALUE,
        ): StabilizationResult = StabilizationResult(
            success = false,
            finalScore = StabilizationScore(
                value = initialScore,
                leftEyeDistance = 0f,
                rightEyeDistance = 0f,
            ),
            passesExecuted = 0,
            passes = emptyList(),
            mode = mode,
            earlyStopReason = reason,
            totalDurationMs = 0L,
            initialScore = initialScore,
        )
    }
}
