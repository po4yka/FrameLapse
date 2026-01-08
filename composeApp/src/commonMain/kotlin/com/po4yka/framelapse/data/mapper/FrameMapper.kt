package com.po4yka.framelapse.data.mapper

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.Landmarks
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.data.local.Frame as DbFrame

/**
 * Maps between SQLDelight-generated Frame entity and domain Frame entity.
 */
object FrameMapper {

    /**
     * Converts a database Frame entity to a domain Frame entity.
     * Uses polymorphic deserialization to handle both FaceLandmarks and BodyLandmarks.
     */
    fun toDomain(entity: DbFrame): Frame = Frame(
        id = entity.id,
        projectId = entity.projectId,
        originalPath = entity.originalPath,
        alignedPath = entity.alignedPath,
        timestamp = entity.timestamp,
        capturedAt = entity.capturedAt,
        confidence = entity.confidence?.toFloat(),
        landmarks = entity.landmarksJson?.let { json ->
            // Try polymorphic deserialization first, fall back to legacy FaceLandmarks
            LandmarksSerializer.deserializeLandmarks(json)
                ?: LandmarksSerializer.deserialize(json)
        },
        sortOrder = entity.sortOrder.toInt(),
        stabilizationResult = entity.stabilizationResultJson?.let {
            LandmarksSerializer.deserializeStabilizationResult(it)
        },
    )

    /**
     * Converts a domain Frame entity to database insert parameters.
     * Uses polymorphic serialization to handle any Landmarks subtype.
     */
    fun toInsertParams(domain: Frame): InsertFrameParams = InsertFrameParams(
        id = domain.id,
        projectId = domain.projectId,
        originalPath = domain.originalPath,
        alignedPath = domain.alignedPath,
        timestamp = domain.timestamp,
        capturedAt = domain.capturedAt,
        confidence = domain.confidence?.toDouble(),
        landmarksJson = domain.landmarks?.let { LandmarksSerializer.serializeLandmarks(it) },
        sortOrder = domain.sortOrder.toLong(),
        stabilizationResultJson = domain.stabilizationResult?.let {
            LandmarksSerializer.serializeStabilizationResult(it)
        },
    )

    /**
     * Converts alignment update data to database parameters.
     * Accepts any Landmarks subtype (FaceLandmarks or BodyLandmarks).
     */
    fun toAlignedParams(
        id: String,
        alignedPath: String,
        confidence: Float,
        landmarks: Landmarks,
        stabilizationResult: StabilizationResult? = null,
    ): AlignedFrameParams = AlignedFrameParams(
        id = id,
        alignedPath = alignedPath,
        confidence = confidence.toDouble(),
        landmarksJson = LandmarksSerializer.serializeLandmarks(landmarks),
        stabilizationResultJson = stabilizationResult?.let {
            LandmarksSerializer.serializeStabilizationResult(it)
        },
    )

    /**
     * Converts alignment update data to database parameters.
     * Overload for backward compatibility with FaceLandmarks.
     */
    @Deprecated("Use toAlignedParams with Landmarks parameter", ReplaceWith("toAlignedParams(id, alignedPath, confidence, landmarks as Landmarks, stabilizationResult)"))
    fun toAlignedParams(
        id: String,
        alignedPath: String,
        confidence: Float,
        landmarks: FaceLandmarks,
        stabilizationResult: StabilizationResult? = null,
    ): AlignedFrameParams = toAlignedParams(
        id = id,
        alignedPath = alignedPath,
        confidence = confidence,
        landmarks = landmarks as Landmarks,
        stabilizationResult = stabilizationResult,
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
    val stabilizationResultJson: String?,
)

/**
 * Parameters for updating a frame with alignment data.
 */
data class AlignedFrameParams(
    val id: String,
    val alignedPath: String,
    val confidence: Double,
    val landmarksJson: String,
    val stabilizationResultJson: String?,
)
