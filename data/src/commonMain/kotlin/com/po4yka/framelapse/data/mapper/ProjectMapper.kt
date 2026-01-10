package com.po4yka.framelapse.data.mapper

import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.BodyProjectContent
import com.po4yka.framelapse.domain.entity.FaceProjectContent
import com.po4yka.framelapse.domain.entity.LandscapeProjectContent
import com.po4yka.framelapse.domain.entity.MuscleProjectContent
import com.po4yka.framelapse.domain.entity.MuscleRegion
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.ProjectContent
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.data.local.Project as DbProject

/**
 * Maps between SQLDelight-generated Project entity and domain Project entity.
 */
object ProjectMapper {

    /**
     * Converts a database Project entity to a domain Project entity.
     */
    fun toDomain(entity: DbProject): Project {
        val contentType = ContentType.fromString(entity.contentType)

        val content: ProjectContent = when (contentType) {
            ContentType.FACE -> FaceProjectContent(
                calibrationImagePath = entity.calibrationImagePath,
                calibrationLeftEyeX = entity.calibrationLeftEyeX?.toFloat(),
                calibrationLeftEyeY = entity.calibrationLeftEyeY?.toFloat(),
                calibrationRightEyeX = entity.calibrationRightEyeX?.toFloat(),
                calibrationRightEyeY = entity.calibrationRightEyeY?.toFloat(),
                calibrationOffsetX = entity.calibrationOffsetX?.toFloat() ?: 0f,
                calibrationOffsetY = entity.calibrationOffsetY?.toFloat() ?: 0f,
            )
            ContentType.BODY -> BodyProjectContent()
            ContentType.MUSCLE -> MuscleProjectContent(
                muscleRegion = entity.muscleRegion?.let { MuscleRegion.fromString(it) },
            )
            ContentType.LANDSCAPE -> LandscapeProjectContent(
                referenceFrameId = entity.referenceFrameId,
            )
        }

        return Project(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            fps = entity.fps.toInt(),
            resolution = Resolution.fromString(entity.resolution),
            orientation = Orientation.fromString(entity.orientation),
            thumbnailPath = entity.thumbnailPath,
            content = content,
        )
    }

    /**
     * Converts a domain Project entity to database insert parameters.
     */
    fun toInsertParams(domain: Project): InsertProjectParams {
        val content = domain.content

        return InsertProjectParams(
            id = domain.id,
            name = domain.name,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            fps = domain.fps.toLong(),
            resolution = domain.resolution.name,
            orientation = domain.orientation.name,
            thumbnailPath = domain.thumbnailPath,
            contentType = content.type.name,
            muscleRegion = (content as? MuscleProjectContent)?.muscleRegion?.name,
            referenceFrameId = (content as? LandscapeProjectContent)?.referenceFrameId,
            calibrationImagePath = (content as? FaceProjectContent)?.calibrationImagePath,
            calibrationLeftEyeX = (content as? FaceProjectContent)?.calibrationLeftEyeX?.toDouble(),
            calibrationLeftEyeY = (content as? FaceProjectContent)?.calibrationLeftEyeY?.toDouble(),
            calibrationRightEyeX = (content as? FaceProjectContent)?.calibrationRightEyeX?.toDouble(),
            calibrationRightEyeY = (content as? FaceProjectContent)?.calibrationRightEyeY?.toDouble(),
            calibrationOffsetX = (content as? FaceProjectContent)?.calibrationOffsetX?.toDouble() ?: 0.0,
            calibrationOffsetY = (content as? FaceProjectContent)?.calibrationOffsetY?.toDouble() ?: 0.0,
        )
    }

    /**
     * Converts a domain Project entity to database update parameters.
     */
    fun toUpdateParams(domain: Project): UpdateProjectParams {
        val content = domain.content

        return UpdateProjectParams(
            id = domain.id,
            name = domain.name,
            updatedAt = domain.updatedAt,
            fps = domain.fps.toLong(),
            resolution = domain.resolution.name,
            orientation = domain.orientation.name,
            thumbnailPath = domain.thumbnailPath,
            contentType = content.type.name,
            muscleRegion = (content as? MuscleProjectContent)?.muscleRegion?.name,
            referenceFrameId = (content as? LandscapeProjectContent)?.referenceFrameId,
            calibrationImagePath = (content as? FaceProjectContent)?.calibrationImagePath,
            calibrationLeftEyeX = (content as? FaceProjectContent)?.calibrationLeftEyeX?.toDouble(),
            calibrationLeftEyeY = (content as? FaceProjectContent)?.calibrationLeftEyeY?.toDouble(),
            calibrationRightEyeX = (content as? FaceProjectContent)?.calibrationRightEyeX?.toDouble(),
            calibrationRightEyeY = (content as? FaceProjectContent)?.calibrationRightEyeY?.toDouble(),
            calibrationOffsetX = (content as? FaceProjectContent)?.calibrationOffsetX?.toDouble() ?: 0.0,
            calibrationOffsetY = (content as? FaceProjectContent)?.calibrationOffsetY?.toDouble() ?: 0.0,
        )
    }

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
