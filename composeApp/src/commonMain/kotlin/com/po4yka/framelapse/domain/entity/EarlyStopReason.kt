package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Reasons for early termination of multi-pass stabilization.
 */
@Serializable
enum class EarlyStopReason {
    /**
     * Score is already below the no-action threshold (< 0.5).
     * No further correction is needed.
     */
    SCORE_BELOW_THRESHOLD,

    /**
     * Score did not improve from the previous pass.
     * Continuing would not yield better results.
     */
    NO_IMPROVEMENT,

    /**
     * Rotation has converged (eye delta Y <= 0.1 pixels).
     * SLOW mode rotation refinement complete.
     */
    ROTATION_CONVERGED,

    /**
     * Scale has converged (eye distance error <= 1.0 pixel).
     * SLOW mode scale refinement complete.
     */
    SCALE_CONVERGED,

    /**
     * Translation has converged (score improvement < 0.05).
     * Translation refinement complete.
     */
    TRANSLATION_CONVERGED,

    /**
     * Maximum number of passes reached.
     * Algorithm terminated due to pass limit.
     */
    MAX_PASSES_REACHED,

    /**
     * Face detection failed during a refinement pass.
     * Cannot continue without detected landmarks.
     */
    FACE_DETECTION_FAILED,
}
