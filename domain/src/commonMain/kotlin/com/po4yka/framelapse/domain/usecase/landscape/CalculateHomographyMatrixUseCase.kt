package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.util.Result
import kotlin.math.abs

/**
 * Computes a homography matrix from matched keypoint pairs.
 *
 * This use case wraps the FeatureMatcher service to compute a 3x3 homography
 * matrix using RANSAC (Random Sample Consensus) to handle outliers robustly.
 *
 * The homography matrix transforms points from the source image coordinate
 * system to the reference image coordinate system, enabling perspective-
 * corrected alignment of landscape/scenery images.
 */
class CalculateHomographyMatrixUseCase(private val featureMatcher: FeatureMatcher) {
    /**
     * Computes a homography matrix from matched keypoints.
     *
     * Uses RANSAC to robustly estimate the homography while rejecting outliers.
     * The resulting matrix can be used with ImageProcessor.applyHomographyTransform()
     * to warp the source image to align with the reference.
     *
     * @param sourceKeypoints Keypoints from the source image.
     * @param referenceKeypoints Keypoints from the reference image.
     * @param matches List of matched indices (sourceIdx, refIdx).
     * @param ransacThreshold RANSAC reprojection error threshold in pixels.
     *                        Points with error above this are considered outliers.
     * @return Result containing pair of (HomographyMatrix, inlierCount).
     */
    suspend operator fun invoke(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float = DEFAULT_RANSAC_THRESHOLD,
    ): Result<Pair<HomographyMatrix, Int>> {
        // Validate input parameters
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
                IllegalArgumentException(
                    "At least $MIN_MATCHES_FOR_HOMOGRAPHY matches required, got ${matches.size}",
                ),
                "Not enough matches for homography computation",
            )
        }

        if (ransacThreshold <= 0f) {
            return Result.Error(
                IllegalArgumentException("RANSAC threshold must be positive"),
                "Invalid RANSAC threshold",
            )
        }

        // Validate match indices are within bounds
        for ((sourceIdx, refIdx) in matches) {
            if (sourceIdx < 0 || sourceIdx >= sourceKeypoints.size) {
                return Result.Error(
                    IndexOutOfBoundsException("Source index $sourceIdx out of bounds"),
                    "Invalid match index in source keypoints",
                )
            }
            if (refIdx < 0 || refIdx >= referenceKeypoints.size) {
                return Result.Error(
                    IndexOutOfBoundsException("Reference index $refIdx out of bounds"),
                    "Invalid match index in reference keypoints",
                )
            }
        }

        // Check if feature matching is available
        if (!featureMatcher.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Feature matching is not available on this device"),
                "Feature matching not available",
            )
        }

        // Compute homography using RANSAC
        val homographyResult = featureMatcher.computeHomography(
            sourceKeypoints = sourceKeypoints,
            referenceKeypoints = referenceKeypoints,
            matches = matches,
            ransacThreshold = ransacThreshold,
        )

        if (homographyResult.isError) {
            return homographyResult
        }

        val (homography, inlierCount) = homographyResult.getOrNull()!!

        // Validate homography matrix is valid (non-singular)
        if (!homography.isValid()) {
            return Result.Error(
                IllegalStateException(
                    "Computed homography is singular (determinant: ${homography.determinant()})",
                ),
                "Invalid homography matrix computed",
            )
        }

        // Validate determinant is within reasonable bounds (not too extreme transformation)
        val determinant = abs(homography.determinant())
        if (determinant < MIN_DETERMINANT || determinant > MAX_DETERMINANT) {
            return Result.Error(
                IllegalStateException(
                    "Homography determinant out of acceptable range: $determinant " +
                        "(expected: $MIN_DETERMINANT to $MAX_DETERMINANT)",
                ),
                "Homography represents an extreme transformation",
            )
        }

        // Validate we have enough inliers for a reliable homography
        val inlierRatio = inlierCount.toFloat() / matches.size
        if (inlierRatio < MIN_INLIER_RATIO) {
            return Result.Error(
                IllegalStateException(
                    "Insufficient inliers: $inlierCount of ${matches.size} " +
                        "(ratio: ${(inlierRatio * 100).toInt() / 100f}, minimum: $MIN_INLIER_RATIO)",
                ),
                "Too many outliers in match set",
            )
        }

        return Result.Success(Pair(homography, inlierCount))
    }

    /**
     * Checks if homography computation is available.
     */
    val isAvailable: Boolean
        get() = featureMatcher.isAvailable

    companion object {
        /** Default RANSAC reprojection threshold in pixels. */
        const val DEFAULT_RANSAC_THRESHOLD = 5.0f

        /** Minimum number of point correspondences needed for homography. */
        const val MIN_MATCHES_FOR_HOMOGRAPHY = 4

        /** Minimum acceptable determinant value. */
        const val MIN_DETERMINANT = 0.01f

        /** Maximum acceptable determinant value. */
        const val MAX_DETERMINANT = 100.0f

        /** Minimum inlier ratio for a valid homography. */
        const val MIN_INLIER_RATIO = 0.2f
    }
}
