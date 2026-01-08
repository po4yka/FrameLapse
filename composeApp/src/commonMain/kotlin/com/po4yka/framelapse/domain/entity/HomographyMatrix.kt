package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * 3x3 homography matrix for perspective transformation.
 *
 * A homography is a projective transformation that maps points from one
 * plane to another. It can represent rotation, translation, scaling,
 * shearing, and perspective distortion.
 *
 * Matrix format:
 * ```
 * | h11  h12  h13 |   | x |   | x' * w' |
 * | h21  h22  h23 | * | y | = | y' * w' |
 * | h31  h32  h33 |   | 1 |   |   w'    |
 * ```
 *
 * Final coordinates: (x'/w', y'/w') where w' = h31*x + h32*y + h33
 *
 * Unlike an affine transformation (2x3), a homography can handle
 * perspective distortion (vanishing points, foreshortening).
 */
@Serializable
data class HomographyMatrix(
    val h11: Float, val h12: Float, val h13: Float,
    val h21: Float, val h22: Float, val h23: Float,
    val h31: Float, val h32: Float, val h33: Float,
) {
    /**
     * Returns the matrix as a flat FloatArray in row-major order.
     * Suitable for passing to OpenCV warpPerspective().
     */
    fun toFloatArray(): FloatArray = floatArrayOf(
        h11, h12, h13,
        h21, h22, h23,
        h31, h32, h33,
    )

    /**
     * Returns the matrix as a 2D array.
     */
    fun to2DArray(): Array<FloatArray> = arrayOf(
        floatArrayOf(h11, h12, h13),
        floatArrayOf(h21, h22, h23),
        floatArrayOf(h31, h32, h33),
    )

    /**
     * Transforms a point using this homography.
     *
     * @param x X coordinate of the source point.
     * @param y Y coordinate of the source point.
     * @return Pair of (x', y') transformed coordinates.
     */
    fun transformPoint(x: Float, y: Float): Pair<Float, Float> {
        val w = h31 * x + h32 * y + h33
        if (abs(w) < EPSILON) {
            // Point at infinity or degenerate case
            return Pair(Float.MAX_VALUE, Float.MAX_VALUE)
        }
        val xPrime = (h11 * x + h12 * y + h13) / w
        val yPrime = (h21 * x + h22 * y + h23) / w
        return Pair(xPrime, yPrime)
    }

    /**
     * Transforms multiple points using this homography.
     *
     * @param points List of (x, y) coordinate pairs.
     * @return List of transformed (x', y') coordinate pairs.
     */
    fun transformPoints(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> =
        points.map { (x, y) -> transformPoint(x, y) }

    /**
     * Checks if this is approximately an identity matrix (no transformation needed).
     *
     * @param tolerance Maximum allowed deviation from identity values.
     */
    fun isNearIdentity(tolerance: Float = DEFAULT_IDENTITY_TOLERANCE): Boolean =
        abs(h11 - 1f) < tolerance &&
            abs(h12) < tolerance &&
            abs(h13) < tolerance &&
            abs(h21) < tolerance &&
            abs(h22 - 1f) < tolerance &&
            abs(h23) < tolerance &&
            abs(h31) < tolerance &&
            abs(h32) < tolerance &&
            abs(h33 - 1f) < tolerance

    /**
     * Computes the determinant of the matrix.
     * A zero determinant indicates a degenerate/singular matrix.
     */
    fun determinant(): Float =
        h11 * (h22 * h33 - h23 * h32) -
            h12 * (h21 * h33 - h23 * h31) +
            h13 * (h21 * h32 - h22 * h31)

    /**
     * Checks if this matrix is valid (non-singular).
     */
    fun isValid(): Boolean = abs(determinant()) > EPSILON

    /**
     * Extracts the approximate rotation angle in degrees.
     * Note: This is an approximation that ignores perspective distortion.
     */
    fun approximateRotationDegrees(): Float {
        val radians = kotlin.math.atan2(h21.toDouble(), h11.toDouble())
        return Math.toDegrees(radians).toFloat()
    }

    /**
     * Extracts the approximate scale factor.
     * Note: This is an approximation based on the first column.
     */
    fun approximateScale(): Float =
        kotlin.math.sqrt((h11 * h11 + h21 * h21).toDouble()).toFloat()

    companion object {
        private const val EPSILON = 1e-6f
        private const val DEFAULT_IDENTITY_TOLERANCE = 0.01f

        /**
         * Identity homography (no transformation).
         */
        val IDENTITY = HomographyMatrix(
            h11 = 1f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 1f, h23 = 0f,
            h31 = 0f, h32 = 0f, h33 = 1f,
        )

        /**
         * Creates a HomographyMatrix from a flat FloatArray.
         *
         * @param values Array of 9 values in row-major order.
         * @throws IllegalArgumentException if values.size != 9.
         */
        fun fromFloatArray(values: FloatArray): HomographyMatrix {
            require(values.size == 9) { "Homography matrix requires exactly 9 values" }
            return HomographyMatrix(
                h11 = values[0], h12 = values[1], h13 = values[2],
                h21 = values[3], h22 = values[4], h23 = values[5],
                h31 = values[6], h32 = values[7], h33 = values[8],
            )
        }

        /**
         * Creates a HomographyMatrix from a DoubleArray (OpenCV format).
         *
         * @param values Array of 9 values in row-major order.
         * @throws IllegalArgumentException if values.size != 9.
         */
        fun fromDoubleArray(values: DoubleArray): HomographyMatrix {
            require(values.size == 9) { "Homography matrix requires exactly 9 values" }
            return HomographyMatrix(
                h11 = values[0].toFloat(), h12 = values[1].toFloat(), h13 = values[2].toFloat(),
                h21 = values[3].toFloat(), h22 = values[4].toFloat(), h23 = values[5].toFloat(),
                h31 = values[6].toFloat(), h32 = values[7].toFloat(), h33 = values[8].toFloat(),
            )
        }

        /**
         * Creates a simple translation homography.
         *
         * @param tx Translation in X direction.
         * @param ty Translation in Y direction.
         */
        fun translation(tx: Float, ty: Float): HomographyMatrix = HomographyMatrix(
            h11 = 1f, h12 = 0f, h13 = tx,
            h21 = 0f, h22 = 1f, h23 = ty,
            h31 = 0f, h32 = 0f, h33 = 1f,
        )

        /**
         * Creates a simple scaling homography.
         *
         * @param sx Scale factor in X direction.
         * @param sy Scale factor in Y direction.
         */
        fun scale(sx: Float, sy: Float): HomographyMatrix = HomographyMatrix(
            h11 = sx, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = sy, h23 = 0f,
            h31 = 0f, h32 = 0f, h33 = 1f,
        )

        /**
         * Creates a rotation homography around the origin.
         *
         * @param degrees Rotation angle in degrees (counterclockwise positive).
         */
        fun rotation(degrees: Float): HomographyMatrix {
            val radians = Math.toRadians(degrees.toDouble())
            val cos = kotlin.math.cos(radians).toFloat()
            val sin = kotlin.math.sin(radians).toFloat()
            return HomographyMatrix(
                h11 = cos, h12 = -sin, h13 = 0f,
                h21 = sin, h22 = cos, h23 = 0f,
                h31 = 0f, h32 = 0f, h33 = 1f,
            )
        }
    }
}
