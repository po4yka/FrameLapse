/*
 * Adapted from alarmee library (https://github.com/nicoschwabe/alarmee)
 * Copyright 2024 Tweener
 * Licensed under the Apache License, Version 2.0
 */

package com.po4yka.framelapse.platform.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.po4yka.framelapse.core.notification.NotificationPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Factory for building Android notifications with rich content support.
 */
object NotificationFactory {

    /** Key for passing deep link URI via Intent extras. */
    const val EXTRA_DEEP_LINK_URI = "deep_link_uri"

    /**
     * Creates a notification with optional rich content.
     *
     * @param context Android context.
     * @param channelId Notification channel ID.
     * @param title Notification title.
     * @param body Notification body text.
     * @param priority Notification priority level.
     * @param iconResId Small icon resource ID.
     * @param iconColor Icon color (ARGB).
     * @param soundUri Custom sound URI, or null for default.
     * @param deepLinkUri Deep link URI for tap action.
     * @param imageUrl URL for image attachment.
     * @return Built notification.
     */
    suspend fun create(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        priority: NotificationPriority = NotificationPriority.DEFAULT,
        iconResId: Int = android.R.drawable.ic_menu_camera,
        iconColor: Int? = null,
        soundUri: Uri? = null,
        deepLinkUri: String? = null,
        imageUrl: String? = null,
    ): Notification {
        // Load image asynchronously if provided
        val bitmap = imageUrl?.let { loadImageFromUrl(it) }

        return NotificationCompat.Builder(context, channelId)
            .apply {
                setContentTitle(title)
                setContentText(body)
                setPriority(mapPriority(priority))
                setSmallIcon(iconResId)
                setAutoCancel(true)

                iconColor?.let { setColor(it) }
                soundUri?.let { setSound(it) }

                // Set up tap action with deep link
                setContentIntent(createPendingIntent(context, deepLinkUri))

                // Add image if available
                bitmap?.let { bmp ->
                    setLargeIcon(bmp)
                    setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bmp)
                            .bigLargeIcon(null as Bitmap?),
                    )
                }
            }
            .build()
    }

    /**
     * Maps NotificationPriority to Android NotificationCompat priority.
     */
    private fun mapPriority(priority: NotificationPriority): Int = when (priority) {
        NotificationPriority.MINIMUM -> NotificationCompat.PRIORITY_MIN
        NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
        NotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        NotificationPriority.MAXIMUM -> NotificationCompat.PRIORITY_MAX
    }

    /**
     * Creates a PendingIntent that launches the app with optional deep link.
     */
    private fun createPendingIntent(context: Context, deepLinkUri: String?): PendingIntent? {
        val intent = getLauncherIntent(context)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            deepLinkUri?.let { putExtra(EXTRA_DEEP_LINK_URI, it) }
        } ?: return null

        return PendingIntent.getActivity(
            context,
            deepLinkUri?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Gets the launcher activity intent for this app.
     */
    private fun getLauncherIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)

    /**
     * Downloads an image from URL and returns as Bitmap.
     */
    private suspend fun loadImageFromUrl(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.connect()

            val inputStream = connection.inputStream
            BitmapFactory.decodeStream(inputStream).also {
                inputStream.close()
                connection.disconnect()
            }
        } catch (e: Exception) {
            println("Failed to load notification image from $imageUrl: ${e.message}")
            null
        }
    }
}
