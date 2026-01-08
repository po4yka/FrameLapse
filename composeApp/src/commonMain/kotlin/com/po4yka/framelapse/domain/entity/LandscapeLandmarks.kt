package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains feature keypoint data extracted from a landscape/scenery image.
 *
 * Unlike face or body landmarks which detect specific anatomical points,
 * landscape landmarks are arbitrary distinctive points (corners, edges, etc.)
 * that can be matched across frames for alignment.
 *
 * Reference points for alignment: centroids of left-half and right-half keypoints.
 * This provides a stable anchor for the transformation algorithm.
 */
@Serializable
@SerialName("landscape")
data class LandscapeLandmarks(
    /**
     * List of detected feature keypoints.
     * Each keypoint includes position, response strength, size, angle, and octave.
     */
    val keypoints: List<FeatureKeypoint>,

    /**
     * The feature detector algorithm used to extract these keypoints.
     */
    val detectorType: FeatureDetectorType,

    /**
     * Total number of keypoints detected (same as keypoints.size).
     */
    val keypointCount: Int,

    /**
     * Bounding box containing all detected keypoints.
     */
    override val boundingBox: BoundingBox,

    /**
     * Overall detection quality score (0.0 to 1.0).
     * Based on keypoint count, distribution, and response strengths.
     */
    val qualityScore: Float,
) : Landmarks {

    /**
     * Returns the centroid of keypoints in the left half of the image.
     * Used as the left reference point for alignment calculations.
     */
    override fun getReferencePointLeft(): LandmarkPoint {
        val leftKeypoints = keypoints.filter { it.position.x < HALF_POINT }
        return if (leftKeypoints.isNotEmpty()) {
            LandmarkPoint(
                x = leftKeypoints.map { it.position.x }.average().toFloat(),
                y = leftKeypoints.map { it.position.y }.average().toFloat(),
            )
        } else {
            // Fallback: left quarter of image center
            LandmarkPoint(LEFT_FALLBACK_X, CENTER_Y)
        }
    }

    /**
     * Returns the centroid of keypoints in the right half of the image.
     * Used as the right reference point for alignment calculations.
     */
    override fun getReferencePointRight(): LandmarkPoint {
        val rightKeypoints = keypoints.filter { it.position.x >= HALF_POINT }
        return if (rightKeypoints.isNotEmpty()) {
            LandmarkPoint(
                x = rightKeypoints.map { it.position.x }.average().toFloat(),
                y = rightKeypoints.map { it.position.y }.average().toFloat(),
            )
        } else {
            // Fallback: right quarter of image center
            LandmarkPoint(RIGHT_FALLBACK_X, CENTER_Y)
        }
    }

    /**
     * Returns the top N keypoints by response strength.
     * Useful for matching only the most distinctive features.
     */
    fun getTopKeypoints(n: Int): List<FeatureKeypoint> =
        keypoints.sortedByDescending { it.response }.take(n)

    /**
     * Returns keypoints within a specific region of the image.
     *
     * @param left Left boundary (0-1 normalized).
     * @param top Top boundary (0-1 normalized).
     * @param right Right boundary (0-1 normalized).
     * @param bottom Bottom boundary (0-1 normalized).
     */
    fun getKeypointsInRegion(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): List<FeatureKeypoint> = keypoints.filter { kp ->
        kp.position.x in left..right && kp.position.y in top..bottom
    }

    /**
     * Checks if there are enough keypoints for reliable matching.
     */
    fun hasEnoughKeypoints(): Boolean = keypointCount >= MIN_KEYPOINTS_REQUIRED

    companion object {
        /** Minimum keypoints required for reliable homography computation. */
        const val MIN_KEYPOINTS_REQUIRED = 10

        /** Recommended number of keypoints for good matching quality. */
        const val RECOMMENDED_KEYPOINTS = 500

        /** Maximum keypoints to store (to limit memory usage). */
        const val MAX_KEYPOINTS = 2000

        private const val HALF_POINT = 0.5f
        private const val LEFT_FALLBACK_X = 0.25f
        private const val RIGHT_FALLBACK_X = 0.75f
        private const val CENTER_Y = 0.5f
    }
}
