package com.po4yka.framelapse.presentation.projectlist

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState

/**
 * UI state for the project list screen.
 */
data class ProjectListState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val newProjectName: String = "",
) : UiState

/**
 * User events for the project list screen.
 */
sealed interface ProjectListEvent : UiEvent {
    /**
     * Load all projects.
     */
    data object LoadProjects : ProjectListEvent

    /**
     * Show the create project dialog.
     */
    data object ShowCreateDialog : ProjectListEvent

    /**
     * Dismiss the create project dialog.
     */
    data object DismissCreateDialog : ProjectListEvent

    /**
     * Update the new project name in the dialog.
     */
    data class UpdateNewProjectName(val name: String) : ProjectListEvent

    /**
     * Create a new project with the current name.
     */
    data object CreateProject : ProjectListEvent

    /**
     * Delete a project by ID.
     */
    data class DeleteProject(val projectId: String) : ProjectListEvent

    /**
     * Select a project to view its details.
     */
    data class SelectProject(val projectId: String) : ProjectListEvent
}

/**
 * One-time side effects for the project list screen.
 */
sealed interface ProjectListEffect : UiEffect {
    /**
     * Navigate to the capture screen for a project.
     */
    data class NavigateToCapture(val projectId: String) : ProjectListEffect

    /**
     * Navigate to the gallery screen for a project.
     */
    data class NavigateToGallery(val projectId: String) : ProjectListEffect

    /**
     * Show an error message.
     */
    data class ShowError(val message: String) : ProjectListEffect
}
