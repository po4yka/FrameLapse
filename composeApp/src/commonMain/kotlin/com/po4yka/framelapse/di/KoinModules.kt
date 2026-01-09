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
import com.po4yka.framelapse.domain.usecase.alignment.AlignContentUseCase
import com.po4yka.framelapse.domain.usecase.body.AlignBodyUseCase
import com.po4yka.framelapse.domain.usecase.body.CalculateBodyAlignmentMatrixUseCase
import com.po4yka.framelapse.domain.usecase.body.DetectBodyPoseUseCase
import com.po4yka.framelapse.domain.usecase.body.MultiPassBodyStabilizationUseCase
import com.po4yka.framelapse.domain.usecase.body.ValidateBodyAlignmentUseCase
import com.po4yka.framelapse.domain.usecase.capture.CaptureImageUseCase
import com.po4yka.framelapse.domain.usecase.export.CompileVideoUseCase
import com.po4yka.framelapse.domain.usecase.export.ExportGifUseCase
import com.po4yka.framelapse.domain.usecase.face.AlignFaceUseCase
import com.po4yka.framelapse.domain.usecase.face.CalculateAlignmentMatrixUseCase
import com.po4yka.framelapse.domain.usecase.face.CalculateStabilizationScoreUseCase
import com.po4yka.framelapse.domain.usecase.face.DetectFaceUseCase
import com.po4yka.framelapse.domain.usecase.face.DetectOvershootUseCase
import com.po4yka.framelapse.domain.usecase.face.MultiPassStabilizationUseCase
import com.po4yka.framelapse.domain.usecase.face.RefineRotationUseCase
import com.po4yka.framelapse.domain.usecase.face.RefineScaleUseCase
import com.po4yka.framelapse.domain.usecase.face.RefineTranslationUseCase
import com.po4yka.framelapse.domain.usecase.face.ValidateAlignmentUseCase
import com.po4yka.framelapse.domain.usecase.frame.AddFrameUseCase
import com.po4yka.framelapse.domain.usecase.frame.DeleteFrameUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetFramesUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetLatestFrameUseCase
import com.po4yka.framelapse.domain.usecase.frame.ImportPhotosUseCase
import com.po4yka.framelapse.domain.usecase.landscape.AlignLandscapeUseCase
import com.po4yka.framelapse.domain.usecase.landscape.CalculateHomographyMatrixUseCase
import com.po4yka.framelapse.domain.usecase.landscape.DetectLandscapeFeaturesUseCase
import com.po4yka.framelapse.domain.usecase.landscape.MatchLandscapeFeaturesUseCase
import com.po4yka.framelapse.domain.usecase.landscape.MultiPassLandscapeStabilizationUseCase
import com.po4yka.framelapse.domain.usecase.landscape.RefineMatchQualityUseCase
import com.po4yka.framelapse.domain.usecase.landscape.RefinePerspectiveStabilityUseCase
import com.po4yka.framelapse.domain.usecase.landscape.RefineRansacThresholdUseCase
import com.po4yka.framelapse.domain.usecase.muscle.AlignMuscleUseCase
import com.po4yka.framelapse.domain.usecase.muscle.CalculateMuscleRegionBoundsUseCase
import com.po4yka.framelapse.domain.usecase.muscle.CropToMuscleRegionUseCase
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

    // Multi-Pass Stabilization Use Cases
    factory { CalculateStabilizationScoreUseCase() }
    factory { DetectOvershootUseCase() }
    factory { RefineRotationUseCase() }
    factory { RefineScaleUseCase() }
    factory { RefineTranslationUseCase() }
    factory {
        MultiPassStabilizationUseCase(
            faceDetector = get(),
            imageProcessor = get(),
            calculateMatrix = get(),
            calculateScore = get(),
            detectOvershoot = get(),
            refineRotation = get(),
            refineScale = get(),
            refineTranslation = get(),
        )
    }

    // Face Alignment Use Case (uses multi-pass stabilization)
    factory {
        AlignFaceUseCase(
            faceDetector = get(),
            imageProcessor = get(),
            frameRepository = get(),
            fileManager = get(),
            multiPassStabilization = get(),
            validateAlignment = get(),
        )
    }

    // Body Processing Use Cases
    factory { DetectBodyPoseUseCase(get()) }
    factory { CalculateBodyAlignmentMatrixUseCase() }
    factory { ValidateBodyAlignmentUseCase() }
    factory {
        MultiPassBodyStabilizationUseCase(
            bodyPoseDetector = get(),
            imageProcessor = get(),
            calculateMatrix = get<CalculateBodyAlignmentMatrixUseCase>(),
        )
    }
    factory {
        AlignBodyUseCase(
            bodyPoseDetector = get(),
            imageProcessor = get(),
            frameRepository = get(),
            fileManager = get(),
            multiPassBodyStabilization = get(),
            validateBodyAlignment = get(),
        )
    }

    // Muscle Processing Use Cases
    factory { CalculateMuscleRegionBoundsUseCase() }
    factory {
        CropToMuscleRegionUseCase(
            imageProcessor = get(),
            calculateBounds = get(),
        )
    }
    factory {
        AlignMuscleUseCase(
            alignBody = get(),
            bodyPoseDetector = get(),
            cropToRegion = get(),
            imageProcessor = get(),
            frameRepository = get(),
            fileManager = get(),
        )
    }

    // Landscape Processing Use Cases
    factory { DetectLandscapeFeaturesUseCase(get()) }
    factory { MatchLandscapeFeaturesUseCase(get()) }
    factory { CalculateHomographyMatrixUseCase(get()) }

    // Landscape Multi-Pass Stabilization Use Cases
    factory { RefineMatchQualityUseCase(get()) }
    factory { RefineRansacThresholdUseCase(get()) }
    factory { RefinePerspectiveStabilityUseCase() }
    factory {
        MultiPassLandscapeStabilizationUseCase(
            featureMatcher = get(),
            imageProcessor = get(),
            detectFeatures = get(),
            matchFeatures = get(),
            calculateHomography = get(),
            refineMatchQuality = get(),
            refineRansacThreshold = get(),
            refinePerspectiveStability = get(),
        )
    }

    factory {
        AlignLandscapeUseCase(
            featureMatcher = get(),
            imageProcessor = get(),
            frameRepository = get(),
            fileManager = get(),
            detectFeatures = get(),
            matchFeatures = get(),
            calculateHomography = get(),
            multiPassStabilization = get(),
        )
    }

    // Content Alignment Dispatcher (routes between Face, Body, Muscle, and Landscape alignment)
    factory {
        AlignContentUseCase(
            alignFace = get(),
            alignBody = get(),
            alignMuscle = get(),
            alignLandscape = get(),
        )
    }

    // Export Use Cases
    factory { CompileVideoUseCase(get(), get(), get()) }
    factory { ExportGifUseCase(get(), get(), get(), get()) }

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
