package com.po4yka.framelapse.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

/**
 * Initializes Koin dependency injection using generated modules.
 *
 * Uses Koin Annotations with compile-time verification (KOIN_CONFIG_CHECK=true):
 * - CommonModule: Infrastructure services (Clock, FileSystem, ModelCapabilities)
 * - DataModule: Repositories, data sources, storage managers, database
 * - DomainModule: All use cases via @ComponentScan
 * - PresentationModule: All ViewModels via @ComponentScan
 * - PlatformModule: Platform-specific services via @Single factory functions
 *
 * @param appDeclaration Optional platform-specific configuration (e.g., androidContext)
 * @return KoinApplication instance
 */
fun initKoin(appDeclaration: (KoinApplication.() -> Unit)? = null): KoinApplication = startKoin {
    appDeclaration?.invoke(this)
    modules(
        CommonModule().module,
        DataModule().module,
        DomainModule().module,
        PresentationModule().module,
        PlatformModule().module,
    )
}
