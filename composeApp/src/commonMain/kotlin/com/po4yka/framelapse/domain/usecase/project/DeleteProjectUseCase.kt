package com.po4yka.framelapse.domain.usecase.project

import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Deletes a project and all its associated data.
 */
class DeleteProjectUseCase(
    private val projectRepository: ProjectRepository,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) {
    /**
     * Deletes a project, its frames, and all associated files.
     *
     * @param projectId The project ID to delete.
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(projectId: String): Result<Unit> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        // Check if project exists
        val projectResult = projectRepository.getProject(projectId)
        if (projectResult.isError) {
            return Result.Error(
                NoSuchElementException("Project not found: $projectId"),
                "Project not found",
            )
        }

        // Delete all frames from database (cascade should handle this, but be explicit)
        val deleteFramesResult = frameRepository.deleteFramesByProject(projectId)
        if (deleteFramesResult.isError) {
            return deleteFramesResult
        }

        // Delete project directory and all files
        val projectDir = fileManager.getProjectDirectory(projectId)
        if (fileManager.fileExists(projectDir)) {
            fileManager.deleteFile(projectDir)
        }

        // Delete project from database
        return projectRepository.deleteProject(projectId)
    }
}
