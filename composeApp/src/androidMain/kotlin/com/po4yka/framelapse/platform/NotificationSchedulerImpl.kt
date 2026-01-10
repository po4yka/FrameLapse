package com.po4yka.framelapse.platform

import android.Manifest
import android.R
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.po4yka.framelapse.core.notification.NotificationChannels
import com.po4yka.framelapse.core.notification.NotificationConfig
import com.po4yka.framelapse.core.notification.NotificationPriority
import com.po4yka.framelapse.core.notification.NotificationUtils
import com.po4yka.framelapse.core.notification.RepeatInterval
import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.notification.NotificationChannelManager
import com.po4yka.framelapse.platform.notification.NotificationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Android implementation of NotificationScheduler using AlarmManager.
 *
 * Supports both legacy daily reminders and enhanced flexible scheduling.
 */
class NotificationSchedulerImpl(private val context: Context) : NotificationScheduler {

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val channelManager = NotificationChannelManager(context)

    init {
        // Register default channels on initialization
        createLegacyNotificationChannel()
        channelManager.registerDefaultChannels()
    }

    override val hasPermission: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    override suspend fun requestPermission(): Result<Boolean> {
        // Permission must be requested through Activity
        // This returns current state; actual request happens via UI
        return Result.Success(hasPermission)
    }

    // ==================== Legacy API ====================

    override suspend fun scheduleDaily(hour: Int, minute: Int): Result<Unit> = try {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If time has passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_LEGACY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            LEGACY_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent,
        )

        // Save scheduled time
        sharedPrefs.edit {
            putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .putBoolean(KEY_SCHEDULED, true)
        }

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

    override suspend fun cancel(): Result<Unit> = try {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_LEGACY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            LEGACY_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.cancel(pendingIntent)

        sharedPrefs.edit {
            putBoolean(KEY_SCHEDULED, false)
        }

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

    override fun isScheduled(): Boolean = sharedPrefs.getBoolean(KEY_SCHEDULED, false)

    override fun getScheduledTime(): Pair<Int, Int>? {
        if (!isScheduled()) return null
        val hour = sharedPrefs.getInt(KEY_HOUR, -1)
        val minute = sharedPrefs.getInt(KEY_MINUTE, -1)
        return if (hour >= 0 && minute >= 0) Pair(hour, minute) else null
    }

    // ==================== Enhanced API ====================

    override suspend fun schedule(config: NotificationConfig): Result<Unit> = try {
        NotificationUtils.validate(config, isScheduled = true)

        // Adjust to future if needed
        val adjustedConfig = NotificationUtils.adjustToFuture(config)

        val pendingIntent = createPendingIntent(adjustedConfig)
        val triggerTimeMillis = adjustedConfig.scheduledDateTime?.let {
            NotificationUtils.run { it.toEpochMillis(adjustedConfig.timeZone) }
        } ?: System.currentTimeMillis()

        val repeatInterval = adjustedConfig.repeatInterval
        if (repeatInterval != null) {
            scheduleRepeating(triggerTimeMillis, repeatInterval, pendingIntent)
        } else {
            scheduleOneTime(triggerTimeMillis, pendingIntent)
        }

        println("Notification '${config.uuid}' scheduled for ${adjustedConfig.scheduledDateTime}")
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun immediate(config: NotificationConfig): Result<Unit> = try {
        NotificationUtils.validate(config, isScheduled = false)

        val notification = NotificationFactory.create(
            context = context,
            channelId = config.androidConfig.channelId,
            title = config.title,
            body = config.body,
            priority = config.androidConfig.priority,
            iconResId = config.androidConfig.iconResId ?: R.drawable.ic_menu_camera,
            iconColor = config.androidConfig.iconColor,
            soundUri = config.androidConfig.soundFilename?.let {
                channelManager.getRawResourceUri(it)
            },
            deepLinkUri = config.deepLinkUri,
            imageUrl = config.imageUrl,
        )

        if (hasPermission) {
            NotificationManagerCompat.from(context)
                .notify(config.uuid.hashCode(), notification)
            println("Notification '${config.uuid}' displayed immediately")
        }

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

    override suspend fun cancelByUuid(uuid: String): Result<Unit> = try {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_ENHANCED_NOTIFICATION
            putExtra(EXTRA_UUID, uuid)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uuid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.cancel(pendingIntent)
        println("Notification '$uuid' cancelled")

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

    override suspend fun cancelAll(): Result<Unit> = try {
        // Cancel legacy reminder
        cancel()

        // Note: Android doesn't provide a way to cancel all alarms
        // We can only cancel known alarms by their PendingIntent
        println("All known notifications cancelled")

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

    // ==================== Private Helpers ====================

    private fun createPendingIntent(config: NotificationConfig): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_ENHANCED_NOTIFICATION
            putExtra(EXTRA_UUID, config.uuid)
            putExtra(EXTRA_TITLE, config.title)
            putExtra(EXTRA_BODY, config.body)
            putExtra(EXTRA_CHANNEL_ID, config.androidConfig.channelId)
            putExtra(EXTRA_PRIORITY, config.androidConfig.priority.ordinal)
            config.androidConfig.iconResId?.let { putExtra(EXTRA_ICON_RES_ID, it) }
            config.androidConfig.iconColor?.let { putExtra(EXTRA_ICON_COLOR, it) }
            config.androidConfig.soundFilename?.let { putExtra(EXTRA_SOUND_FILENAME, it) }
            config.deepLinkUri?.let { putExtra(EXTRA_DEEP_LINK_URI, it) }
            config.imageUrl?.let { putExtra(EXTRA_IMAGE_URL, it) }
        }

        return PendingIntent.getBroadcast(
            context,
            config.uuid.hashCode(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleOneTime(triggerTimeMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fallback to inexact alarm if exact alarms not permitted
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent,
            )
        }
    }

    private fun scheduleRepeating(
        triggerTimeMillis: Long,
        repeatInterval: RepeatInterval,
        pendingIntent: PendingIntent,
    ) {
        val intervalMillis = when (repeatInterval) {
            is RepeatInterval.Hourly -> AlarmManager.INTERVAL_HOUR
            is RepeatInterval.Daily -> AlarmManager.INTERVAL_DAY
            is RepeatInterval.Weekly -> AlarmManager.INTERVAL_DAY * 7
            is RepeatInterval.Monthly -> AlarmManager.INTERVAL_DAY * 30
            is RepeatInterval.Yearly -> AlarmManager.INTERVAL_DAY * 365
            is RepeatInterval.Custom -> repeatInterval.duration.inWholeMilliseconds
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            intervalMillis,
            pendingIntent,
        )
    }

    private fun createLegacyNotificationChannel() {
        val channel = NotificationChannel(
            LEGACY_CHANNEL_ID,
            "Daily Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Daily reminder to capture your photo"
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_HOUR = "scheduled_hour"
        private const val KEY_MINUTE = "scheduled_minute"
        private const val KEY_SCHEDULED = "is_scheduled"
        private const val LEGACY_ALARM_REQUEST_CODE = 1001
        const val LEGACY_CHANNEL_ID = "daily_reminder_channel"
        const val LEGACY_NOTIFICATION_ID = 1

        // Intent actions
        const val ACTION_LEGACY_REMINDER = "com.po4yka.framelapse.LEGACY_REMINDER"
        const val ACTION_ENHANCED_NOTIFICATION = "com.po4yka.framelapse.NOTIFICATION"

        // Intent extras for enhanced notifications
        const val EXTRA_UUID = "notification_uuid"
        const val EXTRA_TITLE = "notification_title"
        const val EXTRA_BODY = "notification_body"
        const val EXTRA_CHANNEL_ID = "notification_channel_id"
        const val EXTRA_PRIORITY = "notification_priority"
        const val EXTRA_ICON_RES_ID = "notification_icon_res_id"
        const val EXTRA_ICON_COLOR = "notification_icon_color"
        const val EXTRA_SOUND_FILENAME = "notification_sound_filename"
        const val EXTRA_DEEP_LINK_URI = "notification_deep_link_uri"
        const val EXTRA_IMAGE_URL = "notification_image_url"
    }
}

/**
 * BroadcastReceiver that handles scheduled notification alarms.
 *
 * Supports both legacy daily reminders and enhanced notifications with
 * images, deep links, and custom configuration.
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            NotificationSchedulerImpl.ACTION_LEGACY_REMINDER -> showLegacyNotification(context)
            NotificationSchedulerImpl.ACTION_ENHANCED_NOTIFICATION -> {
                scope.launch { showEnhancedNotification(context, intent) }
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showLegacyNotification(context: Context) {
        // Create intent to open the app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification =
            NotificationCompat.Builder(context, NotificationSchedulerImpl.LEGACY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_camera)
                .setContentTitle("FrameLapse Reminder")
                .setContentText("Time to capture today's photo!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        if (hasNotificationPermission(context)) {
            NotificationManagerCompat.from(context)
                .notify(NotificationSchedulerImpl.LEGACY_NOTIFICATION_ID, notification)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun showEnhancedNotification(context: Context, intent: Intent) {
        val uuid = intent.getStringExtra(NotificationSchedulerImpl.EXTRA_UUID) ?: return
        val title = intent.getStringExtra(NotificationSchedulerImpl.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(NotificationSchedulerImpl.EXTRA_BODY) ?: return
        val channelId = intent.getStringExtra(NotificationSchedulerImpl.EXTRA_CHANNEL_ID)
            ?: NotificationChannels.REMINDERS
        val priorityOrdinal = intent.getIntExtra(
            NotificationSchedulerImpl.EXTRA_PRIORITY,
            NotificationPriority.DEFAULT.ordinal,
        )
        val priority = NotificationPriority.entries[priorityOrdinal]
        val iconResId = intent.getIntExtra(
            NotificationSchedulerImpl.EXTRA_ICON_RES_ID,
            R.drawable.ic_menu_camera,
        )
        val iconColor = if (intent.hasExtra(NotificationSchedulerImpl.EXTRA_ICON_COLOR)) {
            intent.getIntExtra(NotificationSchedulerImpl.EXTRA_ICON_COLOR, 0)
        } else {
            null
        }
        val soundFilename = intent.getStringExtra(NotificationSchedulerImpl.EXTRA_SOUND_FILENAME)
        val deepLinkUri = intent.getStringExtra(NotificationSchedulerImpl.EXTRA_DEEP_LINK_URI)
        val imageUrl = intent.getStringExtra(NotificationSchedulerImpl.EXTRA_IMAGE_URL)

        val channelManager = NotificationChannelManager(context)
        val soundUri = soundFilename?.let { channelManager.getRawResourceUri(it) }

        val notification = NotificationFactory.create(
            context = context,
            channelId = channelId,
            title = title,
            body = body,
            priority = priority,
            iconResId = iconResId,
            iconColor = iconColor,
            soundUri = soundUri,
            deepLinkUri = deepLinkUri,
            imageUrl = imageUrl,
        )

        if (hasNotificationPermission(context)) {
            NotificationManagerCompat.from(context)
                .notify(uuid.hashCode(), notification)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
