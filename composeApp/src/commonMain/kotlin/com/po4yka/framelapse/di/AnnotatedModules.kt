package com.po4yka.framelapse.di

import com.po4yka.framelapse.data.local.DatabaseDriverFactory
import com.po4yka.framelapse.data.local.FrameLapseDatabase
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.service.FileSystem
import com.po4yka.framelapse.domain.service.ModelCapabilitiesProvider
import com.po4yka.framelapse.domain.service.ModelCapabilitiesProviderImpl
import com.po4yka.framelapse.infra.AppClock
import com.po4yka.framelapse.infra.AppFileSystem
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Common module for shared utilities and infrastructure services.
 * Provides manually-defined dependencies that cannot use @ComponentScan.
 */
@Module
class CommonModule {
    @Single
    fun provideClock(): Clock = AppClock()

    @Single
    fun provideFileSystem(fileManager: com.po4yka.framelapse.platform.FileManager): FileSystem =
        AppFileSystem(fileManager)

    @Single
    fun provideModelCapabilities(
        faceDetector: com.po4yka.framelapse.domain.service.FaceDetector,
        bodyPoseDetector: com.po4yka.framelapse.domain.service.BodyPoseDetector,
        featureMatcher: com.po4yka.framelapse.domain.service.FeatureMatcher,
    ): ModelCapabilitiesProvider = ModelCapabilitiesProviderImpl(faceDetector, bodyPoseDetector, featureMatcher)
}

/**
 * Data layer module.
 * Uses @ComponentScan for annotated classes (repositories, data sources, storage managers)
 * plus explicit database-related providers.
 */
@Module
@ComponentScan("com.po4yka.framelapse.data")
class DataModule {
    @Single
    fun provideSqlDriver(driverFactory: DatabaseDriverFactory) = driverFactory.createDriver()

    @Single
    fun provideDatabase(driver: app.cash.sqldelight.db.SqlDriver) = FrameLapseDatabase(driver)

    @Single
    fun provideProjectQueries(database: FrameLapseDatabase) = database.projectQueries

    @Single
    fun provideFrameQueries(database: FrameLapseDatabase) = database.frameQueries

    @Single
    fun provideSettingsQueries(database: FrameLapseDatabase) = database.settingsQueries

    @Single
    fun provideManualAdjustmentQueries(database: FrameLapseDatabase) = database.manualAdjustmentQueries
}

/**
 * Domain layer module.
 * Uses @ComponentScan to automatically discover all use cases annotated with @Factory.
 */
@Module
@ComponentScan("com.po4yka.framelapse.domain.usecase")
class DomainModule

/**
 * Presentation layer module.
 * Uses @ComponentScan to automatically discover all ViewModels annotated with @Factory.
 */
@Module
@ComponentScan("com.po4yka.framelapse.presentation")
class PresentationModule
