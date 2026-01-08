package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration for landscape/scenery feature matching and alignment.
 *
 * Controls the behavior of the OpenCV-based feature detection,
 * matching, and homography computation pipeline.
 */
@Serializable
data class LandscapeAlignmentSettings(
    /**
     * Feature detector algorithm to use.
     * ORB is faster, AKAZE is more robust.
     */
    val detectorType: FeatureDetectorType = DEFAULT_DETECTOR_TYPE,

    /**
     * Maximum number of keypoints to detect per image.
     * More keypoints = better matching but slower processing.
     */
    val maxKeypoints: Int = DEFAULT_MAX_KEYPOINTS,

    /**
     * Minimum number of matched keypoints required for valid homography.
     * At least 4 points are mathematically required, but more improves robustness.
     */
    val minMatchedKeypoints: Int = DEFAULT_MIN_MATCHED_KEYPOINTS,

    /**
     * Lowe's ratio test threshold for match filtering.
     * Lower = stricter matching (fewer but better matches).
     * Range: 0.5 to 0.95
     */
    val ratioTestThreshold: Float = DEFAULT_RATIO_TEST_THRESHOLD,

    /**
     * RANSAC reprojection error threshold in pixels.
     * Points with reprojection error above this are considered outliers.
     */
    val ransacReprojThreshold: Float = DEFAULT_RANSAC_REPROJ_THRESHOLD,

    /**
     * Output image size in pixels (square output).
     */
    val outputSize: Int = DEFAULT_OUTPUT_SIZE,

    /**
     * Minimum confidence threshold for considering alignment successful.
     * Based on inlier ratio and match quality.
     */
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,

    /**
     * Whether to use cross-check matching for better quality.
     * If true, a match is only accepted if both descriptors are
     * each other's best match. Slower but reduces false matches.
     */
    val useCrossCheck: Boolean = DEFAULT_USE_CROSS_CHECK,

    /**
     * Minimum inlier ratio required for valid homography.
     * Inlier ratio = (inliers / total matches).
     * Range: 0.0 to 1.0
     */
    val minInlierRatio: Float = DEFAULT_MIN_INLIER_RATIO,
) {
    init {
        require(maxKeypoints >= MIN_KEYPOINTS_LIMIT) {
            "Max keypoints must be at least $MIN_KEYPOINTS_LIMIT"
        }
        require(maxKeypoints <= MAX_KEYPOINTS_LIMIT) {
            "Max keypoints must be at most $MAX_KEYPOINTS_LIMIT"
        }
        require(minMatchedKeypoints >= MIN_MATCHES_REQUIRED) {
            "Need at least $MIN_MATCHES_REQUIRED matched keypoints for homography"
        }
        require(ratioTestThreshold in RATIO_TEST_MIN..RATIO_TEST_MAX) {
            "Ratio test threshold must be between $RATIO_TEST_MIN and $RATIO_TEST_MAX"
        }
        require(ransacReprojThreshold > 0f) {
            "RANSAC threshold must be positive"
        }
        require(outputSize in MIN_OUTPUT_SIZE..MAX_OUTPUT_SIZE) {
            "Output size must be between $MIN_OUTPUT_SIZE and $MAX_OUTPUT_SIZE"
        }
        require(minConfidence in 0f..1f) {
            "Minimum confidence must be between 0 and 1"
        }
        require(minInlierRatio in 0f..1f) {
            "Minimum inlier ratio must be between 0 and 1"
        }
    }

    companion object {
        // Default values
        val DEFAULT_DETECTOR_TYPE = FeatureDetectorType.ORB
        const val DEFAULT_MAX_KEYPOINTS = 500
        const val DEFAULT_MIN_MATCHED_KEYPOINTS = 10
        const val DEFAULT_RATIO_TEST_THRESHOLD = 0.75f
        const val DEFAULT_RANSAC_REPROJ_THRESHOLD = 5.0f
        const val DEFAULT_OUTPUT_SIZE = 1080
        const val DEFAULT_MIN_CONFIDENCE = 0.5f
        const val DEFAULT_USE_CROSS_CHECK = true
        const val DEFAULT_MIN_INLIER_RATIO = 0.3f

        // Validation limits
        const val MIN_KEYPOINTS_LIMIT = 10
        const val MAX_KEYPOINTS_LIMIT = 5000
        const val MIN_MATCHES_REQUIRED = 4
        const val RATIO_TEST_MIN = 0.5f
        const val RATIO_TEST_MAX = 0.95f
        const val MIN_OUTPUT_SIZE = 128
        const val MAX_OUTPUT_SIZE = 4096

        /**
         * Preset for fast processing (fewer keypoints, less strict matching).
         */
        val FAST = LandscapeAlignmentSettings(
            detectorType = FeatureDetectorType.ORB,
            maxKeypoints = 200,
            minMatchedKeypoints = 8,
            ratioTestThreshold = 0.8f,
            useCrossCheck = false,
        )

        /**
         * Preset for high quality (more keypoints, stricter matching).
         */
        val HIGH_QUALITY = LandscapeAlignmentSettings(
            detectorType = FeatureDetectorType.AKAZE,
            maxKeypoints = 1000,
            minMatchedKeypoints = 20,
            ratioTestThreshold = 0.7f,
            useCrossCheck = true,
            minInlierRatio = 0.5f,
        )
    }
}
