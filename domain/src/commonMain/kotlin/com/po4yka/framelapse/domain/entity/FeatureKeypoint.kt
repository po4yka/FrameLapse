package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * A single feature keypoint detected in an image.
 *
 * Keypoints are distinctive points in an image that can be reliably
 * detected across different views of the same scene.
 */
@Serializable
data class FeatureKeypoint(
    /**
     * Position of the keypoint in normalized coordinates (0-1).
     * x=0 is left edge, x=1 is right edge.
     * y=0 is top edge, y=1 is bottom edge.
     */
    val position: LandmarkPoint,

    /**
     * Response strength of the keypoint.
     * Higher values indicate more distinctive/reliable keypoints.
     */
    val response: Float,

    /**
     * Size/scale of the keypoint region in pixels.
     * Represents the diameter of the meaningful keypoint neighborhood.
     */
    val size: Float,

    /**
     * Orientation angle in degrees (0-360).
     * Represents the dominant gradient direction at the keypoint.
     */
    val angle: Float,

    /**
     * Octave (pyramid level) where the keypoint was detected.
     * Higher octaves detect features at larger scales.
     */
    val octave: Int,
) {
    /**
     * Converts the keypoint position to pixel coordinates.
     *
     * @param imageWidth Width of the image in pixels.
     * @param imageHeight Height of the image in pixels.
     * @return Pair of (x, y) pixel coordinates.
     */
    fun toPixelCoordinates(imageWidth: Int, imageHeight: Int): Pair<Float, Float> =
        Pair(position.x * imageWidth, position.y * imageHeight)

    companion object {
        /**
         * Creates a keypoint from pixel coordinates.
         *
         * @param x X coordinate in pixels.
         * @param y Y coordinate in pixels.
         * @param imageWidth Width of the image in pixels.
         * @param imageHeight Height of the image in pixels.
         * @param response Response strength of the keypoint.
         * @param size Size/scale of the keypoint.
         * @param angle Orientation angle in degrees.
         * @param octave Pyramid level where detected.
         * @return A FeatureKeypoint with normalized coordinates.
         */
        fun fromPixelCoordinates(
            x: Float,
            y: Float,
            imageWidth: Int,
            imageHeight: Int,
            response: Float = 0f,
            size: Float = 0f,
            angle: Float = 0f,
            octave: Int = 0,
        ): FeatureKeypoint = FeatureKeypoint(
            position = LandmarkPoint(
                x = x / imageWidth,
                y = y / imageHeight,
            ),
            response = response,
            size = size,
            angle = angle,
            octave = octave,
        )
    }
}
