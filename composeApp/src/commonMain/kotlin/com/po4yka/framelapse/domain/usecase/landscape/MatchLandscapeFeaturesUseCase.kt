package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.util.Result

/**
 * Matches feature keypoints between two landscape images.
 *
 * This use case wraps the FeatureMatcher service to find corresponding
 * keypoints between a source image and a reference image. The matches
 * are filtered using Lowe's ratio test to reject ambiguous correspondences.
 *
 * The matched keypoint pairs can then be used to compute a homography
 * matrix for perspective-corrected alignment.
 */
class MatchLandscapeFeaturesUseCase(private val featureMatcher: FeatureMatcher) {
    /**
     * Matches features between source and reference landmarks.
     *
     * Uses brute-force matching with optional ratio test and cross-check
     * to find reliable correspondences between keypoint sets.
     *
     * @param sourceLandmarks Keypoints from the source (current) image.
     * @param referenceLandmarks Keypoints from the reference (target) image.
     * @param ratioTestThreshold Lowe's ratio test threshold (0.5-0.95).
     *                           Lower values = stricter matching = fewer but better matches.
     * @param useCrossCheck If true, only accept matches where both descriptors
     *                      are each other's best match. Slower but more accurate.
     * @param minMatchCount Minimum number of matches required for valid result.
     * @return Result containing list of matched keypoint index pairs (sourceIdx, refIdx).
     */
    suspend operator fun invoke(
        sourceLandmarks: LandscapeLandmarks,
        referenceLandmarks: LandscapeLandmarks,
        ratioTestThreshold: Float = DEFAULT_RATIO_TEST_THRESHOLD,
        useCrossCheck: Boolean = DEFAULT_USE_CROSS_CHECK,
        minMatchCount: Int = DEFAULT_MIN_MATCH_COUNT,
    ): Result<List<Pair<Int, Int>>> {
        // Validate input parameters
        if (sourceLandmarks.keypointCount == 0) {
            return Result.Error(
                IllegalArgumentException("Source landmarks have no keypoints"),
                "Source image has no detected features",
            )
        }

        if (referenceLandmarks.keypointCount == 0) {
            return Result.Error(
                IllegalArgumentException("Reference landmarks have no keypoints"),
                "Reference image has no detected features",
            )
        }

        if (!sourceLandmarks.hasEnoughKeypoints()) {
            return Result.Error(
                IllegalArgumentException("Source image does not have enough keypoints"),
                "Not enough keypoints in source image",
            )
        }

        if (!referenceLandmarks.hasEnoughKeypoints()) {
            return Result.Error(
                IllegalArgumentException("Reference image does not have enough keypoints"),
                "Not enough keypoints in reference image",
            )
        }

        if (ratioTestThreshold !in RATIO_TEST_MIN..RATIO_TEST_MAX) {
            return Result.Error(
                IllegalArgumentException(
                    "Ratio test threshold must be between $RATIO_TEST_MIN and $RATIO_TEST_MAX",
                ),
                "Invalid ratio test threshold",
            )
        }

        if (minMatchCount < MIN_MATCHES_FOR_HOMOGRAPHY) {
            return Result.Error(
                IllegalArgumentException(
                    "Minimum match count must be at least $MIN_MATCHES_FOR_HOMOGRAPHY for homography",
                ),
                "Minimum match count too low",
            )
        }

        // Check if feature matching is available
        if (!featureMatcher.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Feature matching is not available on this device"),
                "Feature matching not available",
            )
        }

        // Perform feature matching
        val matchResult = featureMatcher.matchFeatures(
            sourceFeatures = sourceLandmarks,
            referenceFeatures = referenceLandmarks,
            ratioTestThreshold = ratioTestThreshold,
            useCrossCheck = useCrossCheck,
        )

        if (matchResult.isError) {
            return matchResult
        }

        val matches = matchResult.getOrNull()!!

        // Validate minimum match count
        if (matches.size < minMatchCount) {
            return Result.Error(
                IllegalStateException(
                    "Insufficient matches found: ${matches.size} (minimum required: $minMatchCount)",
                ),
                "Not enough feature matches between images",
            )
        }

        return Result.Success(matches)
    }

    /**
     * Checks if feature matching is available.
     */
    val isAvailable: Boolean
        get() = featureMatcher.isAvailable

    companion object {
        /** Default Lowe's ratio test threshold. */
        const val DEFAULT_RATIO_TEST_THRESHOLD = 0.75f

        /** Default cross-check setting. */
        const val DEFAULT_USE_CROSS_CHECK = true

        /** Default minimum match count. */
        const val DEFAULT_MIN_MATCH_COUNT = 10

        /** Minimum number of matches required for homography computation. */
        const val MIN_MATCHES_FOR_HOMOGRAPHY = 4

        /** Minimum valid ratio test threshold. */
        const val RATIO_TEST_MIN = 0.5f

        /** Maximum valid ratio test threshold. */
        const val RATIO_TEST_MAX = 0.95f
    }
}
