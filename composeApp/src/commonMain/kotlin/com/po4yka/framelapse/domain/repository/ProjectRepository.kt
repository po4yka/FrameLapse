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
