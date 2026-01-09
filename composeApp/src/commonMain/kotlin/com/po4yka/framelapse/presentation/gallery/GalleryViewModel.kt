package com.po4yka.framelapse.presentation.gallery

import androidx.lifecycle.viewModelScope
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.usecase.frame.DeleteFrameUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetFramesUseCase
import com.po4yka.framelapse.domain.usecase.frame.ImportPhotosUseCase
import com.po4yka.framelapse.domain.usecase.project.GetProjectUseCase
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the gallery screen.
 * Handles frame listing, selection, deletion, and import.
 */
class GalleryViewModel(
    private val getProjectUseCase: GetProjectUseCase,
    private val getFramesUseCase: GetFramesUseCase,
    private val deleteFrameUseCase: DeleteFrameUseCase,
    private val importPhotosUseCase: ImportPhotosUseCase,
    private val frameRepository: FrameRepository,
) : BaseViewModel<GalleryState, GalleryEvent, GalleryEffect>(GalleryState()) {

    override fun onEvent(event: GalleryEvent) {
        when (event) {
            is GalleryEvent.Initialize -> initialize(event.projectId)
            is GalleryEvent.ToggleFrameSelection -> toggleFrameSelection(event.frameId)
            is GalleryEvent.SelectAll -> selectAll()
            is GalleryEvent.ClearSelection -> clearSelection()
            is GalleryEvent.DeleteSelected -> deleteSelected()
            is GalleryEvent.ConfirmDeleteSelected -> confirmDeleteSelected()
            is GalleryEvent.ReorderFrames -> reorderFrames(event.fromIndex, event.toIndex)
            is GalleryEvent.NavigateToCapture -> navigateToCapture()
            is GalleryEvent.NavigateToExport -> navigateToExport()
            is GalleryEvent.ImportPhotos -> importPhotos()
            is GalleryEvent.OpenManualAdjustment -> openManualAdjustment(event.frameId)
        }
    }

    private fun initialize(projectId: String) {
        updateState { copy(projectId = projectId, isLoading = true) }
        loadProject(projectId)
        loadFrames(projectId)
    }

    private fun loadProject(projectId: String) {
        viewModelScope.launch {
            getProjectUseCase(projectId)
                .onSuccess { project ->
                    updateState { copy(project = project) }
                }
                .onError { _, message ->
                    sendEffect(GalleryEffect.ShowError(message ?: "Failed to load project"))
                }
        }
    }

    private fun loadFrames(projectId: String) {
        viewModelScope.launch {
            getFramesUseCase(projectId)
                .onSuccess { frames ->
                    updateState { copy(frames = frames, isLoading = false) }
                }
                .onError { _, message ->
                    updateState { copy(isLoading = false, error = message) }
                    sendEffect(GalleryEffect.ShowError(message ?: "Failed to load frames"))
                }
        }
    }

    private fun toggleFrameSelection(frameId: String) {
        val currentSelection = currentState.selectedFrameIds
        val newSelection = if (frameId in currentSelection) {
            currentSelection - frameId
        } else {
            currentSelection + frameId
        }

        updateState {
            copy(
                selectedFrameIds = newSelection,
                isSelectionMode = newSelection.isNotEmpty(),
            )
        }
    }

    private fun selectAll() {
        val allFrameIds = currentState.frames.map { it.id }.toSet()
        updateState {
            copy(
                selectedFrameIds = allFrameIds,
                isSelectionMode = true,
            )
        }
    }

    private fun clearSelection() {
        updateState {
            copy(
                selectedFrameIds = emptySet(),
                isSelectionMode = false,
            )
        }
    }

    private fun deleteSelected() {
        val count = currentState.selectedFrameIds.size
        if (count > 0) {
            sendEffect(GalleryEffect.ShowDeleteConfirmation(count))
        }
    }

    private fun confirmDeleteSelected() {
        val framesToDelete = currentState.selectedFrameIds.toList()
        if (framesToDelete.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            var failureCount = 0

            framesToDelete.forEach { frameId ->
                deleteFrameUseCase(frameId)
                    .onSuccess { successCount++ }
                    .onError { _, _ -> failureCount++ }
            }

            clearSelection()
            loadFrames(currentState.projectId)

            if (failureCount > 0) {
                sendEffect(GalleryEffect.ShowError("Failed to delete $failureCount frame(s)"))
            } else {
                sendEffect(GalleryEffect.ShowMessage("Deleted $successCount frame(s)"))
            }
        }
    }

    private fun reorderFrames(fromIndex: Int, toIndex: Int) {
        val frames = currentState.frames.toMutableList()
        if (fromIndex !in frames.indices || toIndex !in frames.indices) return

        val frame = frames.removeAt(fromIndex)
        frames.add(toIndex, frame)

        updateState { copy(frames = frames) }

        // Persist reorder to database
        viewModelScope.launch {
            frames.forEachIndexed { index, reorderedFrame ->
                frameRepository.updateSortOrder(reorderedFrame.id, index)
            }
        }
    }

    private fun navigateToCapture() {
        sendEffect(GalleryEffect.NavigateToCapture(currentState.projectId))
    }

    private fun navigateToExport() {
        if (currentState.frames.isEmpty()) {
            sendEffect(GalleryEffect.ShowError("No frames to export"))
            return
        }
        sendEffect(GalleryEffect.NavigateToExport(currentState.projectId))
    }

    private fun importPhotos() {
        sendEffect(GalleryEffect.OpenPhotoPicker)
    }

    private fun openManualAdjustment(frameId: String) {
        sendEffect(
            GalleryEffect.NavigateToManualAdjustment(
                frameId = frameId,
                projectId = currentState.projectId,
            ),
        )
    }

    /**
     * Called when photos have been selected from the system picker.
     */
    fun onPhotosSelected(photoPaths: List<String>) {
        if (photoPaths.isEmpty()) return

        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            importPhotosUseCase(currentState.projectId, photoPaths)
                .onSuccess { importedFrames ->
                    loadFrames(currentState.projectId)
                    sendEffect(GalleryEffect.ShowMessage("Imported ${importedFrames.size} photo(s)"))
                }
                .onError { _, message ->
                    updateState { copy(isLoading = false) }
                    sendEffect(GalleryEffect.ShowError(message ?: "Failed to import photos"))
                }
        }
    }
}
