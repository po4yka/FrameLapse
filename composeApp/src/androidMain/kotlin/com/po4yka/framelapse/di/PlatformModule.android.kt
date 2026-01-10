package com.po4yka.framelapse.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.po4yka.framelapse.data.local.DatabaseDriverFactory
import com.po4yka.framelapse.data.local.FrameLapseDatabase
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.FileSystem
import com.po4yka.framelapse.domain.service.GifEncoder
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.service.ModelCapabilitiesProvider
import com.po4yka.framelapse.domain.service.ModelCapabilitiesProviderImpl
import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.domain.service.ShareHandler
import com.po4yka.framelapse.domain.service.SoundPlayer
import com.po4yka.framelapse.domain.service.VideoEncoder
import com.po4yka.framelapse.infra.AppFileSystem
import com.po4yka.framelapse.platform.BodyPoseDetectorImpl
import com.po4yka.framelapse.platform.FaceDetectorImpl
import com.po4yka.framelapse.platform.FeatureMatcherImpl
import com.po4yka.framelapse.platform.FileManager
import com.po4yka.framelapse.platform.GifEncoderImpl
import com.po4yka.framelapse.platform.ImageProcessorImpl
import com.po4yka.framelapse.platform.NotificationSchedulerImpl
import com.po4yka.framelapse.platform.ShareHandlerImpl
import com.po4yka.framelapse.platform.SoundPlayerImpl
import com.po4yka.framelapse.platform.VideoEncoderImpl
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatformTools

/**
 * Android platform module providing platform-specific service implementations.
 * Uses KoinPlatformTools to obtain Context from the Koin context.
 *
 * Also provides infrastructure services that depend on platform services:
 * - FileSystem (depends on FileManager)
 * - ModelCapabilitiesProvider (depends on FaceDetector, BodyPoseDetector, FeatureMatcher)
 * - Database providers (depend on DatabaseDriverFactory)
 */
@Module
actual class PlatformModule actual constructor() {

    /**
     * Obtains Android Context from Koin's platform context.
     * Requires androidContext() to be called during Koin initialization.
     */
    private fun context(): Context = KoinPlatformTools.defaultContext().get().get()

    // Platform service implementations

    @Single
    fun provideDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory(context())

    @Single
    fun provideFileManager(): FileManager = FileManager(context())

    @Single
    fun provideImageProcessor(): ImageProcessor = ImageProcessorImpl(context())

    @Single
    fun provideFaceDetector(): FaceDetector = FaceDetectorImpl(context())

    @Single
    fun provideBodyPoseDetector(): BodyPoseDetector = BodyPoseDetectorImpl(context())

    @Single
    fun provideFeatureMatcher(): FeatureMatcher = FeatureMatcherImpl(context())

    @Single
    fun provideVideoEncoder(): VideoEncoder = VideoEncoderImpl(context())

    @Single
    fun provideGifEncoder(): GifEncoder = GifEncoderImpl(context())

    @Single
    fun provideNotificationScheduler(): NotificationScheduler = NotificationSchedulerImpl(context())

    @Single
    fun provideShareHandler(): ShareHandler = ShareHandlerImpl(context())

    @Single
    fun provideSoundPlayer(): SoundPlayer = SoundPlayerImpl()

    // Infrastructure services that depend on platform services

    @Single
    fun provideFileSystem(fileManager: FileManager): FileSystem = AppFileSystem(fileManager)

    @Single
    fun provideModelCapabilities(
        faceDetector: FaceDetector,
        bodyPoseDetector: BodyPoseDetector,
        featureMatcher: FeatureMatcher,
    ): ModelCapabilitiesProvider = ModelCapabilitiesProviderImpl(faceDetector, bodyPoseDetector, featureMatcher)

    // Database providers

    @Single
    fun provideSqlDriver(driverFactory: DatabaseDriverFactory): SqlDriver = driverFactory.createDriver()

    @Single
    fun provideDatabase(driver: SqlDriver): FrameLapseDatabase = FrameLapseDatabase(driver)

    @Single
    fun provideProjectQueries(database: FrameLapseDatabase) = database.projectQueries

    @Single
    fun provideFrameQueries(database: FrameLapseDatabase) = database.frameQueries

    @Single
    fun provideSettingsQueries(database: FrameLapseDatabase) = database.settingsQueries

    @Single
    fun provideManualAdjustmentQueries(database: FrameLapseDatabase) = database.manualAdjustmentQueries
}
