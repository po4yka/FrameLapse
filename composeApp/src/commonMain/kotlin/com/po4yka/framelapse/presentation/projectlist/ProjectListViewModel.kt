package com.po4yka.framelapse.presentation.projectlist

import com.po4yka.framelapse.domain.usecase.project.CreateProjectUseCase
import com.po4yka.framelapse.domain.usecase.project.DeleteProjectUseCase
import com.po4yka.framelapse.domain.usecase.project.GetProjectsUseCase
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * ViewModel for the project list screen.
 * Handles project listing, creation, and deletion.
 */
class ProjectListViewModel(
    private val getProjectsUseCase: GetProjectsUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
) : BaseViewModel<ProjectListState, ProjectListEvent, ProjectListEffect>(ProjectListState()) {

    init {
        loadProjects()
    }

    override fun onEvent(event: ProjectListEvent) {
        when (event) {
            is ProjectListEvent.LoadProjects -> loadProjects()
            is ProjectListEvent.ShowCreateDialog -> showCreateDialog()
            is ProjectListEvent.DismissCreateDialog -> dismissCreateDialog()
            is ProjectListEvent.UpdateNewProjectName -> updateNewProjectName(event.name)
            is ProjectListEvent.CreateProject -> createProject()
            is ProjectListEvent.DeleteProject -> deleteProject(event.projectId)
            is ProjectListEvent.SelectProject -> selectProject(event.projectId)
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            getProjectsUseCase.observe()
                .onStart { updateState { copy(isLoading = true, error = null) } }
                .catch { error ->
                    updateState { copy(isLoading = false, error = error.message) }
                    sendEffect(ProjectListEffect.ShowError(error.message ?: "Failed to load projects"))
                }
                .collect { projects ->
                    updateState { copy(projects = projects, isLoading = false) }
                }
        }
    }

    private fun showCreateDialog() {
        updateState { copy(showCreateDialog = true, newProjectName = "") }
    }

    private fun dismissCreateDialog() {
        updateState { copy(showCreateDialog = false, newProjectName = "") }
    }

    private fun updateNewProjectName(name: String) {
        updateState { copy(newProjectName = name) }
    }

    private fun createProject() {
        val projectName = currentState.newProjectName.trim()
        if (projectName.isBlank()) {
            sendEffect(ProjectListEffect.ShowError("Project name cannot be empty"))
            return
        }

        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            createProjectUseCase(projectName)
                .onSuccess { project ->
                    updateState { copy(showCreateDialog = false, newProjectName = "", isLoading = false) }
                    sendEffect(ProjectListEffect.NavigateToCapture(project.id))
                }
                .onError { _, message ->
                    updateState { copy(isLoading = false) }
                    sendEffect(ProjectListEffect.ShowError(message ?: "Failed to create project"))
                }
        }
    }

    private fun deleteProject(projectId: String) {
        viewModelScope.launch {
            deleteProjectUseCase(projectId)
                .onError { _, message ->
                    sendEffect(ProjectListEffect.ShowError(message ?: "Failed to delete project"))
                }
        }
    }

    private fun selectProject(projectId: String) {
        sendEffect(ProjectListEffect.NavigateToGallery(projectId))
    }
}
