package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single frame (photo) in a timelapse project.
 */
@Serializable
data class Frame(
    val id: String,
    val projectId: String,
    val originalPath: String,
    val alignedPath: String? = null,
    val timestamp: Long,
    val capturedAt: Long,
    val confidence: Float? = null,
    val landmarks: Landmarks? = null,
    val sortOrder: Int = 0,
    val stabilizationResult: StabilizationResult? = null,
)

/**
 * Base interface for all landmark types used in content stabilization.
 *
 * This sealed interface enables polymorphic storage and serialization of
 * different landmark types (face, body, etc.) while providing a common
 * interface for alignment algorithms.
 */
@Serializable
sealed interface Landmarks {
    /** Bounding box containing the detected content. */
    val boundingBox: BoundingBox

    /**
     * Returns the left reference point used for alignment.
     * For face: left eye center. For body: left shoulder.
     */
    fun getReferencePointLeft(): LandmarkPoint

    /**
     * Returns the right reference point used for alignment.
     * For face: right eye center. For body: right shoulder.
     */
    fun getReferencePointRight(): LandmarkPoint
}

/**
 * Contains facial landmark data extracted from a photo.
 * Uses 478 3D landmark points for face alignment.
 *
 * Reference points for alignment: left eye center and right eye center.
 */
@Serializable
@SerialName("face")
data class FaceLandmarks(
    val points: List<LandmarkPoint>,
    val leftEyeCenter: LandmarkPoint,
    val rightEyeCenter: LandmarkPoint,
    val noseTip: LandmarkPoint,
    override val boundingBox: BoundingBox,
) : Landmarks {

    override fun getReferencePointLeft(): LandmarkPoint = leftEyeCenter

    override fun getReferencePointRight(): LandmarkPoint = rightEyeCenter

    companion object {
        const val LANDMARK_COUNT = 478
    }
}

/**
 * A single 3D landmark point.
 */
@Serializable
data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)

/**
 * Bounding box for a detected face.
 */
@Serializable
data class BoundingBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + width / 2
    val centerY: Float get() = top + height / 2
}

/**
 * Affine transformation matrix for face alignment.
 */
@Serializable
data class AlignmentMatrix(
    val scaleX: Float,
    val skewX: Float,
    val translateX: Float,
    val skewY: Float,
    val scaleY: Float,
    val translateY: Float,
)
