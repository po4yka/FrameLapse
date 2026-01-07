# CLAUDE.md - Project Context for Claude Code

## Project Overview

**FrameLapse** is a Kotlin Multiplatform (KMP) application for creating stabilized daily self-portrait timelapse videos. It automatically aligns facial photographs using ML-based landmark detection and compiles them into smooth timelapse videos.

## Quick Commands

```shell
# Build Android debug APK
./gradlew :composeApp:assembleDebug

# Build Android release APK
./gradlew :composeApp:assembleRelease

# Run all tests
./gradlew test

# Run Android unit tests
./gradlew :composeApp:testDebugUnitTest

# Check code style
./gradlew ktlintCheck

# Format code
./gradlew ktlintFormat

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
├── commonMain/kotlin/     # Shared business logic, UI, ViewModels
├── androidMain/kotlin/    # Android implementations (CameraX, MediaPipe, MediaCodec)
└── iosMain/kotlin/        # iOS implementations (AVFoundation, Vision, AVAssetWriter)

/iosApp/                   # iOS app entry point and SwiftUI code
```

## Key Patterns

### expect/actual for Platform Code
```kotlin
// In commonMain
expect class ImageProcessor {
    fun detectFace(image: ByteArray): FaceLandmarks?
}

// In androidMain
actual class ImageProcessor {
    actual fun detectFace(image: ByteArray): FaceLandmarks? {
        // MediaPipe/ML Kit implementation
    }
}

// In iosMain
actual class ImageProcessor {
    actual fun detectFace(image: ByteArray): FaceLandmarks? {
        // Vision Framework implementation
    }
}
```

### ViewModel State Pattern
```kotlin
data class CaptureState(
    val isProcessing: Boolean = false,
    val currentFrame: Frame? = null,
    val ghostImage: ImageBitmap? = null
)

class CaptureViewModel : ViewModel() {
    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()
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

## Core Domain Entities

- `Project` - A timelapse project with settings (FPS, resolution, orientation)
- `Frame` - Individual photo with metadata (timestamp, alignment data, confidence)
- `AlignmentSettings` - Configuration for face alignment algorithm
- `FaceLandmarks` - 478 3D facial landmark points

## Key Use Cases

- `CaptureImageUseCase` - Captures and processes a new photo
- `AlignFaceUseCase` - Aligns face to reference coordinates
- `CompileVideoUseCase` - Generates timelapse video from frames
- `ImportPhotosUseCase` - Batch imports photos from gallery

## Coding Conventions

- Use Kotlin 2.x features
- Prefer immutable data classes for state
- Use StateFlow for UI state, SharedFlow for events
- Keep platform code minimal - maximize shared logic
- Follow Material 3 design guidelines for UI
- Use suspend functions for async operations
- Handle errors with Result<T> or sealed classes

## Testing Strategy

- Unit tests in `commonTest` for shared logic
- Platform-specific tests in `androidTest`/`iosTest`
- UI tests using Compose testing APIs
- Integration tests for database operations

## Important Notes

- All image processing runs on background dispatchers
- Face alignment uses affine transformation based on eye positions
- Video encoding uses hardware acceleration (no FFmpeg bundled)
- Images stored in app sandbox, not public gallery
- Minimum face confidence threshold: 0.7
