package com.po4yka.framelapse.presentation.adjustment

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.viewModelScope
import com.po4yka.framelapse.domain.entity.AdjustmentCommandFactory
import com.po4yka.framelapse.domain.entity.AdjustmentPointType
import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.BodyManualAdjustment
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.FaceManualAdjustment
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.LandscapeManualAdjustment
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.entity.MuscleManualAdjustment
import com.po4yka.framelapse.domain.entity.toManualAdjustment
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.service.UndoRedoManager
import com.po4yka.framelapse.domain.usecase.adjustment.ApplyManualAdjustmentUseCase
import com.po4yka.framelapse.domain.usecase.adjustment.BatchApplyAdjustmentUseCase
import com.po4yka.framelapse.domain.usecase.adjustment.SuggestSimilarFramesUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.uuid
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * ViewModel for the manual adjustment screen.
 *
 * Handles drag operations, undo/redo, preview generation, and batch application
 * of manual adjustments to frames.
 */
@Factory
class ManualAdjustmentViewModel(
    private val frameRepository: FrameRepository,
    private val applyAdjustmentUseCase: ApplyManualAdjustmentUseCase,
    private val suggestSimilarFramesUseCase: SuggestSimilarFramesUseCase,
    private val batchApplyAdjustmentUseCase: BatchApplyAdjustmentUseCase,
    private val clock: Clock,
) : BaseViewModel<ManualAdjustmentState, ManualAdjustmentEvent, ManualAdjustmentEffect>(
    ManualAdjustmentState(),
) {

    private val undoRedoManager = UndoRedoManager()
    private var previewJob: Job? = null
    private var dragStartPosition: LandmarkPoint? = null

    init {
        observeUndoRedoState()
    }

    override fun onEvent(event: ManualAdjustmentEvent) {
        when (event) {
            is ManualAdjustmentEvent.Initialize -> initialize(event.frameId, event.projectId)
            is ManualAdjustmentEvent.StartDrag -> startDrag(event.pointType)
            is ManualAdjustmentEvent.UpdateDrag -> updateDrag(event.delta)
            is ManualAdjustmentEvent.EndDrag -> endDrag()
            is ManualAdjustmentEvent.GeneratePreview -> generatePreview()
            is ManualAdjustmentEvent.Undo -> undo()
            is ManualAdjustmentEvent.Redo -> redo()
            is ManualAdjustmentEvent.SaveAdjustment -> saveAdjustment()
            is ManualAdjustmentEvent.DiscardChanges -> discardChanges()
            is ManualAdjustmentEvent.RevertToAutoDetected -> revertToAutoDetected()
            is ManualAdjustmentEvent.EnterBatchMode -> enterBatchMode()
            is ManualAdjustmentEvent.ExitBatchMode -> exitBatchMode()
            is ManualAdjustmentEvent.ToggleFrameForBatch -> toggleFrameForBatch(event.frameId)
            is ManualAdjustmentEvent.SelectAllSuggested -> selectAllSuggested()
            is ManualAdjustmentEvent.ClearBatchSelection -> clearBatchSelection()
            is ManualAdjustmentEvent.ApplyToBatch -> applyToBatch(event.strategy)
            is ManualAdjustmentEvent.SetZoom -> setZoom(event.level)
            is ManualAdjustmentEvent.SetPanOffset -> setPanOffset(event.offset)
            is ManualAdjustmentEvent.ResetZoom -> resetZoom()
            is ManualAdjustmentEvent.ToggleComparison -> toggleComparison()
            is ManualAdjustmentEvent.SetComparisonMode -> setComparisonMode(event.mode)
            is ManualAdjustmentEvent.DismissError -> dismissError()
            is ManualAdjustmentEvent.NavigateToNextFrame -> navigateToNextFrame()
            is ManualAdjustmentEvent.NavigateToPreviousFrame -> navigateToPreviousFrame()
        }
    }

    private fun observeUndoRedoState() {
        undoRedoManager.canUndo
            .onEach { canUndo ->
                updateState {
                    copy(
                        canUndo = canUndo,
                        undoDescription = undoRedoManager.getUndoDescription(),
                    )
                }
            }
            .launchIn(viewModelScope)

        undoRedoManager.canRedo
            .onEach { canRedo ->
                updateState {
                    copy(
                        canRedo = canRedo,
                        redoDescription = undoRedoManager.getRedoDescription(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun initialize(frameId: String, projectId: String) {
        updateState { copy(isLoading = true, projectId = projectId) }

        viewModelScope.launch {
            // Load frame
            when (val result = frameRepository.getFrame(frameId)) {
                is Result.Success -> {
                    val frame = result.data
                    val landmarks = frame.landmarks
                    val contentType = determineContentType(landmarks)

                    // Create initial adjustment from auto-detected landmarks
                    val initialAdjustment = createInitialAdjustment(landmarks, contentType)

                    updateState {
                        copy(
                            frame = frame,
                            contentType = contentType,
                            originalLandmarks = landmarks,
                            currentAdjustment = initialAdjustment,
                            isLoading = false,
                        )
                    }

                    // Clear undo/redo for new frame
                    undoRedoManager.clear()

                    // Load all frames for batch mode
                    loadAllFrames(projectId)
                }
                is Result.Error -> {
                    updateState { copy(isLoading = false) }
                    sendEffect(
                        ManualAdjustmentEffect.ShowError(
                            result.message ?: "Failed to load frame",
                        ),
                    )
                }
                is Result.Loading -> {
                    // Already showing loading state
                }
            }
        }
    }

    private fun determineContentType(landmarks: com.po4yka.framelapse.domain.entity.Landmarks?): ContentType =
        when (landmarks) {
            is FaceLandmarks -> ContentType.FACE
            is BodyLandmarks -> ContentType.BODY
            else -> ContentType.FACE // Default
        }

    private fun createInitialAdjustment(
        landmarks: com.po4yka.framelapse.domain.entity.Landmarks?,
        contentType: ContentType,
    ): ManualAdjustment? = when {
        landmarks is FaceLandmarks && contentType == ContentType.FACE -> {
            landmarks.toManualAdjustment(uuid(), clock.nowMillis())
        }
        landmarks is BodyLandmarks && (contentType == ContentType.BODY || contentType == ContentType.MUSCLE) -> {
            landmarks.toManualAdjustment(uuid(), clock.nowMillis())
        }
        else -> null
    }

    private fun loadAllFrames(projectId: String) {
        viewModelScope.launch {
            when (val result = frameRepository.getFramesByProject(projectId)) {
                is Result.Success -> {
                    updateState { copy(allFrames = result.data) }
                }
                is Result.Error -> {
                    // Non-critical, batch mode will work with empty list
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun startDrag(pointType: AdjustmentPointType) {
        val adjustment = currentState.currentAdjustment ?: return

        // Store starting position for undo command
        dragStartPosition = getPointPosition(adjustment, pointType)

        updateState { copy(activeDragPoint = pointType) }
        sendEffect(ManualAdjustmentEffect.HapticFeedback)
    }

    private fun updateDrag(delta: Offset) {
        val adjustment = currentState.currentAdjustment ?: return
        val pointType = currentState.activeDragPoint ?: return

        val updatedAdjustment = movePoint(adjustment, pointType, delta)
        updateState { copy(currentAdjustment = updatedAdjustment) }
    }

    private fun endDrag() {
        val adjustment = currentState.currentAdjustment ?: return
        val pointType = currentState.activeDragPoint ?: return
        val startPos = dragStartPosition ?: return

        val endPos = getPointPosition(adjustment, pointType) ?: return

        // Create command for undo/redo based on adjustment type
        val previousAdjustment = movePointTo(adjustment, pointType, startPos)
        val commandTimestamp = clock.nowMillis()
        val command = when (previousAdjustment) {
            is FaceManualAdjustment -> AdjustmentCommandFactory.createFacePointMove(
                pointType = pointType,
                previousPosition = startPos,
                newPosition = endPos,
                originalAdjustment = previousAdjustment,
                timestamp = commandTimestamp,
            )
            is BodyManualAdjustment -> AdjustmentCommandFactory.createBodyPointMove(
                pointType = pointType,
                previousPosition = startPos,
                newPosition = endPos,
                originalAdjustment = previousAdjustment,
                timestamp = commandTimestamp,
            )
            is MuscleManualAdjustment -> AdjustmentCommandFactory.createBodyPointMove(
                pointType = pointType,
                previousPosition = startPos,
                newPosition = endPos,
                originalAdjustment = previousAdjustment.bodyAdjustment,
                timestamp = commandTimestamp,
            )
            is LandscapeManualAdjustment -> {
                // Landscape adjustments use corner commands - for now skip undo
                updateState { copy(activeDragPoint = null) }
                dragStartPosition = null
                generatePreview()
                return
            }
        }

        undoRedoManager.pushCommand(command)

        updateState { copy(activeDragPoint = null) }
        dragStartPosition = null

        // Auto-generate preview after drag
        generatePreview()
    }

    private fun getPointPosition(adjustment: ManualAdjustment, pointType: AdjustmentPointType): LandmarkPoint? =
        when (adjustment) {
            is FaceManualAdjustment -> when (pointType) {
                AdjustmentPointType.LEFT_EYE -> adjustment.leftEyeCenter
                AdjustmentPointType.RIGHT_EYE -> adjustment.rightEyeCenter
                else -> null
            }
            is BodyManualAdjustment -> when (pointType) {
                AdjustmentPointType.LEFT_SHOULDER -> adjustment.leftShoulder
                AdjustmentPointType.RIGHT_SHOULDER -> adjustment.rightShoulder
                AdjustmentPointType.LEFT_HIP -> adjustment.leftHip
                AdjustmentPointType.RIGHT_HIP -> adjustment.rightHip
                else -> null
            }
            is MuscleManualAdjustment -> getPointPosition(adjustment.bodyAdjustment, pointType)
            is LandscapeManualAdjustment -> when (pointType) {
                AdjustmentPointType.CORNER_TOP_LEFT -> adjustment.cornerKeypoints.getOrNull(0)
                AdjustmentPointType.CORNER_TOP_RIGHT -> adjustment.cornerKeypoints.getOrNull(1)
                AdjustmentPointType.CORNER_BOTTOM_LEFT -> adjustment.cornerKeypoints.getOrNull(2)
                AdjustmentPointType.CORNER_BOTTOM_RIGHT -> adjustment.cornerKeypoints.getOrNull(3)
                else -> null
            }
        }

    private fun movePoint(
        adjustment: ManualAdjustment,
        pointType: AdjustmentPointType,
        delta: Offset,
    ): ManualAdjustment {
        val current = getPointPosition(adjustment, pointType) ?: return adjustment
        val newPos = LandmarkPoint(
            x = current.x + delta.x,
            y = current.y + delta.y,
            z = current.z,
        )
        return movePointTo(adjustment, pointType, newPos)
    }

    private fun movePointTo(
        adjustment: ManualAdjustment,
        pointType: AdjustmentPointType,
        newPos: LandmarkPoint,
    ): ManualAdjustment {
        return when (adjustment) {
            is FaceManualAdjustment -> when (pointType) {
                AdjustmentPointType.LEFT_EYE -> adjustment.copy(leftEyeCenter = newPos)
                AdjustmentPointType.RIGHT_EYE -> adjustment.copy(rightEyeCenter = newPos)
                else -> adjustment
            }
            is BodyManualAdjustment -> when (pointType) {
                AdjustmentPointType.LEFT_SHOULDER -> adjustment.copy(leftShoulder = newPos)
                AdjustmentPointType.RIGHT_SHOULDER -> adjustment.copy(rightShoulder = newPos)
                AdjustmentPointType.LEFT_HIP -> adjustment.copy(leftHip = newPos)
                AdjustmentPointType.RIGHT_HIP -> adjustment.copy(rightHip = newPos)
                else -> adjustment
            }
            is MuscleManualAdjustment -> adjustment.copy(
                bodyAdjustment = movePointTo(adjustment.bodyAdjustment, pointType, newPos) as BodyManualAdjustment,
            )
            is LandscapeManualAdjustment -> {
                val corners = adjustment.cornerKeypoints.toMutableList()
                val index = when (pointType) {
                    AdjustmentPointType.CORNER_TOP_LEFT -> 0
                    AdjustmentPointType.CORNER_TOP_RIGHT -> 1
                    AdjustmentPointType.CORNER_BOTTOM_LEFT -> 2
                    AdjustmentPointType.CORNER_BOTTOM_RIGHT -> 3
                    else -> return adjustment
                }
                if (index in corners.indices) {
                    corners[index] = newPos
                }
                adjustment.copy(cornerKeypoints = corners)
            }
        }
    }

    private fun generatePreview() {
        val frame = currentState.frame ?: return
        val adjustment = currentState.currentAdjustment ?: return

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            updateState { copy(isGeneratingPreview = true) }

            when (
                val result = applyAdjustmentUseCase.generatePreview(
                    frameId = frame.id,
                    adjustment = adjustment,
                    settings = AlignmentSettings(),
                )
            ) {
                is Result.Success -> {
                    // Preview is an ImageData, we'd need to save it temporarily
                    // For now, we'll skip preview path and rely on the overlay
                    updateState { copy(isGeneratingPreview = false) }
                }
                is Result.Error -> {
                    updateState { copy(isGeneratingPreview = false) }
                    sendEffect(ManualAdjustmentEffect.ShowError("Failed to generate preview"))
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun undo() {
        val previousAdjustment = undoRedoManager.undo()
        if (previousAdjustment != null) {
            updateState { copy(currentAdjustment = previousAdjustment) }
            generatePreview()
        }
    }

    private fun redo() {
        val nextAdjustment = undoRedoManager.redo()
        if (nextAdjustment != null) {
            updateState { copy(currentAdjustment = nextAdjustment) }
            generatePreview()
        }
    }

    private fun saveAdjustment() {
        val frame = currentState.frame ?: return
        val adjustment = currentState.currentAdjustment ?: return

        viewModelScope.launch {
            updateState { copy(isSaving = true) }

            when (
                val result = applyAdjustmentUseCase(
                    frameId = frame.id,
                    adjustment = adjustment,
                    contentType = currentState.contentType,
                )
            ) {
                is Result.Success -> {
                    updateState { copy(isSaving = false) }
                    undoRedoManager.clear()
                    sendEffect(ManualAdjustmentEffect.ShowSuccess("Adjustment saved"))
                    sendEffect(ManualAdjustmentEffect.NavigateBack)
                }
                is Result.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(ManualAdjustmentEffect.ShowError(result.message ?: "Failed to save"))
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun discardChanges() {
        if (currentState.hasUnsavedChanges) {
            sendEffect(ManualAdjustmentEffect.ShowDiscardConfirmation)
        } else {
            sendEffect(ManualAdjustmentEffect.NavigateBack)
        }
    }

    private fun revertToAutoDetected() {
        val landmarks = currentState.originalLandmarks
        val contentType = currentState.contentType
        val initialAdjustment = createInitialAdjustment(landmarks, contentType)

        updateState { copy(currentAdjustment = initialAdjustment) }
        undoRedoManager.clear()
        generatePreview()
    }

    private fun enterBatchMode() {
        updateState { copy(isBatchMode = true) }
        loadSuggestions()
    }

    private fun exitBatchMode() {
        updateState {
            copy(
                isBatchMode = false,
                selectedFrameIds = emptySet(),
                suggestedFrames = emptyList(),
            )
        }
    }

    private fun loadSuggestions() {
        val frame = currentState.frame ?: return

        viewModelScope.launch {
            when (
                val result = suggestSimilarFramesUseCase(
                    referenceFrameId = frame.id,
                    projectId = currentState.projectId,
                    contentType = currentState.contentType,
                )
            ) {
                is Result.Success -> {
                    val suggestions = result.data
                    updateState {
                        copy(
                            suggestedFrames = suggestions.similarFrames +
                                suggestions.lowConfidenceFrames +
                                suggestions.noDetectionFrames,
                        )
                    }
                }
                is Result.Error -> {
                    // Non-critical, batch selection still works
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun toggleFrameForBatch(frameId: String) {
        val currentSelection = currentState.selectedFrameIds
        val newSelection = if (frameId in currentSelection) {
            currentSelection - frameId
        } else {
            currentSelection + frameId
        }
        updateState { copy(selectedFrameIds = newSelection) }
    }

    private fun selectAllSuggested() {
        val suggestedIds = currentState.suggestedFrames.map { it.frame.id }.toSet()
        updateState { copy(selectedFrameIds = suggestedIds) }
    }

    private fun clearBatchSelection() {
        updateState { copy(selectedFrameIds = emptySet()) }
    }

    private fun applyToBatch(strategy: BatchApplyAdjustmentUseCase.TransferStrategy) {
        val frame = currentState.frame ?: return
        val targetIds = currentState.selectedFrameIds.toList()

        if (targetIds.isEmpty()) {
            sendEffect(ManualAdjustmentEffect.ShowError("No frames selected"))
            return
        }

        viewModelScope.launch {
            updateState { copy(isBatchApplying = true, batchProgress = 0f) }

            when (
                val result = batchApplyAdjustmentUseCase(
                    sourceFrameId = frame.id,
                    targetFrameIds = targetIds,
                    contentType = currentState.contentType,
                    strategy = strategy,
                    onProgress = { current, total ->
                        updateState { copy(batchProgress = current.toFloat() / total) }
                    },
                )
            ) {
                is Result.Success -> {
                    val batchResult = result.data
                    updateState { copy(isBatchApplying = false, batchProgress = 0f) }
                    sendEffect(
                        ManualAdjustmentEffect.ShowBatchResult(
                            successCount = batchResult.successCount,
                            failedCount = batchResult.failedFrameIds.size,
                        ),
                    )
                    exitBatchMode()
                }
                is Result.Error -> {
                    updateState { copy(isBatchApplying = false, batchProgress = 0f) }
                    sendEffect(ManualAdjustmentEffect.ShowError(result.message ?: "Batch apply failed"))
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun setZoom(level: Float) {
        updateState { copy(zoomLevel = level.coerceIn(MIN_ZOOM, MAX_ZOOM)) }
    }

    private fun setPanOffset(offset: Offset) {
        updateState { copy(panOffset = offset) }
    }

    private fun resetZoom() {
        updateState { copy(zoomLevel = 1f, panOffset = Offset.Zero) }
    }

    private fun toggleComparison() {
        updateState { copy(showComparison = !showComparison) }
    }

    private fun setComparisonMode(mode: ComparisonMode) {
        updateState { copy(comparisonMode = mode) }
    }

    private fun dismissError() {
        updateState { copy(error = null) }
    }

    private fun navigateToNextFrame() {
        val currentIndex = currentState.allFrames.indexOfFirst { it.id == currentState.frame?.id }
        val nextFrame = currentState.allFrames.getOrNull(currentIndex + 1)
        if (nextFrame != null) {
            sendEffect(ManualAdjustmentEffect.NavigateToFrame(nextFrame.id))
        }
    }

    private fun navigateToPreviousFrame() {
        val currentIndex = currentState.allFrames.indexOfFirst { it.id == currentState.frame?.id }
        val prevFrame = currentState.allFrames.getOrNull(currentIndex - 1)
        if (prevFrame != null) {
            sendEffect(ManualAdjustmentEffect.NavigateToFrame(prevFrame.id))
        }
    }

    /**
     * Called when user confirms discarding changes.
     */
    fun confirmDiscard() {
        undoRedoManager.clear()
        sendEffect(ManualAdjustmentEffect.NavigateBack)
    }

    override fun onCleared() {
        previewJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 4f
    }
}
