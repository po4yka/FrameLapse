package com.po4yka.framelapse.di

import com.po4yka.framelapse.data.local.FrameLapseDatabase
import com.po4yka.framelapse.data.local.FrameLocalDataSource
import com.po4yka.framelapse.data.local.ProjectLocalDataSource
import com.po4yka.framelapse.data.local.SettingsLocalDataSource
import com.po4yka.framelapse.data.repository.FrameRepositoryImpl
import com.po4yka.framelapse.data.repository.ProjectRepositoryImpl
import com.po4yka.framelapse.data.repository.SettingsRepositoryImpl
import com.po4yka.framelapse.data.storage.ImageStorageManager
import com.po4yka.framelapse.data.storage.StorageCleanupManager
import com.po4yka.framelapse.data.storage.ThumbnailGenerator
import com.po4yka.framelapse.data.storage.VideoStorageManager
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.repository.SettingsRepository
import com.po4yka.framelapse.domain.usecase.capture.CaptureImageUseCase
import com.po4yka.framelapse.domain.usecase.export.CompileVideoUseCase
import com.po4yka.framelapse.domain.usecase.export.ExportGifUseCase
import com.po4yka.framelapse.domain.usecase.face.AlignFaceUseCase
import com.po4yka.framelapse.domain.usecase.face.CalculateAlignmentMatrixUseCase
import com.po4yka.framelapse.domain.usecase.face.DetectFaceUseCase
import com.po4yka.framelapse.domain.usecase.face.ValidateAlignmentUseCase
import com.po4yka.framelapse.domain.usecase.frame.AddFrameUseCase
import com.po4yka.framelapse.domain.usecase.frame.DeleteFrameUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetFramesUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetLatestFrameUseCase
import com.po4yka.framelapse.domain.usecase.frame.ImportPhotosUseCase
import com.po4yka.framelapse.domain.usecase.project.CreateProjectUseCase
import com.po4yka.framelapse.domain.usecase.project.DeleteProjectUseCase
import com.po4yka.framelapse.domain.usecase.project.GetProjectUseCase
import com.po4yka.framelapse.domain.usecase.project.GetProjectsUseCase
import com.po4yka.framelapse.domain.usecase.project.UpdateProjectSettingsUseCase
import com.po4yka.framelapse.platform.DatabaseDriverFactory
import com.po4yka.framelapse.presentation.capture.CaptureViewModel
import com.po4yka.framelapse.presentation.export.ExportViewModel
import com.po4yka.framelapse.presentation.gallery.GalleryViewModel
import com.po4yka.framelapse.presentation.main.MainViewModel
import com.po4yka.framelapse.presentation.projectlist.ProjectListViewModel
import com.po4yka.framelapse.presentation.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common module for shared utilities and configurations.
 */
val commonModule = module {
    // Add common dependencies here
}

/**
 * Data layer module for repositories and data sources.
 */
val dataModule = module {
    // Database
    single { get<DatabaseDriverFactory>().createDriver() }
    single { FrameLapseDatabase(get()) }
    single { get<FrameLapseDatabase>().projectQueries }
    single { get<FrameLapseDatabase>().frameQueries }
    single { get<FrameLapseDatabase>().settingsQueries }

    // Local Data Sources
    single { ProjectLocalDataSource(get()) }
    single { FrameLocalDataSource(get()) }
    single { SettingsLocalDataSource(get()) }

    // Storage Managers
    single { ImageStorageManager(get()) }
    single { VideoStorageManager(get()) }
    single { ThumbnailGenerator(get(), get()) }
    single { StorageCleanupManager(get(), get(), get()) }

    // Repositories
    single<ProjectRepository> { ProjectRepositoryImpl(get(), get()) }
    single<FrameRepository> { FrameRepositoryImpl(get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
}

/**
 * Domain layer module for use cases.
 */
val domainModule = module {
    // Project Management Use Cases
    factory { CreateProjectUseCase(get()) }
    factory { GetProjectsUseCase(get()) }
    factory { GetProjectUseCase(get()) }
    factory { DeleteProjectUseCase(get(), get(), get()) }
    factory { UpdateProjectSettingsUseCase(get()) }

    // Frame Management Use Cases
    factory { AddFrameUseCase(get(), get(), get()) }
    factory { GetFramesUseCase(get()) }
    factory { GetLatestFrameUseCase(get()) }
    factory { DeleteFrameUseCase(get()) }
    factory { ImportPhotosUseCase(get(), get()) }

    // Face Processing Use Cases
    factory { DetectFaceUseCase(get()) }
    factory { CalculateAlignmentMatrixUseCase() }
    factory { ValidateAlignmentUseCase() }
    factory { AlignFaceUseCase(get(), get(), get(), get(), get(), get()) }

    // Export Use Cases
    factory { CompileVideoUseCase(get(), get(), get()) }
    factory { ExportGifUseCase(get(), get(), get()) }

    // Capture Use Cases (CameraController passed at runtime from UI layer)
    factory { CaptureImageUseCase(get(), get(), get()) }
}

/**
 * Presentation layer module for ViewModels.
 */
val presentationModule = module {
    // ViewModels - factory scope for new instance per screen
    factory { MainViewModel(get()) }
    factory { ProjectListViewModel(get(), get(), get(), get()) }
    factory { CaptureViewModel(get(), get(), get(), get()) }
    factory { GalleryViewModel(get(), get(), get(), get(), get()) }
    factory { ExportViewModel(get(), get(), get()) }
    factory { SettingsViewModel(get(), get()) }
}

/**
 * Platform-specific module - implemented via expect/actual.
 * Provides:
 * - FileManager
 * - DatabaseDriverFactory
 * - Platform service implementations (FaceDetector, ImageProcessor, etc.)
 */
expect val platformModule: Module

/**
 * Returns all application modules for Koin initialization.
 */
fun appModules(): List<Module> = listOf(
    commonModule,
    dataModule,
    domainModule,
    presentationModule,
    platformModule,
)
