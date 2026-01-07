package com.po4yka.framelapse.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

/**
 * Initializes Koin dependency injection.
 *
 * @param appDeclaration Optional platform-specific configuration
 * @return KoinApplication instance
 */
fun initKoin(appDeclaration: (KoinApplication.() -> Unit)? = null): KoinApplication = startKoin {
    appDeclaration?.invoke(this)
    modules(appModules())
}
