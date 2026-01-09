---
name: compose-facade-pattern
description: Implements facade pattern to simplify complex subsystem interactions. Use when coordinating multiple services or creating simplified APIs for complex operations.
---

# Compose Facade Pattern

## Overview

The Facade pattern provides a simplified interface to a complex subsystem. In FrameLapse, this is useful for coordinating multiple services (audio, notifications, analytics) behind a single API, or simplifying complex operations like video export that involve multiple steps.

## When to Use

- Coordinating multiple services for a single operation
- Simplifying complex APIs for UI layer consumption
- Hiding implementation details from ViewModels
- Creating reusable operation bundles

## 1. Basic Facade Pattern

### Music Player Facade Example

```kotlin
// domain/facade/MusicPlayerFacade.kt

class MusicPlayerFacade(
    private val audioPlayer: AudioPlayer,
    private val notificationManager: NotificationManager,
    private val analyticsManager: AnalyticsManager,
) {
    fun playTrack(track: String) {
        audioPlayer.play(track)
        notificationManager.showNotification("Now playing: $track")
        analyticsManager.logEvent("track_played", mapOf("track" to track))
    }

    fun pauseTrack() {
        audioPlayer.pause()
        notificationManager.updateNotification("Paused")
        analyticsManager.logEvent("track_paused")
    }

    fun stopTrack() {
        audioPlayer.stop()
        notificationManager.dismissNotification()
        analyticsManager.logEvent("track_stopped")
    }
}

// Usage in Composable
@Composable
fun MusicPlayerControls() {
    val facade = remember {
        MusicPlayerFacade(
            audioPlayer = AudioPlayerImpl(),
            notificationManager = NotificationManagerImpl(),
            analyticsManager = AnalyticsManagerImpl(),
        )
    }

    Row {
        IconButton(onClick = { facade.playTrack("song.mp3") }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
        IconButton(onClick = { facade.pauseTrack() }) {
            Icon(Icons.Default.Pause, contentDescription = "Pause")
        }
        IconButton(onClick = { facade.stopTrack() }) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
    }
}
```

## 2. Video Export Facade

### Coordinating Export Operations

```kotlin
// domain/facade/VideoExportFacade.kt

class VideoExportFacade(
    private val frameRepository: FrameRepository,
    private val imageProcessor: ImageProcessor,
    private val videoEncoder: VideoEncoder,
    private val fileManager: FileManager,
    private val notificationService: NotificationService,
) {
    suspend fun exportVideo(
        projectId: String,
        settings: ExportSettings,
        onProgress: (ExportProgress) -> Unit,
    ): Result<String> {
        return try {
            // Step 1: Load frames
            onProgress(ExportProgress.Loading)
            val framesResult = frameRepository.getFramesByProject(projectId)
            if (framesResult.isError) {
                return Result.Error(
                    framesResult.exceptionOrNull() ?: Exception("Failed to load frames"),
                    "Failed to load frames",
                )
            }
            val frames = framesResult.getOrNull() ?: emptyList()

            if (frames.isEmpty()) {
                return Result.Error(
                    IllegalStateException("No frames to export"),
                    "Project has no frames",
                )
            }

            // Step 2: Process frames
            onProgress(ExportProgress.Processing(0, frames.size))
            val processedFrames = frames.mapIndexed { index, frame ->
                onProgress(ExportProgress.Processing(index + 1, frames.size))
                val processedPath = imageProcessor.processForExport(
                    inputPath = frame.alignedPath ?: frame.originalPath,
                    resolution = settings.resolution,
                )
                processedPath
            }

            // Step 3: Create output path
            val outputPath = fileManager.createExportPath(
                projectId = projectId,
                extension = settings.format.extension,
            )

            // Step 4: Encode video
            onProgress(ExportProgress.Encoding)
            notificationService.showProgress("Exporting video...", indeterminate = true)

            val encodeResult = videoEncoder.encode(
                frames = processedFrames,
                outputPath = outputPath,
                fps = settings.fps,
                format = settings.format,
            )

            if (encodeResult.isError) {
                notificationService.dismiss()
                return Result.Error(
                    encodeResult.exceptionOrNull() ?: Exception("Encoding failed"),
                    "Video encoding failed",
                )
            }

            // Step 5: Cleanup and notify
            onProgress(ExportProgress.Complete)
            notificationService.showSuccess("Video exported successfully")

            Result.Success(outputPath)
        } catch (e: Exception) {
            notificationService.dismiss()
            Result.Error(e, "Export failed: ${e.message}")
        }
    }

    suspend fun exportGif(
        projectId: String,
        settings: GifSettings,
        onProgress: (ExportProgress) -> Unit,
    ): Result<String> {
        // Similar coordination for GIF export
    }
}

sealed interface ExportProgress {
    data object Loading : ExportProgress
    data class Processing(val current: Int, val total: Int) : ExportProgress
    data object Encoding : ExportProgress
    data object Complete : ExportProgress
}
```

### Using Export Facade in ViewModel

```kotlin
// presentation/export/ExportViewModel.kt

class ExportViewModel(
    private val exportFacade: VideoExportFacade,
) : BaseViewModel<ExportState, ExportEvent, ExportEffect>(ExportState()) {

    override fun onEvent(event: ExportEvent) {
        when (event) {
            is ExportEvent.StartExport -> startExport(event.projectId, event.settings)
            ExportEvent.CancelExport -> cancelExport()
        }
    }

    private var exportJob: Job? = null

    private fun startExport(projectId: String, settings: ExportSettings) {
        exportJob = viewModelScope.launch {
            updateState { copy(isExporting = true, progress = ExportProgress.Loading) }

            val result = exportFacade.exportVideo(
                projectId = projectId,
                settings = settings,
                onProgress = { progress ->
                    updateState { copy(progress = progress) }
                },
            )

            result
                .onSuccess { outputPath ->
                    updateState { copy(isExporting = false, exportedPath = outputPath) }
                    sendEffect(ExportEffect.ShowSuccess("Video saved to $outputPath"))
                }
                .onError { _, message ->
                    updateState { copy(isExporting = false, error = message) }
                    sendEffect(ExportEffect.ShowError(message ?: "Export failed"))
                }
        }
    }

    private fun cancelExport() {
        exportJob?.cancel()
        updateState { copy(isExporting = false, progress = null) }
    }
}
```

## 3. Capture Session Facade

### Coordinating Camera Operations

```kotlin
// domain/facade/CaptureSessionFacade.kt

class CaptureSessionFacade(
    private val cameraController: CameraController,
    private val frameRepository: FrameRepository,
    private val alignmentService: AlignmentService,
    private val feedbackService: FeedbackService,
    private val settingsRepository: SettingsRepository,
) {
    private var currentProjectId: String? = null

    suspend fun startSession(projectId: String): Result<Unit> {
        currentProjectId = projectId

        // Load settings
        val settings = settingsRepository.getCaptureSettings()

        // Configure camera
        return try {
            cameraController.configure(
                resolution = settings.resolution,
                flash = settings.flashMode,
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to start camera")
        }
    }

    suspend fun captureFrame(): Result<Frame> {
        val projectId = currentProjectId
            ?: return Result.Error(
                IllegalStateException("No active session"),
                "Start a session first",
            )

        return try {
            // Step 1: Capture image
            val imagePath = cameraController.captureImage()

            // Step 2: Play feedback
            feedbackService.playCaptureFeedback()

            // Step 3: Get frame count for sort order
            val existingFrames = frameRepository.getFramesByProject(projectId)
                .getOrNull()
                ?.size ?: 0

            // Step 4: Create frame entity
            val frame = Frame(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                originalPath = imagePath,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                sortOrder = existingFrames,
            )

            // Step 5: Save to repository
            val saveResult = frameRepository.addFrame(frame)
            if (saveResult.isError) {
                return saveResult
            }

            // Step 6: Trigger background alignment
            alignmentService.queueAlignment(frame)

            Result.Success(frame)
        } catch (e: Exception) {
            feedbackService.playErrorFeedback()
            Result.Error(e, "Capture failed")
        }
    }

    fun toggleFlash(): FlashMode {
        return cameraController.toggleFlash()
    }

    fun flipCamera(): CameraFacing {
        return cameraController.flipCamera()
    }

    suspend fun endSession() {
        currentProjectId = null
        cameraController.release()
    }
}
```

### Using in Capture Screen

```kotlin
// presentation/capture/CaptureScreen.kt

@Composable
fun CaptureScreen(
    projectId: String,
    onNavigateToGallery: () -> Unit,
) {
    val facade = remember { koinInject<CaptureSessionFacade>() }
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        facade.startSession(projectId)
    }

    DisposableEffect(Unit) {
        onDispose {
            // End session when leaving
            runBlocking { facade.endSession() }
        }
    }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Camera preview
            CameraPreview(modifier = Modifier.fillMaxSize())

            // Controls overlay
            CaptureControls(
                flashMode = flashMode,
                isCapturing = isCapturing,
                onCapture = {
                    isCapturing = true
                    // Launch in coroutine scope
                    facade.captureFrame()
                        .onSuccess { isCapturing = false }
                        .onError { isCapturing = false }
                },
                onToggleFlash = { flashMode = facade.toggleFlash() },
                onFlipCamera = { facade.flipCamera() },
                onViewGallery = onNavigateToGallery,
            )
        }
    }
}
```

## 4. Project Management Facade

### Simplifying Project Operations

```kotlin
// domain/facade/ProjectManagementFacade.kt

class ProjectManagementFacade(
    private val projectRepository: ProjectRepository,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
    private val thumbnailGenerator: ThumbnailGenerator,
) {
    suspend fun createProject(name: String): Result<Project> {
        if (name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Name cannot be blank"),
                "Please enter a project name",
            )
        }

        // Create project directory
        val projectId = UUID.randomUUID().toString()
        fileManager.createProjectDirectory(projectId)

        // Create project entity
        val project = Project(
            id = projectId,
            name = name.trim(),
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )

        return projectRepository.save(project)
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        return try {
            // Get all frames for cleanup
            val frames = frameRepository.getFramesByProject(projectId)
                .getOrNull() ?: emptyList()

            // Delete frame files
            frames.forEach { frame ->
                fileManager.deleteFile(frame.originalPath)
                frame.alignedPath?.let { fileManager.deleteFile(it) }
            }

            // Delete frames from database
            frameRepository.deleteByProject(projectId)

            // Delete project directory
            fileManager.deleteProjectDirectory(projectId)

            // Delete project from database
            projectRepository.delete(projectId)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete project")
        }
    }

    suspend fun duplicateProject(
        projectId: String,
        newName: String,
    ): Result<Project> {
        // Get original project
        val original = projectRepository.getProject(projectId)
            .getOrNull() ?: return Result.Error(
                NotFoundException("Project not found"),
                "Original project not found",
            )

        // Create new project
        val newProjectResult = createProject(newName)
        if (newProjectResult.isError) {
            return newProjectResult
        }
        val newProject = newProjectResult.getOrNull()!!

        // Copy frames
        val frames = frameRepository.getFramesByProject(projectId)
            .getOrNull() ?: emptyList()

        frames.forEach { frame ->
            // Copy file
            val newPath = fileManager.copyFile(
                sourcePath = frame.originalPath,
                targetProjectId = newProject.id,
            )

            // Create new frame entry
            frameRepository.addFrame(
                frame.copy(
                    id = UUID.randomUUID().toString(),
                    projectId = newProject.id,
                    originalPath = newPath,
                    alignedPath = null, // Re-align for new project
                )
            )
        }

        return Result.Success(newProject)
    }

    suspend fun getProjectWithStats(projectId: String): Result<ProjectWithStats> {
        val project = projectRepository.getProject(projectId)
            .getOrNull() ?: return Result.Error(
                NotFoundException("Project not found"),
                "Project not found",
            )

        val frames = frameRepository.getFramesByProject(projectId)
            .getOrNull() ?: emptyList()

        val alignedCount = frames.count { it.alignedPath != null }
        val totalSize = frames.sumOf { frame ->
            fileManager.getFileSize(frame.originalPath) +
                (frame.alignedPath?.let { fileManager.getFileSize(it) } ?: 0L)
        }

        return Result.Success(
            ProjectWithStats(
                project = project,
                frameCount = frames.size,
                alignedFrameCount = alignedCount,
                totalSizeBytes = totalSize,
                lastCaptureTime = frames.maxOfOrNull { it.timestamp },
            )
        )
    }
}

data class ProjectWithStats(
    val project: Project,
    val frameCount: Int,
    val alignedFrameCount: Int,
    val totalSizeBytes: Long,
    val lastCaptureTime: Long?,
)
```

## 5. Dependency Injection for Facades

### Koin Module Setup

```kotlin
// di/FacadeModule.kt

val facadeModule = module {
    // Video export facade
    factory {
        VideoExportFacade(
            frameRepository = get(),
            imageProcessor = get(),
            videoEncoder = get(),
            fileManager = get(),
            notificationService = get(),
        )
    }

    // Capture session facade
    factory {
        CaptureSessionFacade(
            cameraController = get(),
            frameRepository = get(),
            alignmentService = get(),
            feedbackService = get(),
            settingsRepository = get(),
        )
    }

    // Project management facade
    factory {
        ProjectManagementFacade(
            projectRepository = get(),
            frameRepository = get(),
            fileManager = get(),
            thumbnailGenerator = get(),
        )
    }
}
```

## Anti-Patterns

### Avoid: Facade That Does Too Much

```kotlin
// BAD - "God" facade
class AppFacade(
    private val projectRepo: ProjectRepository,
    private val frameRepo: FrameRepository,
    private val cameraController: CameraController,
    private val videoEncoder: VideoEncoder,
    private val audioPlayer: AudioPlayer,
    private val settingsRepo: SettingsRepository,
    // ... 10 more dependencies
) {
    fun createProject() { ... }
    fun captureFrame() { ... }
    fun exportVideo() { ... }
    fun playVideo() { ... }
    fun updateSettings() { ... }
    // Too many unrelated operations!
}

// BETTER - focused facades
class ProjectFacade(...)  // Project CRUD operations
class CaptureFacade(...)  // Capture session operations
class ExportFacade(...)   // Export operations
```

### Avoid: Facade Leaking Implementation Details

```kotlin
// BAD - exposes internal services
class BadExportFacade(
    val videoEncoder: VideoEncoder,  // Public - leaks implementation
) {
    fun export() {
        videoEncoder.encode(...)  // Client can bypass facade
    }
}

// BETTER - hide implementation
class GoodExportFacade(
    private val videoEncoder: VideoEncoder,  // Private
) {
    suspend fun export(settings: ExportSettings): Result<String> {
        // All interaction through facade methods
    }
}
```

## Reference Examples

- Use case orchestration: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/alignment/AlignContentUseCase.kt`
- DI modules: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/di/KoinModules.kt`

## Checklist

### Facade Design
- [ ] Single responsibility (one domain area)
- [ ] Hides complexity of subsystems
- [ ] Provides simplified API for common operations
- [ ] Dependencies are private

### Implementation
- [ ] Coordinates multiple services
- [ ] Handles errors from all subsystems
- [ ] Reports progress for long operations
- [ ] Properly cleans up resources

### Integration
- [ ] Registered in Koin module
- [ ] Used via DI (not direct instantiation)
- [ ] ViewModel calls facade, not individual services
- [ ] UI layer doesn't know about subsystems
