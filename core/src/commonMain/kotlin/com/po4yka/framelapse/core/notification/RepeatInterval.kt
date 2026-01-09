/*
 * Adapted from alarmee library (https://github.com/nicoschwabe/alarmee)
 * Copyright 2024 Tweener
 * Licensed under the Apache License, Version 2.0
 */

package com.po4yka.framelapse.core.notification

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Defines the repeat interval for scheduled notifications.
 *
 * Use one of the predefined intervals (Hourly, Daily, Weekly, etc.) for common
 * scheduling patterns, or use [Custom] with a specific [Duration] for flexible
 * intervals.
 *
 * Example usage:
 * ```
 * // Schedule hourly reminder
 * val hourly = RepeatInterval.Hourly
 *
 * // Schedule every 2 days
 * val custom = RepeatInterval.Custom(2.days)
 * ```
 */
sealed class RepeatInterval {
    /** Repeat every hour at the same minute. */
    data object Hourly : RepeatInterval()

    /** Repeat every day at the same time. */
    data object Daily : RepeatInterval()

    /** Repeat every week on the same day and time. */
    data object Weekly : RepeatInterval()

    /** Repeat every month on the same day and time. */
    data object Monthly : RepeatInterval()

    /** Repeat every year on the same date and time. */
    data object Yearly : RepeatInterval()

    /**
     * Repeat at a custom interval.
     *
     * @param duration The interval between notifications. Must be at least 1 minute.
     */
    data class Custom(val duration: Duration) : RepeatInterval() {
        init {
            require(duration.inWholeMinutes >= 1) {
                "Custom repeat interval must be at least 1 minute"
            }
        }
    }

    /**
     * Returns the duration of this interval for calculation purposes.
     * Note: Monthly and Yearly are approximations (30 and 365 days respectively).
     */
    fun toDuration(): Duration = when (this) {
        is Hourly -> 1.hours
        is Daily -> 1.days
        is Weekly -> 7.days
        is Monthly -> 30.days
        is Yearly -> 365.days
        is Custom -> duration
    }
}
