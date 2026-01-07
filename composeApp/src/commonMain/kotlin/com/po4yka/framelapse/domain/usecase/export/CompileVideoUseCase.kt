package com.po4yka.framelapse.domain.usecase.export

import com.po4yka.framelapse.domain.entity.ExportSettings
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.VideoEncoder
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager
import com.po4yka.framelapse.platform.currentTimeMillis

/**
 * Compiles frames into a timelapse video.
 */
class CompileVideoUseCase(
    private val frameRepository: FrameRepository,
    private val videoEncoder: VideoEncoder,
    private val fileManager: FileManager,
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
            return Result.Error(
                UnsupportedOperationException("Codec ${settings.codec} is not supported"),
                "Codec not supported",
            )
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
            return Result.Error(
                IllegalStateException("No frames to compile"),
                "No frames to compile",
            )
        }

        if (frames.size < MIN_FRAMES_FOR_VIDEO) {
            return Result.Error(
                IllegalStateException("At least $MIN_FRAMES_FOR_VIDEO frames are required"),
                "Need at least $MIN_FRAMES_FOR_VIDEO frames",
            )
        }

        // Collect frame paths (prefer aligned, fallback to original)
        val framePaths = frames.map { frame ->
            frame.alignedPath ?: frame.originalPath
        }

        // Generate output path
        val projectDir = fileManager.getProjectDirectory(projectId)
        val timestamp = currentTimeMillis()
        val extension = if (settings.codec == com.po4yka.framelapse.domain.entity.VideoCodec.HEVC) {
            "mp4" // HEVC still uses mp4 container
        } else {
            "mp4"
        }
        val outputPath = "$projectDir/timelapse_$timestamp.$extension"

        // Encode video
        return videoEncoder.encode(
            framePaths = framePaths,
            outputPath = outputPath,
            settings = settings,
            onProgress = onProgress,
        )
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
    }
}
