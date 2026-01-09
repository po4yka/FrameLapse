/*
 * Adapted from alarmee library (https://github.com/nicoschwabe/alarmee)
 * Copyright 2024 Tweener
 * Licensed under the Apache License, Version 2.0
 */

package com.po4yka.framelapse.core.notification

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/**
 * Configuration for a scheduled or immediate notification.
 *
 * @property uuid Unique identifier for this notification. Used for cancellation and updates.
 * @property title The notification title displayed to the user.
 * @property body The notification body/message displayed to the user.
 * @property scheduledDateTime When to display the notification. Required for scheduled
 *           notifications, optional for immediate notifications.
 * @property timeZone Timezone for the scheduled time. Defaults to system timezone.
 * @property repeatInterval How often to repeat the notification. If null, notification
 *           is one-time only.
 * @property deepLinkUri URI to navigate to when notification is tapped. Format:
 *           "framelapse://screen/arg" for in-app navigation.
 * @property imageUrl URL of an image to display in the notification (rich notification).
 * @property androidConfig Android-specific notification configuration.
 * @property iosConfig iOS-specific notification configuration.
 */
data class NotificationConfig(
    val uuid: String,
    val title: String,
    val body: String,
    val scheduledDateTime: LocalDateTime? = null,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val repeatInterval: RepeatInterval? = null,
    val deepLinkUri: String? = null,
    val imageUrl: String? = null,
    val androidConfig: AndroidNotificationConfig = AndroidNotificationConfig(),
    val iosConfig: IosNotificationConfig = IosNotificationConfig(),
)

/**
 * Android-specific notification configuration.
 *
 * @property channelId Notification channel ID. Must be registered before use.
 *           Defaults to "framelapse_default".
 * @property priority Notification priority level.
 * @property iconResId Resource ID for notification icon. If null, uses default app icon.
 * @property iconColor Color for the notification icon (ARGB format).
 * @property soundFilename Custom sound filename in res/raw (without extension).
 *           If null, uses default sound.
 */
data class AndroidNotificationConfig(
    val channelId: String = DEFAULT_CHANNEL_ID,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
    val iconResId: Int? = null,
    val iconColor: Int? = null,
    val soundFilename: String? = null,
) {
    companion object {
        const val DEFAULT_CHANNEL_ID = "framelapse_default"
    }
}

/**
 * iOS-specific notification configuration.
 *
 * @property soundFilename Custom sound filename (including extension, e.g., "alert.wav").
 *           Must be < 30 seconds. If null, uses default sound.
 * @property badge Badge number to display on app icon. If null, badge is not changed.
 */
data class IosNotificationConfig(val soundFilename: String? = null, val badge: Int? = null)

/**
 * Notification priority levels matching Android notification importance.
 */
enum class NotificationPriority {
    /** Lowest priority. Shows only in notification shade. */
    MINIMUM,

    /** Low priority. No sound or vibration. */
    LOW,

    /** Default priority. Sound and/or vibration based on user settings. */
    DEFAULT,

    /** High priority. May show heads-up notification. */
    HIGH,

    /** Maximum priority. Always shows heads-up notification. */
    MAXIMUM,
}

/**
 * Predefined notification channels for FrameLapse.
 */
object NotificationChannels {
    /** Daily reminder notifications. */
    const val REMINDERS = "framelapse_reminders"

    /** Export/processing complete notifications. */
    const val EXPORT = "framelapse_export"

    /** Progress and status updates. */
    const val PROGRESS = "framelapse_progress"
}
