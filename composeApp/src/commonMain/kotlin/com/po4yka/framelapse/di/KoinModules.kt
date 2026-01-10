package com.po4yka.framelapse.di

import org.koin.core.module.Module

/**
 * Platform-specific module - implemented via expect/actual.
 * Provides platform services requiring Context (Android) or platform-specific implementations:
 * - FileManager
 * - DatabaseDriverFactory
 * - FaceDetector, BodyPoseDetector, FeatureMatcher
 * - ImageProcessor, VideoEncoder, GifEncoder
 * - NotificationScheduler, ShareHandler, SoundPlayer
 *
 * Note: Platform modules remain DSL-based due to Context requirements on Android.
 * This is the recommended pattern when platform dependencies have different constructor
 * signatures between platforms.
 */
expect val platformModule: Module
