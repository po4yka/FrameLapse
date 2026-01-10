package com.po4yka.framelapse.domain.usecase.project

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

/**
 * Retrieves a single project by ID.
 */
@Factory
class GetProjectUseCase(private val projectRepository: ProjectRepository) {
    /**
     * Gets a project by its ID.
     *
     * @param projectId The project ID.
     * @return Result containing the Project or an error if not found.
     */
    suspend operator fun invoke(projectId: String): Result<Project> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        return projectRepository.getProject(projectId)
    }

    /**
     * Observes a project by its ID reactively.
     *
     * @param projectId The project ID.
     * @return Flow emitting the project or null when changes occur.
     */
    fun observe(projectId: String): Flow<Project?> = projectRepository.observeProject(projectId)
}
