package com.po4yka.framelapse.data.mapper

import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.MuscleRegion
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.data.local.Project as DbProject

/**
 * Maps between SQLDelight-generated Project entity and domain Project entity.
 */
object ProjectMapper {

    /**
     * Converts a database Project entity to a domain Project entity.
     */
    fun toDomain(entity: DbProject): Project = Project(
        id = entity.id,
        name = entity.name,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        fps = entity.fps.toInt(),
        resolution = Resolution.fromString(entity.resolution),
        orientation = Orientation.fromString(entity.orientation),
        thumbnailPath = entity.thumbnailPath,
        contentType = ContentType.fromString(entity.contentType),
        muscleRegion = entity.muscleRegion?.let { MuscleRegion.fromString(it) },
        referenceFrameId = entity.referenceFrameId,
        calibrationImagePath = entity.calibrationImagePath,
        calibrationLeftEyeX = entity.calibrationLeftEyeX?.toFloat(),
        calibrationLeftEyeY = entity.calibrationLeftEyeY?.toFloat(),
        calibrationRightEyeX = entity.calibrationRightEyeX?.toFloat(),
        calibrationRightEyeY = entity.calibrationRightEyeY?.toFloat(),
        calibrationOffsetX = entity.calibrationOffsetX?.toFloat() ?: 0f,
        calibrationOffsetY = entity.calibrationOffsetY?.toFloat() ?: 0f,
    )

    /**
     * Converts a domain Project entity to database insert parameters.
     */
    fun toInsertParams(domain: Project): InsertProjectParams = InsertProjectParams(
        id = domain.id,
        name = domain.name,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        fps = domain.fps.toLong(),
        resolution = domain.resolution.name,
        orientation = domain.orientation.name,
        thumbnailPath = domain.thumbnailPath,
        contentType = domain.contentType.name,
        muscleRegion = domain.muscleRegion?.name,
        referenceFrameId = domain.referenceFrameId,
        calibrationImagePath = domain.calibrationImagePath,
        calibrationLeftEyeX = domain.calibrationLeftEyeX?.toDouble(),
        calibrationLeftEyeY = domain.calibrationLeftEyeY?.toDouble(),
        calibrationRightEyeX = domain.calibrationRightEyeX?.toDouble(),
        calibrationRightEyeY = domain.calibrationRightEyeY?.toDouble(),
        calibrationOffsetX = domain.calibrationOffsetX.toDouble(),
        calibrationOffsetY = domain.calibrationOffsetY.toDouble(),
    )

    /**
     * Converts a domain Project entity to database update parameters.
     */
    fun toUpdateParams(domain: Project): UpdateProjectParams = UpdateProjectParams(
        id = domain.id,
        name = domain.name,
        updatedAt = domain.updatedAt,
        fps = domain.fps.toLong(),
        resolution = domain.resolution.name,
        orientation = domain.orientation.name,
        thumbnailPath = domain.thumbnailPath,
        contentType = domain.contentType.name,
        muscleRegion = domain.muscleRegion?.name,
        referenceFrameId = domain.referenceFrameId,
        calibrationImagePath = domain.calibrationImagePath,
        calibrationLeftEyeX = domain.calibrationLeftEyeX?.toDouble(),
        calibrationLeftEyeY = domain.calibrationLeftEyeY?.toDouble(),
        calibrationRightEyeX = domain.calibrationRightEyeX?.toDouble(),
        calibrationRightEyeY = domain.calibrationRightEyeY?.toDouble(),
        calibrationOffsetX = domain.calibrationOffsetX.toDouble(),
        calibrationOffsetY = domain.calibrationOffsetY.toDouble(),
    )

    /**
     * Creates calibration parameters for updating calibration data.
     */
    fun toCalibrationParams(
        projectId: String,
        imagePath: String,
        leftEyeX: Float,
        leftEyeY: Float,
        rightEyeX: Float,
        rightEyeY: Float,
        offsetX: Float,
        offsetY: Float,
        updatedAt: Long,
    ): CalibrationParams = CalibrationParams(
        id = projectId,
        calibrationImagePath = imagePath,
        calibrationLeftEyeX = leftEyeX.toDouble(),
        calibrationLeftEyeY = leftEyeY.toDouble(),
        calibrationRightEyeX = rightEyeX.toDouble(),
        calibrationRightEyeY = rightEyeY.toDouble(),
        calibrationOffsetX = offsetX.toDouble(),
        calibrationOffsetY = offsetY.toDouble(),
        updatedAt = updatedAt,
    )
}

/**
 * Parameters for inserting a new project into the database.
 */
data class InsertProjectParams(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val fps: Long,
    val resolution: String,
    val orientation: String,
    val thumbnailPath: String?,
    val contentType: String,
    val muscleRegion: String?,
    val referenceFrameId: String?,
    val calibrationImagePath: String?,
    val calibrationLeftEyeX: Double?,
    val calibrationLeftEyeY: Double?,
    val calibrationRightEyeX: Double?,
    val calibrationRightEyeY: Double?,
    val calibrationOffsetX: Double,
    val calibrationOffsetY: Double,
)

/**
 * Parameters for updating an existing project in the database.
 */
data class UpdateProjectParams(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val fps: Long,
    val resolution: String,
    val orientation: String,
    val thumbnailPath: String?,
    val contentType: String,
    val muscleRegion: String?,
    val referenceFrameId: String?,
    val calibrationImagePath: String?,
    val calibrationLeftEyeX: Double?,
    val calibrationLeftEyeY: Double?,
    val calibrationRightEyeX: Double?,
    val calibrationRightEyeY: Double?,
    val calibrationOffsetX: Double,
    val calibrationOffsetY: Double,
)

/**
 * Parameters for updating calibration data only.
 */
data class CalibrationParams(
    val id: String,
    val calibrationImagePath: String,
    val calibrationLeftEyeX: Double,
    val calibrationLeftEyeY: Double,
    val calibrationRightEyeX: Double,
    val calibrationRightEyeY: Double,
    val calibrationOffsetX: Double,
    val calibrationOffsetY: Double,
    val updatedAt: Long,
)
