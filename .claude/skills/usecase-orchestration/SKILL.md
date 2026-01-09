---
name: usecase-orchestration
description: Designs use case composition patterns for complex multi-step operations, pipelines, and coordinator use cases. Use when combining multiple use cases, implementing pipelines, or coordinating parallel operations.
---

# Use Case Orchestration

## Overview

This skill helps design use case composition patterns for complex multi-step operations. It covers pipeline patterns, coordinator use cases, progress reporting, and cancellation strategies.

## Core Principle: Single Responsibility

Each use case should do one thing well. Complex operations are achieved by composing simple use cases.

```
Simple Use Cases:
- DetectFaceUseCase
- CalculateAlignmentMatrixUseCase
- ApplyTransformUseCase

Orchestrator Use Case:
- AlignFaceUseCase (composes the above)
```

## 1. Orchestrator Pattern

### Dispatcher Use Case

Routes to different implementations based on input:

```kotlin
/**
 * Unified alignment dispatcher that routes to the appropriate alignment use case
 * based on content type.
 */
class AlignContentUseCase(
    private val alignFace: AlignFaceUseCase,
    private val alignBody: AlignBodyUseCase,
    private val alignMuscle: AlignMuscleUseCase,
    private val alignLandscape: AlignLandscapeUseCase,
) {
    suspend operator fun invoke(
        frame: Frame,
        contentType: ContentType,
        referenceFrame: Frame? = null,
        muscleRegion: MuscleRegion? = null,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> = when (contentType) {
        ContentType.FACE -> alignFace(
            frame = frame,
            referenceFrame = referenceFrame,
            onProgress = onProgress,
        )
        ContentType.BODY -> alignBody(
            frame = frame,
            referenceFrame = referenceFrame,
            onProgress = onProgress,
        )
        ContentType.MUSCLE -> alignMuscle(
            frame = frame,
            referenceFrame = referenceFrame,
            settings = MuscleAlignmentSettings(muscleRegion = muscleRegion ?: MuscleRegion.FULL_BODY),
            onProgress = onProgress,
        )
        ContentType.LANDSCAPE -> alignLandscape(
            frame = frame,
            referenceFrame = referenceFrame,
            settings = LandscapeAlignmentSettings(),
            onProgress = onProgress,
        )
    }

    /**
     * Checks if a specific content type alignment is available on this device.
     */
    fun isAvailable(contentType: ContentType): Boolean = when (contentType) {
        ContentType.FACE -> true
        ContentType.BODY -> true
        ContentType.MUSCLE -> true
        ContentType.LANDSCAPE -> alignLandscape.isAvailable
    }
}
```

## 2. Pipeline Pattern

### Sequential Processing with Progress

```kotlin
class MultiPassStabilizationUseCase(
    private val faceDetector: FaceDetector,
    private val imageProcessor: ImageProcessor,
    private val calculateMatrix: CalculateAlignmentMatrixUseCase,
    private val calculateScore: CalculateStabilizationScoreUseCase,
    private val detectOvershoot: DetectOvershootUseCase,
    private val refineRotation: RefineRotationUseCase,
    private val refineScale: RefineScaleUseCase,
    private val refineTranslation: RefineTranslationUseCase,
) {
    suspend operator fun invoke(
        imageData: ImageData,
        goalLeftEye: LandmarkPoint,
        goalRightEye: LandmarkPoint,
        settings: AlignmentSettings,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Pair<ImageData, StabilizationResult>> {

        val maxPasses = settings.stabilizationMode.passes
        var currentImage = imageData
        var bestResult: StabilizationResult? = null

        for (pass in 1..maxPasses) {
            // Report progress
            onProgress?.invoke(
                StabilizationProgress(
                    currentPass = pass,
                    totalPasses = maxPasses,
                    currentScore = bestResult?.finalScore ?: 0f,
                    phase = "Refining alignment",
                )
            )

            // Step 1: Detect current landmarks
            val landmarks = faceDetector.detectFace(currentImage)
                ?: return Result.Error(
                    FrameLapseError.Processing.FaceNotDetected(),
                    "Face lost during stabilization pass $pass",
                )

            // Step 2: Calculate alignment matrix
            val matrix = calculateMatrix(landmarks, goalLeftEye, goalRightEye)

            // Step 3: Check for overshoot
            val overshoot = detectOvershoot(currentImage, matrix)
            if (overshoot.detected) {
                matrix.scale(overshoot.correctionFactor)
            }

            // Step 4: Apply refinements
            val refinedMatrix = matrix
                .let { refineRotation(it, landmarks, settings) }
                .let { refineScale(it, landmarks, settings) }
                .let { refineTranslation(it, landmarks, settings) }

            // Step 5: Apply transformation
            currentImage = imageProcessor.applyTransform(currentImage, refinedMatrix)

            // Step 6: Calculate score
            val score = calculateScore(currentImage, goalLeftEye, goalRightEye)
            bestResult = StabilizationResult(
                passCount = pass,
                finalScore = score,
                convergenceAchieved = score >= settings.targetScore,
            )

            // Early exit if converged
            if (bestResult.convergenceAchieved) break
        }

        return Result.Success(currentImage to bestResult!!)
    }
}
```

## 3. Parallel Execution Pattern

### Concurrent Operations

```kotlin
class BatchAlignFramesUseCase(
    private val alignContent: AlignContentUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        frames: List<Frame>,
        contentType: ContentType,
        referenceFrame: Frame,
        concurrency: Int = 4,
        onProgress: ((BatchProgress) -> Unit)? = null,
    ): Result<List<Frame>> = coroutineScope {
        val results = mutableListOf<Result<Frame>>()
        val completed = AtomicInteger(0)

        frames.chunked(concurrency).forEach { chunk ->
            val deferreds = chunk.map { frame ->
                async(dispatcher) {
                    alignContent(frame, contentType, referenceFrame).also {
                        val count = completed.incrementAndGet()
                        onProgress?.invoke(
                            BatchProgress(
                                completed = count,
                                total = frames.size,
                                currentFrame = frame.id,
                            )
                        )
                    }
                }
            }
            results.addAll(deferreds.awaitAll())
        }

        // Check for any failures
        val errors = results.filterIsInstance<Result.Error>()
        if (errors.isNotEmpty()) {
            return@coroutineScope Result.Error(
                Exception("${errors.size} frames failed to align"),
                "Some frames could not be aligned",
            )
        }

        Result.Success(results.mapNotNull { it.getOrNull() })
    }
}

data class BatchProgress(
    val completed: Int,
    val total: Int,
    val currentFrame: String,
) {
    val percentage: Float get() = completed.toFloat() / total
}
```

## 4. Composite Use Case Pattern

### Combining Results

```kotlin
class CaptureImageUseCase(
    private val addFrameUseCase: AddFrameUseCase,
    private val alignContent: AlignContentUseCase,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(
        projectId: String,
        imageData: ByteArray,
        contentType: ContentType,
        settings: CaptureSettings,
    ): Result<Frame> {
        // Step 1: Save raw image
        val imagePath = fileManager.saveImage(projectId, imageData)
            ?: return Result.Error(
                FrameLapseError.Storage.WriteFailure(IOException("Failed to save image")),
                "Could not save captured image",
            )

        // Step 2: Create frame record
        val frame = Frame(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            originalPath = imagePath,
            timestamp = System.currentTimeMillis(),
        )

        val addResult = addFrameUseCase(frame)
        if (addResult.isError) return addResult

        // Step 3: Optionally align
        return if (settings.autoAlign) {
            val latestFrame = getLatestAlignedFrame(projectId)
            alignContent(frame, contentType, latestFrame)
        } else {
            Result.Success(frame)
        }
    }
}
```

## 5. Progress Reporting Pattern

### Progress Data Classes

```kotlin
data class StabilizationProgress(
    val currentPass: Int,
    val totalPasses: Int,
    val currentScore: Float,
    val phase: String,
) {
    val percentage: Float get() = currentPass.toFloat() / totalPasses
    val isComplete: Boolean get() = currentPass >= totalPasses
}

data class ExportProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val phase: ExportPhase,
) {
    val percentage: Float get() = when (phase) {
        ExportPhase.Preparing -> 0f
        ExportPhase.Encoding -> currentFrame.toFloat() / totalFrames * 0.9f
        ExportPhase.Finalizing -> 0.95f
        ExportPhase.Complete -> 1f
    }

    enum class ExportPhase { Preparing, Encoding, Finalizing, Complete }
}
```

### Progress Callback Pattern

```kotlin
class CompileVideoUseCase(
    private val videoEncoder: VideoEncoder,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(
        projectId: String,
        settings: ExportSettings,
        onProgress: ((ExportProgress) -> Unit)? = null,
    ): Result<String> {
        onProgress?.invoke(ExportProgress(0, 0, ExportProgress.ExportPhase.Preparing))

        // Get frames
        val framesResult = frameRepository.getFramesByProject(projectId)
        if (framesResult.isError) return framesResult.mapError()

        val frames = framesResult.getOrNull()!!
        val totalFrames = frames.size

        // Encode each frame
        val outputPath = fileManager.getVideoOutputPath(projectId)
        videoEncoder.start(outputPath, settings)

        frames.forEachIndexed { index, frame ->
            onProgress?.invoke(
                ExportProgress(index + 1, totalFrames, ExportProgress.ExportPhase.Encoding)
            )
            videoEncoder.addFrame(frame.alignedPath ?: frame.originalPath)
        }

        onProgress?.invoke(ExportProgress(totalFrames, totalFrames, ExportProgress.ExportPhase.Finalizing))
        videoEncoder.finish()

        onProgress?.invoke(ExportProgress(totalFrames, totalFrames, ExportProgress.ExportPhase.Complete))
        return Result.Success(outputPath)
    }
}
```

## 6. Cancellation Pattern

### Cooperative Cancellation

```kotlin
class LongRunningUseCase(
    private val repository: FrameRepository,
    private val processor: ImageProcessor,
) {
    suspend operator fun invoke(
        frames: List<Frame>,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): Result<List<Frame>> = coroutineScope {
        val results = mutableListOf<Frame>()

        frames.forEachIndexed { index, frame ->
            // Check for cancellation
            ensureActive()

            onProgress?.invoke(index + 1, frames.size)
            val processed = processor.process(frame)
            results.add(processed)
        }

        Result.Success(results)
    }
}

// Usage in ViewModel
class ExportViewModel(...) {
    private var exportJob: Job? = null

    fun startExport() {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            try {
                compileVideoUseCase(projectId, settings, ::updateProgress)
                    .onSuccess { /* handle success */ }
                    .onError { /* handle error */ }
            } catch (e: CancellationException) {
                updateState { copy(exportPhase = ExportPhase.Cancelled) }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
    }
}
```

## 7. Error Aggregation Pattern

### Collecting Multiple Errors

```kotlin
class ValidateProjectUseCase(
    private val validators: List<ProjectValidator>,
) {
    suspend operator fun invoke(project: Project): Result<Project> {
        val errors = validators.mapNotNull { validator ->
            validator.validate(project).errorOrNull()
        }

        return if (errors.isEmpty()) {
            Result.Success(project)
        } else {
            Result.Error(
                ValidationException(errors),
                errors.joinToString("; ") { it.message },
            )
        }
    }
}
```

## Reference Examples

- AlignContentUseCase: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/alignment/AlignContentUseCase.kt`
- MultiPassStabilizationUseCase: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/face/MultiPassStabilizationUseCase.kt`
- CompileVideoUseCase: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/export/CompileVideoUseCase.kt`

## Checklist

### Orchestrator Design
- [ ] Routes to appropriate implementation based on input
- [ ] Provides availability checks
- [ ] Delegates to specialized use cases

### Pipeline Design
- [ ] Steps are clearly sequential
- [ ] Progress reported at each step
- [ ] Early exit on convergence/error

### Parallel Execution
- [ ] Concurrency limit specified
- [ ] Results aggregated properly
- [ ] Errors handled from all branches

### Progress Reporting
- [ ] Progress callback is optional
- [ ] Progress includes phase and percentage
- [ ] Progress updates are throttled if needed

### Cancellation
- [ ] ensureActive() checks in loops
- [ ] CancellationException handled gracefully
- [ ] Resources cleaned up on cancel
