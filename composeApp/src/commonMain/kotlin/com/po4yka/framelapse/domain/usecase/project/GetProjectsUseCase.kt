package com.po4yka.framelapse.domain.usecase.project

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Retrieves all projects.
 */
class GetProjectsUseCase(private val projectRepository: ProjectRepository) {
    /**
     * Gets all projects sorted by last updated.
     *
     * @return Result containing a list of all projects.
     */
    suspend operator fun invoke(): Result<List<Project>> = projectRepository.getAllProjects()

    /**
     * Observes all projects reactively.
     *
     * @return Flow emitting the list of projects when changes occur.
     */
    fun observe(): Flow<List<Project>> = projectRepository.observeProjects()
}
