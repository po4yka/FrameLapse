package com.po4yka.framelapse.domain.entity

/**
 * Stages of the multi-pass stabilization algorithm.
 */
enum class StabilizationStage {
    /**
     * Initial full alignment pass.
     * Applies rotation, scale, and translation based on detected landmarks.
     */
    INITIAL,

    /**
     * Rotation refinement stage (SLOW mode only).
     * Iteratively adjusts rotation until eye delta Y <= 0.1 pixels.
     */
    ROTATION_REFINE,

    /**
     * Scale refinement stage (SLOW mode only).
     * Iteratively adjusts scale until eye distance error <= 1.0 pixel.
     */
    SCALE_REFINE,

    /**
     * Translation refinement stage (both FAST and SLOW modes).
     * Corrects overshoot in eye positions.
     */
    TRANSLATION_REFINE,

    /**
     * Cleanup pass (SLOW mode only).
     * Final refinement if score is still above success threshold.
     */
    CLEANUP,

    // ===== Landscape-specific stages =====

    /**
     * Match quality refinement stage (Landscape SLOW mode).
     * Progressively filters to higher-quality feature matches.
     */
    MATCH_QUALITY_REFINE,

    /**
     * RANSAC threshold refinement stage (Landscape SLOW mode).
     * Progressively tightens reprojection error tolerance.
     */
    RANSAC_THRESHOLD_REFINE,

    /**
     * Perspective stability refinement stage (Landscape SLOW mode).
     * Validates and corrects extreme perspective distortions.
     */
    PERSPECTIVE_STABILITY_REFINE,
}
