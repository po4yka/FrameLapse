package com.po4yka.framelapse.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.po4yka.framelapse.domain.entity.ExportSettings
import com.po4yka.framelapse.domain.entity.VideoCodec
import com.po4yka.framelapse.domain.service.VideoEncoder
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoderImpl(private val context: Context) : VideoEncoder {

    private val encodingInProgress = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    override val isEncoding: Boolean
        get() = encodingInProgress.get()

    override suspend fun encode(
        framePaths: List<String>,
        outputPath: String,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): Result<String> = withContext(Dispatchers.Default) {
        if (encodingInProgress.get()) {
            return@withContext Result.Error(
                IllegalStateException("Encoding already in progress"),
                "Encoding already in progress",
            )
        }

        if (framePaths.isEmpty()) {
            return@withContext Result.Error(
                IllegalArgumentException("No frames to encode"),
                "No frames provided",
            )
        }

        encodingInProgress.set(true)
        cancelRequested.set(false)

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null

        try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            val width = settings.resolution.width
            val height = settings.resolution.height
            val fps = settings.fps
            val mimeType = settings.codec.mimeType
            val bitrate = calculateBitrate(width, height, fps, settings.quality.bitrateMultiplier)

            // Create media format
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            // Find encoder
            val codecName = selectCodec(mimeType)
                ?: return@withContext Result.Error(
                    UnsupportedOperationException("No encoder found for $mimeType"),
                    "No suitable encoder found",
                )

            encoder = MediaCodec.createByCodecName(codecName)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            inputSurface = encoder.createInputSurface()
            encoder.start()

            // Create muxer
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps
            var presentationTimeUs = 0L
            var frameIndex = 0

            while (frameIndex < framePaths.size && isActive && !cancelRequested.get()) {
                // Draw frame to surface
                val framePath = framePaths[frameIndex]
                val bitmap = loadAndScaleBitmap(framePath, width, height)

                if (bitmap != null) {
                    val canvas = inputSurface.lockCanvas(null)
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
                    inputSurface.unlockCanvasAndPost(canvas)
                    bitmap.recycle()

                    // Drain encoder output
                    drainEncoder(encoder, bufferInfo, muxer, trackIndex, muxerStarted) { track, started ->
                        trackIndex = track
                        muxerStarted = started
                    }

                    frameIndex++
                    presentationTimeUs += frameDurationUs

                    onProgress(frameIndex.toFloat() / framePaths.size)
                } else {
                    // Skip frames that can't be loaded
                    frameIndex++
                }
            }

            if (cancelRequested.get()) {
                return@withContext Result.Error(
                    InterruptedException("Encoding cancelled"),
                    "Encoding was cancelled",
                )
            }

            // Signal end of stream
            encoder.signalEndOfInputStream()

            // Drain remaining output
            drainEncoderToEnd(encoder, bufferInfo, muxer, trackIndex, muxerStarted) { track, started ->
                trackIndex = track
                muxerStarted = started
            }

            onProgress(1f)
            Result.Success(outputPath)
        } catch (e: Exception) {
            // Clean up output file on error
            File(outputPath).delete()
            Result.Error(e, "Encoding failed: ${e.message}")
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            inputSurface?.release()
            encodingInProgress.set(false)
        }
    }

    override suspend fun cancel(): Result<Unit> {
        if (!encodingInProgress.get()) {
            return Result.Success(Unit)
        }
        cancelRequested.set(true)
        return Result.Success(Unit)
    }

    override fun getSupportedCodecs(): List<VideoCodec> {
        val supported = mutableListOf<VideoCodec>()
        if (isCodecSupported(VideoCodec.H264)) {
            supported.add(VideoCodec.H264)
        }
        if (isCodecSupported(VideoCodec.HEVC)) {
            supported.add(VideoCodec.HEVC)
        }
        return supported
    }

    override fun isCodecSupported(codec: VideoCodec): Boolean = selectCodec(codec.mimeType) != null

    override fun getMaxSupportedResolution(): Pair<Int, Int> {
        // Most modern Android devices support at least 4K encoding
        return Pair(3840, 2160)
    }

    private fun selectCodec(mimeType: String): String? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) continue
            for (type in codecInfo.supportedTypes) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    return codecInfo.name
                }
            }
        }
        return null
    }

    private fun calculateBitrate(width: Int, height: Int, fps: Int, qualityMultiplier: Float): Int {
        // Base bitrate calculation: pixels * fps * bits per pixel
        val baseBitrate = width * height * fps * BASE_BITS_PER_PIXEL
        return (baseBitrate * qualityMultiplier).toInt().coerceIn(MIN_BITRATE, MAX_BITRATE)
    }

    private fun loadAndScaleBitmap(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            // First, decode with inJustDecodeBounds to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            // Calculate sample size for memory efficiency
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                targetWidth,
                targetHeight,
            )
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

            // Scale to exact target size
            if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                scaled
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean,
        onTrackChanged: (Int, Boolean) -> Unit,
    ) {
        var currentTrackIndex = trackIndex
        var currentMuxerStarted = muxerStarted

        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    currentTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    currentMuxerStarted = true
                    onTrackChanged(currentTrackIndex, currentMuxerStarted)
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && currentMuxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
                else -> break
            }
        }
    }

    private fun drainEncoderToEnd(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean,
        onTrackChanged: (Int, Boolean) -> Unit,
    ) {
        var currentTrackIndex = trackIndex
        var currentMuxerStarted = muxerStarted

        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, DRAIN_TIMEOUT_US)
            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    currentTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    currentMuxerStarted = true
                    onTrackChanged(currentTrackIndex, currentMuxerStarted)
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && currentMuxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
                else -> break
            }
        }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val DRAIN_TIMEOUT_US = 100_000L
        private const val I_FRAME_INTERVAL = 1
        private const val BASE_BITS_PER_PIXEL = 0.1f
        private const val MIN_BITRATE = 1_000_000
        private const val MAX_BITRATE = 50_000_000
    }
}
