package com.po4yka.framelapse.data.mapper

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Landmarks
import com.po4yka.framelapse.domain.entity.StabilizationResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Serializes and deserializes Landmarks (FaceLandmarks, BodyLandmarks),
 * AlignmentMatrix, and StabilizationResult to/from JSON strings for database storage.
 */
object LandmarksSerializer {

    /**
     * Serializers module for polymorphic Landmarks serialization.
     * Enables JSON to include type discriminator for sealed interface subtypes.
     */
    private val landmarksModule = SerializersModule {
        polymorphic(Landmarks::class) {
            subclass(FaceLandmarks::class)
            subclass(BodyLandmarks::class)
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = landmarksModule
    }

    /**
     * Serializes any Landmarks subtype to a JSON string.
     * The JSON will include a type discriminator for polymorphic deserialization.
     */
    fun serializeLandmarks(landmarks: Landmarks): String = json.encodeToString(landmarks)

    /**
     * Deserializes a JSON string to the appropriate Landmarks subtype.
     * Returns null if parsing fails.
     */
    fun deserializeLandmarks(jsonString: String): Landmarks? = runCatching {
        json.decodeFromString<Landmarks>(jsonString)
    }.getOrNull()

    /**
     * Deserializes a JSON string to FaceLandmarks.
     * Returns null if parsing fails.
     *
     * Note: This method attempts both direct FaceLandmarks deserialization
     * and polymorphic Landmarks deserialization for backward compatibility
     * with legacy data that may not have type discriminators.
     */
    fun deserialize(jsonString: String): FaceLandmarks? = runCatching {
        // First try direct FaceLandmarks deserialization (for legacy data without type discriminator)
        json.decodeFromString<FaceLandmarks>(jsonString)
    }.getOrElse {
        // Fall back to polymorphic deserialization
        deserializeLandmarks(jsonString) as? FaceLandmarks
    }

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

    /**
     * Serializes StabilizationResult to a JSON string.
     */
    fun serializeStabilizationResult(result: StabilizationResult): String = json.encodeToString(result)

    /**
     * Deserializes a JSON string to StabilizationResult.
     * Returns null if parsing fails.
     */
    fun deserializeStabilizationResult(jsonString: String): StabilizationResult? = runCatching {
        json.decodeFromString<StabilizationResult>(jsonString)
    }.getOrNull()
}
