package com.po4yka.framelapse.data.mapper

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes and deserializes FaceLandmarks and AlignmentMatrix to/from JSON strings
 * for database storage.
 */
object LandmarksSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serializes FaceLandmarks to a JSON string.
     */
    fun serialize(landmarks: FaceLandmarks): String = json.encodeToString(landmarks)

    /**
     * Deserializes a JSON string to FaceLandmarks.
     * Returns null if parsing fails.
     */
    fun deserialize(jsonString: String): FaceLandmarks? = runCatching {
        json.decodeFromString<FaceLandmarks>(jsonString)
    }.getOrNull()

    /**
     * Serializes AlignmentMatrix to a JSON string.
     */
    fun serializeMatrix(matrix: AlignmentMatrix): String = json.encodeToString(matrix)

    /**
     * Deserializes a JSON string to AlignmentMatrix.
     * Returns null if parsing fails.
     */
    fun deserializeMatrix(jsonString: String): AlignmentMatrix? = runCatching {
        json.decodeFromString<AlignmentMatrix>(jsonString)
    }.getOrNull()
}
