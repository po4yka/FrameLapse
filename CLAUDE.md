# CLAUDE.md - Project Context for Claude Code

## Project Overview

**FrameLapse** is a Kotlin Multiplatform (KMP) application for creating stabilized daily self-portrait timelapse videos. It automatically aligns facial photographs using ML-based landmark detection and compiles them into smooth timelapse videos.

**Status:** Production-ready (all core features implemented)

## Quick Commands

```shell
# Build Android debug APK
./gradlew :composeApp:assembleDebug

# Build Android release APK (requires signing config)
./gradlew :composeApp:assembleRelease

# Run all tests
./gradlew test

# Run Android unit tests
./gradlew :composeApp:testDebugUnitTest

# Static analysis
./gradlew spotlessCheck           # Check code formatting
./gradlew spotlessApply           # Auto-fix formatting issues
./gradlew :composeApp:lintDebug   # Run Android Lint

# Clean build
./gradlew clean
```

## Architecture

Clean Architecture with Unidirectional Data Flow (UDF):

```
UI Layer (Compose Multiplatform)
    ↓
Presentation Layer (ViewModels + StateFlow)
    ↓
Domain Layer (Use Cases, Entities - Pure Kotlin)
    ↓
Data Layer (Repositories, SQLDelight)
    ↓
Platform Layer (expect/actual native implementations)
```

## Project Structure

```
/composeApp/src/
├── commonMain/kotlin/           # Shared business logic, UI, ViewModels
│   └── com/po4yka/framelapse/
│       ├── data/                # Repositories, local data sources, storage
│       ├── di/                  # Koin dependency injection modules
│       ├── domain/              # Entities, use cases, repository interfaces
│       ├── navigation/          # Navigation routes and controller
│       ├── platform/            # expect declarations for platform code
│       ├── presentation/        # ViewModels with State/Event/Effect pattern
│       └── ui/                  # Compose UI (screens, components, theme)
├── commonMain/composeResources/ # Localized string resources
├── commonMain/sqldelight/       # Database schema
├── commonTest/kotlin/           # Shared unit tests
├── androidMain/kotlin/          # Android implementations (CameraX, MediaPipe)
├── androidUnitTest/kotlin/      # Android-specific tests with MockK
└── iosMain/kotlin/              # iOS implementations (AVFoundation, Vision)

/iosApp/                         # iOS app entry point
```

## Key Patterns

### expect/actual for Platform Code
```kotlin
// In commonMain
expect class FaceDetector {
    suspend fun detect(imageBytes: ByteArray): FaceLandmarks?
}

// In androidMain - MediaPipe implementation
// In iosMain - Vision Framework implementation
```

### ViewModel State Pattern (UDF)
```kotlin
data class CaptureState(
    val isProcessing: Boolean = false,
    val frameCount: Int = 0,
    val ghostImagePath: String? = null,
    val faceDetectionConfidence: Float? = null
)

sealed class CaptureEvent {
    data class Initialize(val projectId: String) : CaptureEvent()
    object CaptureImage : CaptureEvent()
    object ToggleFlash : CaptureEvent()
}

sealed class CaptureEffect {
    data class ShowError(val message: String) : CaptureEffect()
    object PlayCaptureSound : CaptureEffect()
}
```

### Result<T> Error Handling
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

## Technology Stack

| Layer | Technology |
|-------|------------|
| UI | Compose Multiplatform |
| DI | Koin |
| Database | SQLDelight |
| Async | Coroutines + Flow |
| Camera (Android) | CameraX |
| Camera (iOS) | AVFoundation |
| Face Detection (Android) | MediaPipe / ML Kit |
| Face Detection (iOS) | Vision Framework |
| Video Encoding (Android) | MediaCodec |
| Video Encoding (iOS) | AVAssetWriter |
| Testing | kotlin-test, Turbine, MockK |

## Core Domain Entities

- `Project` - A timelapse project with settings (FPS, resolution, orientation)
- `Frame` - Individual photo with metadata (timestamp, alignment data, confidence)
- `AlignmentSettings` - Configuration for face alignment algorithm
- `FaceLandmarks` - 478 3D facial landmark points
- `ExportSettings` - Video export configuration (resolution, FPS, codec, quality)

## Key Use Cases

- `CaptureImageUseCase` - Captures and processes a new photo
- `AlignFaceUseCase` - Aligns face to reference coordinates
- `CompileVideoUseCase` - Generates timelapse video from frames
- `ImportPhotosUseCase` - Batch imports photos from gallery
- `ValidateAlignmentUseCase` - Validates face detection quality
- `CalculateAlignmentMatrixUseCase` - Computes affine transformation

## Static Analysis

The project uses strict static analysis:

| Tool | Purpose | Config File |
|------|---------|-------------|
| Spotless | Code formatting (ktlint backend) | `.editorconfig` |
| Android Lint | Android-specific checks | `composeApp/build.gradle.kts` |

**Always run `./gradlew spotlessApply` after making changes.**

## Coding Conventions

- Use Kotlin 2.x features
- Prefer immutable data classes for state
- Use StateFlow for UI state, SharedFlow for one-time effects
- Keep platform code minimal - maximize shared logic
- Follow Material 3 design guidelines for UI
- Use suspend functions for async operations
- Handle errors with Result<T> sealed class
- All UI strings should be in composeResources for localization

## Testing Strategy

The project has comprehensive test coverage:

- **commonTest**: Pure Kotlin tests for domain logic and use cases
- **androidUnitTest**: Android-specific tests with MockK for mocking
- **Test Utilities**: FakeRepositories, TestFixtures for test data

Key testing patterns:
```kotlin
@Test
fun `invoke returns error when projectId is blank`() = runTest {
    val result = useCase("")
    assertTrue(result is Result.Error)
}

// Flow testing with Turbine
viewModel.state.test {
    viewModel.onEvent(LoadProjects)
    assertEquals(expectedProjects, awaitItem().projects)
}
```

## Release Configuration

### Android Signing (Environment Variables)
```shell
KEYSTORE_FILE=/path/to/keystore
KEYSTORE_PASSWORD=password
KEY_ALIAS=framelapse
KEY_PASSWORD=password
```

### Version Management
Versions are managed in `gradle.properties`:
```properties
VERSION_NAME=1.0.0
VERSION_CODE=1
```

## Important Notes

- All image processing runs on background dispatchers
- Face alignment uses affine transformation based on eye positions
- Video encoding uses hardware acceleration (no FFmpeg bundled)
- Images stored in app sandbox, not public gallery
- Minimum face confidence threshold: 0.7
- Storage availability is checked before write operations
- Accessibility: All interactive elements have content descriptions
- Colors comply with WCAG AA contrast requirements
