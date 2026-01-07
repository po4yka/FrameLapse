package com.po4yka.framelapse.domain.repository

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Frame data operations.
 */
interface FrameRepository {

    /**
     * Adds a new frame to a project.
     *
     * @param frame The frame to add.
     * @return Result containing the added Frame or an error.
     */
    suspend fun addFrame(frame: Frame): Result<Frame>

    /**
     * Retrieves a frame by its ID.
     *
     * @param id The frame ID.
     * @return Result containing the Frame or an error if not found.
     */
    suspend fun getFrame(id: String): Result<Frame>

    /**
     * Retrieves all frames for a project.
     *
     * @param projectId The project ID.
     * @return Result containing a list of frames sorted by sortOrder/timestamp.
     */
    suspend fun getFramesByProject(projectId: String): Result<List<Frame>>

    /**
     * Retrieves the latest frame for a project (for ghost image).
     *
     * @param projectId The project ID.
     * @return Result containing the latest Frame or null if no frames exist.
     */
    suspend fun getLatestFrame(projectId: String): Result<Frame?>

    /**
     * Retrieves frames within a timestamp range.
     *
     * @param projectId The project ID.
     * @param startTimestamp The start of the range (inclusive).
     * @param endTimestamp The end of the range (inclusive).
     * @return Result containing matching frames.
     */
    suspend fun getFramesByDateRange(projectId: String, startTimestamp: Long, endTimestamp: Long): Result<List<Frame>>

    /**
     * Gets the frame count for a project.
     *
     * @param projectId The project ID.
     * @return Result containing the frame count.
     */
    suspend fun getFrameCount(projectId: String): Result<Long>

    /**
     * Gets the total frame count across all projects.
     *
     * @return Result containing the total frame count.
     */
    suspend fun getTotalFrameCount(): Result<Long>

    /**
     * Updates a frame with alignment results.
     *
     * @param id The frame ID.
     * @param alignedPath Path to the aligned image.
     * @param confidence Detection confidence score.
     * @param landmarks Detected face landmarks.
     * @return Result indicating success or failure.
     */
    suspend fun updateAlignedFrame(
        id: String,
        alignedPath: String,
        confidence: Float,
        landmarks: FaceLandmarks,
    ): Result<Unit>

    /**
     * Updates the sort order of a frame.
     *
     * @param id The frame ID.
     * @param sortOrder The new sort order.
     * @return Result indicating success or failure.
     */
    suspend fun updateSortOrder(id: String, sortOrder: Int): Result<Unit>

    /**
     * Deletes a frame by its ID.
     * Note: This does not delete the associated image files.
     *
     * @param id The frame ID.
     * @return Result indicating success or failure.
     */
    suspend fun deleteFrame(id: String): Result<Unit>

    /**
     * Deletes all frames for a project.
     * Note: This does not delete the associated image files.
     *
     * @param projectId The project ID.
     * @return Result indicating success or failure.
     */
    suspend fun deleteFramesByProject(projectId: String): Result<Unit>

    /**
     * Observes frames for a project reactively.
     *
     * @param projectId The project ID.
     * @return Flow emitting the list of frames when changes occur.
     */
    fun observeFrames(projectId: String): Flow<List<Frame>>
}
