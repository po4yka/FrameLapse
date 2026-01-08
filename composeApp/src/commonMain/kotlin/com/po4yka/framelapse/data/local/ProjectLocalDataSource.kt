package com.po4yka.framelapse.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
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
