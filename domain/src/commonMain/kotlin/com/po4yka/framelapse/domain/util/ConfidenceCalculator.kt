package com.po4yka.framelapse.domain.util

import com.po4yka.framelapse.domain.entity.StabilizationResult

/**
 * Utility object for calculating confidence scores from stabilization results.
 *
 * This is shared between AlignFaceUseCase and AlignBodyUseCase to avoid
 * code duplication of the confidence calculation logic.
 */
object ConfidenceCalculator {
    /**
     * Calculates confidence score from stabilization result.
     *
     * Uses the final stabilization score:
     * - Score < 0.5: 1.0 confidence (perfect)
     * - Score < 20.0: 0.7-0.99 confidence (good)
     * - Score >= 20.0: 0.3-0.7 confidence (poor)
     *
     * @param result The stabilization result containing the final score.
     * @return Confidence score from 0.3 to 1.0.
     */
    fun fromStabilizationResult(result: StabilizationResult): Float {
        val score = result.finalScore.value
        return when {
            score < 0.5f -> 1.0f
            score < 20.0f -> 0.7f + (20.0f - score) / 20.0f * 0.29f
            else -> (0.7f - (score - 20.0f) / 100.0f * 0.4f).coerceAtLeast(0.3f)
        }
    }
}
