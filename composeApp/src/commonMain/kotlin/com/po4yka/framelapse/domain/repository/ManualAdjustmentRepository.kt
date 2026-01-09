package com.po4yka.framelapse.domain.repository

import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for ManualAdjustment data operations.
 *
 * Handles persistence and retrieval of manual stabilization adjustments
 * that override automatic landmark detection.
 */
interface ManualAdjustmentRepository {

    /**
     * Retrieves the manual adjustment for a specific frame.
     *
     * @param frameId The frame ID.
     * @return Result containing the ManualAdjustment or null if none exists.
     */
    suspend fun getAdjustment(frameId: String): Result<ManualAdjustment?>

    /**
     * Retrieves all manual adjustments for a project.
     *
     * @param projectId The project ID.
     * @return Result containing a list of pairs (frameId, adjustment).
     */
    suspend fun getAdjustmentsByProject(projectId: String): Result<List<Pair<String, ManualAdjustment>>>

    /**
     * Retrieves all active manual adjustments for a project.
     *
     * @param projectId The project ID.
     * @return Result containing a list of pairs (frameId, adjustment).
     */
    suspend fun getActiveAdjustmentsByProject(projectId: String): Result<List<Pair<String, ManualAdjustment>>>

    /**
     * Gets the IDs of frames that have active manual adjustments in a project.
     *
     * @param projectId The project ID.
     * @return Result containing a list of frame IDs.
     */
    suspend fun getFrameIdsWithAdjustments(projectId: String): Result<List<String>>

    /**
     * Counts the number of active manual adjustments in a project.
     *
     * @param projectId The project ID.
     * @return Result containing the count.
     */
    suspend fun countActiveAdjustments(projectId: String): Result<Long>

    /**
     * Checks if a frame has a manual adjustment.
     *
     * @param frameId The frame ID.
     * @return Result containing true if adjustment exists.
     */
    suspend fun hasAdjustment(frameId: String): Result<Boolean>

    /**
     * Saves a manual adjustment for a frame.
     * Creates a new adjustment or updates an existing one.
     *
     * @param frameId The frame ID.
     * @param contentType The content type (FACE, BODY, MUSCLE, LANDSCAPE).
     * @param adjustment The manual adjustment to save.
     * @return Result indicating success or failure.
     */
    suspend fun saveAdjustment(frameId: String, contentType: ContentType, adjustment: ManualAdjustment): Result<Unit>

    /**
     * Toggles the active state of a manual adjustment.
     *
     * @param frameId The frame ID.
     * @param isActive Whether the adjustment should be active.
     * @return Result indicating success or failure.
     */
    suspend fun toggleActive(frameId: String, isActive: Boolean): Result<Unit>

    /**
     * Activates a manual adjustment for a frame.
     *
     * @param frameId The frame ID.
     * @return Result indicating success or failure.
     */
    suspend fun activate(frameId: String): Result<Unit>

    /**
     * Deactivates a manual adjustment for a frame (reverts to auto-detected).
     *
     * @param frameId The frame ID.
     * @return Result indicating success or failure.
     */
    suspend fun deactivate(frameId: String): Result<Unit>

    /**
     * Deletes the manual adjustment for a frame.
     *
     * @param frameId The frame ID.
     * @return Result indicating success or failure.
     */
    suspend fun deleteAdjustment(frameId: String): Result<Unit>

    /**
     * Deletes all manual adjustments for a project.
     *
     * @param projectId The project ID.
     * @return Result indicating success or failure.
     */
    suspend fun deleteAdjustmentsByProject(projectId: String): Result<Unit>

    /**
     * Observes the manual adjustment for a frame reactively.
     *
     * @param frameId The frame ID.
     * @return Flow emitting the adjustment or null when changes occur.
     */
    fun observeAdjustment(frameId: String): Flow<ManualAdjustment?>

    /**
     * Observes all active adjustments for a project reactively.
     *
     * @param projectId The project ID.
     * @return Flow emitting the list of (frameId, adjustment) pairs when changes occur.
     */
    fun observeActiveAdjustments(projectId: String): Flow<List<Pair<String, ManualAdjustment>>>
}
