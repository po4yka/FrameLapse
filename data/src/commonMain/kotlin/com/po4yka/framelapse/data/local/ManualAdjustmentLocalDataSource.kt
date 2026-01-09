package com.po4yka.framelapse.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Local data source for ManualAdjustment database operations. Wraps
 * SQLDelight queries with coroutine-friendly APIs.
 */
class ManualAdjustmentLocalDataSource(private val queries: ManualAdjustmentQueries) {

    /** Observes the adjustment for a frame reactively. */
    fun observeByFrameId(frameId: String): Flow<ManualAdjustment?> =
        queries.selectByFrameId(frameId).asFlow().mapToOneOrNull(Dispatchers.IO)

    /** Observes all active adjustments for a project reactively. */
    fun observeActiveByProject(projectId: String): Flow<List<ManualAdjustment>> =
        queries.selectActiveByProject(projectId).asFlow().mapToList(Dispatchers.IO)

    /** Gets the adjustment for a frame. */
    suspend fun getByFrameId(frameId: String): ManualAdjustment? = withContext(Dispatchers.IO) {
        queries.selectByFrameId(frameId).executeAsOneOrNull()
    }

    /** Gets all adjustments for a project. */
    suspend fun getByProject(projectId: String): List<ManualAdjustment> = withContext(Dispatchers.IO) {
        queries.selectByProject(projectId).executeAsList()
    }

    /** Gets all active adjustments for a project. */
    suspend fun getActiveByProject(projectId: String): List<ManualAdjustment> = withContext(Dispatchers.IO) {
        queries.selectActiveByProject(projectId).executeAsList()
    }

    /** Gets frame IDs that have active manual adjustments in a project. */
    suspend fun getFrameIdsWithAdjustments(projectId: String): List<String> = withContext(Dispatchers.IO) {
        queries.selectFrameIdsWithAdjustments(projectId).executeAsList()
    }

    /** Counts active adjustments for a project. */
    suspend fun countActiveByProject(projectId: String): Long = withContext(Dispatchers.IO) {
        queries.countActiveByProject(projectId).executeAsOne()
    }

    /** Checks if a frame has a manual adjustment. */
    suspend fun existsByFrameId(frameId: String): Boolean = withContext(Dispatchers.IO) {
        queries.existsByFrameId(frameId).executeAsOne()
    }

    /** Inserts a new adjustment. */
    suspend fun insert(
        id: String,
        frameId: String,
        contentType: String,
        adjustmentJson: String,
        isActive: Boolean,
        createdAt: Long,
        updatedAt: Long,
    ): Unit = withContext(Dispatchers.IO) {
        queries.insert(
            id = id,
            frameId = frameId,
            contentType = contentType,
            adjustmentJson = adjustmentJson,
            isActive = if (isActive) 1L else 0L,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /** Inserts or replaces an adjustment (upsert). */
    suspend fun insertOrReplace(
        id: String,
        frameId: String,
        contentType: String,
        adjustmentJson: String,
        isActive: Boolean,
        createdAt: Long,
        updatedAt: Long,
    ): Unit = withContext(Dispatchers.IO) {
        queries.insertOrReplace(
            id = id,
            frameId = frameId,
            contentType = contentType,
            adjustmentJson = adjustmentJson,
            isActive = if (isActive) 1L else 0L,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /** Updates an existing adjustment. */
    suspend fun update(id: String, adjustmentJson: String, isActive: Boolean, updatedAt: Long): Unit =
        withContext(Dispatchers.IO) {
            queries.update(
                adjustmentJson = adjustmentJson,
                isActive = if (isActive) 1L else 0L,
                updatedAt = updatedAt,
                id = id,
            )
        }

    /** Updates an adjustment by frame ID. */
    suspend fun updateByFrameId(frameId: String, adjustmentJson: String, isActive: Boolean, updatedAt: Long): Unit =
        withContext(Dispatchers.IO) {
            queries.updateByFrameId(
                adjustmentJson = adjustmentJson,
                isActive = if (isActive) 1L else 0L,
                updatedAt = updatedAt,
                frameId = frameId,
            )
        }

    /** Toggles the active state of an adjustment. */
    suspend fun toggleActive(frameId: String, isActive: Boolean, updatedAt: Long): Unit = withContext(Dispatchers.IO) {
        queries.toggleActive(
            isActive = if (isActive) 1L else 0L,
            updatedAt = updatedAt,
            frameId = frameId,
        )
    }

    /** Activates an adjustment. */
    suspend fun activate(frameId: String, updatedAt: Long): Unit = withContext(Dispatchers.IO) {
        queries.activate(updatedAt, frameId)
    }

    /** Deactivates an adjustment. */
    suspend fun deactivate(frameId: String, updatedAt: Long): Unit = withContext(Dispatchers.IO) {
        queries.deactivate(updatedAt, frameId)
    }

    /** Deletes an adjustment by ID. */
    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        queries.delete(id)
    }

    /** Deletes an adjustment by frame ID. */
    suspend fun deleteByFrameId(frameId: String): Unit = withContext(Dispatchers.IO) {
        queries.deleteByFrame(frameId)
    }

    /** Deletes all adjustments for a project. */
    suspend fun deleteByProject(projectId: String): Unit = withContext(Dispatchers.IO) {
        queries.deleteByProject(projectId)
    }
}
