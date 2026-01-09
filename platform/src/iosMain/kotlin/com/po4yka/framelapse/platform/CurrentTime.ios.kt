package com.po4yka.framelapse.platform

import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentInstant(): Instant {
    val epochSeconds = NSDate().timeIntervalSince1970
    return Instant.fromEpochSeconds(epochSeconds.toLong(), (epochSeconds % 1 * 1_000_000_000).toInt())
}
