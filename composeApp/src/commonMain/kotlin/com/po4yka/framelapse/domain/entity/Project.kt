package com.po4yka.framelapse.domain.entity

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
    /** The content type for this project's alignment (FACE, BODY, MUSCLE, or LANDSCAPE). */
    val contentType: ContentType = ContentType.FACE,
    /**
     * The muscle region for MUSCLE content type projects.
     * Only used when contentType is MUSCLE, null otherwise.
     */
    val muscleRegion: MuscleRegion? = null,
    /**
     * The reference frame ID for LANDSCAPE content type projects.
     * This is the frame that all other frames will be aligned to.
     * User-selectable; defaults to first captured frame if not set.
     * Only used when contentType is LANDSCAPE, null otherwise.
     */
    val referenceFrameId: String? = null,
    /**
     * Path to the calibration reference image.
     * Used as ghost overlay and alignment target for FACE mode.
     * Null means no calibration is set.
     */
    val calibrationImagePath: String? = null,
    /**
     * Calibrated left eye X position (normalized 0-1).
     * Null means auto-detect from calibration image.
     */
    val calibrationLeftEyeX: Float? = null,
    /**
     * Calibrated left eye Y position (normalized 0-1).
     */
    val calibrationLeftEyeY: Float? = null,
    /**
     * Calibrated right eye X position (normalized 0-1).
     */
    val calibrationRightEyeX: Float? = null,
    /**
     * Calibrated right eye Y position (normalized 0-1).
     */
    val calibrationRightEyeY: Float? = null,
    /**
     * Alignment offset X adjustment (-0.2 to +0.2).
     * Fine-tunes the horizontal alignment target.
     */
    val calibrationOffsetX: Float = 0f,
    /**
     * Alignment offset Y adjustment (-0.2 to +0.2).
     * Fine-tunes the vertical alignment target.
     */
    val calibrationOffsetY: Float = 0f,
)

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
