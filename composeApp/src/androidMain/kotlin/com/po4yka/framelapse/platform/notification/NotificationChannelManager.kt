/*
 * Adapted from alarmee library (https://github.com/nicoschwabe/alarmee)
 * Copyright 2024 Tweener
 * Licensed under the Apache License, Version 2.0
 */

package com.po4yka.framelapse.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.po4yka.framelapse.core.notification.NotificationChannels

/**
 * Manages Android notification channel creation and registration.
 *
 * Channels are required for Android 8.0 (API 26) and above.
 */
class NotificationChannelManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Registers a notification channel if it doesn't already exist.
     *
     * @param id Unique channel ID.
     * @param name User-visible channel name.
     * @param description Optional channel description.
     * @param importance Channel importance level.
     * @param soundFilename Optional sound filename in res/raw (without extension).
     */
    fun register(
        id: String,
        name: String,
        description: String? = null,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        soundFilename: String? = null,
    ) {
        // Check if channel already exists
        if (notificationManager.notificationChannels.any { it.id == id }) {
            return
        }

        val channel = NotificationChannel(id, name, importance).apply {
            description?.let { this.description = it }

            soundFilename?.let { filename ->
                val soundUri = getRawResourceUri(filename)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
        }

        notificationManager.createNotificationChannel(channel)
        println("Notification channel '$name' (ID: '$id') created.")
    }

    /**
     * Registers all default FrameLapse notification channels.
     */
    fun registerDefaultChannels() {
        register(
            id = NotificationChannels.REMINDERS,
            name = "Daily Reminders",
            description = "Daily reminders to capture your photo",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )

        register(
            id = NotificationChannels.EXPORT,
            name = "Export Complete",
            description = "Notifications when video or GIF export is complete",
            importance = NotificationManager.IMPORTANCE_HIGH,
        )

        register(
            id = NotificationChannels.PROGRESS,
            name = "Progress Updates",
            description = "Progress and status updates",
            importance = NotificationManager.IMPORTANCE_LOW,
        )
    }

    /**
     * Gets a URI for a raw resource file.
     *
     * @param filename Resource filename without extension.
     * @return URI pointing to the raw resource.
     */
    fun getRawResourceUri(filename: String): Uri {
        val resourceId = context.resources.getIdentifier(
            filename,
            "raw",
            context.packageName,
        )

        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .path(resourceId.toString())
            .build()
    }

    /**
     * Checks if a channel exists.
     */
    fun channelExists(channelId: String): Boolean = notificationManager.notificationChannels.any { it.id == channelId }
}
