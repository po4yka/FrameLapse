package com.po4yka.framelapse.domain.usecase.body

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BodyAlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import org.koin.core.annotation.Factory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates the affine transformation matrix for body alignment.
 *
 * The algorithm:
 * 1. Calculate the angle between the shoulders to determine rotation
 * 2. Calculate the scale factor to normalize shoulder distance
 * 3. Calculate translation to center the torso
 * 4. Apply vertical offset for head-to-waist framing
 * 5. Compose these into an affine transformation matrix
 */
@Factory
class CalculateBodyAlignmentMatrixUseCase {
    /**
     * Calculates the alignment matrix for the given body landmarks.
     *
     * @param landmarks The detected body landmarks.
     * @param settings Body alignment configuration.
     * @return The affine transformation matrix.
     */
    operator fun invoke(
        landmarks: BodyLandmarks,
        settings: BodyAlignmentSettings = BodyAlignmentSettings(),
    ): AlignmentMatrix {
        val leftShoulder = landmarks.leftShoulder
        val rightShoulder = landmarks.rightShoulder

        // Calculate shoulder center
        val shoulderCenterX = (leftShoulder.x + rightShoulder.x) / 2f
        val shoulderCenterY = (leftShoulder.y + rightShoulder.y) / 2f

        // Calculate rotation angle (shoulders should be horizontal)
        val deltaX = rightShoulder.x - leftShoulder.x
        val deltaY = rightShoulder.y - leftShoulder.y
        val angle = atan2(deltaY, deltaX)

        // Calculate current shoulder distance
        val currentShoulderDistance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Calculate scale to achieve target shoulder distance
        val targetShoulderDistancePixels = settings.outputSize * settings.targetShoulderDistance
        val scale = if (currentShoulderDistance > 0) {
            targetShoulderDistancePixels / currentShoulderDistance
        } else {
            1f
        }

        // Calculate target center position
        // For body alignment, we center based on shoulder position
        // and apply vertical offset to show head-to-waist framing
        val targetCenterX = settings.outputSize / 2f
        val targetCenterY = settings.outputSize * (0.5f - settings.verticalOffset)

        // Adjust vertical position based on head-to-waist ratio
        // Higher ratio means more of the body is shown (shoulder center moves up)
        val verticalAdjustment = settings.outputSize * (1f - settings.headToWaistRatio) * 0.3f

        // Build transformation matrix
        // The transformation order is: translate to origin -> rotate -> scale -> translate to target
        val cosAngle = cos(-angle)
        val sinAngle = sin(-angle)

        // Combined transformation matrix components
        val scaleX = scale * cosAngle
        val skewX = scale * sinAngle
        val skewY = -scale * sinAngle
        val scaleY = scale * cosAngle

        // Translation: first move shoulder center to origin, then to target position
        val translateX = targetCenterX - (shoulderCenterX * scaleX + shoulderCenterY * skewX)
        val translateY = (targetCenterY - verticalAdjustment) -
            (shoulderCenterX * skewY + shoulderCenterY * scaleY)

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
