package com.po4yka.framelapse.domain.entity

import com.po4yka.framelapse.platform.currentTimeMillis
import kotlinx.serialization.Serializable

/**
 * Command pattern for undo/redo of manual adjustments.
 *
 * Each command captures the state before and after a modification,
 * allowing the change to be undone or redone.
 */
@Serializable
sealed interface AdjustmentCommand {
    /** Timestamp when this command was executed. */
    val timestamp: Long

    /** Human-readable description of this command for UI display. */
    val description: String

    /**
     * Executes this command and returns the resulting adjustment state.
     */
    fun execute(): ManualAdjustment

    /**
     * Undoes this command and returns the previous adjustment state.
     * Returns null if undo is not possible.
     */
    fun undo(): ManualAdjustment?
}

/**
 * Command for moving a single adjustment point (eye, shoulder, corner, etc.).
 */
@Serializable
data class PointMoveCommand(
    override val timestamp: Long,
    override val description: String,
    /** The type of point being moved. */
    val pointType: AdjustmentPointType,
    /** Position before the move (normalized 0.0-1.0). */
    val previousPosition: LandmarkPoint,
    /** Position after the move (normalized 0.0-1.0). */
    val newPosition: LandmarkPoint,
    /** The adjustment state before this command. */
    val originalAdjustment: ManualAdjustment,
    /** The adjustment state after this command. */
    val resultAdjustment: ManualAdjustment,
) : AdjustmentCommand {

    override fun execute(): ManualAdjustment = resultAdjustment

    override fun undo(): ManualAdjustment = originalAdjustment
}

/**
 * Command for resizing a muscle region bounds.
 */
@Serializable
data class RegionResizeCommand(
    override val timestamp: Long,
    override val description: String,
    /** Bounds before the resize. */
    val previousBounds: MuscleRegionBounds,
    /** Bounds after the resize. */
    val newBounds: MuscleRegionBounds,
    /** The adjustment state before this command. */
    val originalAdjustment: MuscleManualAdjustment,
    /** The adjustment state after this command. */
    val resultAdjustment: MuscleManualAdjustment,
) : AdjustmentCommand {

    override fun execute(): ManualAdjustment = resultAdjustment

    override fun undo(): ManualAdjustment = originalAdjustment
}

/**
 * Command for moving multiple landscape corner keypoints at once.
 */
@Serializable
data class CornersAdjustCommand(
    override val timestamp: Long,
    override val description: String,
    /** Corner positions before the adjustment. */
    val previousCorners: List<LandmarkPoint>,
    /** Corner positions after the adjustment. */
    val newCorners: List<LandmarkPoint>,
    /** The adjustment state before this command. */
    val originalAdjustment: LandscapeManualAdjustment,
    /** The adjustment state after this command. */
    val resultAdjustment: LandscapeManualAdjustment,
) : AdjustmentCommand {

    override fun execute(): ManualAdjustment = resultAdjustment

    override fun undo(): ManualAdjustment = originalAdjustment
}

/**
 * Types of adjustment points that can be moved by the user.
 */
@Serializable
enum class AdjustmentPointType(val displayName: String) {
    // Face points
    LEFT_EYE("Left Eye"),
    RIGHT_EYE("Right Eye"),
    NOSE_TIP("Nose"),

    // Body points
    LEFT_SHOULDER("Left Shoulder"),
    RIGHT_SHOULDER("Right Shoulder"),
    LEFT_HIP("Left Hip"),
    RIGHT_HIP("Right Hip"),

    // Landscape corners
    CORNER_TOP_LEFT("Top Left"),
    CORNER_TOP_RIGHT("Top Right"),
    CORNER_BOTTOM_LEFT("Bottom Left"),
    CORNER_BOTTOM_RIGHT("Bottom Right"),

    // Muscle region edges
    REGION_LEFT("Left Edge"),
    REGION_TOP("Top Edge"),
    REGION_RIGHT("Right Edge"),
    REGION_BOTTOM("Bottom Edge"),
    ;

    companion object {
        /** Get the face-related point types. */
        val facePoints: Set<AdjustmentPointType> = setOf(LEFT_EYE, RIGHT_EYE, NOSE_TIP)

        /** Get the body-related point types. */
        val bodyPoints: Set<AdjustmentPointType> = setOf(
            LEFT_SHOULDER,
            RIGHT_SHOULDER,
            LEFT_HIP,
            RIGHT_HIP,
        )

        /** Get the landscape corner point types. */
        val landscapePoints: Set<AdjustmentPointType> = setOf(
            CORNER_TOP_LEFT,
            CORNER_TOP_RIGHT,
            CORNER_BOTTOM_LEFT,
            CORNER_BOTTOM_RIGHT,
        )

        /** Get the muscle region edge point types. */
        val regionEdgePoints: Set<AdjustmentPointType> = setOf(
            REGION_LEFT,
            REGION_TOP,
            REGION_RIGHT,
            REGION_BOTTOM,
        )
    }
}

/**
 * Factory functions for creating adjustment commands with proper descriptions.
 */
object AdjustmentCommandFactory {

    /**
     * Creates a PointMoveCommand for moving a face point.
     */
    fun createFacePointMove(
        pointType: AdjustmentPointType,
        previousPosition: LandmarkPoint,
        newPosition: LandmarkPoint,
        originalAdjustment: FaceManualAdjustment,
    ): PointMoveCommand {
        val resultAdjustment = when (pointType) {
            AdjustmentPointType.LEFT_EYE -> originalAdjustment.copy(
                leftEyeCenter = newPosition,
                timestamp = currentTimeMillis(),
            )
            AdjustmentPointType.RIGHT_EYE -> originalAdjustment.copy(
                rightEyeCenter = newPosition,
                timestamp = currentTimeMillis(),
            )
            AdjustmentPointType.NOSE_TIP -> originalAdjustment.copy(
                noseTip = newPosition,
                timestamp = currentTimeMillis(),
            )
            else -> throw IllegalArgumentException("Invalid face point type: $pointType")
        }

        return PointMoveCommand(
            timestamp = currentTimeMillis(),
            description = "Move ${pointType.displayName}",
            pointType = pointType,
            previousPosition = previousPosition,
            newPosition = newPosition,
            originalAdjustment = originalAdjustment,
            resultAdjustment = resultAdjustment,
        )
    }

    /**
     * Creates a PointMoveCommand for moving a body point.
     */
    fun createBodyPointMove(
        pointType: AdjustmentPointType,
        previousPosition: LandmarkPoint,
        newPosition: LandmarkPoint,
        originalAdjustment: BodyManualAdjustment,
    ): PointMoveCommand {
        val resultAdjustment = when (pointType) {
            AdjustmentPointType.LEFT_SHOULDER -> originalAdjustment.copy(
                leftShoulder = newPosition,
                timestamp = currentTimeMillis(),
            )
            AdjustmentPointType.RIGHT_SHOULDER -> originalAdjustment.copy(
                rightShoulder = newPosition,
                timestamp = currentTimeMillis(),
            )
            AdjustmentPointType.LEFT_HIP -> originalAdjustment.copy(
                leftHip = newPosition,
                timestamp = currentTimeMillis(),
            )
            AdjustmentPointType.RIGHT_HIP -> originalAdjustment.copy(
                rightHip = newPosition,
                timestamp = currentTimeMillis(),
            )
            else -> throw IllegalArgumentException("Invalid body point type: $pointType")
        }

        return PointMoveCommand(
            timestamp = currentTimeMillis(),
            description = "Move ${pointType.displayName}",
            pointType = pointType,
            previousPosition = previousPosition,
            newPosition = newPosition,
            originalAdjustment = originalAdjustment,
            resultAdjustment = resultAdjustment,
        )
    }

    /**
     * Creates a PointMoveCommand for moving a landscape corner.
     */
    fun createLandscapeCornerMove(
        pointType: AdjustmentPointType,
        previousPosition: LandmarkPoint,
        newPosition: LandmarkPoint,
        originalAdjustment: LandscapeManualAdjustment,
    ): PointMoveCommand {
        val cornerIndex = when (pointType) {
            AdjustmentPointType.CORNER_TOP_LEFT -> 0
            AdjustmentPointType.CORNER_TOP_RIGHT -> 1
            AdjustmentPointType.CORNER_BOTTOM_LEFT -> 2
            AdjustmentPointType.CORNER_BOTTOM_RIGHT -> 3
            else -> throw IllegalArgumentException("Invalid landscape point type: $pointType")
        }

        val newCorners = originalAdjustment.cornerKeypoints.toMutableList()
        newCorners[cornerIndex] = newPosition

        val resultAdjustment = originalAdjustment.copy(
            cornerKeypoints = newCorners,
            timestamp = currentTimeMillis(),
        )

        return PointMoveCommand(
            timestamp = currentTimeMillis(),
            description = "Move ${pointType.displayName} corner",
            pointType = pointType,
            previousPosition = previousPosition,
            newPosition = newPosition,
            originalAdjustment = originalAdjustment,
            resultAdjustment = resultAdjustment,
        )
    }
}
