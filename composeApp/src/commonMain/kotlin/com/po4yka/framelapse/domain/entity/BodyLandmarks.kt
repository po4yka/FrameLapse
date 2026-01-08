package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains body pose landmark data extracted from a photo.
 *
 * Uses up to 33 keypoints (MediaPipe) or 17 keypoints (iOS Vision) for body alignment.
 * Reference points for alignment: left shoulder center and right shoulder center.
 *
 * Best for: Fitness progress tracking, posture analysis, upper-body timelapses.
 */
@Serializable
@SerialName("body")
data class BodyLandmarks(
    /** All detected body keypoints with their positions and confidence. */
    val keypoints: List<BodyKeypoint>,

    /** Left shoulder position (primary left reference point). */
    val leftShoulder: LandmarkPoint,

    /** Right shoulder position (primary right reference point). */
    val rightShoulder: LandmarkPoint,

    /** Left hip position (for torso framing). */
    val leftHip: LandmarkPoint,

    /** Right hip position (for torso framing). */
    val rightHip: LandmarkPoint,

    /** Neck/chest center position (for vertical framing reference). */
    val neckCenter: LandmarkPoint,

    /** Bounding box containing the detected body. */
    override val boundingBox: BoundingBox,

    /** Overall detection confidence (0.0 to 1.0). */
    val confidence: Float,
) : Landmarks {

    override fun getReferencePointLeft(): LandmarkPoint = leftShoulder

    override fun getReferencePointRight(): LandmarkPoint = rightShoulder

    /**
     * Calculates the center point between the shoulders.
     * Useful for centering the frame on the upper body.
     */
    val shoulderCenter: LandmarkPoint
        get() = LandmarkPoint(
            x = (leftShoulder.x + rightShoulder.x) / 2,
            y = (leftShoulder.y + rightShoulder.y) / 2,
            z = (leftShoulder.z + rightShoulder.z) / 2,
        )

    /**
     * Calculates the center point between the hips.
     * Useful for determining the waist line in framing.
     */
    val hipCenter: LandmarkPoint
        get() = LandmarkPoint(
            x = (leftHip.x + rightHip.x) / 2,
            y = (leftHip.y + rightHip.y) / 2,
            z = (leftHip.z + rightHip.z) / 2,
        )

    /**
     * Calculates the distance between shoulders in normalized coordinates.
     * Used for scale calculations in alignment.
     */
    val shoulderDistance: Float
        get() {
            val dx = rightShoulder.x - leftShoulder.x
            val dy = rightShoulder.y - leftShoulder.y
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

    companion object {
        /** Maximum keypoint count (MediaPipe provides 33). */
        const val MAX_KEYPOINT_COUNT = 33

        /** Minimum keypoint count (iOS Vision provides 17). */
        const val MIN_KEYPOINT_COUNT = 17
    }
}

/**
 * Represents a single body keypoint with its type, position, and confidence.
 */
@Serializable
data class BodyKeypoint(
    /** The type/name of this keypoint (e.g., LEFT_SHOULDER, RIGHT_HIP). */
    val type: BodyKeypointType,

    /** The 3D position of this keypoint (normalized 0-1 for x/y). */
    val position: LandmarkPoint,

    /** Detection confidence for this specific keypoint (0.0 to 1.0). */
    val confidence: Float,

    /** Whether this keypoint is visible (not occluded). */
    val isVisible: Boolean = true,
)

/**
 * Enumeration of body keypoint types.
 *
 * This is a common subset supported by both MediaPipe (Android) and Vision (iOS).
 * MediaPipe provides additional keypoints (fingers, toes) not included here.
 */
@Serializable
enum class BodyKeypointType {
    // Head
    NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EAR,
    RIGHT_EAR,

    // Upper body
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,

    // Lower body
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
}
