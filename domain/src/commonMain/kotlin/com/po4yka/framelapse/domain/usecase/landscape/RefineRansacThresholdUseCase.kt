package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeStabilizationSettings
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.util.Result
import org.koin.core.annotation.Factory

/**
 * Refines homography by progressively tightening RANSAC reprojection threshold.
 *
 * This use case reduces the RANSAC threshold iteratively to obtain a more
 * accurate homography by being stricter about what constitutes an inlier.
 *
 * ## Algorithm:
 * 1. Reduce RANSAC threshold by a factor each pass
 * 2. Recompute homography with tighter threshold
 * 3. Calculate mean reprojection error for inliers
 * 4. Check if mean error is below target threshold
 *
 * ## Threshold Schedule:
 * Starting from 5.0px, each pass multiplies by reduction factor (0.6):
 * - Pass 5: 5.0 * 0.6 = 3.0px
 * - Pass 6: 3.0 * 0.6 = 1.8px
 * - Pass 7: 1.8px (or minThreshold if lower)
 */
@Factory
class RefineRansacThresholdUseCase(private val featureMatcher: FeatureMatcher) {
    /**
     * Result of RANSAC threshold refinement.
     */
    data class RefinementResult(
        /** The refined homography matrix. */
        val homography: HomographyMatrix,

        /** Number of inliers with the tighter threshold. */
        val inlierCount: Int,

        /** Mean reprojection error of inliers in pixels. */
        val meanReprojectionError: Float,

        /** The RANSAC threshold used for this pass. */
        val ransacThreshold: Float,

        /** Whether convergence has been reached. */
        val converged: Boolean,

        /** Inlier ratio after refinement. */
        val inlierRatio: Float,
    )

    /**
     * Performs one pass of RANSAC threshold refinement.
     *
     * @param sourceKeypoints Keypoints from the source image.
     * @param referenceKeypoints Keypoints from the reference image.
     * @param matches Current set of matches.
     * @param previousThreshold RANSAC threshold from the previous pass.
     * @param settings Stabilization settings with thresholds.
     * @param imageWidth Image width for coordinate conversion.
     * @param imageHeight Image height for coordinate conversion.
     * @return Result containing RefinementResult or an error.
     */
    suspend operator fun invoke(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        previousThreshold: Float,
        settings: LandscapeStabilizationSettings,
        imageWidth: Int,
        imageHeight: Int,
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

        if (matches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
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

        // Calculate new threshold
        val newThreshold = (previousThreshold * settings.ransacThresholdReductionFactor)
            .coerceAtLeast(settings.minRansacThreshold)

        // Compute homography with tighter threshold
        val homographyResult = featureMatcher.computeHomography(
            sourceKeypoints = sourceKeypoints,
            referenceKeypoints = referenceKeypoints,
            matches = matches,
            ransacThreshold = newThreshold,
        )

        if (homographyResult.isError) {
            return Result.Error(
                homographyResult.exceptionOrNull()!!,
                "Failed to compute homography with threshold $newThreshold",
            )
        }

        val (homography, inlierCount) = homographyResult.getOrNull()!!

        // Calculate reprojection error
        val reprojResult = featureMatcher.calculateReprojectionError(
            sourceKeypoints = sourceKeypoints,
            referenceKeypoints = referenceKeypoints,
            matches = matches,
            homography = homography,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )

        val meanError = if (reprojResult.isSuccess) {
            reprojResult.getOrNull()!!.meanError
        } else {
            // Fall back to estimating error from threshold
            newThreshold / 2
        }

        // Calculate inlier ratio
        val inlierRatio = if (matches.isNotEmpty()) {
            inlierCount.toFloat() / matches.size
        } else {
            0f
        }

        // Check for convergence
        val converged = meanError < settings.meanReprojErrorThreshold ||
            newThreshold <= settings.minRansacThreshold

        return Result.Success(
            RefinementResult(
                homography = homography,
                inlierCount = inlierCount,
                meanReprojectionError = meanError,
                ransacThreshold = newThreshold,
                converged = converged,
                inlierRatio = inlierRatio,
            ),
        )
    }

    /**
     * Checks if RANSAC threshold refinement is available.
     */
    val isAvailable: Boolean
        get() = featureMatcher.isAvailable

    companion object {
        /** Minimum number of matches required for homography computation. */
        const val MIN_MATCHES_FOR_HOMOGRAPHY = 4
    }
}
