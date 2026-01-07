package com.po4yka.framelapse.presentation.export

import com.po4yka.framelapse.domain.entity.DateRange
import com.po4yka.framelapse.domain.entity.ExportQuality
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.entity.VideoCodec
import com.po4yka.framelapse.domain.usecase.export.CompileVideoUseCase
import com.po4yka.framelapse.domain.usecase.frame.GetFramesUseCase
import com.po4yka.framelapse.domain.usecase.project.GetProjectUseCase
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel for the export screen.
 * Handles export settings configuration and video compilation.
 */
class ExportViewModel(
    private val getProjectUseCase: GetProjectUseCase,
    private val getFramesUseCase: GetFramesUseCase,
    private val compileVideoUseCase: CompileVideoUseCase,
) : BaseViewModel<ExportState, ExportEvent, ExportEffect>(ExportState()) {

    private var exportJob: Job? = null

    override fun onEvent(event: ExportEvent) {
        when (event) {
            is ExportEvent.Initialize -> initialize(event.projectId)
            is ExportEvent.UpdateResolution -> updateResolution(event.resolution)
            is ExportEvent.UpdateFps -> updateFps(event.fps)
            is ExportEvent.UpdateCodec -> updateCodec(event.codec)
            is ExportEvent.UpdateQuality -> updateQuality(event.quality)
            is ExportEvent.UpdateDateRange -> updateDateRange(event.dateRange)
            is ExportEvent.StartExport -> startExport()
            is ExportEvent.CancelExport -> cancelExport()
            is ExportEvent.ShareVideo -> shareVideo()
            is ExportEvent.DismissResult -> dismissResult()
        }
    }

    private fun initialize(projectId: String) {
        updateState { copy(projectId = projectId) }
        loadProject(projectId)
        loadFrameCount(projectId)
    }

    private fun loadProject(projectId: String) {
        viewModelScope.launch {
            getProjectUseCase(projectId)
                .onSuccess { project ->
                    updateState {
                        copy(
                            project = project,
                            exportSettings = exportSettings.copy(
                                resolution = project?.resolution ?: Resolution.HD_1080P,
                                fps = project?.fps ?: DEFAULT_FPS,
                            ),
                        )
                    }
                }
                .onError { _, message ->
                    sendEffect(ExportEffect.ShowError(message ?: "Failed to load project"))
                }
        }
    }

    private fun loadFrameCount(projectId: String) {
        viewModelScope.launch {
            getFramesUseCase(projectId)
                .onSuccess { frames ->
                    updateState { copy(frameCount = frames.size) }
                }
        }
    }

    private fun updateResolution(resolution: Resolution) {
        updateState { copy(exportSettings = exportSettings.copy(resolution = resolution)) }
    }

    private fun updateFps(fps: Int) {
        val clampedFps = fps.coerceIn(MIN_FPS, MAX_FPS)
        updateState { copy(exportSettings = exportSettings.copy(fps = clampedFps)) }
    }

    private fun updateCodec(codec: VideoCodec) {
        updateState { copy(exportSettings = exportSettings.copy(codec = codec)) }
    }

    private fun updateQuality(quality: ExportQuality) {
        updateState { copy(exportSettings = exportSettings.copy(quality = quality)) }
    }

    private fun updateDateRange(dateRange: DateRange?) {
        updateState { copy(exportSettings = exportSettings.copy(dateRange = dateRange)) }
    }

    private fun startExport() {
        if (currentState.isExporting || currentState.frameCount == 0) {
            return
        }

        exportJob = viewModelScope.launch {
            updateState { copy(isExporting = true, exportProgress = 0, error = null) }

            compileVideoUseCase(
                projectId = currentState.projectId,
                settings = currentState.exportSettings,
                onProgress = { progress ->
                    // Convert from 0.0-1.0 to 0-100
                    val progressPercent = (progress * PROGRESS_MULTIPLIER).toInt()
                    updateState { copy(exportProgress = progressPercent) }
                },
            )
                .onSuccess { videoPath ->
                    updateState {
                        copy(
                            isExporting = false,
                            exportProgress = PROGRESS_COMPLETE,
                            exportedVideoPath = videoPath,
                        )
                    }
                    sendEffect(ExportEffect.ExportComplete)
                    sendEffect(ExportEffect.ShowMessage("Video exported successfully"))
                }
                .onError { _, message ->
                    updateState { copy(isExporting = false, error = message) }
                    sendEffect(ExportEffect.ShowError(message ?: "Export failed"))
                }
        }
    }

    private fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        updateState { copy(isExporting = false, exportProgress = 0) }
        sendEffect(ExportEffect.ShowMessage("Export cancelled"))
    }

    private fun shareVideo() {
        val videoPath = currentState.exportedVideoPath
        if (videoPath != null) {
            sendEffect(ExportEffect.ShareVideo(videoPath))
        } else {
            sendEffect(ExportEffect.ShowError("No video to share"))
        }
    }

    private fun dismissResult() {
        updateState { copy(exportedVideoPath = null, exportProgress = 0) }
    }

    override fun onCleared() {
        exportJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_FPS = 30
        private const val MIN_FPS = 1
        private const val MAX_FPS = 60
        private const val PROGRESS_COMPLETE = 100
        private const val PROGRESS_MULTIPLIER = 100
    }
}
