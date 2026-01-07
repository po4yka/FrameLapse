package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for scheduling and managing daily reminder notifications.
 * Platform implementations will use:
 * - Android: WorkManager + NotificationChannel
 * - iOS: UNUserNotificationCenter
 */
interface NotificationScheduler {

    /**
     * Whether notification permissions have been granted.
     */
    val hasPermission: Boolean

    /**
     * Request notification permission from the user.
     *
     * @return Result containing true if permission was granted, false otherwise.
     */
    suspend fun requestPermission(): Result<Boolean>

    /**
     * Schedule a daily reminder notification.
     *
     * @param hour Hour of the day (0-23)
     * @param minute Minute of the hour (0-59)
     * @return Result indicating success or failure.
     */
    suspend fun scheduleDaily(hour: Int, minute: Int): Result<Unit>

    /**
     * Cancel any scheduled daily reminders.
     *
     * @return Result indicating success or failure.
     */
    suspend fun cancel(): Result<Unit>

    /**
     * Check if a daily reminder is currently scheduled.
     *
     * @return True if a reminder is scheduled.
     */
    fun isScheduled(): Boolean

    /**
     * Get the currently scheduled reminder time, if any.
     *
     * @return Pair of (hour, minute) or null if no reminder is scheduled.
     */
    fun getScheduledTime(): Pair<Int, Int>?
}
