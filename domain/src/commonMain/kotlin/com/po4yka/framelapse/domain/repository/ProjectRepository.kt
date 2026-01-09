package com.po4yka.framelapse.domain.repository

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Project data operations.
 */
interface ProjectRepository {

    /**
     * Creates a new project with the given name and optional settings.
     *
     * @param name The name of the project.
     * @param fps Frames per second for video export (default: 30).
     * @return Result containing the created Project or an error.
     */
    suspend fun createProject(name: String, fps: Int = 30): Result<Project>

    /**
     * Retrieves a project by its ID.
     *
     * @param id The project ID.
     * @return Result containing the Project or an error if not found.
     */
    suspend fun getProject(id: String): Result<Project>

    /**
     * Retrieves all projects.
     *
     * @return Result containing a list of all projects.
     */
    suspend fun getAllProjects(): Result<List<Project>>

    /**
     * Updates an existing project.
     *
     * @param project The project with updated values.
     * @return Result indicating success or failure.
     */
    suspend fun updateProject(project: Project): Result<Unit>

    /**
     * Deletes a project by its ID.
     * Note: This does not delete associated frames or files.
     *
     * @param id The project ID.
     * @return Result indicating success or failure.
     */
    suspend fun deleteProject(id: String): Result<Unit>

    /**
     * Updates the thumbnail path for a project.
     *
     * @param id The project ID.
     * @param thumbnailPath The path to the thumbnail image.
     * @return Result indicating success or failure.
     */
    suspend fun updateThumbnail(id: String, thumbnailPath: String): Result<Unit>

    /**
     * Updates calibration data for a project.
     * Used for FACE content type projects to set the alignment calibration.
     *
     * @param projectId The project ID.
     * @param imagePath Path to the calibration reference image.
     * @param leftEyeX Calibrated left eye X position (normalized 0-1).
     * @param leftEyeY Calibrated left eye Y position (normalized 0-1).
     * @param rightEyeX Calibrated right eye X position (normalized 0-1).
     * @param rightEyeY Calibrated right eye Y position (normalized 0-1).
     * @param offsetX Alignment offset X adjustment.
     * @param offsetY Alignment offset Y adjustment.
     * @return Result indicating success or failure.
     */
    suspend fun updateCalibration(
        projectId: String,
        imagePath: String,
        leftEyeX: Float,
        leftEyeY: Float,
        rightEyeX: Float,
        rightEyeY: Float,
        offsetX: Float,
        offsetY: Float,
    ): Result<Unit>

    /**
     * Clears calibration data for a project.
     *
     * @param projectId The project ID.
     * @return Result indicating success or failure.
     */
    suspend fun clearCalibration(projectId: String): Result<Unit>

    /**
     * Checks if a project exists.
     *
     * @param id The project ID.
     * @return Result containing true if exists, false otherwise.
     */
    suspend fun exists(id: String): Result<Boolean>

    /**
     * Gets the total number of projects.
     *
     * @return Result containing the project count.
     */
    suspend fun getProjectCount(): Result<Long>

    /**
     * Observes all projects reactively.
     *
     * @return Flow emitting the list of projects when changes occur.
     */
    fun observeProjects(): Flow<List<Project>>

    /**
     * Observes a single project reactively.
     *
     * @param id The project ID.
     * @return Flow emitting the project or null when changes occur.
     */
    fun observeProject(id: String): Flow<Project?>
}
