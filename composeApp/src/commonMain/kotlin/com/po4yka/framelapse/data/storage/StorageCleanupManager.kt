package com.po4yka.framelapse.data.storage

import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Manages storage cleanup operations for projects.
 */
class StorageCleanupManager(
    private val imageStorageManager: ImageStorageManager,
    private val videoStorageManager: VideoStorageManager,
    private val fileManager: FileManager,
) {

    /**
     * Cleans up all files associated with a project.
     *
     * @param projectId The project ID to clean up.
     * @return Result indicating success or failure.
     */
    suspend fun cleanupProject(projectId: String): Result<Unit> {
        val errors = mutableListOf<String>()

        val imageResult = imageStorageManager.deleteProjectImages(projectId)
        if (imageResult is Result.Error) {
            errors.add("Images: ${imageResult.message}")
        }

        val videoResult = videoStorageManager.deleteProjectExports(projectId)
        if (videoResult is Result.Error) {
            errors.add("Videos: ${videoResult.message}")
        }

        val projectDir = fileManager.getProjectDirectory(projectId)
        if (fileManager.fileExists(projectDir)) {
            val deleted = fileManager.deleteFile(projectDir)
            if (!deleted) {
                errors.add("Project directory: Failed to delete")
            }
        }

        return if (errors.isEmpty()) {
            Result.Success(Unit)
        } else {
            Result.Error(
                IllegalStateException("Partial cleanup failure"),
                "Cleanup completed with errors: ${errors.joinToString("; ")}",
            )
        }
    }

    /**
     * Gets the storage usage for the app.
     *
     * @return StorageUsage data.
     */
    fun getStorageUsage(): StorageUsage {
        // Note: This is a simplified implementation.
        // A full implementation would walk the directory tree and calculate actual sizes.
        // For now, we return placeholder values.
        return StorageUsage(
            totalBytes = 0L,
            imageBytes = 0L,
            videoBytes = 0L,
            thumbnailBytes = 0L,
        )
    }
}

/**
 * Represents storage usage statistics.
 */
data class StorageUsage(val totalBytes: Long, val imageBytes: Long, val videoBytes: Long, val thumbnailBytes: Long)
