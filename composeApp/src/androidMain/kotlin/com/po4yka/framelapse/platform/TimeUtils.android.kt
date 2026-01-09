package com.po4yka.framelapse.platform

import java.util.UUID

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun uuid(): String = UUID.randomUUID().toString()
