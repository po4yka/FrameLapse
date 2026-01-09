package com.po4yka.framelapse.presentation.adjustment

import androidx.compose.ui.geometry.Offset
import com.po4yka.framelapse.domain.entity.AdjustmentPointType
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.Landmarks
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.usecase.adjustment.BatchApplyAdjustmentUseCase
import com.po4yka.framelapse.domain.usecase.adjustment.SuggestSimilarFramesUseCase
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState

/**
 * UI state for the manual adjustment screen.
 *
 * Tracks the current adjustment being edited, preview state, undo/redo availability,
 * and batch selection state.
 */
data class ManualAdjustmentState(
    /** The frame being adjusted. */
    val frame: Frame? = null,

    /** The project ID. */
    val projectId: String = "",

    /** Content type determines which landmarks are adjustable. */
    val contentType: ContentType = ContentType.FACE,

    /** Current adjustment being edited. */
    val currentAdjustment: ManualAdjustment? = null,

    /** Original auto-detected landmarks (for comparison/revert). */
    val originalLandmarks: Landmarks? = null,

    /** Path to the preview image (after adjustment). */
    val previewImagePath: String? = null,

    /** Whether preview is currently being generated. */
    val isGeneratingPreview: Boolean = false,

    /** Which point is currently being dragged (null if none). */
    val activeDragPoint: AdjustmentPointType? = null,

    /** Whether undo is available. */
    val canUndo: Boolean = false,

    /** Whether redo is available. */
    val canRedo: Boolean = false,

    /** Description of the command that would be undone (for UI tooltip). */
    val undoDescription: String? = null,

    /** Description of the command that would be redone (for UI tooltip). */
    val redoDescription: String? = null,

    /** Whether batch mode is active. */
    val isBatchMode: Boolean = false,

    /** IDs of frames selected for batch application. */
    val selectedFrameIds: Set<String> = emptySet(),

    /** Suggested frames for batch application. */
    val suggestedFrames: List<SuggestSimilarFramesUseCase.FrameSuggestion> = emptyList(),

    /** All frames in the project (for batch selection). */
    val allFrames: List<Frame> = emptyList(),

    /** Whether the initial data is loading. */
    val isLoading: Boolean = false,

    /** Whether adjustments are being saved. */
    val isSaving: Boolean = false,

    /** Whether batch apply is in progress. */
    val isBatchApplying: Boolean = false,

    /** Progress of batch apply (0.0 to 1.0). */
    val batchProgress: Float = 0f,

    /** Current zoom level (1.0 = 100%). */
    val zoomLevel: Float = 1f,

    /** Pan offset for zoomed view. */
    val panOffset: Offset = Offset.Zero,

    /** Comparison mode for before/after preview. */
    val comparisonMode: ComparisonMode = ComparisonMode.SLIDER,

    /** Whether comparison preview is visible. */
    val showComparison: Boolean = false,

    /** Error message to display (null if none). */
    val error: String? = null,
) : UiState {
    /** Whether there are unsaved changes. */
    val hasUnsavedChanges: Boolean
        get() = currentAdjustment != null && canUndo

    /** Number of frames selected for batch apply. */
    val selectedCount: Int
        get() = selectedFrameIds.size
}

/**
 * Comparison mode for before/after preview.
 */
enum class ComparisonMode {
    /** Side-by-side slider comparison. */
    SLIDER,

    /** Split screen side-by-side. */
    SIDE_BY_SIDE,

    /** Toggle between original and adjusted. */
    TOGGLE,
}

/**
 * User events for the manual adjustment screen.
 */
sealed interface ManualAdjustmentEvent : UiEvent {
    /**
     * Initialize the screen with a frame ID.
     */
    data class Initialize(val frameId: String, val projectId: String) : ManualAdjustmentEvent

    /**
     * User started dragging a landmark point.
     */
    data class StartDrag(val pointType: AdjustmentPointType) : ManualAdjustmentEvent

    /**
     * User is dragging a landmark point.
     *
     * @param delta The drag delta in normalized coordinates (0.0 to 1.0)
     */
    data class UpdateDrag(val delta: Offset) : ManualAdjustmentEvent

    /**
     * User finished dragging a landmark point.
     */
    data object EndDrag : ManualAdjustmentEvent

    /**
     * Generate a preview with current adjustment.
     */
    data object GeneratePreview : ManualAdjustmentEvent

    /**
     * Undo the last adjustment.
     */
    data object Undo : ManualAdjustmentEvent

    /**
     * Redo the last undone adjustment.
     */
    data object Redo : ManualAdjustmentEvent

    /**
     * Save the current adjustment.
     */
    data object SaveAdjustment : ManualAdjustmentEvent

    /**
     * Discard changes and go back.
     */
    data object DiscardChanges : ManualAdjustmentEvent

    /**
     * Revert to auto-detected landmarks.
     */
    data object RevertToAutoDetected : ManualAdjustmentEvent

    /**
     * Enter batch mode to apply adjustment to multiple frames.
     */
    data object EnterBatchMode : ManualAdjustmentEvent

    /**
     * Exit batch mode.
     */
    data object ExitBatchMode : ManualAdjustmentEvent

    /**
     * Toggle selection of a frame for batch application.
     */
    data class ToggleFrameForBatch(val frameId: String) : ManualAdjustmentEvent

    /**
     * Select all suggested frames.
     */
    data object SelectAllSuggested : ManualAdjustmentEvent

    /**
     * Clear batch selection.
     */
    data object ClearBatchSelection : ManualAdjustmentEvent

    /**
     * Apply adjustment to selected frames.
     */
    data class ApplyToBatch(val strategy: BatchApplyAdjustmentUseCase.TransferStrategy) : ManualAdjustmentEvent

    /**
     * Change zoom level.
     */
    data class SetZoom(val level: Float) : ManualAdjustmentEvent

    /**
     * Update pan offset.
     */
    data class SetPanOffset(val offset: Offset) : ManualAdjustmentEvent

    /**
     * Reset zoom and pan to default.
     */
    data object ResetZoom : ManualAdjustmentEvent

    /**
     * Toggle comparison mode visibility.
     */
    data object ToggleComparison : ManualAdjustmentEvent

    /**
     * Change comparison mode.
     */
    data class SetComparisonMode(val mode: ComparisonMode) : ManualAdjustmentEvent

    /**
     * Dismiss error message.
     */
    data object DismissError : ManualAdjustmentEvent

    /**
     * Navigate to next frame in project.
     */
    data object NavigateToNextFrame : ManualAdjustmentEvent

    /**
     * Navigate to previous frame in project.
     */
    data object NavigateToPreviousFrame : ManualAdjustmentEvent
}

/**
 * One-time side effects for the manual adjustment screen.
 */
sealed interface ManualAdjustmentEffect : UiEffect {
    /**
     * Navigate back to previous screen.
     */
    data object NavigateBack : ManualAdjustmentEffect

    /**
     * Show an error message.
     */
    data class ShowError(val message: String) : ManualAdjustmentEffect

    /**
     * Show a success message.
     */
    data class ShowSuccess(val message: String) : ManualAdjustmentEffect

    /**
     * Navigate to a different frame.
     */
    data class NavigateToFrame(val frameId: String) : ManualAdjustmentEffect

    /**
     * Show discard changes confirmation dialog.
     */
    data object ShowDiscardConfirmation : ManualAdjustmentEffect

    /**
     * Show batch apply result dialog.
     */
    data class ShowBatchResult(val successCount: Int, val failedCount: Int) : ManualAdjustmentEffect

    /**
     * Trigger haptic feedback for drag operations.
     */
    data object HapticFeedback : ManualAdjustmentEffect
}
