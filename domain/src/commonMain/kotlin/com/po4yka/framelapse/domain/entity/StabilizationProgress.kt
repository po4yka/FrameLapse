package com.po4yka.framelapse.domain.entity

/**
 * Progress information for multi-pass stabilization.
 *
 * Used for reporting progress to UI during stabilization.
 */
data class StabilizationProgress(
    /**
     * Current pass number (1-indexed).
     */
    val currentPass: Int,

    /**
     * Maximum number of passes for the current mode.
     */
    val maxPasses: Int,

    /**
     * Current stabilization stage.
     */
    val currentStage: StabilizationStage,

    /**
     * Current alignment score.
     */
    val currentScore: Float,

    /**
     * Progress percentage (0.0 to 1.0).
     */
    val progressPercent: Float,

    /**
     * Human-readable status message.
     */
    val message: String,

    /**
     * The stabilization mode being used.
     */
    val mode: StabilizationMode,
) {
    /**
     * Progress as an integer percentage (0-100).
     */
    val progressPercentInt: Int
        get() = (progressPercent * 100).toInt().coerceIn(0, 100)

    companion object {
        /**
         * Creates initial progress for starting stabilization.
         */
        fun initial(mode: StabilizationMode): StabilizationProgress = StabilizationProgress(
            currentPass = 0,
            maxPasses = when (mode) {
                StabilizationMode.FAST -> StabilizationSettings.MAX_PASSES_FAST
                StabilizationMode.SLOW -> StabilizationSettings.MAX_PASSES_SLOW
            },
            currentStage = StabilizationStage.INITIAL,
            currentScore = 0f,
            progressPercent = 0f,
            message = "Starting stabilization...",
            mode = mode,
        )

        /**
         * Creates progress for a specific pass.
         */
        fun forPass(
            passNumber: Int,
            stage: StabilizationStage,
            score: Float,
            mode: StabilizationMode,
        ): StabilizationProgress {
            val maxPasses = when (mode) {
                StabilizationMode.FAST -> StabilizationSettings.MAX_PASSES_FAST
                StabilizationMode.SLOW -> StabilizationSettings.MAX_PASSES_SLOW
            }

            val message = when (stage) {
                StabilizationStage.INITIAL -> "Initial alignment..."
                StabilizationStage.ROTATION_REFINE -> "Refining rotation (pass $passNumber)..."
                StabilizationStage.SCALE_REFINE -> "Refining scale (pass $passNumber)..."
                StabilizationStage.TRANSLATION_REFINE -> "Refining position (pass $passNumber)..."
                StabilizationStage.CLEANUP -> "Final cleanup..."
                // Landscape-specific stages
                StabilizationStage.MATCH_QUALITY_REFINE -> "Refining match quality (pass $passNumber)..."
                StabilizationStage.RANSAC_THRESHOLD_REFINE -> "Tightening alignment (pass $passNumber)..."
                StabilizationStage.PERSPECTIVE_STABILITY_REFINE -> "Stabilizing perspective (pass $passNumber)..."
            }

            return StabilizationProgress(
                currentPass = passNumber,
                maxPasses = maxPasses,
                currentStage = stage,
                currentScore = score,
                progressPercent = passNumber.toFloat() / maxPasses.toFloat(),
                message = message,
                mode = mode,
            )
        }

        /**
         * Creates completion progress.
         */
        fun completed(
            finalScore: Float,
            passesExecuted: Int,
            mode: StabilizationMode,
            success: Boolean,
        ): StabilizationProgress = StabilizationProgress(
            currentPass = passesExecuted,
            maxPasses = when (mode) {
                StabilizationMode.FAST -> StabilizationSettings.MAX_PASSES_FAST
                StabilizationMode.SLOW -> StabilizationSettings.MAX_PASSES_SLOW
            },
            currentStage = StabilizationStage.CLEANUP,
            currentScore = finalScore,
            progressPercent = 1f,
            message = if (success) "Stabilization complete!" else "Stabilization failed",
            mode = mode,
        )
    }
}
