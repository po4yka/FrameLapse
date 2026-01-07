package com.po4yka.framelapse.data.mapper

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
)
