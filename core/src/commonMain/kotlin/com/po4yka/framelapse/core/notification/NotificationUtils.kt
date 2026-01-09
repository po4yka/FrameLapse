/*
 * Adapted from alarmee library (https://github.com/nicoschwabe/alarmee)
 * Copyright 2024 Tweener
 * Licensed under the Apache License, Version 2.0
 */

package com.po4yka.framelapse.core.notification

import com.po4yka.framelapse.platform.currentInstant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Utility functions for notification scheduling and validation.
 */
object NotificationUtils {

    /**
     * Validates a notification configuration.
     *
     * @param config The configuration to validate.
     * @param isScheduled Whether this is a scheduled (vs immediate) notification.
     * @throws IllegalArgumentException if the configuration is invalid.
     */
    fun validate(config: NotificationConfig, isScheduled: Boolean = false) {
        require(config.uuid.isNotBlank()) { "Notification UUID cannot be blank" }
        require(config.title.isNotBlank()) { "Notification title cannot be blank" }
        require(config.body.isNotBlank()) { "Notification body cannot be blank" }

        if (isScheduled) {
            if (config.repeatInterval == null) {
                requireNotNull(config.scheduledDateTime) {
                    "scheduledDateTime is required for one-off scheduled notifications"
                }
            } else {
                // For repeating notifications, scheduledDateTime defines the base time
                if (config.repeatInterval !is RepeatInterval.Custom) {
                    requireNotNull(config.scheduledDateTime) {
                        "scheduledDateTime is required for repeating notifications"
                    }
                }
            }
        }
    }

    /**
     * Adjusts a scheduled date/time to the future if it has already passed.
     *
     * For one-off notifications, moves to the next day.
     * For repeating notifications, advances by the repeat interval until in the future.
     *
     * @param config The notification configuration.
     * @return The adjusted configuration with updated scheduledDateTime, or the original
     *         if no adjustment was needed.
     */
    fun adjustToFuture(config: NotificationConfig): NotificationConfig {
        val scheduledDateTime = config.scheduledDateTime ?: return config

        val now = currentInstant().toLocalDateTime(config.timeZone)
        var adjusted = scheduledDateTime

        // If already in the future, no adjustment needed
        if (adjusted > now) {
            return config
        }

        // Advance to next valid occurrence
        while (adjusted <= now) {
            adjusted = when (val interval = config.repeatInterval) {
                null -> {
                    // One-off: move to next day at same time
                    adjusted.plus(1, DateTimeUnit.DAY, config.timeZone)
                }
                is RepeatInterval.Hourly -> {
                    adjusted.plus(1, DateTimeUnit.HOUR, config.timeZone)
                }
                is RepeatInterval.Daily -> {
                    adjusted.plus(1, DateTimeUnit.DAY, config.timeZone)
                }
                is RepeatInterval.Weekly -> {
                    adjusted.plus(1, DateTimeUnit.WEEK, config.timeZone)
                }
                is RepeatInterval.Monthly -> {
                    adjusted.plus(1, DateTimeUnit.MONTH, config.timeZone)
                }
                is RepeatInterval.Yearly -> {
                    adjusted.plus(1, DateTimeUnit.YEAR, config.timeZone)
                }
                is RepeatInterval.Custom -> {
                    adjusted.plus(interval.duration, config.timeZone)
                }
            }
        }

        if (adjusted != scheduledDateTime) {
            println(
                "Notification '${config.uuid}': scheduled time was in the past. " +
                    "Adjusted from $scheduledDateTime to $adjusted",
            )
        }

        return config.copy(scheduledDateTime = adjusted)
    }

    /**
     * Converts a LocalDateTime to epoch milliseconds in the given timezone.
     */
    fun LocalDateTime.toEpochMillis(timeZone: TimeZone): Long = this.toInstant(timeZone).toEpochMilliseconds()
}

/**
 * Extension to add a duration to LocalDateTime with timezone support.
 */
internal fun LocalDateTime.plus(duration: kotlin.time.Duration, timeZone: TimeZone): LocalDateTime {
    val instant = this.toInstant(timeZone)
    return instant.plus(duration).toLocalDateTime(timeZone)
}

/**
 * Extension to add date/time units to LocalDateTime with timezone support.
 */
internal fun LocalDateTime.plus(value: Int, unit: DateTimeUnit, timeZone: TimeZone): LocalDateTime {
    val instant = this.toInstant(timeZone)
    return when (unit) {
        is DateTimeUnit.DateBased -> instant.plus(value, unit, timeZone).toLocalDateTime(timeZone)
        is DateTimeUnit.TimeBased -> instant.plus(value.toLong(), unit).toLocalDateTime(timeZone)
    }
}
