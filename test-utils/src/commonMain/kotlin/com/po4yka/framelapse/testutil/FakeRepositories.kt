package com.po4yka.framelapse.testutil

import com.po4yka.framelapse.domain.entity.FaceProjectContent
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.Landmarks
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.repository.SettingsRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake implementation of ProjectRepository for testing.
 */
class FakeProjectRepository : ProjectRepository {

    private val projects = MutableStateFlow<Map<String, Project>>(emptyMap())

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake repository error")

    fun setProjects(projectList: List<Project>) {
        projects.value = projectList.associateBy { it.id }
    }

    fun clear() {
        projects.value = emptyMap()
    }

    override suspend fun createProject(name: String, fps: Int): Result<Project> {
        if (shouldFail) return Result.Error(failureException)

        val id = "project_${projects.value.size + 1}"
        val now = currentTimeMillis()
        val project = Project(
            id = id,
            name = name,
            createdAt = now,
            updatedAt = now,
            fps = fps,
            content = FaceProjectContent(),
        )
        projects.update { it + (id to project) }
        return Result.Success(project)
    }

    override suspend fun getProject(id: String): Result<Project> {
        if (shouldFail) return Result.Error(failureException)

        return projects.value[id]
            ?.let { Result.Success(it) }
            ?: Result.Error(NoSuchElementException("Project not found: $id"))
    }

    override suspend fun getAllProjects(): Result<List<Project>> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(projects.value.values.sortedByDescending { it.updatedAt })
    }

    override suspend fun updateProject(project: Project): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        if (project.id !in projects.value) {
            return Result.Error(NoSuchElementException("Project not found: ${project.id}"))
        }
        projects.update { it + (project.id to project) }
        return Result.Success(Unit)
    }

    override suspend fun deleteProject(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        if (id !in projects.value) {
            return Result.Error(NoSuchElementException("Project not found: $id"))
        }
        projects.update { it - id }
        return Result.Success(Unit)
    }

    override suspend fun updateThumbnail(id: String, thumbnailPath: String): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        val project = projects.value[id]
            ?: return Result.Error(NoSuchElementException("Project not found: $id"))
        projects.update { it + (id to project.copy(thumbnailPath = thumbnailPath)) }
        return Result.Success(Unit)
    }

    override suspend fun exists(id: String): Result<Boolean> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(id in projects.value)
    }

    override suspend fun getProjectCount(): Result<Long> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(projects.value.size.toLong())
    }

    override fun observeProjects(): Flow<List<Project>> =
        projects.map { it.values.sortedByDescending { p -> p.updatedAt } }

    override fun observeProject(id: String): Flow<Project?> = projects.map { it[id] }

    override suspend fun updateCalibration(
        projectId: String,
        imagePath: String,
        leftEyeX: Float,
        leftEyeY: Float,
        rightEyeX: Float,
        rightEyeY: Float,
        offsetX: Float,
        offsetY: Float,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        val project = projects.value[projectId]
            ?: return Result.Error(NoSuchElementException("Project not found: $projectId"))

        val currentContent = project.content as? FaceProjectContent
            ?: return Result.Error(IllegalStateException("Project is not a FACE project"))

        val updatedContent = currentContent.copy(
            calibrationImagePath = imagePath,
            calibrationLeftEyeX = leftEyeX,
            calibrationLeftEyeY = leftEyeY,
            calibrationRightEyeX = rightEyeX,
            calibrationRightEyeY = rightEyeY,
            calibrationOffsetX = offsetX,
            calibrationOffsetY = offsetY,
        )

        val updated = project.copy(
            content = updatedContent,
            updatedAt = currentTimeMillis(),
        )
        projects.update { it + (projectId to updated) }
        return Result.Success(Unit)
    }

    override suspend fun clearCalibration(projectId: String): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        val project = projects.value[projectId]
            ?: return Result.Error(NoSuchElementException("Project not found: $projectId"))

        val currentContent = project.content as? FaceProjectContent
            ?: return Result.Error(IllegalStateException("Project is not a FACE project"))

        val updatedContent = currentContent.copy(
            calibrationImagePath = null,
            calibrationLeftEyeX = null,
            calibrationLeftEyeY = null,
            calibrationRightEyeX = null,
            calibrationRightEyeY = null,
            calibrationOffsetX = 0f,
            calibrationOffsetY = 0f,
        )

        val updated = project.copy(
            content = updatedContent,
            updatedAt = currentTimeMillis(),
        )
        projects.update { it + (projectId to updated) }
        return Result.Success(Unit)
    }
}

/**
 * Fake implementation of FrameRepository for testing.
 */
class FakeFrameRepository : FrameRepository {

    private val frames = MutableStateFlow<Map<String, Frame>>(emptyMap())

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake repository error")

    fun setFrames(frameList: List<Frame>) {
        frames.value = frameList.associateBy { it.id }
    }

    fun clear() {
        frames.value = emptyMap()
    }

    override suspend fun addFrame(frame: Frame): Result<Frame> {
        if (shouldFail) return Result.Error(failureException)

        frames.update { it + (frame.id to frame) }
        return Result.Success(frame)
    }

    override suspend fun getFrame(id: String): Result<Frame> {
        if (shouldFail) return Result.Error(failureException)

        return frames.value[id]
            ?.let { Result.Success(it) }
            ?: Result.Error(NoSuchElementException("Frame not found: $id"))
    }

    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> {
        if (shouldFail) return Result.Error(failureException)

        val projectFrames = frames.value.values
            .filter { it.projectId == projectId }
            .sortedBy { it.sortOrder }
        return Result.Success(projectFrames)
    }

    override suspend fun getLatestFrame(projectId: String): Result<Frame?> {
        if (shouldFail) return Result.Error(failureException)

        val latestFrame = frames.value.values
            .filter { it.projectId == projectId }
            .maxByOrNull { it.timestamp }
        return Result.Success(latestFrame)
    }

    override suspend fun getFramesByDateRange(
        projectId: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): Result<List<Frame>> {
        if (shouldFail) return Result.Error(failureException)

        val matchingFrames = frames.value.values
            .filter { it.projectId == projectId && it.timestamp in startTimestamp..endTimestamp }
            .sortedBy { it.sortOrder }
        return Result.Success(matchingFrames)
    }

    override suspend fun getFrameCount(projectId: String): Result<Long> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(frames.value.values.count { it.projectId == projectId }.toLong())
    }

    override suspend fun getTotalFrameCount(): Result<Long> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(frames.value.size.toLong())
    }

    override suspend fun updateAlignedFrame(
        id: String,
        alignedPath: String,
        confidence: Float,
        landmarks: Landmarks,
        stabilizationResult: StabilizationResult?,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        val frame = frames.value[id]
            ?: return Result.Error(NoSuchElementException("Frame not found: $id"))
        val updated = frame.copy(
            alignedPath = alignedPath,
            confidence = confidence,
            landmarks = landmarks,
            stabilizationResult = stabilizationResult,
        )
        frames.update { it + (id to updated) }
        return Result.Success(Unit)
    }

    override suspend fun updateSortOrder(id: String, sortOrder: Int): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        val frame = frames.value[id]
            ?: return Result.Error(NoSuchElementException("Frame not found: $id"))
        frames.update { it + (id to frame.copy(sortOrder = sortOrder)) }
        return Result.Success(Unit)
    }

    override suspend fun deleteFrame(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        if (id !in frames.value) {
            return Result.Error(NoSuchElementException("Frame not found: $id"))
        }
        frames.update { it - id }
        return Result.Success(Unit)
    }

    override suspend fun deleteFramesByProject(projectId: String): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)

        frames.update { current ->
            current.filterValues { it.projectId != projectId }
        }
        return Result.Success(Unit)
    }

    override fun observeFrames(projectId: String): Flow<List<Frame>> = frames.map { current ->
        current.values
            .filter { it.projectId == projectId }
            .sortedBy { it.sortOrder }
    }
}

/**
 * Fake implementation of SettingsRepository for testing.
 */
class FakeSettingsRepository : SettingsRepository {

    private val settings = MutableStateFlow<Map<String, String>>(emptyMap())

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake repository error")

    fun setSettings(settingsMap: Map<String, String>) {
        settings.value = settingsMap
    }

    fun clear() {
        settings.value = emptyMap()
    }

    override suspend fun getString(key: String): Result<String?> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(settings.value[key])
    }

    override suspend fun setString(key: String, value: String): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)
        settings.update { it + (key to value) }
        return Result.Success(Unit)
    }

    override suspend fun getInt(key: String, default: Int): Result<Int> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(settings.value[key]?.toIntOrNull() ?: default)
    }

    override suspend fun setInt(key: String, value: Int): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)
        settings.update { it + (key to value.toString()) }
        return Result.Success(Unit)
    }

    override suspend fun getBoolean(key: String, default: Boolean): Result<Boolean> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(settings.value[key]?.toBoolean() ?: default)
    }

    override suspend fun setBoolean(key: String, value: Boolean): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)
        settings.update { it + (key to value.toString()) }
        return Result.Success(Unit)
    }

    override suspend fun getFloat(key: String, default: Float): Result<Float> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(settings.value[key]?.toFloatOrNull() ?: default)
    }

    override suspend fun setFloat(key: String, value: Float): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)
        settings.update { it + (key to value.toString()) }
        return Result.Success(Unit)
    }

    override suspend fun remove(key: String): Result<Unit> {
        if (shouldFail) return Result.Error(failureException)
        settings.update { it - key }
        return Result.Success(Unit)
    }

    override suspend fun exists(key: String): Result<Boolean> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(key in settings.value)
    }

    override suspend fun getAll(): Result<Map<String, String>> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(settings.value)
    }
}
