package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.ExportSettings
import com.po4yka.framelapse.domain.entity.VideoCodec
import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for platform-specific video encoding.
 *
 * Implementations will use:
 * - Android: MediaCodec + MediaMuxer
 * - iOS: AVAssetWriter + AVAssetWriterInputPixelBufferAdaptor
 */
interface VideoEncoder {

    /**
     * Encodes a sequence of image files into a video.
     *
     * @param framePaths List of paths to frame images (in order).
     * @param outputPath Destination path for the video file.
     * @param settings Export configuration.
     * @param onProgress Callback for progress updates (0.0 to 1.0).
     * @return Result containing the output file path or an error.
     */
    suspend fun encode(
        framePaths: List<String>,
        outputPath: String,
        settings: ExportSettings,
        onProgress: (Float) -> Unit = {},
    ): Result<String>

    /**
     * Cancels an ongoing encoding operation.
     *
     * @return Result indicating if cancellation was successful.
     */
    suspend fun cancel(): Result<Unit>

    /**
     * Gets the list of supported video codecs on this device.
     *
     * @return List of supported codecs.
     */
    fun getSupportedCodecs(): List<VideoCodec>

    /**
     * Checks if a specific codec is supported.
     *
     * @param codec The codec to check.
     * @return True if the codec is supported.
     */
    fun isCodecSupported(codec: VideoCodec): Boolean

    /**
     * Gets the maximum supported resolution for encoding.
     *
     * @return Maximum width and height as a Pair.
     */
    fun getMaxSupportedResolution(): Pair<Int, Int>

    /**
     * Whether encoding is currently in progress.
     */
    val isEncoding: Boolean
}
