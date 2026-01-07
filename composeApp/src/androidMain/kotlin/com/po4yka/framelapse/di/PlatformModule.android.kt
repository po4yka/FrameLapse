package com.po4yka.framelapse.di

import com.po4yka.framelapse.platform.DatabaseDriverFactory
import com.po4yka.framelapse.platform.FileManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { FileManager(androidContext()) }
}
