package com.po4yka.framelapse.data.storage

import com.po4yka.framelapse.data.local.FrameLocalDataSource
import com.po4yka.framelapse.data.local.ProjectLocalDataSource
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Manages storage cleanup operations for projects.
 */
class StorageCleanupManager(
    private val imageStorageManager: ImageStorageManager,
    private val videoStorageManager: VideoStorageManager,
    private val fileManager: FileManager,
    private val frameLocalDataSource: FrameLocalDataSource,
    private val projectLocalDataSource: ProjectLocalDataSource,
) {

    /**
     * Cleans up all files associated with a project.
     *
     * @param projectId The project ID to clean up.
     * @return Result indicating success or failure.
     */
    suspend fun cleanupProject(projectId: String): Result<Unit> {
        val errors = mutableListOf<String>()

        val frames = try {
            frameLocalDataSource.getByProject(projectId)
        } catch (e: Exception) {
            errors.add("Frames: ${e.message}")
            emptyList()
        }

        fun deleteIfExists(path: String) {
            if (fileManager.fileExists(path) && !fileManager.deleteFile(path)) {
                errors.add(path)
            }
        }

        for (frame in frames) {
            deleteIfExists(frame.originalPath)
            frame.alignedPath?.let { deleteIfExists(it) }
        }

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
            val deleted = fileManager.deleteRecursively(projectDir)
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

    /**
     * Audits storage by listing files under known app directories and computing orphans.
     *
     * @return StorageAudit with totals and orphaned file paths.
     */
    fun getStorageAudit(): StorageAudit {
        val appDir = fileManager.getAppDirectory()
        val projectsRoot = "$appDir/projects"
        val importsRoot = "$appDir/imports"

        val allFiles = buildList {
            addAll(fileManager.listFilesRecursively(projectsRoot))
            addAll(fileManager.listFilesRecursively(importsRoot))
        }.distinct()

        val knownFiles = buildSet {
            val projects = try {
                projectLocalDataSource.getAll()
            } catch (e: Exception) {
                emptyList()
            }
            for (project in projects) {
                project.thumbnailPath?.let { add(it) }
                val frames = try {
                    frameLocalDataSource.getByProject(project.id)
                } catch (e: Exception) {
                    emptyList()
                }
                for (frame in frames) {
                    add(frame.originalPath)
                    frame.alignedPath?.let { add(it) }
                }
            }
        }

        val orphaned = allFiles.filter { it !in knownFiles }
        val totalBytes = allFiles.sumOf { fileManager.getFileSizeBytes(it) }

        return StorageAudit(
            totalFiles = allFiles.size,
            totalBytes = totalBytes,
            knownFiles = knownFiles.size,
            orphanedFiles = orphaned,
        )
    }
}

/**
 * Represents storage usage statistics.
 */
data class StorageUsage(val totalBytes: Long, val imageBytes: Long, val videoBytes: Long, val thumbnailBytes: Long)

/**
 * Represents storage audit results.
 */
data class StorageAudit(
    val totalFiles: Int,
    val totalBytes: Long,
    val knownFiles: Int,
    val orphanedFiles: List<String>,
)
