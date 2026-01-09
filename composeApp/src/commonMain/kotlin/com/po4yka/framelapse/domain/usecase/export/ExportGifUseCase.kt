package com.po4yka.framelapse.domain.usecase.export

import com.po4yka.framelapse.data.storage.VideoStorageManager
import com.po4yka.framelapse.domain.entity.DateRange
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.GifEncoder
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.currentTimeMillis

/**
 * Exports frames as an animated GIF.
 *
 * Uses platform-specific GIF encoding:
 * - Android: Built-in GIF89a encoder with LZW compression
 * - iOS: ImageIO framework (CGImageDestination)
 */
class ExportGifUseCase(
    private val frameRepository: FrameRepository,
    private val imageProcessor: ImageProcessor,
    private val videoStorageManager: VideoStorageManager,
    private val gifEncoder: GifEncoder,
) {
    /**
     * Exports frames as an animated GIF.
     *
     * @param projectId The project ID.
     * @param fps Frames per second for the GIF.
     * @param maxSize Maximum dimension (width or height) for the GIF.
     * @param dateRange Optional date range filter.
     * @param onProgress Callback for progress updates (0.0 to 1.0).
     * @return Result containing the output GIF file path.
     */
    suspend operator fun invoke(
        projectId: String,
        fps: Int = DEFAULT_GIF_FPS,
        maxSize: Int = DEFAULT_GIF_SIZE,
        dateRange: DateRange? = null,
        onProgress: (Float) -> Unit = {},
    ): Result<String> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (fps !in 1..30) {
            return Result.Error(
                IllegalArgumentException("FPS must be between 1 and 30 for GIF"),
                "Invalid FPS for GIF",
            )
        }

        // Get frames
        val framesResult = if (dateRange != null) {
            frameRepository.getFramesByDateRange(
                projectId = projectId,
                startTimestamp = dateRange.startTimestamp,
                endTimestamp = dateRange.endTimestamp,
            )
        } else {
            frameRepository.getFramesByProject(projectId)
        }

        if (framesResult.isError) {
            return Result.Error(
                framesResult.exceptionOrNull()!!,
                "Failed to retrieve frames",
            )
        }

        val frames = framesResult.getOrNull()!!
        if (frames.isEmpty()) {
            return Result.Error(
                IllegalStateException("No frames to export"),
                "No frames to export",
            )
        }

        if (frames.size < MIN_FRAMES_FOR_GIF) {
            return Result.Error(
                IllegalStateException("At least $MIN_FRAMES_FOR_GIF frames are required"),
                "Need at least $MIN_FRAMES_FOR_GIF frames",
            )
        }

        // Generate output path
        val timestamp = currentTimeMillis()
        val filename = videoStorageManager.generateExportFilename(timestamp, "gif")
        val outputPath = videoStorageManager.getExportPath(projectId, filename)

        // Process frames
        val processedFrames = mutableListOf<ImageData>()
        val total = frames.size

        for ((index, frame) in frames.withIndex()) {
            onProgress((index.toFloat() / total) * PROCESSING_PROGRESS_WEIGHT)

            val imagePath = frame.alignedPath ?: frame.originalPath
            val loadResult = imageProcessor.loadImage(imagePath)
            if (loadResult.isError) continue

            val imageData = loadResult.getOrNull()!!

            // Resize if needed
            val resizedResult = if (imageData.width > maxSize || imageData.height > maxSize) {
                val scale = maxSize.toFloat() / maxOf(imageData.width, imageData.height)
                val newWidth = (imageData.width * scale).toInt()
                val newHeight = (imageData.height * scale).toInt()
                imageProcessor.resizeImage(imageData, newWidth, newHeight)
            } else {
                Result.Success(imageData)
            }

            if (resizedResult.isSuccess) {
                processedFrames.add(resizedResult.getOrNull()!!)
            }
        }

        if (processedFrames.isEmpty()) {
            return Result.Error(
                IllegalStateException("Failed to process any frames"),
                "Failed to process frames",
            )
        }

        // Calculate frame delay in milliseconds
        val delayMs = MILLISECONDS_PER_SECOND / fps

        // Encode to GIF using platform-specific encoder
        return gifEncoder.encode(
            frames = processedFrames,
            outputPath = outputPath,
            delayMs = delayMs,
            onProgress = { encodingProgress ->
                // Encoding takes remaining progress (after processing)
                onProgress(PROCESSING_PROGRESS_WEIGHT + encodingProgress * ENCODING_PROGRESS_WEIGHT)
            },
        )
    }

    companion object {
        const val DEFAULT_GIF_FPS = 10
        const val DEFAULT_GIF_SIZE = 480
        const val MIN_FRAMES_FOR_GIF = 2
        private const val MILLISECONDS_PER_SECOND = 1000
        private const val PROCESSING_PROGRESS_WEIGHT = 0.8f
        private const val ENCODING_PROGRESS_WEIGHT = 0.2f
    }
}
