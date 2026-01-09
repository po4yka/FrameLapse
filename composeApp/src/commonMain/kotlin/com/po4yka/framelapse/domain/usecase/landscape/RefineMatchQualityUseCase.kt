package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeStabilizationSettings
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.util.Result

/**
 * Refines homography by filtering to higher-quality feature matches.
 *
 * This use case progressively filters matches based on keypoint response strength,
 * keeping only the highest quality matches for homography computation.
 *
 * ## Algorithm:
 * 1. Calculate combined response strength for each match (src.response * ref.response)
 * 2. Sort matches by combined response in descending order
 * 3. Keep top N% of matches based on current pass
 * 4. Recompute homography with filtered matches
 * 5. Check if inlier ratio improved sufficiently to continue
 *
 * ## Filtering Schedule:
 * - Pass 2: Keep top 85% of matches
 * - Pass 3: Keep top 70% of matches
 * - Pass 4: Keep top 55% of matches
 */
class RefineMatchQualityUseCase(private val featureMatcher: FeatureMatcher) {
    /**
     * Result of match quality refinement.
     */
    data class RefinementResult(
        /** The refined homography matrix. */
        val homography: HomographyMatrix,

        /** Filtered matches after quality filtering. */
        val filteredMatches: List<Pair<Int, Int>>,

        /** Number of inliers in the refined homography. */
        val inlierCount: Int,

        /** Current inlier ratio after refinement. */
        val inlierRatio: Float,

        /** Whether convergence has been reached. */
        val converged: Boolean,

        /** Previous inlier ratio for comparison. */
        val previousInlierRatio: Float,

        /** Improvement in inlier ratio from previous pass. */
        val improvement: Float,
    )

    /**
     * Performs one pass of match quality refinement.
     *
     * @param sourceKeypoints Keypoints from the source image.
     * @param referenceKeypoints Keypoints from the reference image.
     * @param currentMatches Current set of matches to filter.
     * @param previousInlierRatio Inlier ratio from the previous pass.
     * @param passNumber Current pass number (2-4 for match quality stage).
     * @param settings Stabilization settings with thresholds.
     * @return Result containing RefinementResult or an error.
     */
    suspend operator fun invoke(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        currentMatches: List<Pair<Int, Int>>,
        previousInlierRatio: Float,
        passNumber: Int,
        settings: LandscapeStabilizationSettings,
    ): Result<RefinementResult> {
        // Validate inputs
        if (sourceKeypoints.isEmpty()) {
            return Result.Error(
                IllegalArgumentException("Source keypoints list is empty"),
                "No source keypoints provided",
            )
        }

        if (referenceKeypoints.isEmpty()) {
            return Result.Error(
                IllegalArgumentException("Reference keypoints list is empty"),
                "No reference keypoints provided",
            )
        }

        if (currentMatches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
            return Result.Error(
                IllegalArgumentException("At least $MIN_MATCHES_FOR_HOMOGRAPHY matches required"),
                "Not enough matches for refinement",
            )
        }

        if (!featureMatcher.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Feature matching is not available"),
                "Feature matching not available",
            )
        }

        // Calculate the percentile to keep based on pass number
        val keepPercentile = calculateKeepPercentile(passNumber)

        // Calculate combined response strength for each match
        val matchesWithQuality = currentMatches.mapNotNull { (srcIdx, refIdx) ->
            if (srcIdx < 0 || srcIdx >= sourceKeypoints.size) return@mapNotNull null
            if (refIdx < 0 || refIdx >= referenceKeypoints.size) return@mapNotNull null

            val srcKeypoint = sourceKeypoints[srcIdx]
            val refKeypoint = referenceKeypoints[refIdx]
            val combinedResponse = srcKeypoint.response * refKeypoint.response

            MatchWithQuality(srcIdx, refIdx, combinedResponse)
        }

        if (matchesWithQuality.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
            return Result.Error(
                IllegalStateException("Too few valid matches after filtering"),
                "Not enough valid matches",
            )
        }

        // Sort by combined response (highest first) and keep top percentile
        val sortedMatches = matchesWithQuality.sortedByDescending { it.combinedResponse }
        val keepCount = (sortedMatches.size * keepPercentile).toInt()
            .coerceAtLeast(MIN_MATCHES_FOR_HOMOGRAPHY)
            .coerceAtMost(sortedMatches.size)

        val filteredMatches = sortedMatches.take(keepCount)
            .map { Pair(it.srcIdx, it.refIdx) }

        // Recompute homography with filtered matches
        val homographyResult = featureMatcher.computeHomography(
            sourceKeypoints = sourceKeypoints,
            referenceKeypoints = referenceKeypoints,
            matches = filteredMatches,
            ransacThreshold = settings.initialRansacThreshold,
        )

        if (homographyResult.isError) {
            return Result.Error(
                homographyResult.exceptionOrNull()!!,
                "Failed to compute homography with filtered matches",
            )
        }

        val (homography, inlierCount) = homographyResult.getOrNull()!!

        // Calculate new inlier ratio
        val newInlierRatio = if (filteredMatches.isNotEmpty()) {
            inlierCount.toFloat() / filteredMatches.size
        } else {
            0f
        }

        // Calculate improvement
        val improvement = newInlierRatio - previousInlierRatio

        // Check for convergence
        val converged = improvement < settings.inlierRatioImprovementThreshold

        return Result.Success(
            RefinementResult(
                homography = homography,
                filteredMatches = filteredMatches,
                inlierCount = inlierCount,
                inlierRatio = newInlierRatio,
                converged = converged,
                previousInlierRatio = previousInlierRatio,
                improvement = improvement,
            ),
        )
    }

    /**
     * Calculates the percentile of matches to keep based on pass number.
     *
     * - Pass 2: Keep 85%
     * - Pass 3: Keep 70%
     * - Pass 4: Keep 55%
     * - Pass 5+: Keep 40% (minimum)
     */
    private fun calculateKeepPercentile(passNumber: Int): Float = when {
        passNumber <= 1 -> 1.0f // First pass keeps all
        passNumber == 2 -> 0.85f
        passNumber == 3 -> 0.70f
        passNumber == 4 -> 0.55f
        else -> 0.40f // Minimum percentile for later passes
    }

    /**
     * Checks if match quality refinement is available.
     */
    val isAvailable: Boolean
        get() = featureMatcher.isAvailable

    private data class MatchWithQuality(val srcIdx: Int, val refIdx: Int, val combinedResponse: Float)

    companion object {
        /** Minimum number of matches required for homography computation. */
        const val MIN_MATCHES_FOR_HOMOGRAPHY = 4
    }
}
