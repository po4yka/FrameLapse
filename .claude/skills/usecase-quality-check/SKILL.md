---
name: usecase-quality-check
description: Validates use case design including single responsibility, input validation, error handling, and testability. Use when reviewing use cases for quality and correctness.
---

# Use Case Quality Check

## Overview

This skill helps validate use case implementations for quality, correctness, and adherence to Clean Architecture principles. Use it to review use cases for single responsibility, proper error handling, and testability.

## Quality Criteria

### 1. Single Responsibility

Each use case should do ONE thing well.

```kotlin
// VIOLATION: Multiple responsibilities
class SaveAndNotifyUseCase(
    private val repository: ProjectRepository,
    private val notificationService: NotificationService,
    private val analyticsService: AnalyticsService,
) {
    suspend operator fun invoke(project: Project): Result<Unit> {
        repository.save(project)  // Responsibility 1
        notificationService.notify("Project saved")  // Responsibility 2
        analyticsService.track("project_saved")  // Responsibility 3
        return Result.Success(Unit)
    }
}

// CORRECT: Single responsibility
class SaveProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(project: Project): Result<Project> {
        return repository.save(project)
    }
}

// Separate use cases for other responsibilities
class NotifyProjectSavedUseCase(...)
class TrackProjectSavedUseCase(...)
```

### 2. Input Validation

Validate inputs at the use case boundary.

```kotlin
// VIOLATION: No input validation
class GetProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(projectId: String): Result<Project> {
        return repository.getProject(projectId)  // ❌ Blank ID goes to repository
    }
}

// CORRECT: Validate inputs
class GetProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(projectId: String): Result<Project> {
        // ✓ Validate at boundary
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be blank"),
                "Invalid project ID",
            )
        }
        return repository.getProject(projectId)
    }
}
```

### 3. Error Handling

Return `Result<T>` with meaningful errors.

```kotlin
// VIOLATION: Throwing exceptions
class CreateProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(name: String): Project {
        if (name.isBlank()) {
            throw IllegalArgumentException("Name required")  // ❌ Throws
        }
        return repository.create(Project(name = name))  // ❌ May throw
    }
}

// CORRECT: Return Result<T>
class CreateProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(name: String): Result<Project> {
        if (name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Name required"),
                "Project name is required",
            )
        }

        return try {
            val project = Project(name = name.trim())
            repository.save(project)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create project")
        }
    }
}
```

### 4. Suspend Functions

Use suspend for async operations.

```kotlin
// VIOLATION: Blocking call
class GetFramesUseCase(
    private val repository: FrameRepository,
) {
    fun invoke(projectId: String): Result<List<Frame>> {
        return runBlocking {  // ❌ Blocks thread
            repository.getFrames(projectId)
        }
    }
}

// CORRECT: Suspend function
class GetFramesUseCase(
    private val repository: FrameRepository,
) {
    suspend operator fun invoke(projectId: String): Result<List<Frame>> {
        return repository.getFrames(projectId)  // ✓ Suspends
    }
}
```

### 5. Operator Invoke Convention

Use `operator fun invoke` for callable use cases.

```kotlin
// VIOLATION: Named method
class GetProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend fun execute(projectId: String): Result<Project> {  // ❌ Named method
        return repository.getProject(projectId)
    }
}

// CORRECT: Operator invoke
class GetProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(projectId: String): Result<Project> {  // ✓ Callable
        return repository.getProject(projectId)
    }
}

// Usage:
val result = getProjectUseCase("id")  // Clean syntax
```

### 6. Dependency Injection

Inject dependencies through constructor.

```kotlin
// VIOLATION: Creating dependencies
class AlignFaceUseCase {
    private val faceDetector = FaceDetectorImpl()  // ❌ Creates dependency

    suspend operator fun invoke(frame: Frame): Result<Frame> {
        // ...
    }
}

// CORRECT: Inject dependencies
class AlignFaceUseCase(
    private val faceDetector: FaceDetector,  // ✓ Injected interface
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
) {
    suspend operator fun invoke(frame: Frame): Result<Frame> {
        // ...
    }
}
```

### 7. No Side Effects in Domain

Domain use cases should be pure.

```kotlin
// VIOLATION: Side effects in use case
class CaptureImageUseCase(
    private val repository: FrameRepository,
    private val analytics: Analytics,  // ❌ Side effect dependency
) {
    suspend operator fun invoke(frame: Frame): Result<Frame> {
        analytics.track("capture_started")  // ❌ Side effect
        val result = repository.save(frame)
        analytics.track("capture_completed")  // ❌ Side effect
        return result
    }
}

// CORRECT: Pure use case, side effects in presentation
class CaptureImageUseCase(
    private val repository: FrameRepository,
) {
    suspend operator fun invoke(frame: Frame): Result<Frame> {
        return repository.save(frame)  // ✓ Pure
    }
}

// Analytics in ViewModel
class CaptureViewModel(...) {
    private fun captureImage() {
        analytics.track("capture_started")
        captureImageUseCase(frame).onSuccess {
            analytics.track("capture_completed")
        }
    }
}
```

### 8. Return Types

Use appropriate return types.

```kotlin
// Return types guide:
suspend operator fun invoke(...): Result<T>      // Single item with errors
suspend operator fun invoke(...): Result<Unit>   // Action with errors
suspend operator fun invoke(...): T              // Simple getter (rare)
fun invoke(...): Flow<T>                         // Reactive stream
```

## Common Issues

### Issue 1: God Use Case

```kotlin
// VIOLATION: Too many responsibilities
class ManageProjectUseCase(
    private val projectRepo: ProjectRepository,
    private val frameRepo: FrameRepository,
    private val storage: StorageManager,
) {
    suspend fun create(name: String): Result<Project>
    suspend fun update(project: Project): Result<Project>
    suspend fun delete(id: String): Result<Unit>
    suspend fun getFrames(id: String): Result<List<Frame>>
    suspend fun exportVideo(id: String): Result<String>
    // ❌ Too many methods - this is a service, not a use case
}

// CORRECT: Separate use cases
class CreateProjectUseCase(...)
class UpdateProjectUseCase(...)
class DeleteProjectUseCase(...)
class GetFramesUseCase(...)
class ExportVideoUseCase(...)
```

### Issue 2: Leaking Implementation Details

```kotlin
// VIOLATION: Exposing internal types
class GetFramesUseCase(
    private val localDataSource: FrameLocalDataSource,  // ❌ Data layer type
) {
    suspend operator fun invoke(projectId: String): List<FrameEntity> {  // ❌ Data entity
        return localDataSource.getByProject(projectId)
    }
}

// CORRECT: Use domain types
class GetFramesUseCase(
    private val repository: FrameRepository,  // ✓ Domain interface
) {
    suspend operator fun invoke(projectId: String): Result<List<Frame>> {  // ✓ Domain entity
        return repository.getFramesByProject(projectId)
    }
}
```

### Issue 3: Missing Progress Reporting

```kotlin
// VIOLATION: No progress for long operation
class CompileVideoUseCase(...) {
    suspend operator fun invoke(projectId: String): Result<String> {
        // Long operation with no progress feedback
    }
}

// CORRECT: Progress callback
class CompileVideoUseCase(...) {
    suspend operator fun invoke(
        projectId: String,
        settings: ExportSettings,
        onProgress: ((ExportProgress) -> Unit)? = null,  // ✓ Optional progress
    ): Result<String> {
        frames.forEachIndexed { index, frame ->
            onProgress?.invoke(ExportProgress(index + 1, frames.size))
            // Process frame
        }
    }
}
```

## Validation Checklist

### Naming
- [ ] Class name is `{Action}{Entity}UseCase`
- [ ] Examples: `CreateProjectUseCase`, `GetFramesUseCase`, `AlignFaceUseCase`

### Structure
- [ ] Single public method: `operator fun invoke`
- [ ] Dependencies injected via constructor
- [ ] Uses domain types (not data layer types)

### Input Validation
- [ ] Validates all inputs at boundary
- [ ] Returns `Result.Error` for invalid inputs
- [ ] Human-readable error messages

### Error Handling
- [ ] Returns `Result<T>` (not throws)
- [ ] Catches exceptions and wraps in Result.Error
- [ ] Provides user-friendly error messages

### Async
- [ ] Uses `suspend` for async operations
- [ ] No `runBlocking` or `GlobalScope`
- [ ] Progress callback for long operations

### Testability
- [ ] No static dependencies
- [ ] All dependencies are interfaces
- [ ] Can be tested with fakes

## Reference Examples

- Simple use case: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/project/GetProjectUseCase.kt`
- Use case with validation: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/project/CreateProjectUseCase.kt`
- Complex use case: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/face/AlignFaceUseCase.kt`
- Orchestrator: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/alignment/AlignContentUseCase.kt`
