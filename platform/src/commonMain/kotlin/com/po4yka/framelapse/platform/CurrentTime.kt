package com.po4yka.framelapse.platform

import kotlinx.datetime.Instant

/**
 * Returns the current instant from the system clock.
 * This is a workaround for Clock.System not being accessible in some KMP configurations.
 */
expect fun currentInstant(): Instant
