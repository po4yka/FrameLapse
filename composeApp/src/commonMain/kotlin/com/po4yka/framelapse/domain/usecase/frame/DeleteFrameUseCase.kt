package com.po4yka.framelapse.domain.usecase.frame

import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Deletes a frame and its associated files.
 */
class DeleteFrameUseCase(private val frameRepository: FrameRepository, private val fileManager: FileManager) {
    /**
     * Deletes a frame, including its image files.
     *
     * @param frameId The frame ID to delete.
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(frameId: String): Result<Unit> {
        if (frameId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Frame ID cannot be empty"),
                "Frame ID cannot be empty",
            )
        }

        // Get frame to retrieve file paths
        val frameResult = frameRepository.getFrame(frameId)
        if (frameResult.isError) {
            return Result.Error(
                NoSuchElementException("Frame not found: $frameId"),
                "Frame not found",
            )
        }

        val frame = frameResult.getOrNull()!!

        // Delete original image file
        if (fileManager.fileExists(frame.originalPath)) {
            fileManager.deleteFile(frame.originalPath)
        }

        // Delete aligned image file if exists
        frame.alignedPath?.let { alignedPath ->
            if (fileManager.fileExists(alignedPath)) {
                fileManager.deleteFile(alignedPath)
            }
        }

        // Delete frame from database
        return frameRepository.deleteFrame(frameId)
    }

    /**
     * Deletes multiple frames.
     *
     * @param frameIds List of frame IDs to delete.
     * @return Result containing the number of successfully deleted frames.
     */
    suspend fun deleteMultiple(frameIds: List<String>): Result<Int> {
        var deletedCount = 0
        val errors = mutableListOf<String>()

        for (frameId in frameIds) {
            val result = invoke(frameId)
            if (result.isSuccess) {
                deletedCount++
            } else {
                errors.add(frameId)
            }
        }

        return if (errors.isEmpty()) {
            Result.Success(deletedCount)
        } else {
            Result.Success(deletedCount) // Return partial success count
        }
    }
}
