package com.po4yka.framelapse.domain.usecase.statistics

import com.po4yka.framelapse.domain.entity.GlobalStatistics
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.ProjectActivitySummary
import com.po4yka.framelapse.domain.entity.WeeklyCapture
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.util.Result
import org.koin.core.annotation.Factory

private const val MILLIS_PER_WEEK = 7 * 24 * 60 * 60 * 1000L
private const val WEEKS_TO_TRACK = 12

/**
 * Calculates comprehensive statistics across all projects.
 */
@Factory
class GetGlobalStatisticsUseCase(
    private val projectRepository: ProjectRepository,
    private val frameRepository: FrameRepository,
    private val calculateStreakUseCase: CalculateStreakUseCase,
    private val clock: Clock,
) {
    /**
     * Retrieves global statistics across all projects.
     *
     * @return Result containing GlobalStatistics or an error.
     */
    suspend operator fun invoke(): Result<GlobalStatistics> {
        // Get all projects
        val projectsResult = projectRepository.getAllProjects()
        if (projectsResult is Result.Error) {
            return Result.Error(
                projectsResult.exception,
                projectsResult.message ?: "Failed to load projects",
            )
        }
        val projects = (projectsResult as Result.Success).data

        // Get total frame count
        val totalFrameCountResult = frameRepository.getTotalFrameCount()
        if (totalFrameCountResult is Result.Error) {
            return Result.Error(
                totalFrameCountResult.exception,
                totalFrameCountResult.message ?: "Failed to count frames",
            )
        }
        val totalFrames = (totalFrameCountResult as Result.Success).data

        // Collect all capture timestamps across all projects for streak calculation
        val allCapturedAts = mutableListOf<Long>()
        val projectFrameCounts = mutableMapOf<String, Long>()

        for (project in projects) {
            val framesResult = frameRepository.getFramesByProject(project.id)
            if (framesResult is Result.Success) {
                val frames = framesResult.data
                allCapturedAts.addAll(frames.map { it.capturedAt })
                projectFrameCounts[project.id] = frames.size.toLong()
            }
        }

        // Calculate statistics
        val statistics = calculateStatistics(projects, totalFrames, allCapturedAts, projectFrameCounts)

        return Result.Success(statistics)
    }

    private fun calculateStatistics(
        projects: List<Project>,
        totalFrames: Long,
        allCapturedAts: List<Long>,
        projectFrameCounts: Map<String, Long>,
    ): GlobalStatistics {
        // Group projects by content type
        val projectsByContentType = projects.groupBy { it.contentType }
            .mapValues { it.value.size }

        // Calculate global streak (any capture across any project)
        val streakInfo = calculateStreakUseCase(allCapturedAts)

        // Find most active project
        val mostActiveProject = if (projects.isNotEmpty() && projectFrameCounts.isNotEmpty()) {
            val maxEntry = projectFrameCounts.maxByOrNull { it.value }
            maxEntry?.let { (projectId, frameCount) ->
                val project = projects.find { it.id == projectId }
                project?.let {
                    ProjectActivitySummary(
                        projectId = it.id,
                        projectName = it.name,
                        frameCount = frameCount,
                    )
                }
            }
        } else {
            null
        }

        // Calculate weekly capture counts across all projects
        val weeklyCounts = calculateWeeklyCaptures(allCapturedAts)

        return GlobalStatistics(
            totalProjects = projects.size.toLong(),
            totalFrames = totalFrames,
            projectsByContentType = projectsByContentType,
            currentDailyStreak = streakInfo.currentStreak,
            bestDailyStreak = streakInfo.bestStreak,
            weeklyCaptureCounts = weeklyCounts,
            mostActiveProject = mostActiveProject,
        )
    }

    private fun calculateWeeklyCaptures(capturedAts: List<Long>): List<WeeklyCapture> {
        val now = clock.nowMillis()
        val currentWeekStart = getWeekStart(now)

        // Create week buckets for the last N weeks
        val weekBuckets = (0 until WEEKS_TO_TRACK).map { weekOffset ->
            currentWeekStart - (weekOffset * MILLIS_PER_WEEK)
        }.reversed()

        // Count captures per week
        val countsByWeek = weekBuckets.map { weekStart ->
            val weekEnd = weekStart + MILLIS_PER_WEEK
            val count = capturedAts.count { it >= weekStart && it < weekEnd }
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
