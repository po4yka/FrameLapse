/*
 * iOS implementation adapted from alarmee library patterns
 * (https://github.com/nicoschwabe/alarmee)
 * Copyright 2024 Tweener
 * Licensed under the Apache License, Version 2.0
 */

package com.po4yka.framelapse.platform

import com.po4yka.framelapse.core.notification.NotificationConfig
import com.po4yka.framelapse.core.notification.NotificationUtils
import com.po4yka.framelapse.core.notification.RepeatInterval
import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.temporaryDirectory
import platform.Foundation.writeToURL
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAttachment
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS implementation of NotificationScheduler using UNUserNotificationCenter.
 *
 * Supports both legacy daily reminders and enhanced flexible scheduling
 * with images, sounds, and deep links.
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

    // ==================== Legacy API ====================

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
            identifier = LEGACY_NOTIFICATION_ID,
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
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(LEGACY_NOTIFICATION_ID))

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

    // ==================== Enhanced API ====================

    override suspend fun schedule(config: NotificationConfig): Result<Unit> = suspendCoroutine { continuation ->
        try {
            NotificationUtils.validate(config, isScheduled = true)

            // Adjust to future if needed
            val adjustedConfig = NotificationUtils.adjustToFuture(config)

            // Create trigger based on configuration
            val trigger = createTrigger(adjustedConfig)

            // Create and schedule notification
            configureNotification(
                uuid = adjustedConfig.uuid,
                config = adjustedConfig,
                trigger = trigger,
            ) { error ->
                if (error != null) {
                    continuation.resume(Result.Error(Exception(error)))
                } else {
                    println("Notification '${config.uuid}' scheduled for ${adjustedConfig.scheduledDateTime}")
                    continuation.resume(Result.Success(Unit))
                }
            }
        } catch (e: Exception) {
            continuation.resume(Result.Error(e, e.message))
        }
    }

    override suspend fun immediate(config: NotificationConfig): Result<Unit> = suspendCoroutine { continuation ->
        try {
            NotificationUtils.validate(config, isScheduled = false)

            // Create immediate notification (no trigger)
            configureNotification(
                uuid = config.uuid,
                config = config,
                trigger = null,
            ) { error ->
                if (error != null) {
                    continuation.resume(Result.Error(Exception(error)))
                } else {
                    println("Notification '${config.uuid}' displayed immediately")
                    continuation.resume(Result.Success(Unit))
                }
            }
        } catch (e: Exception) {
            continuation.resume(Result.Error(e, e.message))
        }
    }

    override suspend fun cancelByUuid(uuid: String): Result<Unit> {
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(uuid))
        println("Notification '$uuid' cancelled")
        return Result.Success(Unit)
    }

    override suspend fun cancelAll(): Result<Unit> {
        notificationCenter.removeAllPendingNotificationRequests()
        userDefaults.setBool(false, KEY_SCHEDULED)
        userDefaults.synchronize()
        println("All notifications cancelled")
        return Result.Success(Unit)
    }

    // ==================== Private Helpers ====================

    private fun createTrigger(config: NotificationConfig): UNNotificationTrigger? {
        val scheduledDateTime = config.scheduledDateTime ?: return null
        val repeatInterval = config.repeatInterval

        return if (repeatInterval is RepeatInterval.Custom) {
            // Use time interval trigger for custom durations
            UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                timeInterval = repeatInterval.duration.inWholeSeconds.toDouble(),
                repeats = true,
            )
        } else {
            // Use calendar trigger for standard intervals
            val dateComponents = createDateComponents(scheduledDateTime, repeatInterval)
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = dateComponents,
                repeats = repeatInterval != null,
            )
        }
    }

    private fun createDateComponents(dateTime: LocalDateTime, repeatInterval: RepeatInterval?): NSDateComponents {
        val dateComponents = NSDateComponents().apply {
            calendar = NSCalendar.currentCalendar
            setSecond(dateTime.second.toLong())
            setMinute(dateTime.minute.toLong())
        }

        when (repeatInterval) {
            null -> {
                // One-off: include full date
                dateComponents.setYear(dateTime.year.toLong())
                dateComponents.setMonth(dateTime.monthNumber.toLong())
                dateComponents.setDay(dateTime.dayOfMonth.toLong())
                dateComponents.setHour(dateTime.hour.toLong())
            }
            is RepeatInterval.Hourly -> {
                // Only minute matters for hourly
            }
            is RepeatInterval.Daily -> {
                dateComponents.setHour(dateTime.hour.toLong())
            }
            is RepeatInterval.Weekly -> {
                dateComponents.setHour(dateTime.hour.toLong())
                // Convert ISO 8601 day (Monday=1) to NSCalendar weekday (Sunday=1)
                val isoDay = dateTime.dayOfWeek.isoDayNumber
                val nsWeekday = ((isoDay % 7) + 1).toLong()
                dateComponents.setWeekday(nsWeekday)
            }
            is RepeatInterval.Monthly -> {
                dateComponents.setHour(dateTime.hour.toLong())
                dateComponents.setDay(dateTime.dayOfMonth.toLong())
            }
            is RepeatInterval.Yearly -> {
                dateComponents.setHour(dateTime.hour.toLong())
                dateComponents.setDay(dateTime.dayOfMonth.toLong())
                dateComponents.setMonth(dateTime.monthNumber.toLong())
            }
            is RepeatInterval.Custom -> {
                // Handled separately with UNTimeIntervalNotificationTrigger
            }
        }

        return dateComponents
    }

    private fun configureNotification(
        uuid: String,
        config: NotificationConfig,
        trigger: UNNotificationTrigger?,
        completion: (String?) -> Unit,
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(config.title)
            setBody(config.body)

            // Configure sound
            config.iosConfig.soundFilename?.let { filename ->
                setSound(UNNotificationSound.soundNamed(filename))
            } ?: setSound(UNNotificationSound.defaultSound)

            // Configure badge
            config.iosConfig.badge?.let { badge ->
                setBadge(badge.toLong() as platform.Foundation.NSNumber)
            }

            // Configure deep link via user info
            config.deepLinkUri?.let { uri ->
                setUserInfo(mapOf(DEEP_LINK_URI_KEY to uri))
            }

            // Add image attachment if available
            config.imageUrl?.let { imageUrl ->
                downloadImageAsAttachment(imageUrl)?.let { attachment ->
                    setAttachments(listOf(attachment))
                }
            }
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = uuid,
            content = content,
            trigger = trigger,
        )

        notificationCenter.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, authError ->
            if (granted) {
                notificationCenter.addNotificationRequest(request) { requestError ->
                    if (requestError != null) {
                        completion(requestError.localizedDescription)
                    } else {
                        completion(null)
                    }
                }
            } else if (authError != null) {
                completion(authError.localizedDescription)
            } else {
                completion("Notification permission not granted")
            }
        }
    }

    private fun downloadImageAsAttachment(imageUrl: String): UNNotificationAttachment? {
        val url = NSURL.URLWithString(imageUrl) ?: return null
        val data = NSData.dataWithContentsOfURL(url) ?: return null

        val tmpDir = NSFileManager.defaultManager.temporaryDirectory
        val fileName = "${NSUUID().UUIDString}.jpg"
        val tmpFile = tmpDir.URLByAppendingPathComponent(fileName) ?: return null

        return try {
            if (data.writeToURL(tmpFile, atomically = true)) {
                UNNotificationAttachment.attachmentWithIdentifier(
                    identifier = "image",
                    URL = tmpFile,
                    options = null,
                    error = null,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            println("Failed to create notification attachment: ${e.message}")
            null
        }
    }

    private fun LocalDateTime.toNSDateComponents(timeZone: TimeZone): NSDateComponents {
        val epochSeconds = this.toInstant(timeZone).epochSeconds.toDouble()
        val nsDate = NSDate.dateWithTimeIntervalSince1970(epochSeconds)

        return NSCalendar.currentCalendar.components(
            unitFlags = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
            fromDate = nsDate,
        )
    }

    companion object {
        private const val LEGACY_NOTIFICATION_ID = "daily_reminder"
        private const val KEY_HOUR = "scheduled_hour"
        private const val KEY_MINUTE = "scheduled_minute"
        private const val KEY_SCHEDULED = "is_scheduled"
        private const val DEEP_LINK_URI_KEY = "deep_link_uri"
    }
}
