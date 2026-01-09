package com.po4yka.framelapse.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.po4yka.framelapse.data.mapper.CalibrationParams
import com.po4yka.framelapse.data.mapper.InsertProjectParams
import com.po4yka.framelapse.data.mapper.UpdateProjectParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Local data source for Project database operations.
 * Wraps SQLDelight queries with coroutine-friendly APIs.
 */
class ProjectLocalDataSource(private val queries: ProjectQueries) {

    /**
     * Observes all projects reactively, ordered by most recently updated.
     */
    fun observeAll(): Flow<List<Project>> = queries.selectAll().asFlow().mapToList(Dispatchers.IO)

    /**
     * Observes a single project by ID reactively.
     */
    fun observeById(id: String): Flow<Project?> = queries.selectById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    /**
     * Gets a single project by ID.
     */
    suspend fun getById(id: String): Project? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()
    }

    /**
     * Gets all projects, ordered by most recently updated.
     */
    suspend fun getAll(): List<Project> = withContext(Dispatchers.IO) {
        queries.selectAll().executeAsList()
    }

    /**
     * Inserts a new project.
     */
    suspend fun insert(params: InsertProjectParams): Unit = withContext(Dispatchers.IO) {
        queries.insert(
            id = params.id,
            name = params.name,
            createdAt = params.createdAt,
            updatedAt = params.updatedAt,
            fps = params.fps,
            resolution = params.resolution,
            orientation = params.orientation,
            thumbnailPath = params.thumbnailPath,
            contentType = params.contentType,
            muscleRegion = params.muscleRegion,
            referenceFrameId = params.referenceFrameId,
            calibrationImagePath = params.calibrationImagePath,
            calibrationLeftEyeX = params.calibrationLeftEyeX,
            calibrationLeftEyeY = params.calibrationLeftEyeY,
            calibrationRightEyeX = params.calibrationRightEyeX,
            calibrationRightEyeY = params.calibrationRightEyeY,
            calibrationOffsetX = params.calibrationOffsetX,
            calibrationOffsetY = params.calibrationOffsetY,
        )
    }

    /**
     * Updates an existing project.
     */
    suspend fun update(params: UpdateProjectParams): Unit = withContext(Dispatchers.IO) {
        queries.update(
            name = params.name,
            updatedAt = params.updatedAt,
            fps = params.fps,
            resolution = params.resolution,
            orientation = params.orientation,
            thumbnailPath = params.thumbnailPath,
            contentType = params.contentType,
            muscleRegion = params.muscleRegion,
            referenceFrameId = params.referenceFrameId,
            calibrationImagePath = params.calibrationImagePath,
            calibrationLeftEyeX = params.calibrationLeftEyeX,
            calibrationLeftEyeY = params.calibrationLeftEyeY,
            calibrationRightEyeX = params.calibrationRightEyeX,
            calibrationRightEyeY = params.calibrationRightEyeY,
            calibrationOffsetX = params.calibrationOffsetX,
            calibrationOffsetY = params.calibrationOffsetY,
            id = params.id,
        )
    }

    /**
     * Updates only the thumbnail path for a project.
     */
    suspend fun updateThumbnail(id: String, path: String?, updatedAt: Long): Unit = withContext(Dispatchers.IO) {
        queries.updateThumbnail(
            thumbnailPath = path,
            updatedAt = updatedAt,
            id = id,
        )
    }

    /**
     * Updates only the reference frame ID for a project.
     * Used for LANDSCAPE content type projects to set the alignment reference.
     */
    suspend fun updateReferenceFrame(id: String, referenceFrameId: String?, updatedAt: Long): Unit =
        withContext(Dispatchers.IO) {
            queries.updateReferenceFrame(
                referenceFrameId = referenceFrameId,
                updatedAt = updatedAt,
                id = id,
            )
        }

    /**
     * Updates calibration data for a project.
     * Used for FACE content type projects to set the alignment calibration.
     */
    suspend fun updateCalibration(params: CalibrationParams): Unit = withContext(Dispatchers.IO) {
        queries.updateCalibration(
            calibrationImagePath = params.calibrationImagePath,
            calibrationLeftEyeX = params.calibrationLeftEyeX,
            calibrationLeftEyeY = params.calibrationLeftEyeY,
            calibrationRightEyeX = params.calibrationRightEyeX,
            calibrationRightEyeY = params.calibrationRightEyeY,
            calibrationOffsetX = params.calibrationOffsetX,
            calibrationOffsetY = params.calibrationOffsetY,
            updatedAt = params.updatedAt,
            id = params.id,
        )
    }

    /**
     * Clears calibration data for a project.
     */
    suspend fun clearCalibration(id: String, updatedAt: Long): Unit = withContext(Dispatchers.IO) {
        queries.clearCalibration(
            updatedAt = updatedAt,
            id = id,
        )
    }

    /**
     * Deletes a project by ID.
     */
    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        queries.delete(id)
    }

    /**
     * Checks if a project exists.
     */
    suspend fun exists(id: String): Boolean = withContext(Dispatchers.IO) {
        queries.exists(id).executeAsOne()
    }

    /**
     * Gets the total number of projects.
     */
    suspend fun getCount(): Long = withContext(Dispatchers.IO) {
        queries.getProjectCount().executeAsOne()
    }
}
