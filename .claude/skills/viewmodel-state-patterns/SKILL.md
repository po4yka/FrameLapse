---
name: viewmodel-state-patterns
description: Implements complex ViewModel state management with UDF pattern, side effects, loading states, and error handling. Use when designing ViewModel state, adding async operations, or handling multi-step workflows.
---

# ViewModel State Patterns

## Overview

This skill helps implement ViewModel state management using Unidirectional Data Flow (UDF) with the State/Event/Effect pattern. It covers complex state hierarchies, async operations, and multi-step workflows.

## Core Pattern: State/Event/Effect

### Base Classes

```kotlin
abstract class BaseViewModel<State : UiState, Event : UiEvent, Effect : UiEffect>(
    initialState: State
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    protected val currentState: State get() = _state.value

    private val _effect = MutableSharedFlow<Effect>()
    val effect: SharedFlow<Effect> = _effect.asSharedFlow()

    protected fun updateState(reducer: State.() -> State) {
        _state.value = _state.value.reducer()
    }

    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch { _effect.emit(effect) }
    }

    abstract fun onEvent(event: Event)
}

interface UiState
interface UiEvent
interface UiEffect
```

## 1. State Design Patterns

### Basic State

```kotlin
data class CaptureState(
    val projectId: String = "",
    val isProcessing: Boolean = false,
    val isCameraReady: Boolean = false,
    val frameCount: Int = 0,
    val error: String? = null,
) : UiState
```

### State with Loading Substates

```kotlin
data class ExportState(
    val projectId: String = "",
    val exportPhase: ExportPhase = ExportPhase.Idle,
    val progress: Float = 0f,
    val error: ExportError? = null,
) : UiState {

    sealed class ExportPhase {
        data object Idle : ExportPhase()
        data object Preparing : ExportPhase()
        data class Encoding(val frameIndex: Int, val totalFrames: Int) : ExportPhase()
        data class Saving(val fileName: String) : ExportPhase()
        data class Complete(val outputPath: String) : ExportPhase()
    }

    val isExporting: Boolean get() = exportPhase !is ExportPhase.Idle && exportPhase !is ExportPhase.Complete
}
```

### State with Content Types

```kotlin
data class GalleryState(
    val projectId: String = "",
    val content: GalleryContent = GalleryContent.Loading,
    val selectionMode: Boolean = false,
    val selectedFrames: Set<String> = emptySet(),
) : UiState {

    sealed class GalleryContent {
        data object Loading : GalleryContent()
        data class Success(val frames: List<Frame>, val project: Project) : GalleryContent()
        data class Error(val message: String) : GalleryContent()
        data object Empty : GalleryContent()
    }

    val frameCount: Int get() = (content as? GalleryContent.Success)?.frames?.size ?: 0
}
```

## 2. Event Design Patterns

### Grouped Events

```kotlin
sealed interface CaptureEvent : UiEvent {
    // Lifecycle events
    data class Initialize(val projectId: String) : CaptureEvent
    data object CameraReady : CaptureEvent
    data object CameraError : CaptureEvent

    // User actions
    data object CaptureImage : CaptureEvent
    data object ToggleFlash : CaptureEvent
    data object FlipCamera : CaptureEvent

    // Settings changes
    data class UpdateGhostOpacity(val opacity: Float) : CaptureEvent

    // Navigation
    data object NavigateToGallery : CaptureEvent
}
```

### Parameterized Events

```kotlin
sealed interface GalleryEvent : UiEvent {
    data class Initialize(val projectId: String) : GalleryEvent
    data class SelectFrame(val frameId: String) : GalleryEvent
    data class DeleteFrames(val frameIds: List<String>) : GalleryEvent
    data class ReorderFrames(val fromIndex: Int, val toIndex: Int) : GalleryEvent
}
```

## 3. Effect Design Patterns

### Navigation Effects

```kotlin
sealed interface CaptureEffect : UiEffect {
    data class NavigateToGallery(val projectId: String) : CaptureEffect
    data object NavigateBack : CaptureEffect
}
```

### Feedback Effects

```kotlin
sealed interface CaptureEffect : UiEffect {
    data class ShowError(val message: String) : CaptureEffect
    data class ShowSuccess(val message: String) : CaptureEffect
    data object PlayCaptureSound : CaptureEffect
    data object TriggerHaptic : CaptureEffect
}
```

### Action Effects

```kotlin
sealed interface ExportEffect : UiEffect {
    data class ShareVideo(val path: String) : ExportEffect
    data class OpenInGallery(val path: String) : ExportEffect
    data class RequestPermission(val permission: String) : ExportEffect
}
```

## 4. Event Handling Pattern

### Basic Event Handler

```kotlin
override fun onEvent(event: CaptureEvent) {
    when (event) {
        is CaptureEvent.Initialize -> initialize(event.projectId)
        CaptureEvent.CaptureImage -> captureImage()
        CaptureEvent.ToggleFlash -> toggleFlash()
        CaptureEvent.FlipCamera -> flipCamera()
        CaptureEvent.ToggleGrid -> toggleGrid()
        is CaptureEvent.UpdateGhostOpacity -> updateGhostOpacity(event.opacity)
        CaptureEvent.NavigateToGallery -> navigateToGallery()
        CaptureEvent.CameraReady -> updateState { copy(isCameraReady = true) }
        CaptureEvent.CameraError -> handleCameraError()
    }
}
```

### Async Event Handler

```kotlin
private fun captureImage() {
    if (currentState.isProcessing) return

    viewModelScope.launch {
        updateState { copy(isProcessing = true, error = null) }

        captureImageUseCase(currentState.projectId)
            .onSuccess { frame ->
                updateState {
                    copy(
                        isProcessing = false,
                        lastCapturedFrame = frame,
                        frameCount = frameCount + 1,
                    )
                }
                sendEffect(CaptureEffect.PlayCaptureSound)
            }
            .onError { exception, message ->
                updateState { copy(isProcessing = false, error = message) }
                sendEffect(CaptureEffect.ShowError(message ?: "Capture failed"))
            }
    }
}
```

## 5. Multi-Step Workflow Pattern

### Progress Tracking

```kotlin
private fun exportVideo() {
    viewModelScope.launch {
        updateState { copy(exportPhase = ExportPhase.Preparing) }

        compileVideoUseCase(
            projectId = currentState.projectId,
            settings = currentState.exportSettings,
            onProgress = { progress ->
                updateState {
                    copy(
                        exportPhase = ExportPhase.Encoding(
                            frameIndex = progress.currentFrame,
                            totalFrames = progress.totalFrames,
                        ),
                        progress = progress.percentage,
                    )
                }
            }
        ).onSuccess { outputPath ->
            updateState {
                copy(exportPhase = ExportPhase.Complete(outputPath))
            }
            sendEffect(ExportEffect.ShareVideo(outputPath))
        }.onError { _, message ->
            updateState {
                copy(
                    exportPhase = ExportPhase.Idle,
                    error = ExportError.EncodingFailed(message),
                )
            }
        }
    }
}
```

### Cancellable Operations

```kotlin
class ExportViewModel(...) : BaseViewModel<ExportState, ExportEvent, ExportEffect>(...) {
    private var exportJob: Job? = null

    private fun startExport() {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            // Export logic
        }
    }

    private fun cancelExport() {
        exportJob?.cancel()
        updateState { copy(exportPhase = ExportPhase.Idle, progress = 0f) }
    }
}
```

## 6. State Aggregation Pattern

### Combining Multiple Sources

```kotlin
private fun observeData() {
    viewModelScope.launch {
        combine(
            getProjectUseCase(currentState.projectId),
            observeFramesUseCase(currentState.projectId),
            settingsRepository.observeSettings(),
        ) { projectResult, frames, settings ->
            Triple(projectResult, frames, settings)
        }.collect { (projectResult, frames, settings) ->
            updateState {
                when {
                    projectResult is Result.Error -> copy(
                        content = GalleryContent.Error(projectResult.message ?: "Unknown error")
                    )
                    frames.isEmpty() -> copy(content = GalleryContent.Empty)
                    else -> copy(
                        content = GalleryContent.Success(frames, projectResult.getOrNull()!!),
                        settings = settings,
                    )
                }
            }
        }
    }
}
```

## 7. UI Collection Patterns

### State Collection in Compose

```kotlin
@Composable
fun CaptureScreen(viewModel: CaptureViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CaptureEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is CaptureEffect.NavigateToGallery -> navController.navigate(...)
                CaptureEffect.PlayCaptureSound -> soundPlayer.playCapture()
            }
        }
    }

    CaptureContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}
```

### Event-Driven UI

```kotlin
@Composable
fun CaptureContent(
    state: CaptureState,
    onEvent: (CaptureEvent) -> Unit,
) {
    Button(
        onClick = { onEvent(CaptureEvent.CaptureImage) },
        enabled = !state.isProcessing && state.isCameraReady,
    ) {
        if (state.isProcessing) {
            CircularProgressIndicator()
        } else {
            Text("Capture")
        }
    }
}
```

## Reference Examples

- BaseViewModel: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/base/BaseViewModel.kt`
- CaptureContract: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/capture/CaptureContract.kt`
- CaptureViewModel: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/capture/CaptureViewModel.kt`
- GalleryViewModel: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/gallery/GalleryViewModel.kt`

## Checklist

### State Design
- [ ] All properties have sensible defaults
- [ ] All properties are `val` (immutable)
- [ ] Complex states use sealed class substates
- [ ] Computed properties use `get()` instead of stored values

### Event Design
- [ ] Events are exhaustively handled in `onEvent()`
- [ ] Lifecycle events are separate from user actions
- [ ] Parameterized events use data classes

### Effect Design
- [ ] Navigation is an Effect (not state)
- [ ] Toasts/Snackbars are Effects
- [ ] One-time actions (sounds, haptics) are Effects

### Async Operations
- [ ] Loading states are tracked
- [ ] Errors are captured and surfaced
- [ ] Operations are cancellable when needed
- [ ] Progress is reported for long operations
