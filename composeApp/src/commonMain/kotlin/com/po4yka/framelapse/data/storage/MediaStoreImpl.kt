package com.po4yka.framelapse.data.storage

import com.po4yka.framelapse.domain.service.MediaStore
import com.po4yka.framelapse.domain.util.Result

/**
 * Default MediaStore implementation backed by storage managers.
 */
class MediaStoreImpl(
    private val imageStorageManager: ImageStorageManager,
    private val videoStorageManager: VideoStorageManager,
) : MediaStore {

    override fun getCapturePath(projectId: String, timestamp: Long, extension: String): String {
        val filename = imageStorageManager.generateFrameFilename(timestamp, extension)
        return imageStorageManager.getFramePath(projectId, filename)
    }

    override fun getAlignedPath(projectId: String, originalPath: String): String {
        val originalFilename = originalPath.substringAfterLast("/")
        val alignedFilename = imageStorageManager.generateAlignedFilename(originalFilename)
        return imageStorageManager.getAlignedPath(projectId, alignedFilename)
    }

    override fun getMusclePath(projectId: String, frameId: String): String =
        imageStorageManager.getAlignedPath(projectId, "muscle_${frameId}.jpg")

    override fun getExportPath(projectId: String, timestamp: Long, extension: String): String {
        val filename = videoStorageManager.generateExportFilename(timestamp, extension)
        return videoStorageManager.getExportPath(projectId, filename)
    }

    override fun getCalibrationPath(projectId: String, timestamp: Long, extension: String): String {
        val filename = "calibration_$timestamp.$extension"
        return imageStorageManager.getAlignedPath(projectId, filename)
    }

    override suspend fun deleteImage(path: String): Result<Unit> = imageStorageManager.deleteImage(path)
}
