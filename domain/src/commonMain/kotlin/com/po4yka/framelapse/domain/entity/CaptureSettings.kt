package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration for camera capture.
 */
@Serializable
data class CaptureSettings(
    val flashMode: FlashMode = FlashMode.OFF,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val showGrid: Boolean = true,
    val showAlignmentGuide: Boolean = true,
    val ghostOpacity: Float = 0.3f,
) {
    init {
        require(ghostOpacity in 0f..1f) {
            "Ghost opacity must be between 0 and 1"
        }
    }
}

/**
 * Flash modes for camera capture.
 */
@Serializable
enum class FlashMode(val displayName: String) {
    OFF("Off"),
    ON("On"),
    AUTO("Auto"),
}

/**
 * Camera facing direction.
 */
@Serializable
enum class CameraFacing(val displayName: String) {
    FRONT("Front"),
    BACK("Back"),
}
