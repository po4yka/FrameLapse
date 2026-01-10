package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a timelapse project.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val fps: Int = 30,
    val resolution: Resolution = Resolution.HD_1080P,
    val orientation: Orientation = Orientation.PORTRAIT,
    val thumbnailPath: String? = null,
    val content: ProjectContent,
)

/**
 * Represents the content specific data for a project.
 */
@Serializable
sealed interface ProjectContent {
    val type: ContentType
}

@Serializable
@SerialName("face")
data class FaceProjectContent(
    val calibrationImagePath: String? = null,
    val calibrationLeftEyeX: Float? = null,
    val calibrationLeftEyeY: Float? = null,
    val calibrationRightEyeX: Float? = null,
    val calibrationRightEyeY: Float? = null,
    val calibrationOffsetX: Float = 0f,
    val calibrationOffsetY: Float = 0f,
) : ProjectContent {
    override val type: ContentType = ContentType.FACE
}

@Serializable
@SerialName("body")
data class BodyProjectContent(
    // Keeping a placeholder so serialization has a stable structure.
    val dummy: Boolean = false,
) : ProjectContent {
    override val type: ContentType = ContentType.BODY
}

@Serializable
@SerialName("muscle")
data class MuscleProjectContent(val muscleRegion: MuscleRegion? = null) : ProjectContent {
    override val type: ContentType = ContentType.MUSCLE
}

@Serializable
@SerialName("landscape")
data class LandscapeProjectContent(val referenceFrameId: String? = null) : ProjectContent {
    override val type: ContentType = ContentType.LANDSCAPE
}

/**
 * Video resolution options.
 */
@Serializable
enum class Resolution(val width: Int, val height: Int, val displayName: String) {
    SD_480P(640, 480, "480p"),
    HD_720P(1280, 720, "720p"),
    HD_1080P(1920, 1080, "1080p"),
    UHD_4K(3840, 2160, "4K"),
    ;

    companion object {
        fun fromString(value: String): Resolution = entries.find { it.name == value } ?: HD_1080P
    }
}

/**
 * Video orientation options.
 */
@Serializable
enum class Orientation(val displayName: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    ;

    companion object {
        fun fromString(value: String): Orientation = entries.find { it.name == value } ?: PORTRAIT
    }
}
