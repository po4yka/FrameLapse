package com.po4yka.framelapse.data.storage

import com.po4yka.framelapse.domain.error.StorageError
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Manages image file storage for projects.
 * Handles saving, retrieving, and deleting frame images.
 */
@Single
class ImageStorageManager(private val fileManager: FileManager) {

    /**
     * Gets the frames directory path for a project.
     */
    fun getFramesDirectory(projectId: String): String {
        val projectDir = fileManager.getProjectDirectory(projectId)
        val framesDir = "$projectDir/$FRAMES_DIR"
        fileManager.createDirectory(framesDir)
        return framesDir
    }

    /**
     * Gets the aligned frames directory path for a project.
     */
    fun getAlignedDirectory(projectId: String): String {
        val projectDir = fileManager.getProjectDirectory(projectId)
        val alignedDir = "$projectDir/$ALIGNED_DIR"
        fileManager.createDirectory(alignedDir)
        return alignedDir
    }

    /**
     * Gets the thumbnails directory path for a project.
     */
    fun getThumbnailsDirectory(projectId: String): String {
        val projectDir = fileManager.getProjectDirectory(projectId)
        val thumbnailsDir = "$projectDir/$THUMBNAILS_DIR"
        fileManager.createDirectory(thumbnailsDir)
        return thumbnailsDir
    }

    /**
     * Gets the full path for a frame image.
     */
    fun getFramePath(projectId: String, filename: String): String {
        val framesDir = getFramesDirectory(projectId)
        return "$framesDir/$filename"
    }

    /**
     * Gets the full path for an aligned frame image.
     */
    fun getAlignedPath(projectId: String, filename: String): String {
        val alignedDir = getAlignedDirectory(projectId)
        return "$alignedDir/$filename"
    }

    /**
     * Gets the full path for a thumbnail image.
     */
    fun getThumbnailPath(projectId: String, filename: String = DEFAULT_THUMBNAIL_NAME): String {
        val thumbnailsDir = getThumbnailsDirectory(projectId)
        return "$thumbnailsDir/$filename"
    }

    /**
     * Deletes an image file at the given path.
     */
    suspend fun deleteImage(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (fileManager.fileExists(path)) {
                val deleted = fileManager.deleteFile(path)
                if (deleted) {
                    Result.Success(Unit)
                } else {
                    Result.Error(
                        IllegalStateException("Failed to delete image: $path"),
                        "Failed to delete image",
                    )
                }
            } else {
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete image: ${e.message}")
        }
    }

    /**
     * Deletes all images for a project (frames, aligned, and thumbnails).
     */
    suspend fun deleteProjectImages(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val projectDir = fileManager.getProjectDirectory(projectId)

            deleteDirectory("$projectDir/$FRAMES_DIR")
            deleteDirectory("$projectDir/$ALIGNED_DIR")
            deleteDirectory("$projectDir/$THUMBNAILS_DIR")

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete project images: ${e.message}")
        }
    }

    /**
     * Checks if an image file exists at the given path.
     */
    fun imageExists(path: String): Boolean = fileManager.fileExists(path)

    /**
     * Generates a unique filename for a new frame.
     */
    fun generateFrameFilename(timestamp: Long, extension: String = "jpg"): String = "frame_$timestamp.$extension"

    /**
     * Generates a unique filename for an aligned frame.
     */
    fun generateAlignedFilename(originalFilename: String): String {
        val nameWithoutExtension = originalFilename.substringBeforeLast(".")
        val extension = originalFilename.substringAfterLast(".", "jpg")
        return "${nameWithoutExtension}_aligned.$extension"
    }

    private fun deleteDirectory(directoryPath: String) {
        if (fileManager.fileExists(directoryPath)) {
            fileManager.deleteRecursively(directoryPath)
        }
    }

    /**
     * Gets the available storage space in bytes.
     *
     * @return Available bytes on the storage device.
     */
    fun getAvailableStorageBytes(): Long = fileManager.getAvailableStorageBytes()

    /**
     * Checks if there is sufficient storage space for an operation.
     *
     * @param requiredBytes The number of bytes needed.
     * @return Result.Success if sufficient space, Result.Error with InsufficientStorage otherwise.
     */
    fun checkStorageAvailable(requiredBytes: Long): Result<Unit> {
        val availableBytes = getAvailableStorageBytes()
        return if (availableBytes >= requiredBytes) {
            Result.Success(Unit)
        } else {
            Result.Error(
                StorageError.InsufficientStorage(requiredBytes, availableBytes),
                "Not enough storage space",
            )
        }
    }

    /**
     * Checks if there is sufficient storage space with a safety margin.
     * Adds a buffer to account for filesystem overhead.
     *
     * @param requiredBytes The number of bytes needed.
     * @param marginPercent Additional margin percentage (default 10%).
     * @return Result.Success if sufficient space, Result.Error with InsufficientStorage otherwise.
     */
    fun checkStorageAvailableWithMargin(
        requiredBytes: Long,
        marginPercent: Int = DEFAULT_STORAGE_MARGIN_PERCENT,
    ): Result<Unit> {
        val requiredWithMargin = requiredBytes + (requiredBytes * marginPercent / 100)
        return checkStorageAvailable(requiredWithMargin)
    }

    /**
     * Checks if storage is critically low (below minimum threshold).
     *
     * @return True if available storage is below the minimum threshold.
     */
    fun isStorageCriticallyLow(): Boolean = getAvailableStorageBytes() < MIN_STORAGE_THRESHOLD_BYTES

    companion object {
        const val FRAMES_DIR = "frames"
        const val ALIGNED_DIR = "aligned"
        const val THUMBNAILS_DIR = "thumbnails"
        const val DEFAULT_THUMBNAIL_NAME = "thumbnail.jpg"

        /** Minimum storage threshold (50 MB) below which operations should be blocked. */
        const val MIN_STORAGE_THRESHOLD_BYTES = 50L * 1024 * 1024

        /** Default safety margin percentage for storage checks. */
        const val DEFAULT_STORAGE_MARGIN_PERCENT = 10
    }
}
