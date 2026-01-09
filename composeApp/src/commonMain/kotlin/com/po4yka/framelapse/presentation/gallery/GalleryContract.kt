package com.po4yka.framelapse.presentation.gallery

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.ImportPhase
import com.po4yka.framelapse.domain.entity.ImportProgress
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
    /** Current import progress, null when not importing */
    val importProgress: ImportProgress? = null,
    /** Whether to show the import preview bottom sheet */
    val showImportPreview: Boolean = false,
    /** Photo paths pending import confirmation */
    val pendingImportPaths: List<String> = emptyList(),
) : UiState {
    val selectedCount: Int get() = selectedFrameIds.size
    val hasSelection: Boolean get() = selectedFrameIds.isNotEmpty()

    /** Whether an import operation is currently in progress */
    val isImporting: Boolean
        get() = importProgress != null &&
            importProgress.phase != ImportPhase.IDLE &&
            importProgress.phase != ImportPhase.COMPLETE &&
            importProgress.phase != ImportPhase.CANCELLED

    /** Import progress as a percentage (0-100) */
    val importProgressPercent: Int
        get() = importProgress?.progressPercent ?: 0
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

    /**
     * Show import preview bottom sheet before starting import.
     */
    data class ShowImportPreview(val photoPaths: List<String>) : GalleryEvent

    /**
     * Confirm and start import from preview sheet.
     */
    data object ConfirmImport : GalleryEvent

    /**
     * Dismiss the import preview without importing.
     */
    data object DismissImportPreview : GalleryEvent

    /**
     * Cancel an ongoing import operation.
     */
    data object CancelImport : GalleryEvent

    /**
     * Dismiss the import result dialog.
     */
    data object DismissImportResult : GalleryEvent

    /**
     * Retry importing photos that failed.
     */
    data object RetryFailedImports : GalleryEvent
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

    /**
     * Show import result summary dialog.
     */
    data class ShowImportResult(val successCount: Int, val failedCount: Int, val thumbnailPaths: List<String>) :
        GalleryEffect
}
