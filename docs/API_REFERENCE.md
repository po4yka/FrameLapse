# API Reference

This document provides a comprehensive reference for FrameLapse use cases, services, and ViewModels.

## Table of Contents

- [Use Cases](#use-cases)
  - [Project Use Cases](#project-use-cases)
  - [Frame Use Cases](#frame-use-cases)
  - [Face Detection Use Cases](#face-detection-use-cases)
  - [Alignment Use Cases](#alignment-use-cases)
  - [Export Use Cases](#export-use-cases)
- [Services](#services)
- [ViewModels](#viewmodels)
- [Repositories](#repositories)
- [Data Classes](#data-classes)

---

## Use Cases

Use cases encapsulate single business operations. They are located in `domain/src/commonMain/.../usecase/`.

### Project Use Cases

#### CreateProjectUseCase

Creates a new timelapse project.

```kotlin
class CreateProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(
        name: String,
        settings: ProjectSettings = ProjectSettings.default()
    ): Result<Project>
}
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | `String` | Project display name |
| `settings` | `ProjectSettings` | FPS, resolution, orientation settings |

**Returns**: `Result<Project>` - Created project or error

---

#### GetProjectUseCase

Retrieves a project by ID.

```kotlin
class GetProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(projectId: String): Result<Project>
}
```

---

#### GetAllProjectsUseCase

Retrieves all projects.

```kotlin
class GetAllProjectsUseCase(
    private val projectRepository: ProjectRepository
) {
    operator fun invoke(): Flow<List<Project>>
}
```

**Returns**: `Flow<List<Project>>` - Observable list of projects

---

#### DeleteProjectUseCase

Deletes a project and all associated frames.

```kotlin
class DeleteProjectUseCase(
    private val projectRepository: ProjectRepository,
    private val frameRepository: FrameRepository,
    private val storageManager: StorageManager
) {
    suspend operator fun invoke(projectId: String): Result<Unit>
}
```

---

### Frame Use Cases

#### CaptureImageUseCase

Captures and processes a new photo.

```kotlin
class CaptureImageUseCase(
    private val frameRepository: FrameRepository,
    private val faceDetector: FaceDetector,
    private val alignFaceUseCase: AlignFaceUseCase,
    private val imageStorageManager: ImageStorageManager
) {
    suspend operator fun invoke(
        projectId: String,
        imageData: ByteArray,
        timestamp: Long = currentTimeMillis()
    ): Result<Frame>
}
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `projectId` | `String` | Target project ID |
| `imageData` | `ByteArray` | Raw image bytes (JPEG/PNG) |
| `timestamp` | `Long` | Capture timestamp |

**Returns**: `Result<Frame>` - Created frame with alignment data

---

#### GetFramesUseCase

Retrieves frames for a project.

```kotlin
class GetFramesUseCase(
    private val frameRepository: FrameRepository
) {
    operator fun invoke(projectId: String): Flow<List<Frame>>
}
```

---

#### GetLatestFrameUseCase

Gets the most recent frame for ghost overlay.

```kotlin
class GetLatestFrameUseCase(
    private val frameRepository: FrameRepository
) {
    suspend operator fun invoke(projectId: String): Result<Frame?>
}
```

---

#### DeleteFramesUseCase

Deletes selected frames.

```kotlin
class DeleteFramesUseCase(
    private val frameRepository: FrameRepository,
    private val storageManager: StorageManager
) {
    suspend operator fun invoke(frameIds: List<String>): Result<Unit>
}
```

---

#### ImportPhotosUseCase

Batch imports photos from gallery.

```kotlin
class ImportPhotosUseCase(
    private val captureImageUseCase: CaptureImageUseCase
) {
    suspend operator fun invoke(
        projectId: String,
        photos: List<PhotoImport>
    ): Result<ImportResult>
}

data class PhotoImport(
    val uri: String,
    val timestamp: Long
)

data class ImportResult(
    val successCount: Int,
    val failedCount: Int,
    val frames: List<Frame>
)
```

---

### Face Detection Use Cases

#### DetectFaceUseCase

Detects face landmarks in an image.

```kotlin
class DetectFaceUseCase(
    private val faceDetector: FaceDetector
) {
    suspend operator fun invoke(imageData: ByteArray): Result<FaceLandmarks?>
    suspend operator fun invoke(imagePath: String): Result<FaceLandmarks?>
}
```

**Returns**: `Result<FaceLandmarks?>` - Detected landmarks or null if no face found

---

#### DetectFaceRealtimeUseCase

Optimized detection for live preview (lower confidence threshold).

```kotlin
class DetectFaceRealtimeUseCase(
    private val faceDetector: FaceDetector
) {
    suspend operator fun invoke(imageData: ByteArray): Result<FaceLandmarks?>
}
```

---

#### ValidateAlignmentUseCase

Validates face detection quality.

```kotlin
class ValidateAlignmentUseCase {
    operator fun invoke(landmarks: FaceLandmarks): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val confidence: Float,
    val issues: List<ValidationIssue>
)

enum class ValidationIssue {
    LOW_CONFIDENCE,
    FACE_TOO_SMALL,
    FACE_OFF_CENTER,
    EYES_NOT_VISIBLE
}
```

---

### Alignment Use Cases

#### AlignFaceUseCase

Main alignment orchestrator.

```kotlin
class AlignFaceUseCase(
    private val multiPassStabilizationUseCase: MultiPassStabilizationUseCase,
    private val imageProcessor: ImageProcessor
) {
    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame?,
        mode: StabilizationMode = StabilizationMode.FAST
    ): Result<AlignedFrame>
}

enum class StabilizationMode {
    FAST,  // 4 passes, translation only
    SLOW   // 11 passes, full refinement
}

data class AlignedFrame(
    val frame: Frame,
    val alignedImagePath: String,
    val matrix: AlignmentMatrix,
    val score: Float
)
```

---

#### CalculateAlignmentMatrixUseCase

Computes affine transformation matrix.

```kotlin
class CalculateAlignmentMatrixUseCase {
    operator fun invoke(
        sourceLandmarks: FaceLandmarks,
        targetLandmarks: FaceLandmarks,
        canvasSize: Size
    ): AlignmentMatrix
}
```

---

#### CalculateStabilizationScoreUseCase

Measures alignment quality.

```kotlin
class CalculateStabilizationScoreUseCase {
    operator fun invoke(
        detectedLandmarks: FaceLandmarks,
        goalLandmarks: FaceLandmarks,
        canvasHeight: Int
    ): Float
}
```

**Returns**: Score value (lower is better)
- `< 0.5`: Perfect alignment
- `< 20.0`: Successful
- `>= 20.0`: Failed

---

#### MultiPassStabilizationUseCase

Iterative alignment refinement.

```kotlin
class MultiPassStabilizationUseCase(
    private val detectFaceUseCase: DetectFaceUseCase,
    private val calculateMatrixUseCase: CalculateAlignmentMatrixUseCase,
    private val calculateScoreUseCase: CalculateStabilizationScoreUseCase,
    private val refineRotationUseCase: RefineRotationUseCase,
    private val refineScaleUseCase: RefineScaleUseCase,
    private val refineTranslationUseCase: RefineTranslationUseCase,
    private val imageProcessor: ImageProcessor
) {
    suspend operator fun invoke(
        imagePath: String,
        referenceLandmarks: FaceLandmarks,
        mode: StabilizationMode,
        canvasSize: Size
    ): Result<StabilizationResult>
}

data class StabilizationResult(
    val alignedImagePath: String,
    val finalMatrix: AlignmentMatrix,
    val finalScore: Float,
    val passCount: Int
)
```

---

### Export Use Cases

#### CompileVideoUseCase

Assembles frames into video.

```kotlin
class CompileVideoUseCase(
    private val frameRepository: FrameRepository,
    private val imageProcessor: ImageProcessor,
    private val videoEncoder: VideoEncoder,
    private val storageManager: StorageManager
) {
    suspend operator fun invoke(
        projectId: String,
        settings: ExportSettings,
        onProgress: (Float) -> Unit = {}
    ): Result<String>
}
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `projectId` | `String` | Source project |
| `settings` | `ExportSettings` | Resolution, FPS, codec, quality |
| `onProgress` | `(Float) -> Unit` | Progress callback (0.0 - 1.0) |

**Returns**: `Result<String>` - Path to exported video

---

#### ExportGifUseCase

Creates animated GIF from frames.

```kotlin
class ExportGifUseCase(
    private val frameRepository: FrameRepository,
    private val imageProcessor: ImageProcessor,
    private val gifEncoder: GifEncoder
) {
    suspend operator fun invoke(
        projectId: String,
        settings: GifExportSettings,
        onProgress: (Float) -> Unit = {}
    ): Result<String>
}
```

---

## Services

Services provide platform-specific functionality via `expect/actual` declarations.

### FaceDetector

```kotlin
interface FaceDetector {
    val isAvailable: Boolean

    suspend fun detectFace(imageData: ByteArray): Result<FaceLandmarks?>
    suspend fun detectFaceFromPath(imagePath: String): Result<FaceLandmarks?>
    suspend fun detectFaceRealtime(imageData: ByteArray): Result<FaceLandmarks?>
}
```

| Platform | Implementation |
|----------|----------------|
| Android | MediaPipe Face Landmarker |
| iOS | Vision Framework |

---

### ImageProcessor

```kotlin
interface ImageProcessor {
    suspend fun loadImage(path: String): Result<ImageData>
    suspend fun saveImage(imageData: ImageData, path: String, quality: Int = 90): Result<Unit>
    suspend fun applyAffineTransform(imagePath: String, matrix: AlignmentMatrix, outputPath: String): Result<Unit>
    suspend fun applyHomographyTransform(imagePath: String, matrix: HomographyMatrix, outputPath: String): Result<Unit>
    suspend fun cropImage(imagePath: String, boundingBox: BoundingBox, outputPath: String): Result<Unit>
    suspend fun resizeImage(imagePath: String, targetSize: Size, outputPath: String): Result<Unit>
    suspend fun rotateImage(imagePath: String, degrees: Float, outputPath: String): Result<Unit>
    suspend fun getImageDimensions(path: String): Result<Size>
}
```

| Platform | Implementation |
|----------|----------------|
| Android | OpenCV + Android Bitmap APIs |
| iOS | Core Image + OpenCV wrapper |

---

### VideoEncoder

```kotlin
interface VideoEncoder {
    fun getSupportedCodecs(): List<VideoCodec>

    suspend fun encode(
        framePaths: List<String>,
        outputPath: String,
        settings: VideoEncoderSettings,
        onProgress: (Float) -> Unit
    ): Result<Unit>
}

data class VideoEncoderSettings(
    val width: Int,
    val height: Int,
    val fps: Int,
    val codec: VideoCodec,
    val bitrate: Int
)

enum class VideoCodec {
    H264,
    HEVC
}
```

| Platform | Implementation |
|----------|----------------|
| Android | MediaCodec + MediaMuxer |
| iOS | AVAssetWriter |

---

### CameraController

```kotlin
interface CameraController {
    val isAvailable: Boolean

    fun startPreview(surface: Any, facing: CameraFacing)
    fun stopPreview()
    suspend fun capture(): Result<ByteArray>
    fun setFlashMode(mode: FlashMode)
    fun switchCamera()
}

enum class CameraFacing { FRONT, BACK }
enum class FlashMode { OFF, ON, AUTO }
```

| Platform | Implementation |
|----------|----------------|
| Android | CameraX |
| iOS | AVFoundation |

---

## ViewModels

ViewModels follow the State/Event/Effect pattern with `BaseViewModel`.

### BaseViewModel

```kotlin
abstract class BaseViewModel<State, Event, Effect>(
    initialState: State
) : ViewModel() {

    val state: StateFlow<State>
    val effect: SharedFlow<Effect>

    abstract fun onEvent(event: Event)

    protected fun updateState(reducer: State.() -> State)
    protected fun sendEffect(effect: Effect)
}
```

---

### ProjectListViewModel

```kotlin
class ProjectListViewModel(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val getStatisticsUseCase: GetStatisticsUseCase
) : BaseViewModel<ProjectListState, ProjectListEvent, ProjectListEffect>

data class ProjectListState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val selectedProject: Project? = null
)

sealed interface ProjectListEvent {
    data object LoadProjects : ProjectListEvent
    data class CreateProject(val name: String) : ProjectListEvent
    data class DeleteProject(val projectId: String) : ProjectListEvent
    data class SelectProject(val project: Project) : ProjectListEvent
}

sealed interface ProjectListEffect {
    data class NavigateToCapture(val projectId: String) : ProjectListEffect
    data class ShowError(val message: String) : ProjectListEffect
}
```

---

### CaptureViewModel

```kotlin
class CaptureViewModel(
    private val captureImageUseCase: CaptureImageUseCase,
    private val getLatestFrameUseCase: GetLatestFrameUseCase,
    private val getFramesUseCase: GetFramesUseCase,
    private val settingsRepository: SettingsRepository
) : BaseViewModel<CaptureState, CaptureEvent, CaptureEffect>

data class CaptureState(
    val projectId: String = "",
    val isProcessing: Boolean = false,
    val frameCount: Int = 0,
    val ghostImagePath: String? = null,
    val faceDetectionConfidence: Float? = null,
    val isFlashEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val showGrid: Boolean = true,
    val ghostOpacity: Float = 0.3f
)

sealed interface CaptureEvent {
    data class Initialize(val projectId: String) : CaptureEvent
    data object CaptureImage : CaptureEvent
    data object ToggleFlash : CaptureEvent
    data object SwitchCamera : CaptureEvent
    data object ToggleGrid : CaptureEvent
    data class SetGhostOpacity(val opacity: Float) : CaptureEvent
}

sealed interface CaptureEffect {
    data object TriggerCapture : CaptureEffect
    data object PlayCaptureSound : CaptureEffect
    data class ShowError(val message: String) : CaptureEffect
    data object NavigateToGallery : CaptureEffect
}
```

---

### GalleryViewModel

```kotlin
class GalleryViewModel(
    private val getFramesUseCase: GetFramesUseCase,
    private val deleteFramesUseCase: DeleteFramesUseCase,
    private val reorderFramesUseCase: ReorderFramesUseCase,
    private val filterFramesUseCase: FilterFramesUseCase,
    private val thumbnailGenerator: ThumbnailGenerator
) : BaseViewModel<GalleryState, GalleryEvent, GalleryEffect>

data class GalleryState(
    val frames: List<FrameWithThumbnail> = emptyList(),
    val selectedFrameIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val filter: FrameFilter = FrameFilter.None,
    val isLoading: Boolean = false
)
```

---

### ExportViewModel

```kotlin
class ExportViewModel(
    private val compileVideoUseCase: CompileVideoUseCase,
    private val exportGifUseCase: ExportGifUseCase,
    private val shareHandler: ShareHandler
) : BaseViewModel<ExportState, ExportEvent, ExportEffect>

data class ExportState(
    val projectId: String = "",
    val settings: ExportSettings = ExportSettings.default(),
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val exportedFilePath: String? = null
)

sealed interface ExportEvent {
    data class Initialize(val projectId: String) : ExportEvent
    data class UpdateSettings(val settings: ExportSettings) : ExportEvent
    data object StartExport : ExportEvent
    data object CancelExport : ExportEvent
    data object ShareVideo : ExportEvent
}
```

---

## Repositories

### ProjectRepository

```kotlin
interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    suspend fun getProject(id: String): Project?
    suspend fun createProject(project: Project): Result<Unit>
    suspend fun updateProject(project: Project): Result<Unit>
    suspend fun deleteProject(id: String): Result<Unit>
}
```

---

### FrameRepository

```kotlin
interface FrameRepository {
    fun getFrames(projectId: String): Flow<List<Frame>>
    suspend fun getFrame(id: String): Frame?
    suspend fun getLatestFrame(projectId: String): Frame?
    suspend fun addFrame(frame: Frame): Result<Unit>
    suspend fun updateFrame(frame: Frame): Result<Unit>
    suspend fun deleteFrames(ids: List<String>): Result<Unit>
    suspend fun getFrameCount(projectId: String): Int
}
```

---

### SettingsRepository

```kotlin
interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings): Result<Unit>
    suspend fun getSetting(key: String): String?
    suspend fun setSetting(key: String, value: String): Result<Unit>
}
```

---

## Data Classes

### Core Entities

```kotlin
data class Project(
    val id: String,
    val name: String,
    val settings: ProjectSettings,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String?
)

data class ProjectSettings(
    val fps: Int = 30,
    val resolution: Resolution = Resolution.HD_1080,
    val orientation: Orientation = Orientation.PORTRAIT
)

data class Frame(
    val id: String,
    val projectId: String,
    val originalPath: String,
    val alignedPath: String?,
    val thumbnailPath: String?,
    val landmarks: FaceLandmarks?,
    val alignmentMatrix: AlignmentMatrix?,
    val confidence: Float?,
    val capturedAt: Long,
    val createdAt: Long
)

data class FaceLandmarks(
    val points: List<LandmarkPoint>,
    val leftEyeCenter: LandmarkPoint,
    val rightEyeCenter: LandmarkPoint,
    val noseTip: LandmarkPoint,
    val boundingBox: BoundingBox,
    val confidence: Float
)

data class LandmarkPoint(
    val x: Float,  // Normalized 0-1
    val y: Float,  // Normalized 0-1
    val z: Float   // Depth
)

data class AlignmentMatrix(
    val scaleX: Float,
    val skewX: Float,
    val translateX: Float,
    val skewY: Float,
    val scaleY: Float,
    val translateY: Float
)

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
```

### Export Settings

```kotlin
data class ExportSettings(
    val resolution: Resolution,
    val fps: Int,
    val codec: VideoCodec,
    val quality: ExportQuality
)

enum class Resolution(val width: Int, val height: Int) {
    SD_480(854, 480),
    HD_720(1280, 720),
    HD_1080(1920, 1080),
    UHD_4K(3840, 2160)
}

enum class ExportQuality(val multiplier: Float) {
    LOW(0.5f),
    MEDIUM(1.0f),
    HIGH(2.0f),
    ULTRA(3.0f)
}
```

### Result Type

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Throwable,
        val message: String? = null
    ) : Result<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.data
    fun getOrThrow(): T = (this as Success).data
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (Throwable, String?) -> Unit): Result<T> {
    if (this is Result.Error) action(exception, message)
    return this
}
```

---

## Related Documentation

- [Algorithms](./ALGORITHMS.md) - Detailed algorithm implementations
- [Navigation Map](./NAVIGATION_MAP.md) - Screen flow and navigation
- [Architecture](../Architecture.md) - System architecture overview
