package com.po4yka.framelapse.platform

import org.opencv.android.OpenCVLoader

internal class OpenCvInitializer {
    private var isInitialized = false

    fun ensureInitialized() {
        if (isInitialized) return

        isInitialized = try {
            OpenCVLoader.initLocal()
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            OpenCVLoader.initDebug()
        }

        if (!isInitialized) {
            throw IllegalStateException("Failed to initialize OpenCV")
        }
    }

    fun isInitialized(): Boolean = isInitialized
}
