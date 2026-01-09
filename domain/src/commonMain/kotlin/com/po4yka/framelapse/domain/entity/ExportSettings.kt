package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Configuration for video export.
 */
@Serializable
data class ExportSettings(
    val resolution: Resolution = Resolution.HD_1080P,
    val fps: Int = 30,
    val codec: VideoCodec = VideoCodec.H264,
    val quality: ExportQuality = ExportQuality.HIGH,
    val dateRange: DateRange? = null,
)

/**
 * Supported video codecs for export.
 */
@Serializable
enum class VideoCodec(val displayName: String, val mimeType: String) {
    H264("H.264", "video/avc"),
    HEVC("HEVC/H.265", "video/hevc"),
}

/**
 * Export quality presets affecting bitrate and encoding settings.
 */
@Serializable
enum class ExportQuality(val displayName: String, val bitrateMultiplier: Float) {
    LOW("Low", 0.5f),
    MEDIUM("Medium", 1.0f),
    HIGH("High", 1.5f),
    MAXIMUM("Maximum", 2.5f),
}

/**
 * Date range filter for selecting frames to include in export.
 */
@Serializable
data class DateRange(val startTimestamp: Long, val endTimestamp: Long) {
    init {
        require(startTimestamp <= endTimestamp) {
            "Start timestamp must be before or equal to end timestamp"
        }
    }

    fun contains(timestamp: Long): Boolean = timestamp in startTimestamp..endTimestamp
}
