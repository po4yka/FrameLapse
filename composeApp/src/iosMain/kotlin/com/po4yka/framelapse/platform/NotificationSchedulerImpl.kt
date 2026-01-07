package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDateComponents
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS implementation of NotificationScheduler using UNUserNotificationCenter.
 */
@OptIn(ExperimentalForeignApi::class)
class NotificationSchedulerImpl : NotificationScheduler {

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
    private val userDefaults = NSUserDefaults.standardUserDefaults

    override val hasPermission: Boolean
        get() {
            var authorized = false
            notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
                authorized = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            }
            return authorized
        }

    override suspend fun requestPermission(): Result<Boolean> = suspendCoroutine { continuation ->
        notificationCenter.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, nsError ->
            if (nsError != null) {
                continuation.resume(Result.Error(Exception(nsError.localizedDescription)))
            } else {
                continuation.resume(Result.Success(granted))
            }
        }
    }

    override suspend fun scheduleDaily(hour: Int, minute: Int): Result<Unit> = suspendCoroutine { continuation ->
        // Create notification content
        val content = UNMutableNotificationContent().apply {
            setTitle("FrameLapse Reminder")
            setBody("Time to capture today's photo!")
            setSound(UNNotificationSound.defaultSound)
        }

        // Create date components for trigger
        val dateComponents = NSDateComponents().apply {
            setHour(hour.toLong())
            setMinute(minute.toLong())
        }

        // Create trigger that repeats daily
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = dateComponents,
            repeats = true,
        )

        // Create request
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = NOTIFICATION_ID,
            content = content,
            trigger = trigger,
        )

        // Schedule the notification
        notificationCenter.addNotificationRequest(request) { nsError ->
            if (nsError != null) {
                continuation.resume(Result.Error(Exception(nsError.localizedDescription)))
            } else {
                // Save scheduled time
                userDefaults.setInteger(hour.toLong(), KEY_HOUR)
                userDefaults.setInteger(minute.toLong(), KEY_MINUTE)
                userDefaults.setBool(true, KEY_SCHEDULED)
                userDefaults.synchronize()

                continuation.resume(Result.Success(Unit))
            }
        }
    }

    override suspend fun cancel(): Result<Unit> {
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(NOTIFICATION_ID))

        userDefaults.setBool(false, KEY_SCHEDULED)
        userDefaults.synchronize()

        return Result.Success(Unit)
    }

    override fun isScheduled(): Boolean = userDefaults.boolForKey(KEY_SCHEDULED)

    override fun getScheduledTime(): Pair<Int, Int>? {
        if (!isScheduled()) return null
        val hour = userDefaults.integerForKey(KEY_HOUR).toInt()
        val minute = userDefaults.integerForKey(KEY_MINUTE).toInt()
        return Pair(hour, minute)
    }

    companion object {
        private const val NOTIFICATION_ID = "daily_reminder"
        private const val KEY_HOUR = "scheduled_hour"
        private const val KEY_MINUTE = "scheduled_minute"
        private const val KEY_SCHEDULED = "is_scheduled"
    }
}
