# FrameLapse

[![CI](https://github.com/po4yka/FrameLapse/actions/workflows/ci.yml/badge.svg)](https://github.com/po4yka/FrameLapse/actions/workflows/ci.yml)
[![CI - iOS](https://github.com/po4yka/FrameLapse/actions/workflows/ci-ios.yml/badge.svg)](https://github.com/po4yka/FrameLapse/actions/workflows/ci-ios.yml)

A Kotlin Multiplatform application for creating stabilized daily self-portrait timelapse videos. FrameLapse automatically aligns and compiles facial photographs to document physical transformations over time.

## Features

### Photo Capture & Import
- Built-in camera with ghost/guide photo overlay for consistent positioning
- Grid overlay for manual frame alignment
- Batch import from device gallery
- Photo sorting by date, filename, or custom order

### Automatic Face Alignment
- Facial landmark detection (478 3D landmarks)
- Affine transformation for eye position standardization
- Automatic scaling and rotation correction
- Confidence thresholds for quality assurance

### Video Compilation
- Adjustable frame rate (24, 30, 60 FPS)
- Multiple resolution options (480p to 4K)
- Hardware-accelerated encoding (H.264/HEVC)
- Export to MP4/MOV formats

### Project Management
- Multi-project support
- Frame reordering and deletion
- Color grading and filters
- Local-first storage (no cloud required)

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
│    (Repositories, SQLDelight, Okio)     │
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
│   ├── /commonMain     # Shared code for all platforms
│   ├── /androidMain    # Android-specific implementations
│   └── /iosMain        # iOS-specific implementations
/iosApp                 # iOS application entry point
```

## Building

### Prerequisites
- JDK 17+
- Android Studio (for Android development)
- Xcode 15+ (for iOS development)

### Android

```shell
# macOS/Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

Or use the run configuration in Android Studio.

### iOS

Open the `/iosApp` directory in Xcode and run, or use the run configuration in your IDE.

## Processing Pipeline

1. **Capture** - Camera captures photo or imports from gallery
2. **Detection** - ML model detects face and extracts 478 landmarks
3. **Alignment** - Calculates affine transformation matrix
4. **Transform** - Warps and crops image to standardized position
5. **Storage** - Saves aligned image and metadata to database
6. **Compilation** - Hardware encoder assembles frames into video

## Documentation

- [Architecture Details](./Architecture.md) - Technical implementation details
- [Feature Research](./Daily_Selfie_Timelapse_Apps.md) - Comprehensive feature and technology analysis

## License

[Add license information]
