package com.po4yka.framelapse.di

import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.domain.service.ShareHandler
import com.po4yka.framelapse.domain.service.SoundPlayer
import com.po4yka.framelapse.domain.service.VideoEncoder
import com.po4yka.framelapse.platform.CameraControllerImpl
import com.po4yka.framelapse.platform.DatabaseDriverFactory
import com.po4yka.framelapse.platform.FaceDetectorImpl
import com.po4yka.framelapse.platform.FileManager
import com.po4yka.framelapse.platform.ImageProcessorImpl
import com.po4yka.framelapse.platform.NotificationSchedulerImpl
import com.po4yka.framelapse.platform.ShareHandlerImpl
import com.po4yka.framelapse.platform.SoundPlayerImpl
import com.po4yka.framelapse.platform.VideoEncoderImpl
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
    single { FileManager() }
    single<ImageProcessor> { ImageProcessorImpl() }
    single<FaceDetector> { FaceDetectorImpl() }
    single<VideoEncoder> { VideoEncoderImpl() }
    single<CameraController> { CameraControllerImpl() }
    single<NotificationScheduler> { NotificationSchedulerImpl() }
    single<ShareHandler> { ShareHandlerImpl() }
    single<SoundPlayer> { SoundPlayerImpl() }
}
