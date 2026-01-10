package com.po4yka.framelapse.di

import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.infra.AppClock
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Common module for shared utilities and infrastructure services.
 * Only provides platform-independent dependencies.
 * Platform-dependent services (FileSystem, ModelCapabilities) are in PlatformModule.
 */
@Module
class CommonModule {
    @Single
    fun provideClock(): Clock = AppClock()
}

/**
 * Data layer module.
 * Uses @ComponentScan for annotated classes (repositories, data sources, storage managers).
 * Database providers are in PlatformModule since they depend on DatabaseDriverFactory.
 */
@Module
@ComponentScan("com.po4yka.framelapse.data")
class DataModule

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
