package com.po4yka.framelapse.domain.usecase.muscle

import com.po4yka.framelapse.domain.entity.BodyKeypointType
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.MuscleAlignmentSettings
import com.po4yka.framelapse.domain.entity.MuscleRegion
import com.po4yka.framelapse.domain.entity.MuscleRegionBounds
import org.koin.core.annotation.Factory
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates crop bounds for a muscle region based on body landmarks.
 *
 * Each region uses different body keypoints to determine the optimal crop area:
 * - FULL_BODY: nose to ankles, shoulder/hip width
 * - UPPER_BODY: nose to hips, shoulder width
 * - LOWER_BODY: hips to ankles, hip width
 * - ARMS: shoulders to wrists, arm span
 * - BACK: shoulders to hips, shoulder width
 *
 * Works with both MediaPipe (33 keypoints) and iOS Vision (17 keypoints).
 */
@Factory
class CalculateMuscleRegionBoundsUseCase {

    /**
     * Calculates the crop bounds for the specified muscle region.
     *
     * @param landmarks Body landmarks detected in the image.
     * @param region Target muscle region for cropping.
     * @param padding Padding around the region as a fraction of region size.
     * @return Normalized bounds (0.0-1.0) ready for cropping, converted to square.
     */
    operator fun invoke(
        landmarks: BodyLandmarks,
        region: MuscleRegion,
        padding: Float = MuscleAlignmentSettings.DEFAULT_REGION_PADDING,
    ): MuscleRegionBounds {
        val rawBounds = when (region) {
            MuscleRegion.FULL_BODY -> calculateFullBodyBounds(landmarks)
            MuscleRegion.UPPER_BODY -> calculateUpperBodyBounds(landmarks)
            MuscleRegion.LOWER_BODY -> calculateLowerBodyBounds(landmarks)
            MuscleRegion.ARMS -> calculateArmsBounds(landmarks)
            MuscleRegion.BACK -> calculateBackBounds(landmarks)
        }

        return rawBounds.withPadding(padding).toSquareBounds()
    }

    private fun calculateFullBodyBounds(landmarks: BodyLandmarks): MuscleRegionBounds {
        val keypoints = landmarks.keypoints
        val nose = keypoints.find { it.type == BodyKeypointType.NOSE }?.position
        val leftAnkle = keypoints.find { it.type == BodyKeypointType.LEFT_ANKLE }?.position
        val rightAnkle = keypoints.find { it.type == BodyKeypointType.RIGHT_ANKLE }?.position

        // Top: nose or shoulder with offset
        val top = (nose?.y ?: landmarks.shoulderCenter.y) - HEAD_MARGIN

        // Bottom: lowest ankle or estimated from hips
        val bottom = max(
            leftAnkle?.y ?: (landmarks.hipCenter.y + LOWER_BODY_ESTIMATE),
            rightAnkle?.y ?: (landmarks.hipCenter.y + LOWER_BODY_ESTIMATE),
        ) + ANKLE_MARGIN

        // Left/Right: widest point between shoulders and hips
        val left = min(landmarks.leftShoulder.x, landmarks.leftHip.x) - SIDE_MARGIN
        val right = max(landmarks.rightShoulder.x, landmarks.rightHip.x) + SIDE_MARGIN

        return MuscleRegionBounds(
            region = MuscleRegion.FULL_BODY,
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
    }

    private fun calculateUpperBodyBounds(landmarks: BodyLandmarks): MuscleRegionBounds {
        val keypoints = landmarks.keypoints
        val nose = keypoints.find { it.type == BodyKeypointType.NOSE }?.position

        // Top: nose or neck with offset
        val top = (nose?.y ?: landmarks.neckCenter.y) - HEAD_MARGIN

        // Bottom: hip line
        val bottom = landmarks.hipCenter.y + HIP_MARGIN

        // Left/Right: shoulder span with margin
        val left = landmarks.leftShoulder.x - SHOULDER_MARGIN
        val right = landmarks.rightShoulder.x + SHOULDER_MARGIN

        return MuscleRegionBounds(
            region = MuscleRegion.UPPER_BODY,
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
    }

    private fun calculateLowerBodyBounds(landmarks: BodyLandmarks): MuscleRegionBounds {
        val keypoints = landmarks.keypoints
        val leftAnkle = keypoints.find { it.type == BodyKeypointType.LEFT_ANKLE }?.position
        val rightAnkle = keypoints.find { it.type == BodyKeypointType.RIGHT_ANKLE }?.position
        val leftKnee = keypoints.find { it.type == BodyKeypointType.LEFT_KNEE }?.position
        val rightKnee = keypoints.find { it.type == BodyKeypointType.RIGHT_KNEE }?.position

        // Top: hip line with small offset above
        val top = landmarks.hipCenter.y - HIP_TOP_MARGIN

        // Bottom: lowest ankle
        val bottom = max(
            leftAnkle?.y ?: (landmarks.hipCenter.y + LOWER_BODY_ESTIMATE),
            rightAnkle?.y ?: (landmarks.hipCenter.y + LOWER_BODY_ESTIMATE),
        ) + ANKLE_MARGIN

        // Left/Right: widest point of hips, knees, or ankles
        val leftMost = minOf(
            landmarks.leftHip.x,
            leftKnee?.x ?: landmarks.leftHip.x,
            leftAnkle?.x ?: landmarks.leftHip.x,
        ) - SIDE_MARGIN

        val rightMost = maxOf(
            landmarks.rightHip.x,
            rightKnee?.x ?: landmarks.rightHip.x,
            rightAnkle?.x ?: landmarks.rightHip.x,
        ) + SIDE_MARGIN

        return MuscleRegionBounds(
            region = MuscleRegion.LOWER_BODY,
            left = leftMost.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = rightMost.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
    }

    private fun calculateArmsBounds(landmarks: BodyLandmarks): MuscleRegionBounds {
        val keypoints = landmarks.keypoints
        val leftElbow = keypoints.find { it.type == BodyKeypointType.LEFT_ELBOW }?.position
        val rightElbow = keypoints.find { it.type == BodyKeypointType.RIGHT_ELBOW }?.position
        val leftWrist = keypoints.find { it.type == BodyKeypointType.LEFT_WRIST }?.position
        val rightWrist = keypoints.find { it.type == BodyKeypointType.RIGHT_WRIST }?.position

        // Top: above shoulders
        val top = min(landmarks.leftShoulder.y, landmarks.rightShoulder.y) - SHOULDER_TOP_MARGIN

        // Bottom: lowest wrist or elbow
        val bottom = maxOf(
            leftWrist?.y ?: landmarks.shoulderCenter.y + ARM_LENGTH_ESTIMATE,
            rightWrist?.y ?: landmarks.shoulderCenter.y + ARM_LENGTH_ESTIMATE,
            leftElbow?.y ?: landmarks.shoulderCenter.y,
            rightElbow?.y ?: landmarks.shoulderCenter.y,
        ) + WRIST_MARGIN

        // Left: leftmost arm position (shoulder, elbow, or wrist)
        val left = minOf(
            landmarks.leftShoulder.x,
            leftElbow?.x ?: landmarks.leftShoulder.x,
            leftWrist?.x ?: landmarks.leftShoulder.x,
        ) - ARM_SIDE_MARGIN

        // Right: rightmost arm position
        val right = maxOf(
            landmarks.rightShoulder.x,
            rightElbow?.x ?: landmarks.rightShoulder.x,
            rightWrist?.x ?: landmarks.rightShoulder.x,
        ) + ARM_SIDE_MARGIN

        return MuscleRegionBounds(
            region = MuscleRegion.ARMS,
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
    }

    private fun calculateBackBounds(landmarks: BodyLandmarks): MuscleRegionBounds {
        // Back view uses same bounds as upper body but with slightly wider shoulder span
        // to capture the full back muscle spread (lats, traps, etc.)

        val keypoints = landmarks.keypoints
        val nose = keypoints.find { it.type == BodyKeypointType.NOSE }?.position

        // Top: head/neck area
        val top = (nose?.y ?: landmarks.neckCenter.y) - HEAD_MARGIN

        // Bottom: just below hip line
        val bottom = landmarks.hipCenter.y + BACK_HIP_MARGIN

        // Left/Right: wider than upper body to show lat spread
        val left = landmarks.leftShoulder.x - BACK_SIDE_MARGIN
        val right = landmarks.rightShoulder.x + BACK_SIDE_MARGIN

        return MuscleRegionBounds(
            region = MuscleRegion.BACK,
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
    }

    companion object {
        // Margin constants (as fractions of normalized image coordinates)
        private const val HEAD_MARGIN = 0.05f
        private const val SIDE_MARGIN = 0.03f
        private const val SHOULDER_MARGIN = 0.05f
        private const val SHOULDER_TOP_MARGIN = 0.08f
        private const val HIP_MARGIN = 0.03f
        private const val HIP_TOP_MARGIN = 0.02f
        private const val ANKLE_MARGIN = 0.02f
        private const val WRIST_MARGIN = 0.03f
        private const val ARM_SIDE_MARGIN = 0.05f
        private const val BACK_SIDE_MARGIN = 0.08f
        private const val BACK_HIP_MARGIN = 0.05f

        // Fallback estimates when keypoints are not detected
        private const val LOWER_BODY_ESTIMATE = 0.35f
        private const val ARM_LENGTH_ESTIMATE = 0.25f
    }
}
