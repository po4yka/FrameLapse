package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeStabilizationSettings
import com.po4yka.framelapse.domain.util.Result
import kotlin.math.abs

/**
 * Ensures the homography doesn't introduce extreme perspective distortion.
 *
 * This use case validates and corrects homography matrices that would
 * cause unreasonable perspective transformations.
 *
 * ## Validation Criteria:
 * 1. Determinant within bounds (0.5 to 2.0) - prevents extreme scaling/flipping
 * 2. Scale factor within bounds (0.5 to 2.0) - prevents excessive zoom
 * 3. Rotation within bounds (< 45 degrees) - prevents extreme rotation
 * 4. Corner transformation forms a convex quadrilateral
 *
 * ## Correction Strategy:
 * If perspective is too extreme, blend with identity matrix to reduce distortion.
 *
 * ## Convergence:
 * Converges when determinant change between passes < threshold (0.01)
 */
class RefinePerspectiveStabilityUseCase {
    /**
     * Result of perspective stability refinement.
     */
    data class RefinementResult(
        /** The refined (potentially blended) homography matrix. */
        val homography: HomographyMatrix,

        /** Determinant of the homography matrix. */
        val determinant: Float,

        /** Approximate scale factor extracted from the matrix. */
        val approximateScale: Float,

        /** Approximate rotation in degrees extracted from the matrix. */
        val approximateRotationDegrees: Float,

        /** Whether the perspective transformation is valid. */
        val perspectiveValid: Boolean,

        /** Blend factor used (1.0 = original, 0.0 = identity). */
        val blendFactor: Float,

        /** Whether convergence has been reached. */
        val converged: Boolean,

        /** Change in determinant from previous pass. */
        val determinantChange: Float,

        /** Issues found with the perspective, if any. */
        val issues: List<String>,
    )

    /**
     * Performs one pass of perspective stability refinement.
     *
     * @param currentHomography The current homography matrix to validate/refine.
     * @param previousDeterminant Determinant from the previous pass (null if first pass).
     * @param settings Stabilization settings with thresholds.
     * @return Result containing RefinementResult or an error.
     */
    operator fun invoke(
        currentHomography: HomographyMatrix,
        previousDeterminant: Float?,
        settings: LandscapeStabilizationSettings,
    ): Result<RefinementResult> {
        // Validate homography is not singular
        if (!currentHomography.isValid()) {
            return Result.Error(
                IllegalArgumentException("Homography matrix is singular or invalid"),
                "Invalid homography matrix",
            )
        }

        // Extract properties
        val determinant = currentHomography.determinant()
        val scale = currentHomography.approximateScale()
        val rotationDegrees = currentHomography.approximateRotationDegrees()

        // Check for issues
        val issues = mutableListOf<String>()

        val determinantValid = determinant in settings.minDeterminant..settings.maxDeterminant
        if (!determinantValid) {
            issues.add(
                "Determinant $determinant outside bounds [${settings.minDeterminant}, ${settings.maxDeterminant}]",
            )
        }

        val scaleValid = scale in settings.minScaleFactor..settings.maxScaleFactor
        if (!scaleValid) {
            issues.add("Scale $scale outside bounds [${settings.minScaleFactor}, ${settings.maxScaleFactor}]")
        }

        val rotationValid = abs(rotationDegrees) <= settings.maxRotationDegrees
        if (!rotationValid) {
            issues.add("Rotation $rotationDegrees exceeds maximum ${settings.maxRotationDegrees} degrees")
        }

        // Check corner transformation validity
        val cornersValid = validateCornerTransformation(currentHomography)
        if (!cornersValid) {
            issues.add("Corner transformation produces non-convex or invalid quadrilateral")
        }

        val perspectiveValid = issues.isEmpty()

        // Determine if we need to blend with identity
        val (refinedHomography, blendFactor) = if (!perspectiveValid) {
            blendWithIdentity(currentHomography, settings.perspectiveBlendFactor)
        } else {
            Pair(currentHomography, 1.0f)
        }

        // Calculate determinant change for convergence check
        val determinantChange = if (previousDeterminant != null) {
            abs(determinant - previousDeterminant)
        } else {
            Float.MAX_VALUE
        }

        // Check for convergence
        val converged = perspectiveValid &&
            (
                determinantChange < settings.determinantChangeThreshold ||
                    previousDeterminant == null
                )

        return Result.Success(
            RefinementResult(
                homography = refinedHomography,
                determinant = refinedHomography.determinant(),
                approximateScale = refinedHomography.approximateScale(),
                approximateRotationDegrees = refinedHomography.approximateRotationDegrees(),
                perspectiveValid = perspectiveValid,
                blendFactor = blendFactor,
                converged = converged,
                determinantChange = determinantChange,
                issues = issues,
            ),
        )
    }

    /**
     * Validates that transforming the unit square corners produces a convex quadrilateral.
     */
    private fun validateCornerTransformation(homography: HomographyMatrix): Boolean {
        // Transform normalized corners [0,1] x [0,1]
        val corners = listOf(
            Pair(0f, 0f), // Top-left
            Pair(1f, 0f), // Top-right
            Pair(1f, 1f), // Bottom-right
            Pair(0f, 1f), // Bottom-left
        )

        val transformedCorners = corners.map { (x, y) ->
            homography.transformPoint(x, y)
        }

        // Check for infinite/NaN values
        if (transformedCorners.any { (x, y) ->
                x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()
            }
        ) {
            return false
        }

        // Check if the quadrilateral is convex using cross product of edges
        return isConvexQuadrilateral(transformedCorners)
    }

    /**
     * Checks if four points form a convex quadrilateral (all cross products have same sign).
     */
    private fun isConvexQuadrilateral(points: List<Pair<Float, Float>>): Boolean {
        if (points.size != 4) return false

        var lastSign: Int? = null

        for (i in 0 until 4) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]

            // Vector AB
            val abX = b.first - a.first
            val abY = b.second - a.second

            // Vector BC
            val bcX = c.first - b.first
            val bcY = c.second - b.second

            // Cross product (AB x BC)
            val crossProduct = abX * bcY - abY * bcX

            val currentSign = when {
                crossProduct > EPSILON -> 1
                crossProduct < -EPSILON -> -1
                else -> 0
            }

            if (currentSign != 0) {
                if (lastSign != null && currentSign != lastSign) {
                    return false // Different signs mean non-convex
                }
                lastSign = currentSign
            }
        }

        return lastSign != null // At least one non-zero cross product
    }

    /**
     * Blends a homography with the identity matrix to reduce extreme distortion.
     *
     * @param homography The original homography.
     * @param blendFactor How much of the original to keep (0.0 = identity, 1.0 = original).
     * @return Pair of (blended homography, actual blend factor used).
     */
    private fun blendWithIdentity(homography: HomographyMatrix, blendFactor: Float): Pair<HomographyMatrix, Float> {
        val t = blendFactor.coerceIn(0f, 1f)
        val identity = HomographyMatrix.IDENTITY

        // Linear interpolation between identity and original
        val blended = HomographyMatrix(
            h11 = lerp(identity.h11, homography.h11, t),
            h12 = lerp(identity.h12, homography.h12, t),
            h13 = lerp(identity.h13, homography.h13, t),
            h21 = lerp(identity.h21, homography.h21, t),
            h22 = lerp(identity.h22, homography.h22, t),
            h23 = lerp(identity.h23, homography.h23, t),
            h31 = lerp(identity.h31, homography.h31, t),
            h32 = lerp(identity.h32, homography.h32, t),
            h33 = lerp(identity.h33, homography.h33, t),
        )

        return Pair(blended, t)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        private const val EPSILON = 1e-6f
    }
}
