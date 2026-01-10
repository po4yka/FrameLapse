package com.po4yka.framelapse.domain.usecase.statistics

import com.po4yka.framelapse.domain.entity.StreakInfo
import com.po4yka.framelapse.domain.service.Clock
import org.koin.core.annotation.Factory

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

/**
 * Calculates daily streak information from a list of capture timestamps.
 *
 * A streak is consecutive days with at least one capture.
 * Current streak counts from today or yesterday backwards.
 * Best streak is the longest consecutive sequence ever achieved.
 */
@Factory
class CalculateStreakUseCase(private val clock: Clock) {

    /**
     * Calculates streak information from capture timestamps.
     *
     * @param capturedAtTimestamps List of capture timestamps in milliseconds.
     * @return StreakInfo containing current and best streak values.
     */
    operator fun invoke(capturedAtTimestamps: List<Long>): StreakInfo {
        if (capturedAtTimestamps.isEmpty()) {
            return StreakInfo(
                currentStreak = 0,
                bestStreak = 0,
                lastCaptureDate = null,
            )
        }

        // Normalize all timestamps to day index (days since epoch)
        val captureDays = capturedAtTimestamps
            .map { it / MILLIS_PER_DAY }
            .distinct()
            .sorted()

        val today = clock.nowMillis() / MILLIS_PER_DAY
        val lastCaptureDay = captureDays.last()
        val lastCaptureTimestamp = capturedAtTimestamps.maxOrNull()

        // Calculate current streak
        // Streak is valid if last capture was today or yesterday
        val currentStreak = if (lastCaptureDay >= today - 1) {
            calculateStreakFromDay(captureDays, lastCaptureDay)
        } else {
            0
        }

        // Calculate best streak by checking all possible end points
        val bestStreak = calculateBestStreak(captureDays)

        return StreakInfo(
            currentStreak = currentStreak,
            bestStreak = maxOf(currentStreak, bestStreak),
            lastCaptureDate = lastCaptureTimestamp,
        )
    }

    /**
     * Calculates streak length counting backwards from a given day.
     */
    private fun calculateStreakFromDay(sortedDays: List<Long>, endDay: Long): Int {
        val daySet = sortedDays.toSet()
        var streak = 0
        var currentDay = endDay

        while (currentDay in daySet) {
            streak++
            currentDay--
        }

        return streak
    }

    /**
     * Calculates the best (longest) streak in the entire history.
     */
    private fun calculateBestStreak(sortedDays: List<Long>): Int {
        if (sortedDays.isEmpty()) return 0
        if (sortedDays.size == 1) return 1

        var bestStreak = 1
        var currentStreak = 1

        for (i in 1 until sortedDays.size) {
            val diff = sortedDays[i] - sortedDays[i - 1]
            if (diff == 1L) {
                currentStreak++
                bestStreak = maxOf(bestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return bestStreak
    }
}
