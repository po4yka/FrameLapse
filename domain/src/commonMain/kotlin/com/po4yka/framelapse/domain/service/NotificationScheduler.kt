package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.core.notification.NotificationConfig
import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for scheduling and managing notifications.
 *
 * Platform implementations:
 * - Android: AlarmManager + NotificationChannel
 * - iOS: UNUserNotificationCenter
 *
 * Supports both simple daily reminders (legacy API) and flexible scheduling
 * with rich notifications (enhanced API).
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

    // ==================== Legacy API (backward compatible) ====================

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

    // ==================== Enhanced API ====================

    /**
     * Schedule a notification with full configuration options.
     *
     * Supports one-time and repeating notifications with optional:
     * - Custom repeat intervals (hourly, daily, weekly, monthly, yearly, custom)
     * - Deep links for in-app navigation
     * - Image attachments (rich notifications)
     * - Platform-specific customization
     *
     * If the scheduled time is in the past, it will be automatically
     * adjusted to the next valid occurrence.
     *
     * @param config Notification configuration.
     * @return Result indicating success or failure.
     */
    suspend fun schedule(config: NotificationConfig): Result<Unit>

    /**
     * Display a notification immediately.
     *
     * Useful for event-driven notifications like export completion.
     *
     * @param config Notification configuration. scheduledDateTime is ignored.
     * @return Result indicating success or failure.
     */
    suspend fun immediate(config: NotificationConfig): Result<Unit>

    /**
     * Cancel a specific scheduled notification by UUID.
     *
     * @param uuid The unique identifier of the notification to cancel.
     * @return Result indicating success or failure.
     */
    suspend fun cancelByUuid(uuid: String): Result<Unit>

    /**
     * Cancel all scheduled notifications.
     *
     * @return Result indicating success or failure.
     */
    suspend fun cancelAll(): Result<Unit>
}
