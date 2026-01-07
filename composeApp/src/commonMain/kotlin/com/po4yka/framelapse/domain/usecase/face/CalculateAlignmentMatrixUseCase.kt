package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates the affine transformation matrix for face alignment.
 *
 * The algorithm:
 * 1. Calculate the angle between the eyes to determine rotation
 * 2. Calculate the scale factor to normalize eye distance
 * 3. Calculate translation to center the face
 * 4. Compose these into an affine transformation matrix
 */
class CalculateAlignmentMatrixUseCase {
    /**
     * Calculates the alignment matrix for the given landmarks.
     *
     * @param landmarks The detected face landmarks.
     * @param settings Alignment configuration.
     * @return The affine transformation matrix.
     */
    operator fun invoke(landmarks: FaceLandmarks, settings: AlignmentSettings = AlignmentSettings()): AlignmentMatrix {
        val leftEye = landmarks.leftEyeCenter
        val rightEye = landmarks.rightEyeCenter

        // Calculate eye positions
        val eyeCenterX = (leftEye.x + rightEye.x) / 2f
        val eyeCenterY = (leftEye.y + rightEye.y) / 2f

        // Calculate rotation angle (eyes should be horizontal)
        val deltaX = rightEye.x - leftEye.x
        val deltaY = rightEye.y - leftEye.y
        val angle = atan2(deltaY, deltaX)

        // Calculate current eye distance
        val currentEyeDistance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Calculate scale to achieve target eye distance
        val targetEyeDistancePixels = settings.outputSize * settings.targetEyeDistance
        val scale = if (currentEyeDistance > 0) {
            targetEyeDistancePixels / currentEyeDistance
        } else {
            1f
        }

        // Calculate target center position
        val targetCenterX = settings.outputSize / 2f
        val targetCenterY = settings.outputSize * (0.5f - settings.verticalOffset)

        // Build transformation matrix
        // The transformation order is: translate to origin -> rotate -> scale -> translate to target
        val cosAngle = cos(-angle)
        val sinAngle = sin(-angle)

        // Combined transformation matrix components
        val scaleX = scale * cosAngle
        val skewX = scale * sinAngle
        val skewY = -scale * sinAngle
        val scaleY = scale * cosAngle

        // Translation: first move eye center to origin, then to target position
        val translateX = targetCenterX - (eyeCenterX * scaleX + eyeCenterY * skewX)
        val translateY = targetCenterY - (eyeCenterX * skewY + eyeCenterY * scaleY)

        return AlignmentMatrix(
            scaleX = scaleX,
            skewX = skewX,
            translateX = translateX,
            skewY = skewY,
            scaleY = scaleY,
            translateY = translateY,
        )
    }
}
