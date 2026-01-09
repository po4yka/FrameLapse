package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration settings for multi-pass landscape stabilization algorithm.
 *
 * Unlike face/body stabilization which uses anatomical landmarks, landscape
 * stabilization refines homography through:
 * - Match quality filtering
 * - RANSAC threshold tightening
 * - Perspective stability validation
 */
@Serializable
data class LandscapeStabilizationSettings(
    /**
     * Stabilization mode (FAST or SLOW).
     * FAST: Single-pass homography computation.
     * SLOW: Multi-pass iterative refinement.
     */
    val mode: StabilizationMode = StabilizationMode.FAST,

    // ===== Match Quality Refinement Settings =====

    /**
     * Minimum percentile of matches to keep based on quality.
     * Lower value = keep only highest quality matches.
     * Default: 0.5 (keep top 50% of matches by response strength)
     */
    val minMatchQualityPercentile: Float = DEFAULT_MIN_MATCH_QUALITY_PERCENTILE,

    /**
     * Minimum inlier ratio improvement between passes to continue.
     * If improvement < this value, convergence is declared.
     * Default: 0.01 (1% improvement required)
     */
    val inlierRatioImprovementThreshold: Float = DEFAULT_INLIER_RATIO_IMPROVEMENT_THRESHOLD,

    // ===== RANSAC Threshold Refinement Settings =====

    /**
     * Target mean reprojection error in pixels.
     * RANSAC refinement stops when mean error < this value.
     * Default: 1.0 pixel
     */
    val meanReprojErrorThreshold: Float = DEFAULT_MEAN_REPROJ_ERROR_THRESHOLD,

    /**
     * Initial RANSAC reprojection threshold in pixels.
     * Default: 5.0 pixels
     */
    val initialRansacThreshold: Float = DEFAULT_INITIAL_RANSAC_THRESHOLD,

    /**
     * Minimum RANSAC reprojection threshold in pixels.
     * Threshold won't be reduced below this value.
     * Default: 1.5 pixels
     */
    val minRansacThreshold: Float = DEFAULT_MIN_RANSAC_THRESHOLD,

    /**
     * RANSAC threshold reduction factor per pass.
     * newThreshold = oldThreshold * reductionFactor
     * Default: 0.6 (reduces by 40% each pass)
     */
    val ransacThresholdReductionFactor: Float = DEFAULT_RANSAC_THRESHOLD_REDUCTION_FACTOR,

    // ===== Perspective Stability Settings =====

    /**
     * Minimum acceptable homography determinant.
     * Determinants below this indicate excessive shrinking/flipping.
     * Default: 0.5
     */
    val minDeterminant: Float = DEFAULT_MIN_DETERMINANT,

    /**
     * Maximum acceptable homography determinant.
     * Determinants above this indicate excessive scaling.
     * Default: 2.0
     */
    val maxDeterminant: Float = DEFAULT_MAX_DETERMINANT,

    /**
     * Maximum acceptable rotation in degrees.
     * Rotations beyond this are considered excessive.
     * Default: 45.0 degrees
     */
    val maxRotationDegrees: Float = DEFAULT_MAX_ROTATION_DEGREES,

    /**
     * Maximum acceptable scale factor.
     * Default: 2.0 (200% scaling)
     */
    val maxScaleFactor: Float = DEFAULT_MAX_SCALE_FACTOR,

    /**
     * Minimum acceptable scale factor.
     * Default: 0.5 (50% scaling)
     */
    val minScaleFactor: Float = DEFAULT_MIN_SCALE_FACTOR,

    /**
     * Determinant change threshold for perspective convergence.
     * Convergence when |current_det - previous_det| < this value.
     * Default: 0.01
     */
    val determinantChangeThreshold: Float = DEFAULT_DETERMINANT_CHANGE_THRESHOLD,

    /**
     * Blend factor for mixing with identity when perspective is extreme.
     * 0 = full identity, 1 = full computed homography.
     * Default: 0.5 (50% blend)
     */
    val perspectiveBlendFactor: Float = DEFAULT_PERSPECTIVE_BLEND_FACTOR,

    // ===== General Settings =====

    /**
     * Minimum confidence threshold for success.
     * Default: 0.7 (70% confidence)
     */
    val successConfidenceThreshold: Float = DEFAULT_SUCCESS_CONFIDENCE_THRESHOLD,

    /**
     * General convergence threshold for score improvement.
     * Default: 0.05
     */
    val convergenceThreshold: Float = DEFAULT_CONVERGENCE_THRESHOLD,
) {
    /**
     * Maximum number of passes for the current mode.
     */
    val maxPasses: Int
        get() = when (mode) {
            StabilizationMode.FAST -> MAX_PASSES_FAST
            StabilizationMode.SLOW -> MAX_PASSES_SLOW
        }

    init {
        require(minMatchQualityPercentile in 0f..1f) {
            "minMatchQualityPercentile must be between 0 and 1"
        }
        require(inlierRatioImprovementThreshold > 0f) {
            "inlierRatioImprovementThreshold must be positive"
        }
        require(meanReprojErrorThreshold > 0f) {
            "meanReprojErrorThreshold must be positive"
        }
        require(initialRansacThreshold > 0f) {
            "initialRansacThreshold must be positive"
        }
        require(minRansacThreshold > 0f) {
            "minRansacThreshold must be positive"
        }
        require(minRansacThreshold <= initialRansacThreshold) {
            "minRansacThreshold must be <= initialRansacThreshold"
        }
        require(ransacThresholdReductionFactor in 0f..1f) {
            "ransacThresholdReductionFactor must be between 0 and 1"
        }
        require(minDeterminant > 0f) {
            "minDeterminant must be positive"
        }
        require(maxDeterminant > minDeterminant) {
            "maxDeterminant must be greater than minDeterminant"
        }
        require(maxRotationDegrees > 0f) {
            "maxRotationDegrees must be positive"
        }
        require(minScaleFactor > 0f) {
            "minScaleFactor must be positive"
        }
        require(maxScaleFactor > minScaleFactor) {
            "maxScaleFactor must be greater than minScaleFactor"
        }
        require(determinantChangeThreshold > 0f) {
            "determinantChangeThreshold must be positive"
        }
        require(perspectiveBlendFactor in 0f..1f) {
            "perspectiveBlendFactor must be between 0 and 1"
        }
        require(successConfidenceThreshold in 0f..1f) {
            "successConfidenceThreshold must be between 0 and 1"
        }
        require(convergenceThreshold > 0f) {
            "convergenceThreshold must be positive"
        }
    }

    companion object {
        // Match quality defaults
        const val DEFAULT_MIN_MATCH_QUALITY_PERCENTILE = 0.5f
        const val DEFAULT_INLIER_RATIO_IMPROVEMENT_THRESHOLD = 0.01f

        // RANSAC threshold defaults
        const val DEFAULT_MEAN_REPROJ_ERROR_THRESHOLD = 1.0f
        const val DEFAULT_INITIAL_RANSAC_THRESHOLD = 5.0f
        const val DEFAULT_MIN_RANSAC_THRESHOLD = 1.5f
        const val DEFAULT_RANSAC_THRESHOLD_REDUCTION_FACTOR = 0.6f

        // Perspective stability defaults
        const val DEFAULT_MIN_DETERMINANT = 0.5f
        const val DEFAULT_MAX_DETERMINANT = 2.0f
        const val DEFAULT_MAX_ROTATION_DEGREES = 45.0f
        const val DEFAULT_MAX_SCALE_FACTOR = 2.0f
        const val DEFAULT_MIN_SCALE_FACTOR = 0.5f
        const val DEFAULT_DETERMINANT_CHANGE_THRESHOLD = 0.01f
        const val DEFAULT_PERSPECTIVE_BLEND_FACTOR = 0.5f

        // General defaults
        const val DEFAULT_SUCCESS_CONFIDENCE_THRESHOLD = 0.7f
        const val DEFAULT_CONVERGENCE_THRESHOLD = 0.05f

        // Pass limits
        const val MAX_PASSES_FAST = 1
        const val MAX_PASSES_SLOW = 10

        /**
         * Default settings for FAST mode.
         */
        val FAST = LandscapeStabilizationSettings(
            mode = StabilizationMode.FAST,
        )

        /**
         * Default settings for SLOW mode with standard parameters.
         */
        val SLOW = LandscapeStabilizationSettings(
            mode = StabilizationMode.SLOW,
        )

        /**
         * SLOW mode settings optimized for high-quality output.
         * Uses stricter thresholds for better alignment.
         */
        val HIGH_QUALITY = LandscapeStabilizationSettings(
            mode = StabilizationMode.SLOW,
            minMatchQualityPercentile = 0.3f,
            inlierRatioImprovementThreshold = 0.005f,
            meanReprojErrorThreshold = 0.5f,
            minRansacThreshold = 1.0f,
            successConfidenceThreshold = 0.8f,
        )
    }
}
