package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.util.Result

/**
 * Shared abstraction for media path generation and cleanup.
 */
interface MediaStore {
    fun getCapturePath(projectId: String, timestamp: Long, extension: String = "jpg"): String
    fun getAlignedPath(projectId: String, originalPath: String): String
    fun getMusclePath(projectId: String, frameId: String): String
    fun getExportPath(projectId: String, timestamp: Long, extension: String = "mp4"): String
    fun getCalibrationPath(projectId: String, timestamp: Long, extension: String = "jpg"): String
    suspend fun deleteImage(path: String): Result<Unit>
}
