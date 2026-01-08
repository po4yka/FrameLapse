# FrameLapse

[![CI](https://github.com/po4yka/FrameLapse/actions/workflows/ci.yml/badge.svg)](https://github.com/po4yka/FrameLapse/actions/workflows/ci.yml)
[![CI - iOS](https://github.com/po4yka/FrameLapse/actions/workflows/ci-ios.yml/badge.svg)](https://github.com/po4yka/FrameLapse/actions/workflows/ci-ios.yml)

A Kotlin Multiplatform application for creating stabilized daily self-portrait timelapse videos. FrameLapse automatically aligns and compiles facial photographs to document physical transformations over time.

## Features

### Photo Capture & Import
- Built-in camera with ghost/guide photo overlay for consistent positioning
- Grid overlay for manual frame alignment
- Adjustable ghost image opacity
- Batch import from device gallery
- Front/back camera switching with flash control

### Automatic Face Alignment
- Facial landmark detection (478 3D landmarks)
- Affine transformation for eye position standardization
- Automatic scaling and rotation correction
- Confidence thresholds for quality assurance
- Real-time face detection feedback

### Video Compilation
- Adjustable frame rate (1-60 FPS)
- Multiple resolution options (480p to 4K)
- Hardware-accelerated encoding (H.264/HEVC)
- Quality presets (Low, Medium, High, Ultra)
- Export progress tracking with cancellation support

### Project Management
- Multi-project support with thumbnails
- Frame selection, reordering, and deletion
- Date-based frame organization
- Daily reminder notifications
- Local-first storage (no cloud required)

### Accessibility & Localization
- Full screen reader support with content descriptions
- WCAG AA color contrast compliance
- Touch target size optimization (48dp minimum)
- Localization-ready string resources

## Architecture

FrameLapse follows **Clean Architecture** with **Unidirectional Data Flow (UDF)**, maximizing shared code while keeping heavy media processing native for optimal performance.

```
┌─────────────────────────────────────────┐
│         UI Layer                        │
│    (Compose Multiplatform)              │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Presentation Layer                 │
│    (Shared ViewModels + StateFlow)      │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         Domain Layer                    │
│   (Use Cases, Entities - Pure Kotlin)   │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│          Data Layer                     │
│    (Repositories, SQLDelight)           │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│        Platform Layer                   │
│   (expect/actual native implementations)│
└─────────────────────────────────────────┘
```

## Tech Stack

| Capability | Shared (KMP) | Android | iOS |
|:-----------|:-------------|:--------|:----|
| **Language** | Kotlin 2.x | Kotlin (JVM) | Kotlin/Native |
| **UI** | Compose Multiplatform | Jetpack Compose | Compose (Skia) + UIKit |
| **DI** | Koin | Koin Android | Koin Native |
| **Database** | SQLDelight | SQLite (Android Driver) | SQLite (Native Driver) |
| **Async** | Coroutines & Flow | Dispatchers.Main/IO | Dispatchers.Main/Default |
| **Camera** | Abstract Interface | CameraX | AVFoundation |
| **ML/Vision** | Abstract Interface | MediaPipe / ML Kit | Vision Framework |
| **Video Encoding** | Abstract Interface | MediaCodec | AVAssetWriter |

## Project Structure

```
/composeApp
├── /src
│   ├── /commonMain      # Shared code for all platforms
│   │   ├── /kotlin      # Business logic, UI, ViewModels
│   │   ├── /sqldelight  # Database schema
│   │   └── /composeResources  # Localized strings
│   ├── /commonTest      # Shared unit tests
│   ├── /androidMain     # Android-specific implementations
│   └── /iosMain         # iOS-specific implementations
/iosApp                  # iOS application entry point
```

## Building

### Prerequisites
- JDK 17+
- Android Studio (for Android development)
- Xcode 15+ (for iOS development)

### Android

```shell
# Debug build
./gradlew :composeApp:assembleDebug

# Release build (requires signing configuration)
./gradlew :composeApp:assembleRelease

# Run tests
./gradlew :composeApp:testDebugUnitTest

# Static analysis
./gradlew spotlessCheck :composeApp:lintDebug
```

### iOS

Open the `/iosApp` directory in Xcode and run, or use the run configuration in your IDE.

## Processing Pipeline

1. **Capture** - Camera captures photo or imports from gallery
2. **Detection** - ML model detects face and extracts 478 landmarks
3. **Validation** - Confidence check ensures face quality
4. **Alignment** - Calculates affine transformation matrix
5. **Transform** - Warps and crops image to standardized position
6. **Storage** - Saves aligned image and metadata to database
7. **Compilation** - Hardware encoder assembles frames into video

## Testing

The project includes comprehensive test coverage:

- **Unit Tests**: Domain layer use cases, alignment algorithms
- **Integration Tests**: Repository implementations, database operations
- **ViewModel Tests**: State management, event/effect handling

```shell
# Run all tests
./gradlew test

# Run Android unit tests
./gradlew :composeApp:testDebugUnitTest
```

## Release Configuration

### Android Signing

Release builds require signing configuration via environment variables:

```shell
export KEYSTORE_FILE=/path/to/release.keystore
export KEYSTORE_PASSWORD=your_keystore_password
export KEY_ALIAS=framelapse
export KEY_PASSWORD=your_key_password
```

### Version Management

Version information is managed in `gradle.properties`:

```properties
VERSION_NAME=1.0.0
VERSION_CODE=1
```

## Documentation

- [Architecture Details](./Architecture.md) - Technical implementation details
- [Feature Research](./Daily_Selfie_Timelapse_Apps.md) - Comprehensive feature and technology analysis
- [Agent Definitions](./AGENTS.md) - Specialized AI agents for development tasks

## License

[Add license information]
