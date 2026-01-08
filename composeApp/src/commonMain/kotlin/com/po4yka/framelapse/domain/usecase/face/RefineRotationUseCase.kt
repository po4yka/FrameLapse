package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.StabilizationSettings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Refines the rotation component of the alignment matrix.
 *
 * This use case is used in SLOW mode to iteratively adjust rotation
 * until the eye delta Y is below the threshold (0.1 pixels by default).
 *
 * Algorithm:
 * 1. Calculate the current eye delta Y (vertical difference between eyes)
 * 2. If delta Y <= threshold, rotation is converged
 * 3. Otherwise, calculate rotation correction angle
 * 4. Apply rotation correction to the current matrix
 */
class RefineRotationUseCase {

    /**
     * Result of rotation refinement.
     *
     * @property matrix The refined alignment matrix.
     * @property converged Whether rotation has converged (delta Y <= threshold).
     * @property eyeDeltaY The current eye delta Y in pixels.
     */
    data class RefinementResult(val matrix: AlignmentMatrix, val converged: Boolean, val eyeDeltaY: Float)

    /**
     * Refines rotation based on detected landmarks.
     *
     * @param currentMatrix The current alignment matrix.
     * @param landmarks Detected face landmarks from the transformed image.
     * @param settings Stabilization settings containing thresholds.
     * @param canvasWidth Canvas width in pixels.
     * @param canvasHeight Canvas height in pixels.
     * @return RefinementResult with refined matrix and convergence status.
     */
    operator fun invoke(
        currentMatrix: AlignmentMatrix,
        landmarks: FaceLandmarks,
        settings: StabilizationSettings,
        canvasWidth: Int,
        canvasHeight: Int,
    ): RefinementResult {
        val leftEye = landmarks.leftEyeCenter
        val rightEye = landmarks.rightEyeCenter

        // Calculate eye delta Y in pixels
        // Note: landmarks are normalized (0-1), so we convert to pixels
        val leftEyeYPixels = leftEye.y * canvasHeight
        val rightEyeYPixels = rightEye.y * canvasHeight
        val leftEyeXPixels = leftEye.x * canvasWidth
        val rightEyeXPixels = rightEye.x * canvasWidth

        val eyeDeltaY = rightEyeYPixels - leftEyeYPixels
        val eyeDeltaX = rightEyeXPixels - leftEyeXPixels

        // Check if rotation has converged
        if (abs(eyeDeltaY) <= settings.rotationStopThreshold) {
            return RefinementResult(
                matrix = currentMatrix,
                converged = true,
                eyeDeltaY = eyeDeltaY,
            )
        }

        // Calculate the angle to correct
        // Negative because we want to counteract the detected rotation
        val detectedAngle = atan2(eyeDeltaY, eyeDeltaX)

        // Apply rotation correction to the matrix
        val refinedMatrix = applyRotationCorrection(currentMatrix, -detectedAngle)

        return RefinementResult(
            matrix = refinedMatrix,
            converged = false,
            eyeDeltaY = eyeDeltaY,
        )
    }

    /**
     * Applies a rotation correction to an existing alignment matrix.
     *
     * This composes the correction rotation with the existing matrix transformation.
     *
     * @param matrix The current alignment matrix.
     * @param angleRadians The rotation angle to apply (in radians).
     * @return The corrected alignment matrix.
     */
    private fun applyRotationCorrection(matrix: AlignmentMatrix, angleRadians: Float): AlignmentMatrix {
        val cosA = cos(angleRadians)
        val sinA = sin(angleRadians)

        // Compose rotation with existing matrix
        // [cosA -sinA 0]   [scaleX  skewX  tx]
        // [sinA  cosA 0] * [skewY   scaleY ty]
        // [0     0    1]   [0       0      1 ]
        return AlignmentMatrix(
            scaleX = cosA * matrix.scaleX - sinA * matrix.skewY,
            skewX = cosA * matrix.skewX - sinA * matrix.scaleY,
            translateX = cosA * matrix.translateX - sinA * matrix.translateY,
            skewY = sinA * matrix.scaleX + cosA * matrix.skewY,
            scaleY = sinA * matrix.skewX + cosA * matrix.scaleY,
            translateY = sinA * matrix.translateX + cosA * matrix.translateY,
        )
    }
}
