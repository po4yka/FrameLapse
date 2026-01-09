package com.po4yka.framelapse.data.mapper

import com.po4yka.framelapse.domain.entity.BodyManualAdjustment
import com.po4yka.framelapse.domain.entity.FaceManualAdjustment
import com.po4yka.framelapse.domain.entity.LandscapeManualAdjustment
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.entity.MuscleManualAdjustment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Serializes and deserializes ManualAdjustment to/from JSON strings for database storage.
 */
object ManualAdjustmentSerializer {

    /**
     * Serializers module for polymorphic ManualAdjustment serialization.
     * Enables JSON to include type discriminator for sealed interface subtypes.
     */
    private val adjustmentModule = SerializersModule {
        polymorphic(ManualAdjustment::class) {
            subclass(FaceManualAdjustment::class)
            subclass(BodyManualAdjustment::class)
            subclass(MuscleManualAdjustment::class)
            subclass(LandscapeManualAdjustment::class)
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = adjustmentModule
    }

    /**
     * Serializes any ManualAdjustment subtype to a JSON string.
     * The JSON will include a type discriminator for polymorphic deserialization.
     */
    fun serialize(adjustment: ManualAdjustment): String = json.encodeToString(adjustment)

    /**
     * Deserializes a JSON string to the appropriate ManualAdjustment subtype.
     * Returns null if parsing fails.
     */
    fun deserialize(jsonString: String): ManualAdjustment? = runCatching {
        json.decodeFromString<ManualAdjustment>(jsonString)
    }.getOrNull()

    /**
     * Serializes FaceManualAdjustment to a JSON string.
     */
    fun serializeFace(adjustment: FaceManualAdjustment): String = json.encodeToString(adjustment)

    /**
     * Deserializes a JSON string to FaceManualAdjustment.
     * Returns null if parsing fails.
     */
    fun deserializeFace(jsonString: String): FaceManualAdjustment? = runCatching {
        json.decodeFromString<FaceManualAdjustment>(jsonString)
    }.getOrElse {
        deserialize(jsonString) as? FaceManualAdjustment
    }

    /**
     * Serializes BodyManualAdjustment to a JSON string.
     */
    fun serializeBody(adjustment: BodyManualAdjustment): String = json.encodeToString(adjustment)

    /**
     * Deserializes a JSON string to BodyManualAdjustment.
     * Returns null if parsing fails.
     */
    fun deserializeBody(jsonString: String): BodyManualAdjustment? = runCatching {
        json.decodeFromString<BodyManualAdjustment>(jsonString)
    }.getOrElse {
        deserialize(jsonString) as? BodyManualAdjustment
    }

    /**
     * Serializes MuscleManualAdjustment to a JSON string.
     */
    fun serializeMuscle(adjustment: MuscleManualAdjustment): String = json.encodeToString(adjustment)

    /**
     * Deserializes a JSON string to MuscleManualAdjustment.
     * Returns null if parsing fails.
     */
    fun deserializeMuscle(jsonString: String): MuscleManualAdjustment? = runCatching {
        json.decodeFromString<MuscleManualAdjustment>(jsonString)
    }.getOrElse {
        deserialize(jsonString) as? MuscleManualAdjustment
    }

    /**
     * Serializes LandscapeManualAdjustment to a JSON string.
     */
    fun serializeLandscape(adjustment: LandscapeManualAdjustment): String = json.encodeToString(adjustment)

    /**
     * Deserializes a JSON string to LandscapeManualAdjustment.
     * Returns null if parsing fails.
     */
    fun deserializeLandscape(jsonString: String): LandscapeManualAdjustment? = runCatching {
        json.decodeFromString<LandscapeManualAdjustment>(jsonString)
    }.getOrElse {
        deserialize(jsonString) as? LandscapeManualAdjustment
    }
}
