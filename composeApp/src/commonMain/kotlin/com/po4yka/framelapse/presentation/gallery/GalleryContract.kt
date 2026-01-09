package com.po4yka.framelapse.presentation.gallery

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState

/**
 * UI state for the gallery screen.
 */
data class GalleryState(
    val projectId: String = "",
    val project: Project? = null,
    val frames: List<Frame> = emptyList(),
    val selectedFrameIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSelectionMode: Boolean = false,
    val error: String? = null,
) : UiState {
    val selectedCount: Int get() = selectedFrameIds.size
    val hasSelection: Boolean get() = selectedFrameIds.isNotEmpty()
}

/**
 * User events for the gallery screen.
 */
sealed interface GalleryEvent : UiEvent {
    /**
     * Initialize the gallery for a project.
     */
    data class Initialize(val projectId: String) : GalleryEvent

    /**
     * Select a frame (toggle selection).
     */
    data class ToggleFrameSelection(val frameId: String) : GalleryEvent

    /**
     * Select all frames.
     */
    data object SelectAll : GalleryEvent

    /**
     * Clear all selections.
     */
    data object ClearSelection : GalleryEvent

    /**
     * Delete selected frames.
     */
    data object DeleteSelected : GalleryEvent

    /**
     * Confirm deletion of selected frames.
     */
    data object ConfirmDeleteSelected : GalleryEvent

    /**
     * Reorder frames by moving from one position to another.
     */
    data class ReorderFrames(val fromIndex: Int, val toIndex: Int) : GalleryEvent

    /**
     * Navigate to capture screen.
     */
    data object NavigateToCapture : GalleryEvent

    /**
     * Navigate to export screen.
     */
    data object NavigateToExport : GalleryEvent

    /**
     * Import photos from gallery.
     */
    data object ImportPhotos : GalleryEvent

    /**
     * Open manual adjustment screen for a frame.
     */
    data class OpenManualAdjustment(val frameId: String) : GalleryEvent
}

/**
 * One-time side effects for the gallery screen.
 */
sealed interface GalleryEffect : UiEffect {
    /**
     * Navigate to capture screen.
     */
    data class NavigateToCapture(val projectId: String) : GalleryEffect

    /**
     * Navigate to export screen.
     */
    data class NavigateToExport(val projectId: String) : GalleryEffect

    /**
     * Show an error message.
     */
    data class ShowError(val message: String) : GalleryEffect

    /**
     * Open the system photo picker.
     */
    data object OpenPhotoPicker : GalleryEffect

    /**
     * Show delete confirmation dialog.
     */
    data class ShowDeleteConfirmation(val count: Int) : GalleryEffect

    /**
     * Show a success message.
     */
    data class ShowMessage(val message: String) : GalleryEffect

    /**
     * Navigate to manual adjustment screen.
     */
    data class NavigateToManualAdjustment(val frameId: String, val projectId: String) : GalleryEffect
}
