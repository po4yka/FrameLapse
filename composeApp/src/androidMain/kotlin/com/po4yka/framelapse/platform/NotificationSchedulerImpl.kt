package com.po4yka.framelapse.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.domain.util.Result
import java.util.Calendar

/**
 * Android implementation of NotificationScheduler using AlarmManager.
 */
class NotificationSchedulerImpl(private val context: Context) : NotificationScheduler {

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createNotificationChannel()
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

        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
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
        sharedPrefs.edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putBoolean(KEY_SCHEDULED, true)
            .apply()

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

    override suspend fun cancel(): Result<Unit> = try {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.cancel(pendingIntent)

        sharedPrefs.edit()
            .putBoolean(KEY_SCHEDULED, false)
            .apply()

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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
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
        private const val ALARM_REQUEST_CODE = 1001
        const val CHANNEL_ID = "daily_reminder_channel"
        const val NOTIFICATION_ID = 1
    }
}

/**
 * BroadcastReceiver that shows the daily reminder notification.
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        showNotification(context)
    }

    private fun showNotification(context: Context) {
        // Create intent to open the app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationSchedulerImpl.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("FrameLapse Reminder")
            .setContentText("Time to capture today's photo!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasPermission) {
            NotificationManagerCompat.from(context)
                .notify(NotificationSchedulerImpl.NOTIFICATION_ID, notification)
        }
    }
}
