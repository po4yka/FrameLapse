package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.ExportSettings
import com.po4yka.framelapse.domain.entity.VideoCodec
import com.po4yka.framelapse.domain.service.VideoEncoder
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVAssetWriterStatusFailed
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecH264
import platform.AVFoundation.AVVideoCodecHEVC
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoWidthKey
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferCGBitmapContextCompatibilityKey
import platform.CoreVideo.kCVPixelBufferCGImageCompatibilityKey
import platform.CoreVideo.kCVPixelBufferHeightKey
import platform.CoreVideo.kCVPixelBufferWidthKey
import platform.CoreVideo.kCVPixelFormatType_32ARGB
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import kotlin.concurrent.AtomicInt

@OptIn(ExperimentalForeignApi::class)
class VideoEncoderImpl : VideoEncoder {

    private val encodingState = AtomicInt(STATE_IDLE)
    private val cancelledState = AtomicInt(0)

    override val isEncoding: Boolean
        get() = encodingState.value == STATE_ENCODING

    override suspend fun encode(
        framePaths: List<String>,
        outputPath: String,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!encodingState.compareAndSet(STATE_IDLE, STATE_ENCODING)) {
            return@withContext Result.Error(
                IllegalStateException("Encoding already in progress"),
                "Encoding already in progress",
            )
        }

        cancelledState.value = 0

        if (framePaths.isEmpty()) {
            encodingState.value = STATE_IDLE
            return@withContext Result.Error(
                IllegalArgumentException("No frames to encode"),
                "No frames provided",
            )
        }

        try {
            val fileManager = NSFileManager.defaultManager
            if (fileManager.fileExistsAtPath(outputPath)) {
                fileManager.removeItemAtPath(outputPath, null)
            }

            val outputURL = NSURL.fileURLWithPath(outputPath)
            val assetWriter = AVAssetWriter.assetWriterWithURL(outputURL, AVFileTypeMPEG4, null)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to create asset writer"),
                    "Failed to create video writer",
                ).also { encodingState.value = STATE_IDLE }

            val width = settings.resolution.width
            val height = settings.resolution.height
            val fps = settings.fps
            val bitrate = calculateBitrate(width, height, fps, settings.quality.bitrateMultiplier)

            val codecType = when (settings.codec) {
                VideoCodec.H264 -> AVVideoCodecH264
                VideoCodec.HEVC -> AVVideoCodecHEVC
            }

            val videoSettings = mapOf<Any?, Any?>(
                AVVideoCodecKey to codecType,
                AVVideoWidthKey to width,
                AVVideoHeightKey to height,
                AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(
                    AVVideoAverageBitRateKey to bitrate,
                ),
            )

            val writerInput = AVAssetWriterInput.assetWriterInputWithMediaType(
                AVMediaTypeVideo,
                videoSettings,
            )
            writerInput.expectsMediaDataInRealTime = false

            val sourcePixelBufferAttributes = mapOf<Any?, Any?>(
                kCVPixelBufferWidthKey to width,
                kCVPixelBufferHeightKey to height,
                kCVPixelBufferCGImageCompatibilityKey to true,
                kCVPixelBufferCGBitmapContextCompatibilityKey to true,
            )

            val adaptor = AVAssetWriterInputPixelBufferAdaptor.assetWriterInputPixelBufferAdaptorWithAssetWriterInput(
                writerInput,
                sourcePixelBufferAttributes,
            )

            if (!assetWriter.canAddInput(writerInput)) {
                encodingState.value = STATE_IDLE
                return@withContext Result.Error(
                    IllegalStateException("Cannot add video input to asset writer"),
                    "Failed to configure video writer",
                )
            }

            assetWriter.addInput(writerInput)

            if (!assetWriter.startWriting()) {
                encodingState.value = STATE_IDLE
                return@withContext Result.Error(
                    IllegalStateException("Failed to start writing: ${assetWriter.error?.localizedDescription}"),
                    "Failed to start video encoding",
                )
            }

            assetWriter.startSessionAtSourceTime(CMTimeMake(value = 0, timescale = fps))

            var frameIndex = 0

            for (framePath in framePaths) {
                if (!isActive || cancelledState.value != 0) {
                    assetWriter.cancelWriting()
                    fileManager.removeItemAtPath(outputPath, null)
                    encodingState.value = STATE_IDLE
                    return@withContext Result.Error(
                        CancellationException("Encoding cancelled"),
                        "Encoding was cancelled",
                    )
                }

                while (!writerInput.readyForMoreMediaData) {
                    if (!isActive || cancelledState.value != 0) break
                    kotlinx.coroutines.delay(10)
                }

                val image = UIImage.imageWithContentsOfFile(framePath)
                if (image != null) {
                    val pixelBuffer = createPixelBufferFromImage(image, width, height)
                    if (pixelBuffer != null) {
                        val presentationTime = CMTimeMake(value = frameIndex.toLong(), timescale = fps)
                        adaptor.appendPixelBuffer(pixelBuffer, presentationTime)
                        CVPixelBufferRelease(pixelBuffer)
                    }
                }

                frameIndex++
                onProgress(frameIndex.toFloat() / framePaths.size)
            }

            writerInput.markAsFinished()
            assetWriter.finishWritingWithCompletionHandler { }

            while (assetWriter.status != AVAssetWriterStatusCompleted &&
                assetWriter.status != AVAssetWriterStatusFailed
            ) {
                kotlinx.coroutines.delay(50)
            }

            encodingState.value = STATE_IDLE

            if (assetWriter.status == AVAssetWriterStatusFailed) {
                fileManager.removeItemAtPath(outputPath, null)
                return@withContext Result.Error(
                    IllegalStateException("Encoding failed: ${assetWriter.error?.localizedDescription}"),
                    "Video encoding failed",
                )
            }

            onProgress(1f)
            Result.Success(outputPath)
        } catch (e: Exception) {
            encodingState.value = STATE_IDLE
            NSFileManager.defaultManager.removeItemAtPath(outputPath, null)
            Result.Error(e, "Encoding failed: ${e.message}")
        }
    }

    override suspend fun cancel(): Result<Unit> {
        if (encodingState.value != STATE_ENCODING) {
            return Result.Success(Unit)
        }
        cancelledState.value = 1
        return Result.Success(Unit)
    }

    override fun getSupportedCodecs(): List<VideoCodec> = listOf(VideoCodec.H264, VideoCodec.HEVC)

    override fun isCodecSupported(codec: VideoCodec): Boolean = true

    override fun getMaxSupportedResolution(): Pair<Int, Int> = Pair(3840, 2160)

    private fun calculateBitrate(width: Int, height: Int, fps: Int, qualityMultiplier: Float): Int {
        val baseBitrate = (width * height * fps * BASE_BITS_PER_PIXEL).toInt()
        return (baseBitrate * qualityMultiplier).toInt().coerceIn(MIN_BITRATE, MAX_BITRATE)
    }

    private fun createPixelBufferFromImage(
        image: UIImage,
        targetWidth: Int,
        targetHeight: Int,
    ): platform.CoreVideo.CVPixelBufferRef? {
        val cgImage = image.CGImage ?: return null

        return memScoped {
            val pixelBufferPtr = alloc<platform.CoreVideo.CVPixelBufferRefVar>()

            val result = CVPixelBufferCreate(
                allocator = null,
                width = targetWidth.toULong(),
                height = targetHeight.toULong(),
                pixelFormatType = kCVPixelFormatType_32ARGB,
                pixelBufferAttributes = null,
                pixelBufferOut = pixelBufferPtr.ptr,
            )

            if (result != 0) {
                return@memScoped null
            }

            val pixelBuffer = pixelBufferPtr.value ?: return@memScoped null

            CVPixelBufferLockBaseAddress(pixelBuffer, 0u)

            val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer)
            val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
            val bufferWidth = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val bufferHeight = CVPixelBufferGetHeight(pixelBuffer).toInt()

            val colorSpace = platform.CoreGraphics.CGColorSpaceCreateDeviceRGB()
            val context = platform.CoreGraphics.CGBitmapContextCreate(
                data = baseAddress,
                width = bufferWidth.toULong(),
                height = bufferHeight.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow,
                space = colorSpace,
                bitmapInfo = platform.CoreGraphics.CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or
                    platform.CoreGraphics.kCGBitmapByteOrder32Little,
            )

            if (context != null) {
                val imageWidth = CGImageGetWidth(cgImage).toDouble()
                val imageHeight = CGImageGetHeight(cgImage).toDouble()

                val scaleX = bufferWidth.toDouble() / imageWidth
                val scaleY = bufferHeight.toDouble() / imageHeight
                val scale = minOf(scaleX, scaleY)

                val scaledWidth = imageWidth * scale
                val scaledHeight = imageHeight * scale
                val x = (bufferWidth - scaledWidth) / 2.0
                val y = (bufferHeight - scaledHeight) / 2.0

                val rect = CGRectMake(x, y, scaledWidth, scaledHeight)
                platform.CoreGraphics.CGContextDrawImage(context, rect, cgImage)
                platform.CoreGraphics.CGContextRelease(context)
            }

            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)

            pixelBuffer
        }
    }

    companion object {
        private const val STATE_IDLE = 0
        private const val STATE_ENCODING = 1
        private const val BASE_BITS_PER_PIXEL = 0.1f
        private const val MIN_BITRATE = 1_000_000
        private const val MAX_BITRATE = 50_000_000
    }
}
