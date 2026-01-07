package com.po4yka.framelapse.testutil

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake implementation that mimics GetProjectsUseCase behavior for testing.
 * Note: We use composition instead of inheritance since use cases are final classes.
 */
class FakeGetProjectsUseCase {

    private val projects = MutableStateFlow<List<Project>>(emptyList())

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake use case error")

    fun setProjects(projectList: List<Project>) {
        projects.value = projectList.sortedByDescending { it.updatedAt }
    }

    fun clear() {
        projects.value = emptyList()
    }

    suspend operator fun invoke(): Result<List<Project>> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(projects.value)
    }

    fun observe(): Flow<List<Project>> = projects.map { it.sortedByDescending { p -> p.updatedAt } }
}

/**
 * Fake implementation that mimics CreateProjectUseCase behavior for testing.
 * Note: We use composition instead of inheritance since use cases are final classes.
 */
class FakeCreateProjectUseCase {

    private val createdProjects = mutableListOf<Project>()
    private var projectIdCounter = 0

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake use case error")
    var validateName = true

    fun getCreatedProjects(): List<Project> = createdProjects.toList()

    fun clear() {
        createdProjects.clear()
        projectIdCounter = 0
    }

    suspend operator fun invoke(name: String, fps: Int = 30): Result<Project> {
        if (validateName && name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project name cannot be empty"),
                "Project name cannot be empty",
            )
        }

        if (fps !in 1..60) {
            return Result.Error(
                IllegalArgumentException("FPS must be between 1 and 60"),
                "FPS must be between 1 and 60",
            )
        }

        if (shouldFail) return Result.Error(failureException)

        val id = "project_${++projectIdCounter}"
        val now = System.currentTimeMillis()
        val project = Project(
            id = id,
            name = name.trim(),
            createdAt = now,
            updatedAt = now,
            fps = fps,
        )
        createdProjects.add(project)
        return Result.Success(project)
    }
}

/**
 * Fake implementation that mimics DeleteProjectUseCase behavior for testing.
 * Note: We use composition instead of inheritance since use cases are final classes.
 */
class FakeDeleteProjectUseCase {

    private val deletedProjectIds = mutableListOf<String>()

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake use case error")

    fun getDeletedProjectIds(): List<String> = deletedProjectIds.toList()

    fun clear() {
        deletedProjectIds.clear()
    }

    suspend operator fun invoke(projectId: String): Result<Unit> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (shouldFail) return Result.Error(failureException)

        deletedProjectIds.add(projectId)
        return Result.Success(Unit)
    }
}

/**
 * Fake implementation that mimics FileManager behavior for testing.
 * Note: FileManager is an expect class, so this provides test-only behavior.
 * This is a standalone fake, not an actual FileManager implementation.
 */
class FakeFileManagerTestHelper {

    private val existingFiles = mutableSetOf<String>()
    private val directories = mutableSetOf<String>()

    fun addExistingFile(path: String) {
        existingFiles.add(path)
    }

    fun clear() {
        existingFiles.clear()
        directories.clear()
    }

    fun getAppDirectory(): String = "/fake/app/directory"

    fun getProjectDirectory(projectId: String): String = "/fake/projects/$projectId"

    fun deleteFile(path: String): Boolean {
        existingFiles.remove(path)
        directories.remove(path)
        return true
    }

    fun fileExists(path: String): Boolean = path in existingFiles || path in directories

    fun createDirectory(path: String): Boolean {
        directories.add(path)
        return true
    }
}

/**
 * Fake implementation that mimics StorageCleanupManager behavior for testing.
 * StorageCleanupManager depends on platform-specific managers, so this fake
 * provides controllable test behavior.
 */
class FakeStorageCleanupManager {
    var storageUsageBytes: Long = 0L
    var imageBytes: Long = 0L
    var videoBytes: Long = 0L
    var thumbnailBytes: Long = 0L
    var cleanupCallCount = 0

    fun setStorageUsage(totalBytes: Long, imageBytes: Long = 0L, videoBytes: Long = 0L, thumbnailBytes: Long = 0L) {
        this.storageUsageBytes = totalBytes
        this.imageBytes = imageBytes
        this.videoBytes = videoBytes
        this.thumbnailBytes = thumbnailBytes
    }

    fun getStorageUsage(): com.po4yka.framelapse.data.storage.StorageUsage =
        com.po4yka.framelapse.data.storage.StorageUsage(
            totalBytes = storageUsageBytes,
            imageBytes = imageBytes,
            videoBytes = videoBytes,
            thumbnailBytes = thumbnailBytes,
        )

    suspend fun cleanupProject(projectId: String): com.po4yka.framelapse.domain.util.Result<Unit> {
        cleanupCallCount++
        return com.po4yka.framelapse.domain.util.Result.Success(Unit)
    }

    fun reset() {
        storageUsageBytes = 0L
        imageBytes = 0L
        videoBytes = 0L
        thumbnailBytes = 0L
        cleanupCallCount = 0
    }
}
