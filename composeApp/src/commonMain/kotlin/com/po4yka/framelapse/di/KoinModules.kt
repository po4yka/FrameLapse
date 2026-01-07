package com.po4yka.framelapse.di

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
    // Add data layer dependencies here
    // single { ProjectRepositoryImpl(get()) } bind ProjectRepository::class
}

/**
 * Domain layer module for use cases.
 */
val domainModule = module {
    // Add domain layer dependencies here
    // factory { CreateProjectUseCase(get()) }
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
