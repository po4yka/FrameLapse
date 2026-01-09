package com.po4yka.framelapse.di

import com.po4yka.framelapse.data.local.DatabaseDriverFactory
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.GifEncoder
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.service.NotificationScheduler
import com.po4yka.framelapse.domain.service.ProcessingQueue
import com.po4yka.framelapse.domain.service.ShareHandler
import com.po4yka.framelapse.domain.service.SoundPlayer
import com.po4yka.framelapse.domain.service.VideoEncoder
import com.po4yka.framelapse.platform.BodyPoseDetectorImpl
import com.po4yka.framelapse.platform.FaceDetectorImpl
import com.po4yka.framelapse.platform.FeatureMatcherImpl
import com.po4yka.framelapse.platform.FileManager
import com.po4yka.framelapse.platform.GifEncoderImpl
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
    single<BodyPoseDetector> { BodyPoseDetectorImpl() }
    single<FeatureMatcher> { FeatureMatcherImpl() }
    single<VideoEncoder> { VideoEncoderImpl() }
    single<GifEncoder> { GifEncoderImpl() }
    single<NotificationScheduler> { NotificationSchedulerImpl() }
    single { ProcessingQueue() }
    single<ShareHandler> { ShareHandlerImpl() }
    single<SoundPlayer> { SoundPlayerImpl() }
}
