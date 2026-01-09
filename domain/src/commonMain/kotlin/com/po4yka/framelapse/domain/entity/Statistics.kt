package com.po4yka.framelapse.domain.entity

/**
 * Statistics for a single project.
 */
data class ProjectStatistics(
    /** Project identifier. */
    val projectId: String,
    /** Project name for display. */
    val projectName: String,
    /** Content type of the project. */
    val contentType: ContentType,
    /** Total number of frames in the project. */
    val totalFrames: Long,
    /** Timestamp of first capture (null if no frames). */
    val firstCaptureDate: Long?,
    /** Timestamp of most recent capture (null if no frames). */
    val lastCaptureDate: Long?,
    /** Average face detection confidence (0-1, null if no confidence data). */
    val averageConfidence: Float?,
    /** Percentage of frames with successful alignment (0-100, null if no data). */
    val alignmentSuccessRate: Float?,
    /** Current consecutive days streak of captures. */
    val currentDailyStreak: Int,
    /** Best consecutive days streak ever achieved. */
    val bestDailyStreak: Int,
    /** Weekly capture counts for the last 12 weeks. */
    val weeklyCaptureCounts: List<WeeklyCapture>,
)

/**
 * Global statistics across all projects.
 */
data class GlobalStatistics(
    /** Total number of projects. */
    val totalProjects: Long,
    /** Total frames across all projects. */
    val totalFrames: Long,
    /** Count of projects by content type. */
    val projectsByContentType: Map<ContentType, Int>,
    /** Current consecutive days streak of captures (any project). */
    val currentDailyStreak: Int,
    /** Best consecutive days streak ever achieved (any project). */
    val bestDailyStreak: Int,
    /** Weekly capture counts for the last 12 weeks across all projects. */
    val weeklyCaptureCounts: List<WeeklyCapture>,
    /** Most active project by frame count (null if no projects). */
    val mostActiveProject: ProjectActivitySummary?,
)

/**
 * Weekly capture count for activity visualization.
 */
data class WeeklyCapture(
    /** Start timestamp of the week (Monday 00:00 UTC). */
    val weekStartTimestamp: Long,
    /** Number of captures during this week. */
    val captureCount: Int,
)

/**
 * Summary of project activity for "most active" display.
 */
data class ProjectActivitySummary(
    /** Project identifier. */
    val projectId: String,
    /** Project name for display. */
    val projectName: String,
    /** Number of frames in the project. */
    val frameCount: Long,
)

/**
 * Result of streak calculation.
 */
data class StreakInfo(
    /** Current consecutive days streak. */
    val currentStreak: Int,
    /** Best consecutive days streak ever achieved. */
    val bestStreak: Int,
    /** Timestamp of the last capture (null if no captures). */
    val lastCaptureDate: Long?,
)
