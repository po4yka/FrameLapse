# CLAUDE.md - Project Context for Claude Code

## Project Overview

**FrameLapse** is a Kotlin Multiplatform (KMP) application for creating stabilized daily self-portrait timelapse videos. It automatically aligns facial photographs using ML-based landmark detection and compiles them into smooth timelapse videos.

**Status:** Production-ready (all core features implemented)

## Documentation Quick Links

| Document | Purpose |
|----------|---------|
| [Quick Start](./docs/QUICK_START.md) | 5-minute setup guide |
| [Algorithms](./docs/ALGORITHMS.md) | Face detection, alignment, stabilization algorithms |
| [Navigation Map](./docs/NAVIGATION_MAP.md) | Screen flow and navigation |
| [API Reference](./docs/API_REFERENCE.md) | Use cases, services, ViewModels |
| [Architecture](./Architecture.md) | System design with diagrams |
| [Troubleshooting](./docs/TROUBLESHOOTING.md) | Common issues and solutions |

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
./gradlew :androidApp:lintDebug   # Run Android Lint

# Dependency updates
./gradlew dependencyUpdates       # Check for available updates (report only)
./gradlew versionCatalogUpdate    # Update libs.versions.toml with new versions

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

### Koin Annotations for Dependency Injection

The project uses Koin Annotations with KSP for compile-time safe dependency injection.

**Annotations:**
- `@Single` - Singleton instance (repositories, data sources, services)
- `@Factory` - New instance per injection (use cases, ViewModels)
- `@Module` - Module class definition
- `@ComponentScan` - Auto-discover annotated classes in package

**Examples:**
```kotlin
// Repository with interface binding
@Single(binds = [ProjectRepository::class])
class ProjectRepositoryImpl(
    private val localDataSource: ProjectLocalDataSource,
    private val fileManager: FileManager
) : ProjectRepository

// Use case - new instance per injection
@Factory
class CreateProjectUseCase(
    private val projectRepository: ProjectRepository
)

// ViewModel - new instance per screen
@Factory
class CaptureViewModel(
    private val captureImageUseCase: CaptureImageUseCase,
    private val getLatestFrameUseCase: GetLatestFrameUseCase
)
```

**Module structure** (see `composeApp/src/commonMain/.../di/AnnotatedModules.kt`):
- `CommonModule` - Infrastructure services (Clock, FileSystem, ModelCapabilities)
- `DataModule` - Repositories, data sources, database via `@ComponentScan`
- `DomainModule` - Use cases via `@ComponentScan`
- `PresentationModule` - ViewModels via `@ComponentScan`
- `platformModule` - Platform services (DSL-based for Android Context support)

**Generated module access:**
```kotlin
import org.koin.ksp.generated.module

fun initKoin() = startKoin {
    modules(
        CommonModule().module,
        DataModule().module,
        DomainModule().module,
        PresentationModule().module,
        platformModule,
    )
}
```

## Technology Stack

| Layer | Technology |
|-------|------------|
| UI | Compose Multiplatform |
| DI | Koin + Koin Annotations (KSP) |
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

## Local CI Testing

**Always test GitHub Actions locally before pushing CI changes.**

After modifying any workflow in `.github/workflows/`:

```bash
# Install act if not already installed
brew install act

# List available jobs
./scripts/run-ci-local.sh list

# Dry run to verify workflow syntax
./scripts/run-ci-local.sh dry-run

# Run specific job locally
./scripts/run-ci-local.sh lint    # static-analysis job
./scripts/run-ci-local.sh test    # test job
./scripts/run-ci-local.sh build   # build-android-debug job

# For iOS workflows (cannot run in Docker, uses Gradle directly)
./scripts/run-ci-local.sh ios
```

Configuration files:
- `.actrc` - act runner configuration
- `.secrets` - local secrets (gitignored)

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
- Use Koin annotations (`@Single`, `@Factory`) for new dependencies
- Add `@Factory` to use cases and ViewModels, `@Single` to repositories and services

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

## Algorithm Overview

> For full details with diagrams, see [docs/ALGORITHMS.md](./docs/ALGORITHMS.md)

### Face Alignment Pipeline
1. **Detection**: MediaPipe (Android) / Vision (iOS) extracts 478 3D landmarks
2. **Key Points**: Left eye (#468), Right eye (#473), Nose (#1)
3. **Matrix Calculation**: Rotation, scale, translation to align eyes horizontally
4. **Multi-Pass Stabilization**: FAST (4 passes) or SLOW (11 passes) refinement

### Stabilization Score
```
score = ((leftEyeError + rightEyeError) / 2) * 1000 / canvasHeight
- < 0.5: Perfect (no correction needed)
- < 20.0: Success
- >= 20.0: Failed (manual adjustment needed)
```

### Video Compilation
- Bitrate: `width × height × fps × 0.1 × qualityMultiplier`
- Codecs: H.264 (universal) or HEVC (better compression)
- Uses MediaCodec (Android) / AVAssetWriter (iOS)

## Important Notes

- All image processing runs on background dispatchers
- Face alignment uses affine transformation based on eye positions
- Video encoding uses hardware acceleration (no FFmpeg bundled)
- Images stored in app sandbox, not public gallery
- Minimum face confidence threshold: 0.7 (standard) / 0.3 (realtime preview)
- Storage availability is checked before write operations
- Accessibility: All interactive elements have content descriptions
- Colors comply with WCAG AA contrast requirements
