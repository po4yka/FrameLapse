package com.po4yka.framelapse.platform

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

actual fun currentInstant(): Instant = Clock.System.now()
