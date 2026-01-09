---
name: error-handling-strategy
description: Implements global error handling including error hierarchies, error mapping, user-friendly messages, and recovery strategies. Use when improving error handling consistency.
---

# Error Handling Strategy

## Overview

This skill helps implement consistent error handling across all architecture layers. It covers error hierarchies, mapping technical errors to user-friendly messages, and recovery strategies.

## Core Pattern: Result<T>

### Result Sealed Class

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Throwable,
        val message: String? = null,
    ) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
    val errorMessage: String? get() = (this as? Error)?.message

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Throwable, String?) -> Unit): Result<T> {
        if (this is Error) action(exception, message)
        return this
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }
}
```

## 1. Domain Error Hierarchy

### Create Feature-Specific Errors

```kotlin
sealed class FrameLapseError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    // Storage Errors
    sealed class Storage(message: String, cause: Throwable? = null) : FrameLapseError(message, cause) {
        class InsufficientSpace(val requiredBytes: Long) : Storage(
            "Insufficient storage space. Need ${formatBytes(requiredBytes)}."
        )
        class FileNotFound(val path: String) : Storage("File not found: $path")
        class WriteFailure(cause: Throwable) : Storage("Failed to write file", cause)
        class ReadFailure(cause: Throwable) : Storage("Failed to read file", cause)
        class PermissionDenied : Storage("Storage permission denied")
    }

    // Processing Errors
    sealed class Processing(message: String, cause: Throwable? = null) : FrameLapseError(message, cause) {
        class FaceNotDetected : Processing("No face detected in image")
        class BodyNotDetected : Processing("No body pose detected in image")
        class LowConfidence(val confidence: Float, val threshold: Float) : Processing(
            "Detection confidence too low: $confidence < $threshold"
        )
        class AlignmentFailed(cause: Throwable) : Processing("Alignment failed", cause)
        class EncodingFailed(val frameIndex: Int) : Processing("Video encoding failed at frame $frameIndex")
    }

    // Project Errors
    sealed class Project(message: String, cause: Throwable? = null) : FrameLapseError(message, cause) {
        class NotFound(val projectId: String) : Project("Project not found: $projectId")
        class InvalidName(val name: String) : Project("Invalid project name: $name")
        class DuplicateName(val name: String) : Project("Project with name '$name' already exists")
    }

    // Camera Errors
    sealed class Camera(message: String, cause: Throwable? = null) : FrameLapseError(message, cause) {
        class NotAvailable : Camera("Camera not available")
        class PermissionDenied : Camera("Camera permission denied")
        class InitializationFailed(cause: Throwable) : Camera("Camera initialization failed", cause)
    }
}
```

## 2. Error Mapping by Layer

### Repository Layer

```kotlin
class FrameRepositoryImpl(
    private val localDataSource: FrameLocalDataSource,
    private val imageStorageManager: ImageStorageManager,
) : FrameRepository {

    override suspend fun addFrame(frame: Frame): Result<Frame> = try {
        // Check preconditions with specific errors
        if (!imageStorageManager.hasSpace(frame.estimatedSize)) {
            return Result.Error(
                FrameLapseError.Storage.InsufficientSpace(frame.estimatedSize),
                "Not enough storage space to save photo",
            )
        }

        val sortOrder = (localDataSource.getMaxSortOrder(frame.projectId) ?: -1) + 1
        val frameWithSortOrder = frame.copy(sortOrder = sortOrder.toInt())
        localDataSource.insert(FrameMapper.toInsertParams(frameWithSortOrder))
        Result.Success(frameWithSortOrder)
    } catch (e: IOException) {
        Result.Error(
            FrameLapseError.Storage.WriteFailure(e),
            "Failed to save photo. Please check storage permissions.",
        )
    } catch (e: SQLiteException) {
        Result.Error(e, "Failed to save frame to database")
    } catch (e: Exception) {
        Result.Error(e, "An unexpected error occurred")
    }
}
```

### Use Case Layer

```kotlin
class AlignFaceUseCase(
    private val faceDetector: FaceDetector,
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
    // ...
) {
    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame? = null,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> {
        // Validate inputs
        if (!faceDetector.isAvailable) {
            return Result.Error(
                FrameLapseError.Camera.NotAvailable(),
                "Face detection is not available on this device",
            )
        }

        // Detect face
        val landmarks = faceDetector.detectFace(frame.originalPath)
            ?: return Result.Error(
                FrameLapseError.Processing.FaceNotDetected(),
                "No face detected. Please ensure your face is visible.",
            )

        // Validate confidence
        if (landmarks.confidence < MIN_CONFIDENCE) {
            return Result.Error(
                FrameLapseError.Processing.LowConfidence(landmarks.confidence, MIN_CONFIDENCE),
                "Face detection confidence too low. Please try better lighting.",
            )
        }

        return try {
            // Process alignment
            val alignedImage = imageProcessor.align(frame, landmarks, goalPositions)
            val updatedFrame = frame.copy(alignedPath = alignedImage.path, confidence = landmarks.confidence)
            frameRepository.updateAlignedFrame(updatedFrame)
            Result.Success(updatedFrame)
        } catch (e: Exception) {
            Result.Error(
                FrameLapseError.Processing.AlignmentFailed(e),
                "Face alignment failed. Please try again.",
            )
        }
    }
}
```

### ViewModel Layer

```kotlin
class CaptureViewModel(
    private val captureImageUseCase: CaptureImageUseCase,
    // ...
) : BaseViewModel<CaptureState, CaptureEvent, CaptureEffect>(...) {

    private fun captureImage() {
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

                    // Map error to appropriate effect
                    val effect = when (exception) {
                        is FrameLapseError.Storage.InsufficientSpace ->
                            CaptureEffect.ShowStorageWarning(exception.requiredBytes)
                        is FrameLapseError.Processing.FaceNotDetected ->
                            CaptureEffect.ShowError("No face detected. Adjust your position.")
                        is FrameLapseError.Camera.PermissionDenied ->
                            CaptureEffect.RequestCameraPermission
                        else ->
                            CaptureEffect.ShowError(message ?: "Capture failed")
                    }
                    sendEffect(effect)
                }
        }
    }
}
```

## 3. User-Friendly Message Mapping

### Error Message Provider

```kotlin
object ErrorMessageProvider {

    fun getUserMessage(error: FrameLapseError): String = when (error) {
        // Storage errors
        is FrameLapseError.Storage.InsufficientSpace ->
            "Not enough storage. Free up ${formatBytes(error.requiredBytes)} to continue."
        is FrameLapseError.Storage.FileNotFound ->
            "The file could not be found. It may have been deleted."
        is FrameLapseError.Storage.WriteFailure ->
            "Failed to save. Please check storage permissions."
        is FrameLapseError.Storage.PermissionDenied ->
            "Storage access denied. Please grant permission in Settings."

        // Processing errors
        is FrameLapseError.Processing.FaceNotDetected ->
            "No face detected. Please ensure your face is clearly visible."
        is FrameLapseError.Processing.BodyNotDetected ->
            "No body detected. Please step back so your body is visible."
        is FrameLapseError.Processing.LowConfidence ->
            "Detection quality too low. Try better lighting."
        is FrameLapseError.Processing.AlignmentFailed ->
            "Alignment failed. Please try again."
        is FrameLapseError.Processing.EncodingFailed ->
            "Video encoding failed at frame ${error.frameIndex}. Try reducing quality."

        // Project errors
        is FrameLapseError.Project.NotFound ->
            "Project not found. It may have been deleted."
        is FrameLapseError.Project.InvalidName ->
            "Invalid project name. Use letters, numbers, and spaces only."
        is FrameLapseError.Project.DuplicateName ->
            "A project named '${error.name}' already exists."

        // Camera errors
        is FrameLapseError.Camera.NotAvailable ->
            "Camera not available. Please close other camera apps."
        is FrameLapseError.Camera.PermissionDenied ->
            "Camera access denied. Please grant permission in Settings."
        is FrameLapseError.Camera.InitializationFailed ->
            "Camera failed to start. Please restart the app."
    }

    fun getRecoveryAction(error: FrameLapseError): RecoveryAction? = when (error) {
        is FrameLapseError.Storage.InsufficientSpace ->
            RecoveryAction.OpenStorageSettings
        is FrameLapseError.Storage.PermissionDenied ->
            RecoveryAction.RequestStoragePermission
        is FrameLapseError.Camera.PermissionDenied ->
            RecoveryAction.RequestCameraPermission
        is FrameLapseError.Processing.LowConfidence ->
            RecoveryAction.RetryWithGuidance("Improve lighting and face visibility")
        else -> null
    }

    sealed class RecoveryAction {
        data object OpenStorageSettings : RecoveryAction()
        data object RequestStoragePermission : RecoveryAction()
        data object RequestCameraPermission : RecoveryAction()
        data class RetryWithGuidance(val message: String) : RecoveryAction()
    }
}
```

## 4. Logging Strategy

### Error Logger

```kotlin
object ErrorLogger {
    private val logger = Logger.withTag("FrameLapse")

    fun log(error: FrameLapseError, context: String? = null) {
        val severity = getSeverity(error)
        val message = buildString {
            context?.let { append("[$it] ") }
            append(error::class.simpleName)
            append(": ")
            append(error.message)
        }

        when (severity) {
            Severity.DEBUG -> logger.d { message }
            Severity.INFO -> logger.i { message }
            Severity.WARNING -> logger.w(error.cause) { message }
            Severity.ERROR -> logger.e(error.cause) { message }
        }
    }

    private fun getSeverity(error: FrameLapseError): Severity = when (error) {
        is FrameLapseError.Processing.FaceNotDetected -> Severity.DEBUG
        is FrameLapseError.Processing.LowConfidence -> Severity.INFO
        is FrameLapseError.Storage.InsufficientSpace -> Severity.WARNING
        is FrameLapseError.Processing.EncodingFailed -> Severity.ERROR
        else -> Severity.WARNING
    }

    enum class Severity { DEBUG, INFO, WARNING, ERROR }
}
```

## 5. Error Handling in UI

### Error UI Component

```kotlin
@Composable
fun ErrorBanner(
    error: String?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    AnimatedVisibility(visible = error != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = error ?: "",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                onRetry?.let {
                    TextButton(onClick = it) {
                        Text("Retry")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss")
                }
            }
        }
    }
}
```

## Reference Examples

- Result class: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/util/Result.kt`
- Repository error handling: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/repository/FrameRepositoryImpl.kt`
- ViewModel error handling: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/capture/CaptureViewModel.kt`

## Checklist

### Error Hierarchy
- [ ] Domain errors extend sealed class hierarchy
- [ ] Errors are specific and actionable
- [ ] Errors include context (IDs, paths, values)

### Repository Layer
- [ ] All exceptions are caught
- [ ] Technical errors mapped to domain errors
- [ ] User-friendly messages provided

### Use Case Layer
- [ ] Input validation before processing
- [ ] Specific error types returned
- [ ] Errors include recovery hints

### ViewModel Layer
- [ ] Errors stored in state for display
- [ ] Error-specific effects triggered
- [ ] Recovery actions available

### UI Layer
- [ ] Errors displayed consistently
- [ ] Retry/dismiss options available
- [ ] Accessibility supported
