package com.po4yka.framelapse.di

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
 * Note: Repository implementations will be added in Phase 4.
 */
val dataModule = module {
    // Add data layer dependencies here
    // single { ProjectRepositoryImpl(get()) } bind ProjectRepository::class
    // single { FrameRepositoryImpl(get()) } bind FrameRepository::class
    // single { SettingsRepositoryImpl(get()) } bind SettingsRepository::class
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
    factory { DeleteFrameUseCase(get(), get()) }
    factory { ImportPhotosUseCase(get(), get()) }

    // Face Processing Use Cases
    factory { DetectFaceUseCase(get()) }
    factory { CalculateAlignmentMatrixUseCase() }
    factory { ValidateAlignmentUseCase() }
    factory { AlignFaceUseCase(get(), get(), get(), get(), get(), get()) }

    // Export Use Cases
    factory { CompileVideoUseCase(get(), get(), get()) }
    factory { ExportGifUseCase(get(), get(), get()) }

    // Capture Use Cases
    factory { CaptureImageUseCase(get(), get(), get(), get()) }
}

/**
 * Presentation layer module for ViewModels.
 */
val presentationModule = module {
    // Add presentation layer dependencies here
    // viewModel { ProjectListViewModel(get()) }
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
