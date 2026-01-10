package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.StabilizationSettings
import org.koin.core.annotation.Factory
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Refines the scale component of the alignment matrix.
 *
 * This use case is used in SLOW mode to iteratively adjust scale
 * until the eye distance error is below the threshold (1.0 pixel by default).
 *
 * Algorithm:
 * 1. Calculate the current eye distance
 * 2. Compare with goal eye distance to get scale error
 * 3. If error <= threshold, scale is converged
 * 4. Otherwise, calculate scale correction factor
 * 5. Apply scale correction to the current matrix
 */
@Factory
class RefineScaleUseCase {

    /**
     * Result of scale refinement.
     *
     * @property matrix The refined alignment matrix.
     * @property converged Whether scale has converged (error <= threshold).
     * @property scaleError The current scale error in pixels.
     * @property currentEyeDistance The current eye distance in pixels.
     */
    data class RefinementResult(
        val matrix: AlignmentMatrix,
        val converged: Boolean,
        val scaleError: Float,
        val currentEyeDistance: Float,
    )

    /**
     * Refines scale based on detected landmarks.
     *
     * @param currentMatrix The current alignment matrix.
     * @param landmarks Detected face landmarks from the transformed image.
     * @param goalEyeDistance Target eye distance in pixels.
     * @param settings Stabilization settings containing thresholds.
     * @param canvasWidth Canvas width in pixels.
     * @param canvasHeight Canvas height in pixels.
     * @return RefinementResult with refined matrix and convergence status.
     */
    operator fun invoke(
        currentMatrix: AlignmentMatrix,
        landmarks: FaceLandmarks,
        goalEyeDistance: Float,
        settings: StabilizationSettings,
        canvasWidth: Int,
        canvasHeight: Int,
    ): RefinementResult {
        val leftEye = landmarks.leftEyeCenter
        val rightEye = landmarks.rightEyeCenter

        // Calculate current eye distance in pixels
        // Note: landmarks are normalized (0-1), so we convert to pixels
        val leftEyeXPixels = leftEye.x * canvasWidth
        val leftEyeYPixels = leftEye.y * canvasHeight
        val rightEyeXPixels = rightEye.x * canvasWidth
        val rightEyeYPixels = rightEye.y * canvasHeight

        val deltaX = rightEyeXPixels - leftEyeXPixels
        val deltaY = rightEyeYPixels - leftEyeYPixels
        val currentEyeDistance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Calculate scale error
        val scaleError = abs(currentEyeDistance - goalEyeDistance)

        // Check if scale has converged
        if (scaleError <= settings.scaleErrorThreshold) {
            return RefinementResult(
                matrix = currentMatrix,
                converged = true,
                scaleError = scaleError,
                currentEyeDistance = currentEyeDistance,
            )
        }

        // Calculate scale correction factor
        val scaleCorrection = if (currentEyeDistance > 0f) {
            goalEyeDistance / currentEyeDistance
        } else {
            1f
        }

        // Apply scale correction to the matrix
        val refinedMatrix = applyScaleCorrection(currentMatrix, scaleCorrection)

        return RefinementResult(
            matrix = refinedMatrix,
            converged = false,
            scaleError = scaleError,
            currentEyeDistance = currentEyeDistance,
        )
    }

    /**
     * Applies a scale correction to an existing alignment matrix.
     *
     * This multiplies all matrix components by the scale factor.
     *
     * @param matrix The current alignment matrix.
     * @param scaleFactor The scale factor to apply.
     * @return The corrected alignment matrix.
     */
    private fun applyScaleCorrection(matrix: AlignmentMatrix, scaleFactor: Float): AlignmentMatrix {
        // Scale all components except translation
        // Translation needs to be adjusted for the center point
        return AlignmentMatrix(
            scaleX = matrix.scaleX * scaleFactor,
            skewX = matrix.skewX * scaleFactor,
            translateX = matrix.translateX, // Translation adjusted during centering
            skewY = matrix.skewY * scaleFactor,
            scaleY = matrix.scaleY * scaleFactor,
            translateY = matrix.translateY, // Translation adjusted during centering
        )
    }
}
