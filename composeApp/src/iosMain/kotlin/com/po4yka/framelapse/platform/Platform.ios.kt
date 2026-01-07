package com.po4yka.framelapse.platform

import platform.UIKit.UIDevice

actual class Platform actual constructor() {
    actual val name: String = "iOS"
    actual val version: String = UIDevice.currentDevice.systemVersion
}
