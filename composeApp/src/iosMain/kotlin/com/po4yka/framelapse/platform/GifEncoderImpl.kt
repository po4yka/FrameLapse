package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.service.GifEncoder
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFURLRef
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.numberWithDouble
import platform.Foundation.numberWithInt
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithURL
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageDestinationRef
import platform.ImageIO.CGImageDestinationSetProperties
import platform.ImageIO.kCGImagePropertyGIFDelayTime
import platform.ImageIO.kCGImagePropertyGIFDictionary
import platform.ImageIO.kCGImagePropertyGIFLoopCount
import platform.UIKit.UIImage
import platform.UniformTypeIdentifiers.UTTypeGIF
import kotlin.concurrent.AtomicInt

/**
 * iOS implementation of GIF encoder using ImageIO framework.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("MagicNumber", "TooManyFunctions")
class GifEncoderImpl : GifEncoder {

    private val encodingState = AtomicInt(STATE_IDLE)
    private val cancelledState = AtomicInt(0)

    override val isEncoding: Boolean
        get() = encodingState.value == STATE_ENCODING

    override suspend fun encode(
        frames: List<ImageData>,
        outputPath: String,
        delayMs: Int,
        onProgress: (Float) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!encodingState.compareAndSet(STATE_IDLE, STATE_ENCODING)) {
            return@withContext Result.Error(
                IllegalStateException("Encoding already in progress"),
                "Encoding already in progress",
            )
        }

        cancelledState.value = 0

        if (frames.isEmpty()) {
            encodingState.value = STATE_IDLE
            return@withContext Result.Error(
                IllegalArgumentException("No frames to encode"),
                "No frames provided",
            )
        }

        var destination: CGImageDestinationRef? = null

        try {
            val fileManager = NSFileManager.defaultManager
            if (fileManager.fileExistsAtPath(outputPath)) {
                fileManager.removeItemAtPath(outputPath, null)
            }

            val outputURL = NSURL.fileURLWithPath(outputPath)

            // Convert NSURL to CFURLRef for ImageIO
            @Suppress("UNCHECKED_CAST")
            val cfURL = CFBridgingRetain(outputURL) as CFURLRef?

            // Convert UTType identifier to CFString
            @Suppress("UNCHECKED_CAST")
            val cfTypeIdentifier = CFBridgingRetain(UTTypeGIF.identifier) as CFStringRef?

            // Create GIF destination using UTTypeGIF identifier
            destination = CGImageDestinationCreateWithURL(
                cfURL,
                cfTypeIdentifier,
                frames.size.toULong(),
                null,
            )

            // Release the bridged references
            cfURL?.let { CFRelease(it) }
            cfTypeIdentifier?.let { CFRelease(it) }

            if (destination == null) {
                encodingState.value = STATE_IDLE
                return@withContext Result.Error(
                    IllegalStateException("Failed to create GIF destination"),
                    "Failed to create GIF file",
                )
            }

            // Set GIF properties (looping)
            val loopCount = NSNumber.numberWithInt(0) // 0 = infinite loop
            val gifDict = mapOf<Any?, Any?>(
                kCGImagePropertyGIFLoopCount to loopCount,
            )
            val gifProperties = mapOf<Any?, Any?>(
                kCGImagePropertyGIFDictionary to gifDict,
            )

            @Suppress("UNCHECKED_CAST")
            val cfGifProperties = CFBridgingRetain(gifProperties) as CFDictionaryRef?
            CGImageDestinationSetProperties(destination, cfGifProperties)
            cfGifProperties?.let { CFRelease(it) }

            // Frame delay in seconds
            val delaySeconds = delayMs / 1000.0

            frames.forEachIndexed { index, imageData ->
                if (!isActive || cancelledState.value != 0) {
                    CFRelease(destination)
                    fileManager.removeItemAtPath(outputPath, null)
                    encodingState.value = STATE_IDLE
                    return@withContext Result.Error(
                        CancellationException("Encoding cancelled"),
                        "Encoding was cancelled",
                    )
                }

                val cgImage = imageDataToCGImage(imageData)
                if (cgImage != null) {
                    // Create frame properties with delay
                    val delayNumber = NSNumber.numberWithDouble(delaySeconds)
                    val frameGifDict = mapOf<Any?, Any?>(
                        kCGImagePropertyGIFDelayTime to delayNumber,
                    )
                    val frameProperties = mapOf<Any?, Any?>(
                        kCGImagePropertyGIFDictionary to frameGifDict,
                    )

                    @Suppress("UNCHECKED_CAST")
                    val cfFrameProperties = CFBridgingRetain(frameProperties) as CFDictionaryRef?
                    CGImageDestinationAddImage(destination, cgImage, cfFrameProperties)
                    cfFrameProperties?.let { CFRelease(it) }

                    CGImageRelease(cgImage)
                }

                onProgress((index + 1).toFloat() / frames.size)
            }

            val success = CGImageDestinationFinalize(destination)
            CFRelease(destination)
            destination = null

            encodingState.value = STATE_IDLE

            if (!success) {
                fileManager.removeItemAtPath(outputPath, null)
                return@withContext Result.Error(
                    IllegalStateException("Failed to finalize GIF"),
                    "Failed to write GIF file",
                )
            }

            onProgress(1f)
            Result.Success(outputPath)
        } catch (e: Exception) {
            destination?.let { CFRelease(it) }
            encodingState.value = STATE_IDLE
            NSFileManager.defaultManager.removeItemAtPath(outputPath, null)
            Result.Error(e, "GIF encoding failed: ${e.message}")
        }
    }

    override fun cancel(): Result<Unit> {
        if (encodingState.value != STATE_ENCODING) {
            return Result.Success(Unit)
        }
        cancelledState.value = 1
        return Result.Success(Unit)
    }

    /**
     * Converts ImageData to CGImage for GIF encoding.
     * The ImageData bytes are expected to be PNG encoded from ImageProcessor.
     */
    private fun imageDataToCGImage(imageData: ImageData): CGImageRef? {
        // The ImageData.bytes from ImageProcessor are PNG encoded
        // We need to decode them to raw pixels first using UIImage
        val data = imageData.bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageData.bytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(data) ?: return null
        return uiImage.CGImage
    }

    companion object {
        private const val STATE_IDLE = 0
        private const val STATE_ENCODING = 1
    }
}
