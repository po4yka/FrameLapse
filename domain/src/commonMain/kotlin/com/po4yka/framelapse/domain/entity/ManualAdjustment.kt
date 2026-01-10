package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a manual adjustment to stabilization landmarks.
 *
 * When automatic face/body/landscape detection fails or produces suboptimal results,
 * users can manually adjust the reference points (eyes, shoulders, corners) to improve
 * alignment quality.
 *
 * This sealed interface enables polymorphic storage and serialization of different
 * adjustment types while providing a common interface for the adjustment system.
 */
@Serializable
sealed interface ManualAdjustment {
    /** Unique identifier for this adjustment. */
    val id: String

    /** Timestamp when adjustment was created or last modified. */
    val timestamp: Long

    /** Whether this manual adjustment is currently active (vs using auto-detected landmarks). */
    val isActive: Boolean

    /**
     * Converts this manual adjustment to the corresponding Landmarks type for alignment.
     * Returns null if conversion is not possible (e.g., missing required data).
     */
    fun toLandmarks(): Landmarks?
}

/**
 * Manual adjustment for face alignment.
 * Allows user to override the detected eye center positions.
 *
 * Eye positions are normalized (0.0-1.0) relative to the image dimensions.
 */
@Serializable
@SerialName("face")
data class FaceManualAdjustment(
    override val id: String,
    override val timestamp: Long,
    override val isActive: Boolean = true,
    /** Manually adjusted left eye center position (normalized 0.0-1.0). */
    val leftEyeCenter: LandmarkPoint,
    /** Manually adjusted right eye center position (normalized 0.0-1.0). */
    val rightEyeCenter: LandmarkPoint,
    /** Optional manually adjusted nose tip position. */
    val noseTip: LandmarkPoint? = null,
) : ManualAdjustment {

    override fun toLandmarks(): FaceLandmarks? {
        val nose = noseTip ?: LandmarkPoint(
            x = (leftEyeCenter.x + rightEyeCenter.x) / 2,
            y = leftEyeCenter.y + 0.15f, // Estimated nose position below eyes
            z = 0f,
        )

        // Calculate bounding box from eye positions with padding
        val eyeDistance = kotlin.math.abs(rightEyeCenter.x - leftEyeCenter.x)
        val padding = eyeDistance * 0.8f

        return FaceLandmarks(
            points = emptyList(), // Manual adjustments don't have full landmark points
            leftEyeCenter = leftEyeCenter,
            rightEyeCenter = rightEyeCenter,
            noseTip = nose,
            boundingBox = BoundingBox(
                left = (leftEyeCenter.x - padding).coerceAtLeast(0f),
                top = (leftEyeCenter.y - padding).coerceAtLeast(0f),
                right = (rightEyeCenter.x + padding).coerceAtMost(1f),
                bottom = (nose.y + padding).coerceAtMost(1f),
            ),
        )
    }
}

/**
 * Manual adjustment for body alignment.
 * Allows user to override the detected shoulder and hip positions.
 *
 * All positions are normalized (0.0-1.0) relative to the image dimensions.
 */
@Serializable
@SerialName("body")
data class BodyManualAdjustment(
    override val id: String,
    override val timestamp: Long,
    override val isActive: Boolean = true,
    /** Manually adjusted left shoulder position (normalized 0.0-1.0). */
    val leftShoulder: LandmarkPoint,
    /** Manually adjusted right shoulder position (normalized 0.0-1.0). */
    val rightShoulder: LandmarkPoint,
    /** Manually adjusted left hip position (normalized 0.0-1.0). */
    val leftHip: LandmarkPoint,
    /** Manually adjusted right hip position (normalized 0.0-1.0). */
    val rightHip: LandmarkPoint,
) : ManualAdjustment {

    override fun toLandmarks(): BodyLandmarks? {
        // Calculate neck center between shoulders
        val neckCenter = LandmarkPoint(
            x = (leftShoulder.x + rightShoulder.x) / 2,
            y = leftShoulder.y - 0.05f, // Slightly above shoulders
            z = 0f,
        )

        // Calculate bounding box from keypoints with padding
        val padding = 0.05f
        val minX = minOf(leftShoulder.x, rightShoulder.x, leftHip.x, rightHip.x)
        val maxX = maxOf(leftShoulder.x, rightShoulder.x, leftHip.x, rightHip.x)
        val minY = minOf(leftShoulder.y, rightShoulder.y)
        val maxY = maxOf(leftHip.y, rightHip.y)

        return BodyLandmarks(
            keypoints = emptyList(), // Manual adjustments don't have full keypoints
            leftShoulder = leftShoulder,
            rightShoulder = rightShoulder,
            leftHip = leftHip,
            rightHip = rightHip,
            neckCenter = neckCenter,
            boundingBox = BoundingBox(
                left = (minX - padding).coerceAtLeast(0f),
                top = (minY - padding).coerceAtLeast(0f),
                right = (maxX + padding).coerceAtMost(1f),
                bottom = (maxY + padding).coerceAtMost(1f),
            ),
            confidence = 1.0f, // Manual adjustments are considered high confidence
        )
    }
}

/**
 * Manual adjustment for muscle region alignment.
 * Combines body alignment with region-specific bounds adjustment.
 *
 * All positions are normalized (0.0-1.0) relative to the image dimensions.
 */
@Serializable
@SerialName("muscle")
data class MuscleManualAdjustment(
    override val id: String,
    override val timestamp: Long,
    override val isActive: Boolean = true,
    /** Body adjustment for shoulder/hip positioning. */
    val bodyAdjustment: BodyManualAdjustment,
    /** Manually adjusted region bounds for cropping. */
    val regionBounds: MuscleRegionBounds,
) : ManualAdjustment {

    override fun toLandmarks(): BodyLandmarks? = bodyAdjustment.toLandmarks()
}

/**
 * Manual adjustment for landscape alignment.
 * Allows user to specify corner keypoints for homography calculation.
 *
 * All positions are normalized (0.0-1.0) relative to the image dimensions.
 * Corners should be provided in order: top-left, top-right, bottom-left, bottom-right.
 */
@Serializable
@SerialName("landscape")
data class LandscapeManualAdjustment(
    override val id: String,
    override val timestamp: Long,
    override val isActive: Boolean = true,
    /**
     * Four corner keypoints for homography calculation.
     * Order: [top-left, top-right, bottom-left, bottom-right]
     */
    val cornerKeypoints: List<LandmarkPoint>,
) : ManualAdjustment {

    init {
        require(cornerKeypoints.size == CORNER_COUNT) {
            "Landscape adjustment requires exactly $CORNER_COUNT corner keypoints"
        }
    }

    /** Top-left corner keypoint. */
    val topLeft: LandmarkPoint get() = cornerKeypoints[0]

    /** Top-right corner keypoint. */
    val topRight: LandmarkPoint get() = cornerKeypoints[1]

    /** Bottom-left corner keypoint. */
    val bottomLeft: LandmarkPoint get() = cornerKeypoints[2]

    /** Bottom-right corner keypoint. */
    val bottomRight: LandmarkPoint get() = cornerKeypoints[3]

    /**
     * Landscape adjustments don't convert to standard Landmarks type
     * since they use homography transformation instead of affine.
     */
    override fun toLandmarks(): Landmarks? = null

    companion object {
        const val CORNER_COUNT = 4
    }
}

/**
 * Creates a FaceManualAdjustment from existing FaceLandmarks.
 * Useful when initializing the adjustment UI with auto-detected values.
 */
fun FaceLandmarks.toManualAdjustment(id: String, timestamp: Long): FaceManualAdjustment = FaceManualAdjustment(
    id = id,
    timestamp = timestamp,
    isActive = true,
    leftEyeCenter = leftEyeCenter,
    rightEyeCenter = rightEyeCenter,
    noseTip = noseTip,
)

/**
 * Creates a BodyManualAdjustment from existing BodyLandmarks.
 * Useful when initializing the adjustment UI with auto-detected values.
 */
fun BodyLandmarks.toManualAdjustment(id: String, timestamp: Long): BodyManualAdjustment = BodyManualAdjustment(
    id = id,
    timestamp = timestamp,
    isActive = true,
    leftShoulder = leftShoulder,
    rightShoulder = rightShoulder,
    leftHip = leftHip,
    rightHip = rightHip,
)
