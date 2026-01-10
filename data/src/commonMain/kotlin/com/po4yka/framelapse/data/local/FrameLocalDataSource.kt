package com.po4yka.framelapse.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.po4yka.framelapse.data.mapper.AlignedFrameParams
import com.po4yka.framelapse.data.mapper.InsertFrameParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Local data source for Frame database operations.
 * Wraps SQLDelight queries with coroutine-friendly APIs.
 */
@Single
class FrameLocalDataSource(private val queries: FrameQueries) {

    /**
     * Observes all frames for a project reactively.
     */
    fun observeByProject(projectId: String): Flow<List<Frame>> =
        queries.selectByProject(projectId).asFlow().mapToList(Dispatchers.IO)

    /**
     * Gets all frames for a project.
     */
    suspend fun getByProject(projectId: String): List<Frame> = withContext(Dispatchers.IO) {
        queries.selectByProject(projectId).executeAsList()
    }

    /**
     * Gets a single frame by ID.
     */
    suspend fun getById(id: String): Frame? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()
    }

    /**
     * Gets the latest frame for a project (by timestamp).
     */
    suspend fun getLatestByProject(projectId: String): Frame? = withContext(Dispatchers.IO) {
        queries.selectLatestByProject(projectId).executeAsOneOrNull()
    }

    /**
     * Gets frames within a date range.
     */
    suspend fun getByDateRange(projectId: String, startTimestamp: Long, endTimestamp: Long): List<Frame> =
        withContext(Dispatchers.IO) {
            queries.selectByDateRange(projectId, startTimestamp, endTimestamp).executeAsList()
        }

    /**
     * Inserts a new frame.
     */
    suspend fun insert(params: InsertFrameParams): Unit = withContext(Dispatchers.IO) {
        queries.insert(
            id = params.id,
            projectId = params.projectId,
            originalPath = params.originalPath,
            alignedPath = params.alignedPath,
            timestamp = params.timestamp,
            capturedAt = params.capturedAt,
            confidence = params.confidence,
            landmarksJson = params.landmarksJson,
            sortOrder = params.sortOrder,
            stabilizationResultJson = params.stabilizationResultJson,
        )
    }

    /**
     * Updates a frame with alignment data.
     */
    suspend fun updateAligned(params: AlignedFrameParams): Unit = withContext(Dispatchers.IO) {
        queries.updateAligned(
            alignedPath = params.alignedPath,
            confidence = params.confidence,
            landmarksJson = params.landmarksJson,
            stabilizationResultJson = params.stabilizationResultJson,
            id = params.id,
        )
    }

    /**
     * Updates the sort order of a frame.
     */
    suspend fun updateSortOrder(id: String, sortOrder: Long): Unit = withContext(Dispatchers.IO) {
        queries.updateSortOrder(sortOrder, id)
    }

    /**
     * Deletes a frame by ID.
     */
    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        queries.delete(id)
    }

    /**
     * Deletes all frames for a project.
     */
    suspend fun deleteByProject(projectId: String): Unit = withContext(Dispatchers.IO) {
        queries.deleteByProject(projectId)
    }

    /**
     * Gets the frame count for a project.
     */
    suspend fun getCount(projectId: String): Long = withContext(Dispatchers.IO) {
        queries.getFrameCount(projectId).executeAsOne()
    }

    /**
     * Gets the total frame count across all projects.
     */
    suspend fun getTotalCount(): Long = withContext(Dispatchers.IO) {
        queries.getTotalFrameCount().executeAsOne()
    }

    /**
     * Gets the maximum sort order for a project (for adding new frames).
     */
    suspend fun getMaxSortOrder(projectId: String): Long? = withContext(Dispatchers.IO) {
        queries.getMaxSortOrder(projectId).executeAsOneOrNull()?.MAX
    }
}
