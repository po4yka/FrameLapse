package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for platform-specific animated GIF encoding.
 *
 * Implementations will use:
 * - Android: Native GIF encoder library
 * - iOS: ImageIO framework (CGImageDestination)
 */
interface GifEncoder {

    /**
     * Whether encoding is currently in progress.
     */
    val isEncoding: Boolean

    /**
     * Encodes a sequence of images into an animated GIF.
     *
     * @param frames List of image data to encode as frames (in order).
     * @param outputPath Destination path for the GIF file.
     * @param delayMs Delay between frames in milliseconds.
     * @param onProgress Callback for progress updates (0.0 to 1.0).
     * @return Result containing the output file path or an error.
     */
    suspend fun encode(
        frames: List<ImageData>,
        outputPath: String,
        delayMs: Int,
        onProgress: (Float) -> Unit = {},
    ): Result<String>

    /**
     * Cancels an ongoing encoding operation.
     *
     * @return Result indicating if cancellation was successful.
     */
    fun cancel(): Result<Unit>
}
