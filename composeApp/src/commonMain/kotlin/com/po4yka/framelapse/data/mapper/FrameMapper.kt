package com.po4yka.framelapse.data.mapper

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.data.local.Frame as DbFrame

/**
 * Maps between SQLDelight-generated Frame entity and domain Frame entity.
 */
object FrameMapper {

    /**
     * Converts a database Frame entity to a domain Frame entity.
     */
    fun toDomain(entity: DbFrame): Frame = Frame(
        id = entity.id,
        projectId = entity.projectId,
        originalPath = entity.originalPath,
        alignedPath = entity.alignedPath,
        timestamp = entity.timestamp,
        capturedAt = entity.capturedAt,
        confidence = entity.confidence?.toFloat(),
        landmarks = entity.landmarksJson?.let { LandmarksSerializer.deserialize(it) },
        sortOrder = entity.sortOrder.toInt(),
    )

    /**
     * Converts a domain Frame entity to database insert parameters.
     */
    fun toInsertParams(domain: Frame): InsertFrameParams = InsertFrameParams(
        id = domain.id,
        projectId = domain.projectId,
        originalPath = domain.originalPath,
        alignedPath = domain.alignedPath,
        timestamp = domain.timestamp,
        capturedAt = domain.capturedAt,
        confidence = domain.confidence?.toDouble(),
        landmarksJson = domain.landmarks?.let { LandmarksSerializer.serialize(it) },
        sortOrder = domain.sortOrder.toLong(),
    )

    /**
     * Converts alignment update data to database parameters.
     */
    fun toAlignedParams(
        id: String,
        alignedPath: String,
        confidence: Float,
        landmarks: FaceLandmarks,
    ): AlignedFrameParams = AlignedFrameParams(
        id = id,
        alignedPath = alignedPath,
        confidence = confidence.toDouble(),
        landmarksJson = LandmarksSerializer.serialize(landmarks),
    )
}

/**
 * Parameters for inserting a new frame into the database.
 */
data class InsertFrameParams(
    val id: String,
    val projectId: String,
    val originalPath: String,
    val alignedPath: String?,
    val timestamp: Long,
    val capturedAt: Long,
    val confidence: Double?,
    val landmarksJson: String?,
    val sortOrder: Long,
)

/**
 * Parameters for updating a frame with alignment data.
 */
data class AlignedFrameParams(
    val id: String,
    val alignedPath: String,
    val confidence: Double,
    val landmarksJson: String,
)
