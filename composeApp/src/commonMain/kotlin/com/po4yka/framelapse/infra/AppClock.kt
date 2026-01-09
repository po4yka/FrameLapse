package com.po4yka.framelapse.infra

import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.platform.currentTimeMillis

class AppClock : Clock {
    override fun nowMillis(): Long = currentTimeMillis()
}
