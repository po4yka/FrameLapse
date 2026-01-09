package com.po4yka.framelapse.domain.usecase.export

import com.po4yka.framelapse.data.storage.StorageError
import com.po4yka.framelapse.domain.entity.ExportSettings
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.MediaStore
import com.po4yka.framelapse.domain.service.VideoEncoder
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager
import com.po4yka.framelapse.platform.currentTimeMillis

/**
 * Error types specific to video compilation.
 */
sealed class VideoCompilationError(message: String, val userMessage: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** No frames available to compile. */
    class NoFrames :
        VideoCompilationError(
            "No frames available to compile",
            "No photos to create video. Please capture some photos first.",
        )

    /** Not enough frames for video. */
    class InsufficientFrames(val count: Int, val required: Int) :
        VideoCompilationError(
            "Insufficient frames: $count, required: $required",
            "Need at least $required photos. You have $count.",
        )

    /** Video codec is not supported. */
    class UnsupportedCodec(val codec: String) :
        VideoCompilationError(
            "Unsupported codec: $codec",
            "The selected video format ($codec) is not supported on this device.",
        )

    /** Encoder failed during video creation. */
    class EncoderFailed(val reason: String, cause: Throwable? = null) :
        VideoCompilationError(
            "Encoder failed: $reason",
            "Failed to create video: $reason",
            cause,
        )

    /** Video compilation was cancelled. */
    class Cancelled :
        VideoCompilationError(
            "Video compilation cancelled",
            "Video creation was cancelled.",
        )

    /** Storage error during compilation. */
    class StorageFull(val storageError: StorageError.InsufficientStorage) :
        VideoCompilationError(
            storageError.message ?: "Storage full",
            storageError.userMessage,
        )

    /** Failed to read source frames. */
    class FrameReadError(val path: String) :
        VideoCompilationError(
            "Failed to read frame: $path",
            "Could not read photo file. It may have been deleted.",
        )

    /** Unknown error during compilation. */
    class Unknown(cause: Throwable) :
        VideoCompilationError(
            cause.message ?: "Unknown error",
            "An unexpected error occurred. Please try again.",
            cause,
        )
}

/**
 * Compiles frames into a timelapse video.
 */
class CompileVideoUseCase(
    private val frameRepository: FrameRepository,
    private val videoEncoder: VideoEncoder,
    private val fileManager: FileManager,
    private val mediaStore: MediaStore,
) {
    /**
     * Compiles all frames in a project into a video.
     *
     * @param projectId The project ID.
     * @param settings Export configuration.
     * @param onProgress Callback for progress updates (0.0 to 1.0).
     * @return Result containing the output video file path.
     */
    suspend operator fun invoke(
        projectId: String,
        settings: ExportSettings,
        onProgress: (Float) -> Unit = {},
    ): Result<String> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        // Check if codec is supported
        if (!videoEncoder.isCodecSupported(settings.codec)) {
            val error = VideoCompilationError.UnsupportedCodec(settings.codec.name)
            return Result.Error(error, error.userMessage)
        }

        // Get frames
        val framesResult = if (settings.dateRange != null) {
            frameRepository.getFramesByDateRange(
                projectId = projectId,
                startTimestamp = settings.dateRange.startTimestamp,
                endTimestamp = settings.dateRange.endTimestamp,
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
            val error = VideoCompilationError.NoFrames()
            return Result.Error(error, error.userMessage)
        }

        if (frames.size < MIN_FRAMES_FOR_VIDEO) {
            val error = VideoCompilationError.InsufficientFrames(frames.size, MIN_FRAMES_FOR_VIDEO)
            return Result.Error(error, error.userMessage)
        }

        // Collect frame paths (prefer aligned, fallback to original)
        val framePaths = frames.map { frame ->
            frame.alignedPath ?: frame.originalPath
        }

        // Verify all frame files exist before starting encoding
        for (path in framePaths) {
            if (!fileManager.fileExists(path)) {
                val error = VideoCompilationError.FrameReadError(path)
                return Result.Error(error, error.userMessage)
            }
        }

        // Check available storage (estimate: 2MB per frame for video)
        val estimatedVideoSize = frames.size * ESTIMATED_BYTES_PER_FRAME
        val availableStorage = fileManager.getAvailableStorageBytes()
        if (availableStorage < estimatedVideoSize) {
            val storageError = StorageError.InsufficientStorage(estimatedVideoSize, availableStorage)
            val error = VideoCompilationError.StorageFull(storageError)
            return Result.Error(error, error.userMessage)
        }

        // Generate output path
        val timestamp = currentTimeMillis()
        val extension = "mp4" // Both H.264 and HEVC use mp4 container
        val outputPath = mediaStore.getExportPath(projectId, timestamp, extension)

        // Encode video with error handling and cleanup
        return try {
            val encodeResult = videoEncoder.encode(
                framePaths = framePaths,
                outputPath = outputPath,
                settings = settings,
                onProgress = onProgress,
            )

            when {
                encodeResult.isSuccess -> encodeResult
                encodeResult.isError -> {
                    // Clean up partial file on failure
                    cleanupPartialFile(outputPath)

                    // Map the error to a user-friendly message
                    val originalError = encodeResult.exceptionOrNull()
                    val mappedError = mapEncoderError(originalError)
                    Result.Error(mappedError, mappedError.userMessage)
                }
                else -> encodeResult
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Clean up on cancellation
            cleanupPartialFile(outputPath)
            val error = VideoCompilationError.Cancelled()
            Result.Error(error, error.userMessage)
        } catch (e: Exception) {
            // Clean up on any unexpected error
            cleanupPartialFile(outputPath)
            val error = mapEncoderError(e)
            Result.Error(error, error.userMessage)
        }
    }

    /**
     * Maps encoder exceptions to user-friendly VideoCompilationError types.
     */
    private fun mapEncoderError(throwable: Throwable?): VideoCompilationError = when {
        throwable == null -> VideoCompilationError.Unknown(Exception("Unknown error"))

        throwable is VideoCompilationError -> throwable

        throwable is StorageError.InsufficientStorage -> VideoCompilationError.StorageFull(throwable)

        throwable.message?.contains("storage", ignoreCase = true) == true ||
            throwable.message?.contains("space", ignoreCase = true) == true ||
            throwable.message?.contains("ENOSPC", ignoreCase = true) == true -> {
            val available = fileManager.getAvailableStorageBytes()
            val storageError = StorageError.InsufficientStorage(
                requiredBytes = available + 1,
                availableBytes = available,
            )
            VideoCompilationError.StorageFull(storageError)
        }

        throwable.message?.contains("codec", ignoreCase = true) == true ||
            throwable.message?.contains("encoder", ignoreCase = true) == true ||
            throwable.message?.contains("MediaCodec", ignoreCase = true) == true -> {
            VideoCompilationError.EncoderFailed(
                throwable.message ?: "Encoder initialization failed",
                throwable,
            )
        }

        throwable.message?.contains("cancel", ignoreCase = true) == true -> {
            VideoCompilationError.Cancelled()
        }

        else -> VideoCompilationError.Unknown(throwable)
    }

    /**
     * Cleans up a partial video file after a failed or cancelled encoding.
     */
    private fun cleanupPartialFile(outputPath: String) {
        try {
            if (fileManager.fileExists(outputPath)) {
                fileManager.deleteFile(outputPath)
            }
        } catch (e: Exception) {
            // Ignore cleanup errors - best effort only
        }
    }

    /**
     * Cancels an ongoing video compilation.
     */
    suspend fun cancel(): Result<Unit> = videoEncoder.cancel()

    /**
     * Checks if video compilation is in progress.
     */
    val isEncoding: Boolean
        get() = videoEncoder.isEncoding

    companion object {
        const val MIN_FRAMES_FOR_VIDEO = 2

        /** Estimated bytes per frame in the output video (conservative estimate). */
        private const val ESTIMATED_BYTES_PER_FRAME = 2L * 1024 * 1024 // 2 MB
    }
}
