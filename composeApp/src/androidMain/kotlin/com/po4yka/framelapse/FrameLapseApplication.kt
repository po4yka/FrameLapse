package com.po4yka.framelapse

import android.app.Application
import com.po4yka.framelapse.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class FrameLapseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@FrameLapseApplication)
        }
    }
}
