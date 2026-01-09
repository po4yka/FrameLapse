package com.po4yka.framelapse.domain.usecase.project

import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result

/** Deletes a project and all its associated data. */
class DeleteProjectUseCase(private val projectRepository: ProjectRepository) {
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

        // Delete project and cleanup remaining files
        return projectRepository.deleteProject(projectId)
    }
}
