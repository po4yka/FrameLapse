package com.po4yka.framelapse.data.storage

import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Manages video file storage for projects.
 * Handles export paths and video file operations.
 */
class VideoStorageManager(private val fileManager: FileManager) {

    /**
     * Gets the exports directory path for a project.
     */
    fun getExportsDirectory(projectId: String): String {
        val projectDir = fileManager.getProjectDirectory(projectId)
        val exportsDir = "$projectDir/$EXPORTS_DIR"
        fileManager.createDirectory(exportsDir)
        return exportsDir
    }

    /**
     * Gets the full path for an export file.
     */
    fun getExportPath(projectId: String, filename: String): String {
        val exportsDir = getExportsDirectory(projectId)
        return "$exportsDir/$filename"
    }

    /**
     * Generates a unique filename for a new export.
     */
    fun generateExportFilename(timestamp: Long, extension: String = "mp4"): String = "timelapse_$timestamp.$extension"

    /**
     * Deletes an export file at the given path.
     */
    suspend fun deleteExport(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (fileManager.fileExists(path)) {
                val deleted = fileManager.deleteFile(path)
                if (deleted) {
                    Result.Success(Unit)
                } else {
                    Result.Error(
                        IllegalStateException("Failed to delete export: $path"),
                        "Failed to delete export",
                    )
                }
            } else {
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete export: ${e.message}")
        }
    }

    /**
     * Deletes all exports for a project.
     */
    suspend fun deleteProjectExports(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val exportsDir = getExportsDirectory(projectId)
            if (fileManager.fileExists(exportsDir)) {
                fileManager.deleteFile(exportsDir)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete project exports: ${e.message}")
        }
    }

    /**
     * Checks if an export file exists at the given path.
     */
    fun exportExists(path: String): Boolean = fileManager.fileExists(path)

    companion object {
        const val EXPORTS_DIR = "exports"
    }
}
