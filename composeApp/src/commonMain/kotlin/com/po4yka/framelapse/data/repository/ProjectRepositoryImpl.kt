package com.po4yka.framelapse.data.repository

import com.po4yka.framelapse.data.local.ProjectLocalDataSource
import com.po4yka.framelapse.data.mapper.ProjectMapper
import com.po4yka.framelapse.data.storage.StorageCleanupManager
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of ProjectRepository using SQLDelight local data source.
 */
class ProjectRepositoryImpl(
    private val localDataSource: ProjectLocalDataSource,
    private val cleanupManager: StorageCleanupManager,
) : ProjectRepository {

    override suspend fun createProject(name: String, fps: Int): Result<Project> = try {
        val now = currentTimeMillis()
        val project = Project(
            id = generateId(),
            name = name,
            createdAt = now,
            updatedAt = now,
            fps = fps,
            resolution = Resolution.HD_1080P,
            orientation = Orientation.PORTRAIT,
            thumbnailPath = null,
        )

        localDataSource.insert(ProjectMapper.toInsertParams(project))
        Result.Success(project)
    } catch (e: Exception) {
        Result.Error(e, "Failed to create project: ${e.message}")
    }

    override suspend fun getProject(id: String): Result<Project> = try {
        val entity = localDataSource.getById(id)
        if (entity != null) {
            Result.Success(ProjectMapper.toDomain(entity))
        } else {
            Result.Error(
                NoSuchElementException("Project not found: $id"),
                "Project not found",
            )
        }
    } catch (e: Exception) {
        Result.Error(e, "Failed to get project: ${e.message}")
    }

    override suspend fun getAllProjects(): Result<List<Project>> = try {
        val entities = localDataSource.getAll()
        Result.Success(entities.map { ProjectMapper.toDomain(it) })
    } catch (e: Exception) {
        Result.Error(e, "Failed to get projects: ${e.message}")
    }

    override suspend fun updateProject(project: Project): Result<Unit> = try {
        val updatedProject = project.copy(
            updatedAt = currentTimeMillis(),
        )
        localDataSource.update(ProjectMapper.toUpdateParams(updatedProject))
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to update project: ${e.message}")
    }

    override suspend fun deleteProject(id: String): Result<Unit> = try {
        val cleanupResult = cleanupManager.cleanupProject(id)
        if (cleanupResult is Result.Error) {
            return cleanupResult
        }
        localDataSource.delete(id)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to delete project: ${e.message}")
    }

    override suspend fun updateThumbnail(id: String, thumbnailPath: String): Result<Unit> = try {
        val now = currentTimeMillis()
        localDataSource.updateThumbnail(id, thumbnailPath, now)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to update thumbnail: ${e.message}")
    }

    override suspend fun exists(id: String): Result<Boolean> = try {
        Result.Success(localDataSource.exists(id))
    } catch (e: Exception) {
        Result.Error(e, "Failed to check project existence: ${e.message}")
    }

    override suspend fun getProjectCount(): Result<Long> = try {
        Result.Success(localDataSource.getCount())
    } catch (e: Exception) {
        Result.Error(e, "Failed to get project count: ${e.message}")
    }

    override fun observeProjects(): Flow<List<Project>> = localDataSource.observeAll().map { entities ->
        entities.map { ProjectMapper.toDomain(it) }
    }

    override fun observeProject(id: String): Flow<Project?> = localDataSource.observeById(id).map { entity ->
        entity?.let { ProjectMapper.toDomain(it) }
    }

    private fun generateId(): String {
        // Generate a simple UUID-like string
        val timestamp = currentTimeMillis()
        val random = (0..999999).random()
        return "proj_${timestamp}_$random"
    }
}
