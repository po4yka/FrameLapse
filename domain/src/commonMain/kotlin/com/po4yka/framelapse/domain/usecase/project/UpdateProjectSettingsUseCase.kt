package com.po4yka.framelapse.domain.usecase.project

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.currentTimeMillis

/**
 * Updates project settings.
 */
class UpdateProjectSettingsUseCase(private val projectRepository: ProjectRepository) {
    /**
     * Updates a project's settings.
     *
     * @param project The project with updated values.
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(project: Project): Result<Unit> {
        // Validate project data
        if (project.id.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (project.name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project name cannot be empty"),
                "Project name cannot be empty",
            )
        }

        if (project.fps !in 1..60) {
            return Result.Error(
                IllegalArgumentException("FPS must be between 1 and 60"),
                "FPS must be between 1 and 60",
            )
        }

        // Check if project exists
        val existsResult = projectRepository.exists(project.id)
        if (existsResult.isError) {
            return Result.Error(existsResult.exceptionOrNull()!!, "Failed to check project existence")
        }
        if (existsResult.getOrNull() != true) {
            return Result.Error(
                NoSuchElementException("Project not found: ${project.id}"),
                "Project not found",
            )
        }

        // Update project with current timestamp
        val updatedProject = project.copy(updatedAt = currentTimeMillis())
        return projectRepository.updateProject(updatedProject)
    }
}
