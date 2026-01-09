package com.po4yka.framelapse.domain.service

/**
 * Provides the current time for domain logic without binding to platform APIs.
 */
interface Clock {
    fun nowMillis(): Long
}
