package com.po4yka.framelapse.domain.entity

/**
 * Stabilization mode for multi-pass face alignment.
 */
enum class StabilizationMode {
    /**
     * Fast mode: Translation-only refinement.
     * - Maximum 4 passes
     * - Pass 1: Full alignment (rotation + scale + translation)
     * - Passes 2-4: Translation correction only
     * - Best for quick processing with good results
     */
    FAST,

    /**
     * Slow mode: Full affine refinement.
     * - Maximum 10 passes (+ 1 optional cleanup)
     * - Passes 1-4: Rotation refinement
     * - Passes 5-7: Scale refinement
     * - Passes 8-10: Translation refinement
     * - Pass 11: Optional cleanup if needed
     * - Best for highest quality alignment
     */
    SLOW,
}
