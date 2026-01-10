package com.po4yka.framelapse.di

import org.koin.core.annotation.Module

/**
 * Platform-specific module - implemented via expect/actual @Module class.
 * Provides platform services requiring Context (Android) or platform-specific implementations:
 * - FileManager
 * - DatabaseDriverFactory
 * - FaceDetector, BodyPoseDetector, FeatureMatcher
 * - ImageProcessor, VideoEncoder, GifEncoder
 * - NotificationScheduler, ShareHandler, SoundPlayer
 *
 * Uses @Module annotation for compile-time verification with KOIN_CONFIG_CHECK.
 * Android actual uses KoinPlatformTools to obtain Context.
 * iOS actual creates services with no-arg constructors.
 */
@Module
expect class PlatformModule()
