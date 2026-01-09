package com.po4yka.framelapse.presentation.statistics

import com.po4yka.framelapse.domain.entity.GlobalStatistics
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.ProjectStatistics
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState
import com.po4yka.framelapse.presentation.common.CommonEffect

/**
 * Statistics display mode.
 */
enum class StatisticsMode {
    /** Per-project statistics. */
    PROJECT,

    /** Cross-project global overview. */
    GLOBAL,
}

/**
 * UI state for the statistics screen.
 */
data class StatisticsState(
    /** Current display mode (project or global). */
    val mode: StatisticsMode = StatisticsMode.GLOBAL,
    /** Selected project ID for project mode. */
    val selectedProjectId: String? = null,
    /** List of available projects for project selector. */
    val projects: List<Project> = emptyList(),
    /** Statistics for the selected project. */
    val projectStats: ProjectStatistics? = null,
    /** Global statistics across all projects. */
    val globalStats: GlobalStatistics? = null,
    /** Loading indicator. */
    val isLoading: Boolean = false,
    /** Error message if any. */
    val error: String? = null,
) : UiState

/**
 * User events for the statistics screen.
 */
sealed interface StatisticsEvent : UiEvent {
    /**
     * Initialize the statistics screen.
     * @param projectId Optional project ID to open in project mode with that project selected.
     */
    data class Initialize(val projectId: String? = null) : StatisticsEvent

    /**
     * Switch between project and global mode.
     */
    data class SwitchMode(val mode: StatisticsMode) : StatisticsEvent

    /**
     * Select a different project (in project mode).
     */
    data class SelectProject(val projectId: String) : StatisticsEvent

    /**
     * Refresh statistics data.
     */
    data object Refresh : StatisticsEvent
}

/**
 * One-time side effects for the statistics screen.
 */
sealed interface StatisticsEffect : UiEffect {
    /**
     * Show an error message. Delegates to [CommonEffect.ShowError].
     */
    data class ShowError(val message: String) : StatisticsEffect {
        /** Convert to common effect for unified handling. */
        fun toCommon(): CommonEffect.ShowError = CommonEffect.ShowError(message)
    }
}
