# AGENTS.md - Specialized Agents for FrameLapse

This document defines specialized agents for working on the FrameLapse codebase.

## Agent Definitions

### face-alignment-agent

**Purpose:** Implement and optimize face detection and alignment algorithms.

**Expertise:**
- MediaPipe and ML Kit Face Detection APIs
- Apple Vision Framework for iOS
- Affine transformation mathematics
- Facial landmark processing (478 landmarks)
- Image warping and cropping algorithms

**When to use:**
- Implementing `ImageProcessor` expect/actual classes
- Optimizing face detection performance
- Fixing alignment drift or jitter issues
- Adding new alignment modes (multi-face, profile view)

**Key files:**
- `composeApp/src/commonMain/kotlin/domain/usecase/AlignFaceUseCase.kt`
- `composeApp/src/androidMain/kotlin/platform/FaceDetector.kt`
- `composeApp/src/iosMain/kotlin/platform/FaceDetector.kt`

---

### video-encoding-agent

**Purpose:** Handle video compilation and hardware-accelerated encoding.

**Expertise:**
- Android MediaCodec and MediaMuxer APIs
- iOS AVAssetWriter and AVAssetWriterInputPixelBufferAdaptor
- H.264/HEVC codec configuration
- Frame rate and resolution optimization
- Memory-efficient frame streaming

**When to use:**
- Implementing `VideoEncoder` expect/actual classes
- Optimizing encoding performance
- Adding new export formats (GIF, WebM)
- Fixing video quality or sync issues

**Key files:**
- `composeApp/src/commonMain/kotlin/domain/usecase/CompileVideoUseCase.kt`
- `composeApp/src/androidMain/kotlin/platform/VideoEncoder.kt`
- `composeApp/src/iosMain/kotlin/platform/VideoEncoder.kt`

---

### camera-integration-agent

**Purpose:** Implement camera capture and preview functionality.

**Expertise:**
- Android CameraX API and lifecycle management
- iOS AVFoundation and AVCaptureSession
- Camera permission handling
- Real-time preview with overlay compositing
- Image capture and format conversion

**When to use:**
- Implementing camera preview Composables
- Adding ghost image overlay functionality
- Handling camera permissions and lifecycle
- Fixing capture quality or orientation issues

**Key files:**
- `composeApp/src/commonMain/kotlin/ui/camera/CameraPreview.kt`
- `composeApp/src/androidMain/kotlin/platform/CameraCapture.kt`
- `composeApp/src/iosMain/kotlin/platform/CameraCapture.kt`

---

### compose-ui-agent

**Purpose:** Build and maintain the Compose Multiplatform UI layer.

**Expertise:**
- Jetpack Compose and Compose Multiplatform
- Material 3 design system
- State management with StateFlow
- Navigation (Voyager or Navigation Compose)
- Animations and transitions

**When to use:**
- Creating new screens and components
- Implementing timeline/gallery views
- Building settings and project management UI
- Fixing layout or theming issues

**Key files:**
- `composeApp/src/commonMain/kotlin/ui/**`
- `composeApp/src/commonMain/kotlin/presentation/**`

---

### database-agent

**Purpose:** Manage data persistence with SQLDelight.

**Expertise:**
- SQLDelight schema design and migrations
- Repository pattern implementation
- Coroutines Flow for reactive queries
- File system operations with Okio
- Data caching strategies

**When to use:**
- Designing database schema for projects/frames
- Implementing repository classes
- Adding data migration logic
- Optimizing query performance

**Key files:**
- `composeApp/src/commonMain/sqldelight/**`
- `composeApp/src/commonMain/kotlin/data/repository/**`
- `composeApp/src/commonMain/kotlin/data/local/**`

---

### kmp-platform-agent

**Purpose:** Handle Kotlin Multiplatform expect/actual patterns and platform interop.

**Expertise:**
- expect/actual declarations
- Kotlin/Native memory management
- Swift/Kotlin interoperability
- Platform-specific dependency injection
- Gradle KMP configuration

**When to use:**
- Setting up new platform abstractions
- Debugging platform-specific crashes
- Configuring Gradle for new dependencies
- Bridging iOS native code with Kotlin

**Key files:**
- `composeApp/build.gradle.kts`
- `composeApp/src/*/kotlin/platform/**`
- `composeApp/src/*/kotlin/di/**`

---

### testing-agent

**Purpose:** Write and maintain tests across all layers.

**Expertise:**
- Kotlin test frameworks (kotlin.test, JUnit)
- Compose UI testing
- Mocking with MockK
- Test fixtures and factories
- Platform-specific test configuration

**When to use:**
- Writing unit tests for use cases
- Creating integration tests for repositories
- Building UI tests for screens
- Setting up test infrastructure

**Key files:**
- `composeApp/src/commonTest/**`
- `composeApp/src/androidTest/**`
- `composeApp/src/iosTest/**`

---

## Agent Selection Guide

| Task | Recommended Agent |
|------|-------------------|
| Face detection not working | face-alignment-agent |
| Video export crashes | video-encoding-agent |
| Camera preview black screen | camera-integration-agent |
| UI layout broken | compose-ui-agent |
| Data not persisting | database-agent |
| Platform crash on iOS/Android | kmp-platform-agent |
| Need more test coverage | testing-agent |
| Alignment jittery/drifting | face-alignment-agent |
| Export taking too long | video-encoding-agent |
| Ghost overlay not showing | camera-integration-agent |

## Usage Notes

1. **Combine agents** when tasks span multiple domains (e.g., camera + face detection)
2. **Start with exploration** - agents should read relevant files before making changes
3. **Verify platform parity** - changes to shared code should work on both Android and iOS
4. **Test after changes** - always run relevant tests after modifications
