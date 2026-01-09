package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for platform-specific feature detection and matching.
 *
 * Implementations will use OpenCV:
 * - Android: OpenCV Android SDK (opencv-android)
 * - iOS: OpenCV iOS CocoaPod with Kotlin/Native interop
 *
 * Feature matching enables alignment of landscape/scenery images
 * where face or body landmarks are not available.
 */
interface FeatureMatcher {

    /**
     * Whether feature matching is available on this device.
     * Returns false if OpenCV failed to initialize.
     */
    val isAvailable: Boolean

    /**
     * Detects feature keypoints in an image.
     *
     * Uses ORB or AKAZE algorithm to find distinctive points
     * that can be reliably matched across frames.
     *
     * @param imageData The image to analyze.
     * @param detectorType The feature detector algorithm to use.
     * @param maxKeypoints Maximum number of keypoints to detect.
     * @return Result containing LandscapeLandmarks or an error.
     */
    suspend fun detectFeatures(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks>

    /**
     * Detects feature keypoints from an image file path.
     *
     * @param imagePath Path to the image file.
     * @param detectorType The feature detector algorithm to use.
     * @param maxKeypoints Maximum number of keypoints to detect.
     * @return Result containing LandscapeLandmarks or an error.
     */
    suspend fun detectFeaturesFromPath(
        imagePath: String,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks>

    /**
     * Matches features between two sets of landmarks.
     *
     * Uses brute-force matching with Lowe's ratio test to filter
     * good matches and reject ambiguous ones.
     *
     * @param sourceFeatures Keypoints from the source (current) image.
     * @param referenceFeatures Keypoints from the reference (target) image.
     * @param ratioTestThreshold Lowe's ratio test threshold (0.5-0.95).
     * @param useCrossCheck If true, only accept matches where both descriptors
     *                      are each other's best match.
     * @return Result containing list of matched keypoint pairs (sourceIdx, refIdx).
     */
    suspend fun matchFeatures(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>>

    /**
     * Computes a homography matrix from matched keypoint pairs using RANSAC.
     *
     * The homography transforms points from the source image coordinate system
     * to the reference image coordinate system.
     *
     * @param sourceKeypoints Keypoints from the source image.
     * @param referenceKeypoints Keypoints from the reference image.
     * @param matches List of matched indices (sourceIdx, refIdx).
     * @param ransacThreshold RANSAC reprojection error threshold in pixels.
     * @return Result containing pair of (HomographyMatrix, inlierCount).
     */
    suspend fun computeHomography(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>>

    /**
     * Convenience method that combines detection, matching, and homography.
     *
     * Performs the full pipeline for aligning a source image to a reference:
     * 1. Detect features in both images
     * 2. Match features between images
     * 3. Compute homography using RANSAC
     *
     * @param sourceImageData The source image to align.
     * @param referenceImageData The reference image to align to.
     * @param detectorType The feature detector algorithm to use.
     * @param maxKeypoints Maximum keypoints to detect per image.
     * @param ratioTestThreshold Lowe's ratio test threshold.
     * @param ransacThreshold RANSAC reprojection threshold in pixels.
     * @return Result containing HomographyMatrix and quality metrics.
     */
    suspend fun findHomography(
        sourceImageData: ImageData,
        referenceImageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        ratioTestThreshold: Float,
        ransacThreshold: Float,
    ): Result<FeatureMatchResult>

    /**
     * Calculates the mean reprojection error for a set of matches given a homography.
     *
     * For each matched pair, transforms the source point using the homography
     * and measures the distance to the corresponding reference point.
     *
     * @param sourceKeypoints Keypoints from the source image.
     * @param referenceKeypoints Keypoints from the reference image.
     * @param matches List of matched indices (sourceIdx, refIdx).
     * @param homography The homography matrix to evaluate.
     * @param imageWidth Width of the image (for coordinate conversion).
     * @param imageHeight Height of the image (for coordinate conversion).
     * @return Result containing ReprojectionErrorResult with mean error and inlier info.
     */
    suspend fun calculateReprojectionError(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        homography: HomographyMatrix,
        imageWidth: Int,
        imageHeight: Int,
    ): Result<ReprojectionErrorResult>

    /**
     * Releases resources held by the feature matcher.
     * Should be called when the matcher is no longer needed.
     */
    fun release()
}

/**
 * Result of a complete feature matching and homography computation.
 */
data class FeatureMatchResult(
    /** The computed homography matrix. */
    val homography: HomographyMatrix,

    /** Landmarks detected in the source image. */
    val sourceLandmarks: LandscapeLandmarks,

    /** Landmarks detected in the reference image. */
    val referenceLandmarks: LandscapeLandmarks,

    /** Number of matched keypoint pairs. */
    val matchCount: Int,

    /** Number of inliers after RANSAC. */
    val inlierCount: Int,

    /** Confidence score based on match quality (0.0 to 1.0). */
    val confidence: Float,
) {
    /** Inlier ratio = inliers / total matches. */
    val inlierRatio: Float
        get() = if (matchCount > 0) inlierCount.toFloat() / matchCount else 0f

    /** Whether this result meets minimum quality thresholds. */
    fun isValid(minInlierRatio: Float = 0.3f, minMatches: Int = 10): Boolean =
        matchCount >= minMatches && inlierRatio >= minInlierRatio && homography.isValid()
}

/**
 * Result of reprojection error calculation.
 */
data class ReprojectionErrorResult(
    /** Mean reprojection error across all matches in pixels. */
    val meanError: Float,

    /** Median reprojection error in pixels. */
    val medianError: Float,

    /** Maximum reprojection error in pixels. */
    val maxError: Float,

    /** Number of inliers (matches with error below threshold). */
    val inlierCount: Int,

    /** Total number of matches evaluated. */
    val totalMatches: Int,

    /** The threshold used for inlier classification. */
    val inlierThreshold: Float,

    /** Individual errors for each match (for advanced analysis). */
    val errors: List<Float>,
) {
    /** Inlier ratio = inliers / total matches. */
    val inlierRatio: Float
        get() = if (totalMatches > 0) inlierCount.toFloat() / totalMatches else 0f

    /** Whether the reprojection quality is acceptable. */
    fun isAcceptable(maxMeanError: Float = 2.0f, minInlierRatio: Float = 0.5f): Boolean =
        meanError <= maxMeanError && inlierRatio >= minInlierRatio
}
