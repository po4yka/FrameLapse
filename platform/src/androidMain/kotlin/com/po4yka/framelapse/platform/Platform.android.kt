package com.po4yka.framelapse.platform

import android.os.Build

actual class Platform actual constructor() {
    actual val name: String = "Android"
    actual val version: String = "${Build.VERSION.SDK_INT}"
}
