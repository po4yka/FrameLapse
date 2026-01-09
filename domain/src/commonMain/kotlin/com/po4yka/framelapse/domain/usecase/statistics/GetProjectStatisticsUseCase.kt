package com.po4yka.framelapse.domain.usecase.statistics

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.ProjectStatistics
import com.po4yka.framelapse.domain.entity.WeeklyCapture
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.util.Result

private const val MILLIS_PER_WEEK = 7 * 24 * 60 * 60 * 1000L
private const val WEEKS_TO_TRACK = 12

/**
 * Calculates comprehensive statistics for a single project.
 */
class GetProjectStatisticsUseCase(
    private val projectRepository: ProjectRepository,
    private val frameRepository: FrameRepository,
    private val calculateStreakUseCase: CalculateStreakUseCase,
    private val clock: Clock,
) {
    /**
     * Retrieves statistics for a project.
     *
     * @param projectId The project ID.
     * @return Result containing ProjectStatistics or an error.
     */
    suspend operator fun invoke(projectId: String): Result<ProjectStatistics> {
        // Get project details
        val projectResult = projectRepository.getProject(projectId)
        if (projectResult is Result.Error) {
            return Result.Error(
                projectResult.exception,
                projectResult.message ?: "Failed to load project",
            )
        }
        val project = (projectResult as Result.Success).data

        // Get all frames for the project
        val framesResult = frameRepository.getFramesByProject(projectId)
        if (framesResult is Result.Error) {
            return Result.Error(
                framesResult.exception,
                framesResult.message ?: "Failed to load frames",
            )
        }
        val frames = (framesResult as Result.Success).data

        // Calculate statistics from frames
        val statistics = calculateStatistics(project.id, project.name, project.contentType, frames)

        return Result.Success(statistics)
    }

    private fun calculateStatistics(
        projectId: String,
        projectName: String,
        contentType: com.po4yka.framelapse.domain.entity.ContentType,
        frames: List<Frame>,
    ): ProjectStatistics {
        // Basic counts and dates
        val totalFrames = frames.size.toLong()
        val sortedByCapture = frames.sortedBy { it.capturedAt }
        val firstCaptureDate = sortedByCapture.firstOrNull()?.capturedAt
        val lastCaptureDate = sortedByCapture.lastOrNull()?.capturedAt

        // Calculate average confidence (only from frames with confidence data)
        val confidenceValues = frames.mapNotNull { it.confidence }
        val averageConfidence = if (confidenceValues.isNotEmpty()) {
            confidenceValues.average().toFloat()
        } else {
            null
        }

        // Calculate alignment success rate
        val framesWithStabilization = frames.filter { it.stabilizationResult != null }
        val alignmentSuccessRate = if (framesWithStabilization.isNotEmpty()) {
            val successCount = framesWithStabilization.count { it.stabilizationResult?.success == true }
            (successCount.toFloat() / framesWithStabilization.size) * 100f
        } else {
            null
        }

        // Calculate streak
        val capturedAtTimestamps = frames.map { it.capturedAt }
        val streakInfo = calculateStreakUseCase(capturedAtTimestamps)

        // Calculate weekly capture counts
        val weeklyCounts = calculateWeeklyCaptures(frames)

        return ProjectStatistics(
            projectId = projectId,
            projectName = projectName,
            contentType = contentType,
            totalFrames = totalFrames,
            firstCaptureDate = firstCaptureDate,
            lastCaptureDate = lastCaptureDate,
            averageConfidence = averageConfidence,
            alignmentSuccessRate = alignmentSuccessRate,
            currentDailyStreak = streakInfo.currentStreak,
            bestDailyStreak = streakInfo.bestStreak,
            weeklyCaptureCounts = weeklyCounts,
        )
    }

    private fun calculateWeeklyCaptures(frames: List<Frame>): List<WeeklyCapture> {
        val now = clock.nowMillis()
        val currentWeekStart = getWeekStart(now)

        // Create week buckets for the last N weeks
        val weekBuckets = (0 until WEEKS_TO_TRACK).map { weekOffset ->
            currentWeekStart - (weekOffset * MILLIS_PER_WEEK)
        }.reversed()

        // Group frames by week
        val frameCapturedAts = frames.map { it.capturedAt }
        val countsByWeek = weekBuckets.map { weekStart ->
            val weekEnd = weekStart + MILLIS_PER_WEEK
            val count = frameCapturedAts.count { it >= weekStart && it < weekEnd }
            WeeklyCapture(weekStartTimestamp = weekStart, captureCount = count)
        }

        return countsByWeek
    }

    /**
     * Gets the start of the week (Monday 00:00 UTC) for a given timestamp.
     */
    private fun getWeekStart(timestamp: Long): Long {
        val millisPerDay = 24 * 60 * 60 * 1000L
        val daysSinceEpoch = timestamp / millisPerDay
        // Epoch (1970-01-01) was a Thursday, so we need to adjust
        // Monday = 0, Tuesday = 1, ..., Sunday = 6
        // Thursday offset from Monday = 3
        val dayOfWeek = ((daysSinceEpoch + 3) % 7).toInt() // 0 = Monday
        val daysToSubtract = dayOfWeek
        return (daysSinceEpoch - daysToSubtract) * millisPerDay
    }
}
