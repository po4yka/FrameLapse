package com.po4yka.framelapse.presentation.statistics

import androidx.lifecycle.viewModelScope
import com.po4yka.framelapse.domain.usecase.project.GetProjectsUseCase
import com.po4yka.framelapse.domain.usecase.statistics.GetGlobalStatisticsUseCase
import com.po4yka.framelapse.domain.usecase.statistics.GetProjectStatisticsUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * ViewModel for the statistics screen.
 * Manages mode switching, project selection, and loading statistics data.
 */
@Factory
class StatisticsViewModel(
    private val getProjectsUseCase: GetProjectsUseCase,
    private val getProjectStatisticsUseCase: GetProjectStatisticsUseCase,
    private val getGlobalStatisticsUseCase: GetGlobalStatisticsUseCase,
) : BaseViewModel<StatisticsState, StatisticsEvent, StatisticsEffect>(StatisticsState()) {

    override fun onEvent(event: StatisticsEvent) {
        when (event) {
            is StatisticsEvent.Initialize -> initialize(event.projectId)
            is StatisticsEvent.SwitchMode -> switchMode(event.mode)
            is StatisticsEvent.SelectProject -> selectProject(event.projectId)
            is StatisticsEvent.Refresh -> refresh()
        }
    }

    private fun initialize(projectId: String?) {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            // Load projects for selector dropdown
            when (val projectsResult = getProjectsUseCase()) {
                is Result.Success -> {
                    updateState { copy(projects = projectsResult.data) }
                }
                is Result.Error -> {
                    sendEffect(StatisticsEffect.ShowError(projectsResult.message ?: "Failed to load projects"))
                }
                is Result.Loading -> { /* ignored */ }
            }

            // If projectId provided, start in project mode with that project selected
            if (projectId != null) {
                updateState {
                    copy(
                        mode = StatisticsMode.PROJECT,
                        selectedProjectId = projectId,
                    )
                }
                loadProjectStats(projectId)
            } else {
                // Start in global mode by default
                loadGlobalStats()
            }
        }
    }

    private fun switchMode(mode: StatisticsMode) {
        updateState { copy(mode = mode, error = null) }

        when (mode) {
            StatisticsMode.PROJECT -> {
                // Use selected project or first available project
                val projectId = currentState.selectedProjectId
                    ?: currentState.projects.firstOrNull()?.id

                if (projectId != null) {
                    updateState { copy(selectedProjectId = projectId) }
                    loadProjectStats(projectId)
                } else {
                    updateState { copy(isLoading = false) }
                }
            }
            StatisticsMode.GLOBAL -> loadGlobalStats()
        }
    }

    private fun selectProject(projectId: String) {
        updateState { copy(selectedProjectId = projectId, error = null) }
        loadProjectStats(projectId)
    }

    private fun loadProjectStats(projectId: String) {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            when (val result = getProjectStatisticsUseCase(projectId)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            projectStats = result.data,
                            isLoading = false,
                        )
                    }
                }
                is Result.Error -> {
                    val message = result.message ?: "Failed to load project statistics"
                    updateState {
                        copy(
                            isLoading = false,
                            error = message,
                        )
                    }
                    sendEffect(StatisticsEffect.ShowError(message))
                }
                is Result.Loading -> { /* ignored */ }
            }
        }
    }

    private fun loadGlobalStats() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            when (val result = getGlobalStatisticsUseCase()) {
                is Result.Success -> {
                    updateState {
                        copy(
                            globalStats = result.data,
                            isLoading = false,
                        )
                    }
                }
                is Result.Error -> {
                    val message = result.message ?: "Failed to load global statistics"
                    updateState {
                        copy(
                            isLoading = false,
                            error = message,
                        )
                    }
                    sendEffect(StatisticsEffect.ShowError(message))
                }
                is Result.Loading -> { /* ignored */ }
            }
        }
    }

    private fun refresh() {
        when (currentState.mode) {
            StatisticsMode.PROJECT -> {
                currentState.selectedProjectId?.let { loadProjectStats(it) }
            }
            StatisticsMode.GLOBAL -> loadGlobalStats()
        }
    }
}
