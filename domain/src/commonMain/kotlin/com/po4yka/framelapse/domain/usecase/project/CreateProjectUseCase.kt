package com.po4yka.framelapse.domain.usecase.project

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result

/**
 * Creates a new timelapse project.
 */
class CreateProjectUseCase(private val projectRepository: ProjectRepository) {
    /**
     * Creates a new project with the given name.
     *
     * @param name The name for the new project.
     * @param fps Optional frames per second setting (default: 30).
     * @return Result containing the created Project or an error.
     */
    suspend operator fun invoke(name: String, fps: Int = 30): Result<Project> {
        if (name.isBlank()) {
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

        return projectRepository.createProject(name.trim(), fps)
    }
}
