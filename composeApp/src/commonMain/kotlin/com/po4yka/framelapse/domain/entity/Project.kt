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
enum class Orientation {
    PORTRAIT,
    LANDSCAPE,
    ;

    companion object {
        fun fromString(value: String): Orientation = entries.find { it.name == value } ?: PORTRAIT
    }
}
